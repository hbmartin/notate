package com.alexdremov.notate.hwr

import android.content.Context
import com.alexdremov.notate.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Device-local, bounded recognition diagnostics. Deliberately stores no images, ink geometry,
 * notebook identity, or stroke IDs.
 */
class RecognitionDiagnosticsStore private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val lock = Any()

    suspend fun record(
        provider: HandwritingRecognitionProvider,
        candidates: List<RecognitionCandidate>,
        acceptedText: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (!PreferencesManager.isRecognitionDebugEnabled(appContext)) return@withContext
        val timestamp = System.currentTimeMillis()
        val payload =
            candidates.joinToString(separator = "\n", postfix = if (candidates.isEmpty()) "" else "\n") { candidate ->
                JSONObject()
                    .put("provider", provider.id)
                    .put("revision", provider.revision)
                    .put("time", timestamp)
                    .put("confidence", candidate.confidence ?: JSONObject.NULL)
                    .put("candidateText", candidate.text)
                    .put("outcome", if (candidate.text == acceptedText) "accepted" else "rejected")
                    .toString()
            }
        if (payload.isBlank()) return@withContext
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.appendText(payload)
            trimLocked()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (file.exists()) file.writeBytes(ByteArray(0))
        }
    }

    private fun trimLocked() {
        if (file.length() <= MAX_BYTES) return
        val lines = file.readLines()
        var retainedBytes = 0L
        val retained = ArrayDeque<String>()
        for (index in lines.indices.reversed()) {
            val line = lines[index]
            val bytes = line.toByteArray().size + 1L
            if (retainedBytes + bytes > RETAIN_AFTER_TRIM_BYTES) break
            retained.addFirst(line)
            retainedBytes += bytes
        }
        file.writeText(retained.joinToString(separator = "\n", postfix = if (retained.isEmpty()) "" else "\n"))
    }

    companion object {
        private const val FILE_NAME = "recognition-diagnostics.jsonl"
        private const val MAX_BYTES = 10L * 1024L * 1024L
        private const val RETAIN_AFTER_TRIM_BYTES = 9L * 1024L * 1024L

        @Volatile private var instance: RecognitionDiagnosticsStore? = null

        fun get(context: Context): RecognitionDiagnosticsStore =
            instance ?: synchronized(this) {
                instance ?: RecognitionDiagnosticsStore(context).also { instance = it }
            }
    }
}
