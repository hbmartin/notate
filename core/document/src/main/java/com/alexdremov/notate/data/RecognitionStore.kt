package com.alexdremov.notate.data

import android.graphics.Matrix
import com.alexdremov.notate.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Notebook-owned persistence for accepted handwriting transcriptions.
 *
 * Recognition is intentionally outside canvas undo/redo. Every mutating method writes the
 * versioned entry atomically so a later background notebook pack includes the same state.
 */
class RecognitionStore(
    sessionDir: File,
) {
    private val file = File(sessionDir, FILE_NAME)
    private val mutex = Mutex()
    private var cached: RecognitionDocument? = null

    suspend fun snapshot(): List<HandwritingLine> = mutex.withLock { loadLocked().lines.toList() }

    suspend fun replaceAll(lines: List<HandwritingLine>) =
        mutex.withLock {
            persistLocked(RecognitionDocument(lines = lines.distinctBy(HandwritingLine::id)))
        }

    suspend fun upsert(line: HandwritingLine) =
        mutex.withLock {
            val current = loadLocked().lines.toMutableList()
            val index = current.indexOfFirst { it.id == line.id }
            if (index >= 0) current[index] = line else current += line
            persistLocked(RecognitionDocument(lines = current))
        }

    suspend fun removeForStrokeIds(strokeIds: Set<String>): List<HandwritingLine> =
        mutex.withLock {
            if (strokeIds.isEmpty()) return@withLock loadLocked().lines
            val kept = loadLocked().lines.filterNot { line -> line.sourceStrokeIds.any(strokeIds::contains) }
            persistLocked(RecognitionDocument(lines = kept))
            kept
        }

    suspend fun markStaleForStrokeIds(strokeIds: Set<String>): List<HandwritingLine> =
        mutex.withLock {
            if (strokeIds.isEmpty()) return@withLock loadLocked().lines
            val updated =
                loadLocked().lines.map { line ->
                    if (line.sourceStrokeIds.any(strokeIds::contains)) {
                        line.copy(state = TranscriptionState.STALE)
                    } else {
                        line
                    }
                }
            persistLocked(RecognitionDocument(lines = updated))
            updated
        }

    /**
     * Whole-line transforms preserve validity; any line containing only part of the transformed
     * stroke set becomes stale.
     */
    suspend fun applyStrokeTransform(
        transformedStrokeIds: Set<String>,
        matrix: Matrix,
    ): List<HandwritingLine> =
        mutex.withLock {
            if (transformedStrokeIds.isEmpty()) return@withLock loadLocked().lines
            val updated =
                loadLocked().lines.map { line ->
                    val linked = line.sourceStrokeIds.toSet()
                    when {
                        linked.all(transformedStrokeIds::contains) ->
                            line.copy(geometry = line.geometry.transformed(matrix))
                        linked.any(transformedStrokeIds::contains) ->
                            line.copy(state = TranscriptionState.STALE)
                        else -> line
                    }
                }
            persistLocked(RecognitionDocument(lines = updated))
            updated
        }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadLocked(): RecognitionDocument {
        cached?.let { return it }
        val loaded =
            if (!file.exists()) {
                RecognitionDocument()
            } else {
                try {
                    ProtoBuf.decodeFromByteArray(RecognitionDocument.serializer(), file.readBytes())
                } catch (error: Exception) {
                    Logger.e(TAG, "Unable to read ${file.absolutePath}", error)
                    RecognitionDocument()
                }
            }
        cached = loaded
        return loaded
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun persistLocked(document: RecognitionDocument) {
        val bytes = ProtoBuf.encodeToByteArray(RecognitionDocument.serializer(), document)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            cached = document
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    companion object {
        const val FILE_NAME = "recognition.bin"
        private const val TAG = "RecognitionStore"
    }
}
