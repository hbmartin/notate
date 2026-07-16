package com.alexdremov.notate.ocr

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class OcrModelFile(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class OcrModelPackDescriptor(
    val id: String,
    val files: List<OcrModelFile>,
) {
    val totalBytes: Long = files.sumOf(OcrModelFile::sizeBytes)
}

object OcrModelPackCatalog {
    val current =
        OcrModelPackDescriptor(
            id = "ppocrv3-zh-en-mobile-v1",
            files =
                listOf(
                    OcrModelFile(
                        name = "det_db.nb",
                        url = "https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_det_slim_infer.nb",
                        sizeBytes = 1_092_190,
                        sha256 = "998632e7fc99a962a5012caaf76065ce8260e6500996b63364f243ee41b34093",
                    ),
                    OcrModelFile(
                        name = "rec_crnn.nb",
                        url = "https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_rec_infer.nb",
                        sizeBytes = 10_786_246,
                        sha256 = "6280cd7a336390c4c2f31f5688f4621052c450f8f3cb8e35cca7222aa47efad2",
                    ),
                    OcrModelFile(
                        name = "ppocr_keys_v1.txt",
                        url =
                            "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/" +
                                "211989f046cc1878460f9e65574690c00a127a1a/ppocr/utils/ppocr_keys_v1.txt",
                        sizeBytes = 26_250,
                        sha256 = "a1c84d9bdb9ab29043c58896224d32941783eb821629618416dcb08f12886492",
                    ),
                ),
        )
}

sealed interface OcrModelPackState {
    data object Checking : OcrModelPackState

    data object NotInstalled : OcrModelPackState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : OcrModelPackState {
        val progress: Float = if (totalBytes == 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data class Ready(
        val packId: String,
        val installedBytes: Long,
    ) : OcrModelPackState

    data class Failed(
        val message: String,
    ) : OcrModelPackState
}

class OcrModelsNotInstalledException(
    message: String = "Text recognition files have not been downloaded",
) : IllegalStateException(message)

class OcrModelPackManager private constructor(
    context: Context,
    fetcher: OcrModelFileFetcher = HttpOcrModelFileFetcher(),
) {
    private val appContext = context.applicationContext
    private val pack = OcrModelPackCatalog.current
    private val installer =
        OcrModelPackInstaller(
            currentDirectory = File(appContext.filesDir, "ocr/model-packs/${pack.id}"),
            stagingDirectory = File(appContext.filesDir, "ocr/model-packs/${pack.id}.downloading"),
            legacyDirectory = File(appContext.filesDir, "ocr/ppocrv3"),
            pack = pack,
            fetcher = fetcher,
        )
    private val mutex = Mutex()
    private val _state =
        MutableStateFlow<OcrModelPackState>(
            if (installer.quickInstalledDirectory() == null) {
                OcrModelPackState.NotInstalled
            } else {
                OcrModelPackState.Ready(pack.id, pack.totalBytes)
            },
        )

    val state: StateFlow<OcrModelPackState> = _state.asStateFlow()
    val downloadSizeBytes: Long = pack.totalBytes

    fun isInstalled(): Boolean = installer.quickInstalledDirectory() != null

    suspend fun refresh(): OcrModelPackState =
        mutex.withLock {
            _state.value = OcrModelPackState.Checking
            val installed = withContext(Dispatchers.IO) { installer.verifiedInstalledDirectory() }
            val refreshed =
                if (installed == null) {
                    OcrModelPackState.NotInstalled
                } else {
                    OcrModelPackState.Ready(pack.id, pack.totalBytes)
                }
            _state.value = refreshed
            refreshed
        }

    suspend fun install(): File =
        mutex.withLock {
            try {
                _state.value = OcrModelPackState.Downloading(0L, pack.totalBytes)
                val installed =
                    withContext(Dispatchers.IO) {
                        installer.install { downloaded, total ->
                            _state.value = OcrModelPackState.Downloading(downloaded, total)
                        }
                    }
                PaddleOcrProvider.reset()
                _state.value = OcrModelPackState.Ready(pack.id, pack.totalBytes)
                installed
            } catch (cancelled: CancellationException) {
                _state.value = OcrModelPackState.NotInstalled
                throw cancelled
            } catch (error: Throwable) {
                _state.value = OcrModelPackState.Failed(error.message ?: "Unable to download text recognition files")
                throw error
            }
        }

    suspend fun remove() {
        mutex.withLock {
            withContext(Dispatchers.IO) { installer.remove() }
            PaddleOcrProvider.reset()
            _state.value = OcrModelPackState.NotInstalled
        }
    }

    internal fun installedDirectory(): File? = installer.quickInstalledDirectory()

    companion object {
        @Volatile private var instance: OcrModelPackManager? = null

        fun get(context: Context): OcrModelPackManager =
            instance ?: synchronized(this) {
                instance ?: OcrModelPackManager(context).also { instance = it }
            }
    }
}

internal interface OcrModelFileFetcher {
    suspend fun fetch(
        model: OcrModelFile,
        destination: File,
        onProgress: (Long) -> Unit,
    )
}

internal class HttpOcrModelFileFetcher : OcrModelFileFetcher {
    override suspend fun fetch(
        model: OcrModelFile,
        destination: File,
        onProgress: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) { "Unable to create OCR download directory" }
        }
        if (destination.length() > model.sizeBytes) destination.delete()
        if (destination.length() == model.sizeBytes) {
            onProgress(model.sizeBytes)
            return@withContext
        }

        var resumeAt = destination.length()
        repeat(2) { attempt ->
            currentCoroutineContext().ensureActive()
            val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "Notate-PP-OCRv3/${OcrModelPackCatalog.current.id}")
                if (resumeAt > 0L) setRequestProperty("Range", "bytes=$resumeAt-")
            }
            try {
                val response = connection.responseCode
                if (response == HTTP_RANGE_NOT_SATISFIABLE && resumeAt > 0L && attempt == 0) {
                    check(destination.delete()) { "Unable to restart OCR model download" }
                    resumeAt = 0L
                    return@repeat
                }
                if (response !in 200..299) throw IOException("OCR model download returned HTTP $response")
                if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                    throw IOException("OCR model download redirected to a non-HTTPS URL")
                }

                val append = resumeAt > 0L && response == HttpURLConnection.HTTP_PARTIAL
                if (!append && destination.exists()) {
                    check(destination.delete()) { "Unable to replace partial OCR model download" }
                    resumeAt = 0L
                }
                var downloaded = if (append) resumeAt else 0L
                onProgress(downloaded)
                connection.inputStream.buffered().use { input ->
                    FileOutputStream(destination, append).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            if (downloaded > model.sizeBytes) throw IOException("OCR model download exceeded expected size: ${model.name}")
                            output.write(buffer, 0, count)
                            onProgress(downloaded)
                        }
                        output.fd.sync()
                    }
                }
                if (downloaded != model.sizeBytes) {
                    throw IOException("OCR model download was incomplete: ${model.name}")
                }
                return@withContext
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Unable to resume OCR model download: ${model.name}")
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}

internal class OcrModelPackInstaller(
    private val currentDirectory: File,
    private val stagingDirectory: File,
    private val legacyDirectory: File?,
    private val pack: OcrModelPackDescriptor,
    private val fetcher: OcrModelFileFetcher,
) {
    fun quickInstalledDirectory(): File? = candidateDirectories().firstOrNull(::hasExpectedFiles)

    fun verifiedInstalledDirectory(): File? = candidateDirectories().firstOrNull(::hasVerifiedFiles)

    suspend fun install(onProgress: (Long, Long) -> Unit): File {
        verifiedInstalledDirectory()?.let {
            onProgress(pack.totalBytes, pack.totalBytes)
            return it
        }
        check(stagingDirectory.isDirectory || stagingDirectory.mkdirs()) { "Unable to create OCR staging directory" }

        var completedBytes = 0L
        pack.files.forEach { model ->
            val installedFile = File(stagingDirectory, model.name)
            if (installedFile.isFile && verifyFile(installedFile, model)) {
                completedBytes += model.sizeBytes
                onProgress(completedBytes, pack.totalBytes)
                return@forEach
            }
            if (installedFile.exists()) installedFile.delete()

            val partialFile = File(stagingDirectory, "${model.name}.part")
            fetcher.fetch(model, partialFile) { fileBytes ->
                onProgress(completedBytes + fileBytes, pack.totalBytes)
            }
            if (!verifyFile(partialFile, model)) {
                partialFile.delete()
                throw IOException("OCR model checksum failed: ${model.name}")
            }
            check(partialFile.renameTo(installedFile)) { "Unable to finalize OCR model: ${model.name}" }
            completedBytes += model.sizeBytes
            onProgress(completedBytes, pack.totalBytes)
        }

        check(hasVerifiedFiles(stagingDirectory)) { "Downloaded OCR model pack did not verify" }
        if (currentDirectory.exists()) check(currentDirectory.deleteRecursively()) { "Unable to replace OCR model pack" }
        currentDirectory.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) { "Unable to create OCR model directory" }
        }
        check(stagingDirectory.renameTo(currentDirectory)) { "Unable to activate OCR model pack" }
        return currentDirectory
    }

    fun remove() {
        listOfNotNull(currentDirectory, stagingDirectory, legacyDirectory).forEach { directory ->
            if (directory.exists()) check(directory.deleteRecursively()) { "Unable to remove OCR model files" }
        }
    }

    private fun candidateDirectories(): List<File> = listOfNotNull(currentDirectory, legacyDirectory)

    private fun hasExpectedFiles(directory: File): Boolean =
        directory.isDirectory && pack.files.all { model -> File(directory, model.name).let { it.isFile && it.length() == model.sizeBytes } }

    private fun hasVerifiedFiles(directory: File): Boolean = hasExpectedFiles(directory) && pack.files.all { verifyFile(File(directory, it.name), it) }

    private fun verifyFile(
        file: File,
        model: OcrModelFile,
    ): Boolean = file.isFile && file.length() == model.sizeBytes && sha256(file) == model.sha256

    companion object {
        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
