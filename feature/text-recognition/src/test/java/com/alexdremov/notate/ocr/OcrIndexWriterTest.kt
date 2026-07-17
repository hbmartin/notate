@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.alexdremov.notate.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alexdremov.notate.data.CanvasData
import com.alexdremov.notate.data.RegionProto
import com.alexdremov.notate.data.StrokeData
import com.alexdremov.notate.model.StrokeType
import com.alexdremov.notate.ocr.index.OcrBlockEntity
import com.alexdremov.notate.ocr.index.OcrDocumentEntity
import com.alexdremov.notate.ocr.index.OcrIndexDatabase
import com.alexdremov.notate.ocr.index.OcrIndexWriter
import com.alexdremov.notate.ocr.index.OcrSearchNormalizer
import com.alexdremov.notate.ocr.index.OcrSearchRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class OcrIndexWriterTest {
    private lateinit var context: Context
    private lateinit var database: OcrIndexDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, OcrIndexDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ftsStoresChineseSubstringTokensForVerifiedQueries() = runTest {
        val dao = database.dao()
        dao.upsertDocument(OcrDocumentEntity("doc", "project", "/note.notate", "Note", 1, "v", "INDEXED"))
        val text = "这是手写笔记"
        dao.insertBlocks(
            listOf(
                OcrBlockEntity(
                    stableId = "block",
                    documentId = "doc",
                    regionId = "r_0_0",
                    regionHash = "hash",
                    source = OcrTextSource.INK_OCR.name,
                    text = text,
                    normalizedText = OcrSearchNormalizer.normalize(text),
                    searchTokens = OcrSearchNormalizer.searchableTokens(text),
                    confidence = 0.9f,
                    left = 0f,
                    top = 0f,
                    right = 10f,
                    bottom = 10f,
                ),
            ),
        )
        assertThat(dao.getFtsRows()).hasSize(1)
        assertThat(dao.getFtsRows().single().searchTokens).contains("z51997b14")
        assertThat(OcrSearchNormalizer.ftsQuery("写笔")).contains("z51997b14")
    }

    @Test
    fun missingModelIndexesAvailableContentWithoutRerunningInkRecognition() = runTest {
        val dao = database.dao()
        val documentId = "doc-stale"
        dao.upsertDocument(OcrDocumentEntity(documentId, null, "/note.notate", "Note", 1, "old", "INDEXED"))
        dao.insertBlocks(
            listOf(
                OcrBlockEntity(
                    stableId = "old-block",
                    documentId = documentId,
                    regionId = "r_0_0",
                    regionHash = "old-hash",
                    source = OcrTextSource.INK_OCR.name,
                    text = "previous text",
                    normalizedText = "previous text",
                    searchTokens = "previous text",
                    confidence = 0.8f,
                    left = 1f,
                    top = 2f,
                    right = 3f,
                    bottom = 4f,
                ),
            ),
        )

        val session = File(context.cacheDir, "ocr-stale-test").apply { deleteRecursively(); mkdirs() }
        File(session, "manifest.bin").writeBytes(ProtoBuf.encodeToByteArray(CanvasData.serializer(), CanvasData(uuid = documentId)))
        val stroke =
            StrokeData(
                pointsPacked = floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 10f, 10f, 1f, 1f, 0f, 0f),
                timestampsPacked = longArrayOf(1, 2),
                color = android.graphics.Color.BLACK,
                width = 2f,
                style = StrokeType.BALLPOINT,
            )
        File(session, "r_0_0.bin").writeBytes(
            ProtoBuf.encodeToByteArray(RegionProto.serializer(), RegionProto(0, 0, strokes = listOf(stroke))),
        )

        val outcome = OcrIndexWriter(context, UnavailableEngine, dao).indexSession(session, "/note.notate", 2)
        assertThat(outcome.staleRegions).isEqualTo(0)
        assertThat(dao.getBlocksForRegion(documentId, "r_0_0")).isEmpty()
        assertThat(dao.getDocument(documentId)?.status).isEqualTo("INDEXED")
        session.deleteRecursively()
    }

    @Test
    fun emptyRegionsPersistTheirHashAndAreSkippedOnTheNextPass() = runTest {
        val dao = database.dao()
        val documentId = "doc-empty"
        val session = File(context.cacheDir, "ocr-empty-test").apply { deleteRecursively(); mkdirs() }
        File(session, "manifest.bin").writeBytes(
            ProtoBuf.encodeToByteArray(CanvasData.serializer(), CanvasData(uuid = documentId)),
        )
        File(session, "r_0_0.bin").writeBytes(
            ProtoBuf.encodeToByteArray(RegionProto.serializer(), RegionProto(0, 0)),
        )
        val writer = OcrIndexWriter(context, UnavailableEngine, dao)

        val first = writer.indexSession(session, "/empty.notate", 1)
        val storedHash = dao.getRegionHash(documentId, "r_0_0")
        val second = writer.indexSession(session, "/empty.notate", 1)

        assertThat(first.indexedRegions).isEqualTo(1)
        assertThat(storedHash).isNotNull()
        assertThat(dao.getBlocksForRegion(documentId, "r_0_0")).isEmpty()
        assertThat(second.unchangedRegions).isEqualTo(1)
        assertThat(second.indexedRegions).isEqualTo(0)
        session.deleteRecursively()
    }

    @Test
    fun mixedAndSpacedCjkQueryMatchesIndependentSegments() = runTest {
        val dao = database.dao()
        dao.upsertDocument(OcrDocumentEntity("mixed", "project", "/mixed.notate", "Mixed", 1, "v", "INDEXED"))
        val text = "中文 note"
        dao.insertBlocks(
            listOf(
                OcrBlockEntity(
                    stableId = "mixed-block",
                    documentId = "mixed",
                    regionId = "r_0_0",
                    regionHash = "hash",
                    source = OcrTextSource.INK_OCR.name,
                    text = text,
                    normalizedText = OcrSearchNormalizer.normalize(text),
                    searchTokens = OcrSearchNormalizer.searchableTokens(text),
                    confidence = 0.9f,
                    left = 0f,
                    top = 0f,
                    right = 10f,
                    bottom = 10f,
                ),
            ),
        )

        assertThat(dao.search(OcrSearchNormalizer.ftsQuery("note 中 文"), 10)).isNotEmpty()
        assertThat(OcrSearchRepository(dao).search("note 中 文").map { it.documentId }).containsExactly("mixed")
    }

    @Test
    fun documentRekeyAtTheSamePathRemovesOldBlocksAndFtsRows() = runTest {
        val dao = database.dao()
        dao.upsertDocument(OcrDocumentEntity("old-id", null, "/same.notate", "Same", 1, "v", "INDEXED"))
        dao.replaceRegion(
            "old-id",
            "r_0_0",
            "hash",
            listOf(
                OcrBlockEntity(
                    stableId = "old-block",
                    documentId = "old-id",
                    regionId = "r_0_0",
                    regionHash = "hash",
                    source = OcrTextSource.INK_OCR.name,
                    text = "orphan candidate",
                    normalizedText = "orphan candidate",
                    searchTokens = "orphan candidate",
                    confidence = 1f,
                    left = null,
                    top = null,
                    right = null,
                    bottom = null,
                ),
            ),
        )

        dao.upsertDocumentSafely(OcrDocumentEntity("new-id", null, "/same.notate", "Same", 2, "v", "INDEXED"))

        assertThat(dao.getDocument("old-id")).isNull()
        assertThat(dao.getBlocksForRegion("old-id", "r_0_0")).isEmpty()
        assertThat(dao.getFtsRows()).isEmpty()
        assertThat(dao.getDocument("new-id")).isNotNull()
    }

    @Test
    fun interruptedAndRepeatedlyFailingDocumentsReachATerminalState() = runTest {
        val dao = database.dao()
        dao.upsertDocument(OcrDocumentEntity("poison", null, "/poison.notate", "Poison", 1, "v", "INDEXING"))

        dao.repairInterruptedDocuments()
        repeat(3) { dao.recordIndexFailure("poison", "bad region", 3) }

        assertThat(dao.getDocument("poison")?.status).isEqualTo("FAILED")
        assertThat(dao.getDocument("poison")?.failureCount).isEqualTo(3)
    }

    private object UnavailableEngine : PaddleOcrEngine {
        override val modelInfo = OcrModelInfo()
        override suspend fun recognize(bitmap: Bitmap): List<OcrBlock> = error("Model missing")
        override fun isAvailable(): Boolean = false
        override fun close() = Unit
    }
}
