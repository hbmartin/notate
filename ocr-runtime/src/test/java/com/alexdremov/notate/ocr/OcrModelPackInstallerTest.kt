package com.alexdremov.notate.ocr

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

class OcrModelPackInstallerTest {
    private lateinit var root: File
    private lateinit var current: File
    private lateinit var staging: File
    private lateinit var legacy: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("notate-ocr-pack").toFile()
        current = File(root, "current")
        staging = File(root, "staging")
        legacy = File(root, "legacy")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun installResumesPartialFilesAndAtomicallyActivatesPack() = runTest {
        val content = mapOf("det.nb" to "detector".toByteArray(), "rec.nb" to "recognizer".toByteArray())
        val pack = descriptor(content)
        staging.mkdirs()
        File(staging, "det.nb.part").writeBytes(content.getValue("det.nb").copyOfRange(0, 3))
        val fetcher = RecordingFetcher(content)
        val progress = mutableListOf<Pair<Long, Long>>()
        val installer = installer(pack, fetcher)

        val installed = installer.install { downloaded, total -> progress += downloaded to total }

        assertThat(installed).isEqualTo(current)
        assertThat(staging.exists()).isFalse()
        assertThat(File(current, "det.nb").readBytes()).isEqualTo(content.getValue("det.nb"))
        assertThat(File(current, "rec.nb").readBytes()).isEqualTo(content.getValue("rec.nb"))
        assertThat(fetcher.resumeOffsets.getValue("det.nb")).isEqualTo(3L)
        assertThat(progress.last()).isEqualTo(pack.totalBytes to pack.totalBytes)
        assertThat(installer.verifiedInstalledDirectory()).isEqualTo(current)
    }

    @Test
    fun validLegacyInstallDoesNotDownloadAgain() = runTest {
        val content = mapOf("model.nb" to "legacy-model".toByteArray())
        val pack = descriptor(content)
        legacy.mkdirs()
        File(legacy, "model.nb").writeBytes(content.getValue("model.nb"))
        val fetcher = RecordingFetcher(content)
        val installer = installer(pack, fetcher)

        val installed = installer.install { _, _ -> }

        assertThat(installed).isEqualTo(legacy)
        assertThat(fetcher.resumeOffsets).isEmpty()
    }

    @Test
    fun checksumFailureDoesNotActivateCorruptPack() = runTest {
        val expected = mapOf("model.nb" to "expected".toByteArray())
        val corrupt = mapOf("model.nb" to "corrupt!".toByteArray())
        val installer = installer(descriptor(expected), RecordingFetcher(corrupt))

        val error = runCatching { installer.install { _, _ -> } }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(current.exists()).isFalse()
        assertThat(File(staging, "model.nb.part").exists()).isFalse()
    }

    @Test
    fun removeDeletesCurrentPartialAndLegacyCopies() {
        listOf(current, staging, legacy).forEach { directory ->
            directory.mkdirs()
            File(directory, "file").writeText("data")
        }
        val installer = installer(descriptor(mapOf("file" to "data".toByteArray())), RecordingFetcher(emptyMap()))

        installer.remove()

        assertThat(current.exists()).isFalse()
        assertThat(staging.exists()).isFalse()
        assertThat(legacy.exists()).isFalse()
    }

    private fun installer(
        pack: OcrModelPackDescriptor,
        fetcher: OcrModelFileFetcher,
    ) = OcrModelPackInstaller(current, staging, legacy, pack, fetcher)

    private fun descriptor(content: Map<String, ByteArray>) =
        OcrModelPackDescriptor(
            id = "test-pack",
            files =
                content.map { (name, bytes) ->
                    OcrModelFile(name, "https://example.invalid/$name", bytes.size.toLong(), sha256(bytes))
                },
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class RecordingFetcher(
        private val content: Map<String, ByteArray>,
    ) : OcrModelFileFetcher {
        val resumeOffsets = mutableMapOf<String, Long>()

        override suspend fun fetch(
            model: OcrModelFile,
            destination: File,
            onProgress: (Long) -> Unit,
        ) {
            val bytes = content.getValue(model.name)
            val offset = destination.length()
            resumeOffsets[model.name] = offset
            destination.appendBytes(bytes.copyOfRange(offset.toInt(), bytes.size))
            onProgress(destination.length())
        }
    }
}
