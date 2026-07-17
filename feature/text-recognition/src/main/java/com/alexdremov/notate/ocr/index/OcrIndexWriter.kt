package com.alexdremov.notate.ocr.index

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.alexdremov.notate.data.CanvasData
import com.alexdremov.notate.data.CanvasSerializer
import com.alexdremov.notate.data.PathRelations
import com.alexdremov.notate.data.PreferencesManager
import com.alexdremov.notate.data.RecognitionDocument
import com.alexdremov.notate.data.RecognitionStore
import com.alexdremov.notate.data.RegionProto
import com.alexdremov.notate.data.TranscriptionState
import com.alexdremov.notate.model.Stroke
import com.alexdremov.notate.ocr.OcrBlock
import com.alexdremov.notate.ocr.OcrModelInfo
import com.alexdremov.notate.ocr.OcrTextSource
import com.alexdremov.notate.ocr.PaddleOcrEngine
import com.alexdremov.notate.ocr.PaddleOcrProvider
import com.alexdremov.notate.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.security.MessageDigest

data class OcrIndexOutcome(
    val documentId: String,
    val indexedRegions: Int,
    val unchangedRegions: Int,
    val staleRegions: Int,
)

class OcrIndexWriter(
    context: Context,
    private val engine: PaddleOcrEngine = PaddleOcrProvider.get(context),
    private val dao: OcrIndexDao = OcrIndexDatabase.get(context).dao(),
) {
    private val appContext = context.applicationContext

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun indexSession(
        sessionDir: File,
        targetPath: String,
        lastModified: Long = System.currentTimeMillis(),
        shouldStop: () -> Boolean = { false },
    ): OcrIndexOutcome =
        OcrIndexCoordinator.withDocumentLock(targetPath) {
            indexSessionLocked(sessionDir, targetPath, lastModified, shouldStop)
        }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun indexSessionLocked(
        sessionDir: File,
        targetPath: String,
        lastModified: Long,
        shouldStop: () -> Boolean,
    ): OcrIndexOutcome {
        if (shouldStop()) throw CancellationException("OCR indexing stopped")
        val manifestFile = File(sessionDir, "manifest.bin")
        check(manifestFile.isFile) { "Missing canvas manifest" }
        val manifest = ProtoBuf.decodeFromByteArray(CanvasData.serializer(), manifestFile.readBytes())
        val documentId = manifest.uuid ?: sha256(targetPath.toByteArray())
        val name = noteName(targetPath)
        val projectId = projectIdFor(targetPath)
        val modelVersion = engine.modelInfo.indexVersion
        dao.getDocumentByPath(targetPath)?.let { current ->
            if (current.lastModified > lastModified && current.modelVersion == modelVersion) {
                return OcrIndexOutcome(documentId, 0, dao.getRegionStateIds(current.documentId).size, 0)
            }
        }
        val previous = dao.getDocument(documentId)
        val inheritedFailures =
            if (previous?.lastModified == lastModified && previous.modelVersion == modelVersion) previous.failureCount else 0

        dao.upsertDocumentSafely(
            OcrDocumentEntity(
                documentId = documentId,
                projectId = projectId,
                path = targetPath,
                name = name,
                lastModified = lastModified,
                modelVersion = modelVersion,
                status = STATUS_INDEXING,
                indexedAt = previous?.indexedAt ?: 0L,
                failureCount = inheritedFailures,
            ),
        )

        replaceFilename(documentId, name, modelVersion)
        var indexed = 0
        var unchanged = 0
        var stale = 0
        val liveRegionIds = mutableSetOf(FILENAME_REGION)

        File(sessionDir, RecognitionStore.FILE_NAME).takeIf(File::isFile)?.let { recognitionFile ->
            liveRegionIds += RECOGNITION_REGION
            val recognitionBytes = recognitionFile.readBytes()
            val recognitionHash = sha256(recognitionBytes, RECOGNITION_INDEX_REVISION.toByteArray())
            if (dao.getRegionHash(documentId, RECOGNITION_REGION) == recognitionHash) {
                unchanged++
            } else {
                val recognition =
                    ProtoBuf.decodeFromByteArray(RecognitionDocument.serializer(), recognitionBytes)
                val blocks =
                    recognition.lines.mapIndexed { index, line ->
                        val source =
                            if (line.state == TranscriptionState.STALE) {
                                OcrTextSource.STALE_HANDWRITING
                            } else {
                                OcrTextSource.ACCEPTED_HANDWRITING
                            }
                        block(
                            documentId = documentId,
                            regionId = RECOGNITION_REGION,
                            regionHash = recognitionHash,
                            source = source,
                            text = line.acceptedText,
                            confidence = line.confidence ?: 1f,
                            bounds = line.geometry.toRectF(),
                            index = index,
                        )
                    }
                dao.replaceRegion(documentId, RECOGNITION_REGION, recognitionHash, blocks)
                indexed++
            }
        }

        sessionDir.listFiles { file -> file.isFile && REGION_FILE.matches(file.name) }
            .orEmpty()
            .sortedBy(File::getName)
            .forEach { file ->
                if (shouldStop()) throw CancellationException("OCR indexing stopped")
                val regionId = file.nameWithoutExtension
                liveRegionIds += regionId
                val bytes = runCatching { file.readBytes() }
                    .getOrElse { error ->
                        stale++
                        Logger.e("OcrIndex", "Unable to read $regionId", error)
                        return@forEach
                    }
                val regionHash = sha256(bytes, modelVersion.toByteArray())
                if (dao.getRegionHash(documentId, regionId) == regionHash) {
                    unchanged++
                    return@forEach
                }

                val region = runCatching {
                    ProtoBuf.decodeFromByteArray(RegionProto.serializer(), bytes)
                }.getOrElse { error ->
                    stale++
                    Logger.e("OcrIndex", "Unable to decode $regionId", error)
                    return@forEach
                }

                val blocks = runCatching { buildRegionBlocks(documentId, regionId, regionHash, region) }
                    .getOrElse { error ->
                        stale++
                        Logger.e("OcrIndex", "OCR failed for $regionId; retaining previous rows", error)
                        return@forEach
                    }
                if (shouldStop()) throw CancellationException("OCR indexing stopped")
                dao.replaceRegion(documentId, regionId, regionHash, blocks)
                indexed++
            }

        if (shouldStop()) throw CancellationException("OCR indexing stopped")
        if (stale == 0) {
            (dao.getRegionStateIds(documentId).toSet() - liveRegionIds).forEach { dao.deleteRegion(documentId, it) }
        }
        dao.upsertDocumentSafely(
            OcrDocumentEntity(
                documentId = documentId,
                projectId = projectId,
                path = targetPath,
                name = name,
                lastModified = lastModified,
                modelVersion = modelVersion,
                status = if (stale == 0) STATUS_INDEXED else STATUS_STALE,
                errorMessage = if (stale == 0) null else "$stale region(s) retained from the previous index",
                indexedAt = System.currentTimeMillis(),
                failureCount = if (stale == 0) 0 else inheritedFailures,
            ),
        )
        return OcrIndexOutcome(documentId, indexed, unchanged, stale)
    }

    private suspend fun replaceFilename(
        documentId: String,
        name: String,
        modelVersion: String,
    ) {
        val hash = sha256(name.toByteArray(), modelVersion.toByteArray())
        if (dao.getRegionHash(documentId, FILENAME_REGION) == hash) return
        dao.replaceRegion(
            documentId,
            FILENAME_REGION,
            hash,
            listOf(block(documentId, FILENAME_REGION, hash, OcrTextSource.FILENAME, name, 1f, null, 0)),
        )
    }

    private suspend fun buildRegionBlocks(
        documentId: String,
        regionId: String,
        regionHash: String,
        region: RegionProto,
    ): List<OcrBlockEntity> {
        val output = mutableListOf<OcrBlockEntity>()
        region.texts.forEachIndexed { index, text ->
            if (text.text.isNotBlank()) {
                output += block(
                    documentId,
                    regionId,
                    regionHash,
                    OcrTextSource.TYPED_TEXT,
                    text.text,
                    1f,
                    RectF(text.x, text.y, text.x + text.width, text.y + text.height),
                    index,
                )
            }
        }

        // Accepted Transcriptions above are authoritative. Raw OCR remains a best-effort
        // supplement for ink which has not yet been reviewed and stored.
        if (region.strokes.isNotEmpty() && engine.isAvailable()) {
            val strokes = region.strokes.map(CanvasSerializer::fromStrokeData)
            try {
                val recognized = OcrTiledRecognizer.recognize(strokes, engine)
                recognized.forEachIndexed { index, recognizedBlock ->
                    output += block(
                        documentId,
                        regionId,
                        regionHash,
                        OcrTextSource.INK_OCR,
                        recognizedBlock.text,
                        recognizedBlock.confidence,
                        recognizedBlock.bounds,
                        index,
                    )
                }
            } finally {
                strokes.forEach(Stroke::recycle)
            }
        }
        return output
    }

    private fun block(
        documentId: String,
        regionId: String,
        regionHash: String,
        source: OcrTextSource,
        text: String,
        confidence: Float,
        bounds: RectF?,
        index: Int,
    ): OcrBlockEntity {
        val normalized = OcrSearchNormalizer.normalize(text)
        val stableId = sha256("$documentId|$regionId|${source.name}|$index|$normalized".toByteArray())
        return OcrBlockEntity(
            stableId = stableId,
            documentId = documentId,
            regionId = regionId,
            regionHash = regionHash,
            source = source.name,
            text = text,
            normalizedText = normalized,
            searchTokens = OcrSearchNormalizer.searchableTokens(text),
            confidence = confidence,
            left = bounds?.left,
            top = bounds?.top,
            right = bounds?.right,
            bottom = bounds?.bottom,
        )
    }

    private fun projectIdFor(path: String): String? {
        val projects = PreferencesManager.getProjects(appContext)
        return projects.filter { PathRelations.contains(it.uri, path) }.maxByOrNull { it.uri.length }?.id
    }

    private fun noteName(path: String): String {
        val raw =
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                DocumentFile.fromSingleUri(appContext, uri)?.name ?: uri.lastPathSegment?.substringAfterLast(':')
            } else {
                File(path).name
            }
        return raw.orEmpty().substringAfterLast('/').removeSuffix(".notate").ifBlank { "Untitled" }
    }

    private fun sha256(vararg parts: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach(digest::update)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val REGION_FILE = Regex("r_-?\\d+_-?\\d+\\.bin")
        private const val FILENAME_REGION = "__filename__"
        private const val RECOGNITION_REGION = "__accepted_handwriting__"
        private const val RECOGNITION_INDEX_REVISION = "accepted-handwriting-v1"
        const val STATUS_INDEXING = "INDEXING"
        const val STATUS_INDEXED = "INDEXED"
        const val STATUS_STALE = "STALE"
        const val STATUS_FAILED = "FAILED"
    }
}

private object OcrIndexCoordinator {
    private val locks = Array(64) { Mutex() }

    suspend fun <T> withDocumentLock(
        path: String,
        block: suspend () -> T,
    ): T = locks[Math.floorMod(path.hashCode(), locks.size)].withLock { block() }
}

object OcrBlockDeduplicator {
    fun deduplicate(blocks: List<OcrBlock>): List<OcrBlock> {
        val ordered = blocks.sortedByDescending(OcrBlock::confidence).map { it to OcrSearchNormalizer.normalize(it.text) }
        val kept = mutableListOf<Pair<OcrBlock, String>>()
        ordered.forEach { candidate ->
            val duplicate = kept.any { existing ->
                nearEquivalent(existing.second, candidate.second) &&
                    intersectionOverUnion(existing.first.bounds, candidate.first.bounds) >= 0.35f
            }
            if (!duplicate) kept += candidate
        }
        return kept.map { it.first }.sortedWith(compareBy<OcrBlock> { it.bounds.top }.thenBy { it.bounds.left })
    }

    private fun nearEquivalent(
        first: String,
        second: String,
    ): Boolean {
        if (first == second) return true
        val firstPoints = first.codePoints().toArray()
        val secondPoints = second.codePoints().toArray()
        val longest = maxOf(firstPoints.size, secondPoints.size)
        if (longest < 4) return false
        val allowed = maxOf(1, longest / 5)
        if (kotlin.math.abs(firstPoints.size - secondPoints.size) > allowed) return false
        return editDistanceAtMost(firstPoints, secondPoints, allowed)
    }

    private fun editDistanceAtMost(
        first: IntArray,
        second: IntArray,
        limit: Int,
    ): Boolean {
        var previous = IntArray(second.size + 1) { it }
        first.forEachIndexed { firstIndex, firstCodePoint ->
            val current = IntArray(second.size + 1)
            current[0] = firstIndex + 1
            var rowMinimum = current[0]
            second.forEachIndexed { secondIndex, secondCodePoint ->
                current[secondIndex + 1] =
                    minOf(
                        current[secondIndex] + 1,
                        previous[secondIndex + 1] + 1,
                        previous[secondIndex] + if (firstCodePoint == secondCodePoint) 0 else 1,
                    )
                rowMinimum = minOf(rowMinimum, current[secondIndex + 1])
            }
            if (rowMinimum > limit) return false
            previous = current
        }
        return previous.last() <= limit
    }

    internal fun intersectionOverUnion(first: RectF, second: RectF): Float {
        val intersection = RectF(first)
        if (!intersection.intersect(second)) return 0f
        val intersectionArea = intersection.width() * intersection.height()
        val unionArea = first.width() * first.height() + second.width() * second.height() - intersectionArea
        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }
}
