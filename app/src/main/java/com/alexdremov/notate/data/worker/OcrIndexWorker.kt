package com.alexdremov.notate.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alexdremov.notate.data.PreferencesManager
import com.alexdremov.notate.ocr.OcrModelPackManager
import com.alexdremov.notate.ocr.index.OcrIndexDatabase
import com.alexdremov.notate.ocr.index.OcrIndexWriter
import com.alexdremov.notate.util.Logger
import kotlinx.coroutines.CancellationException
import java.io.File

class OcrIndexWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val sessionPath = inputData.getString(KEY_SESSION_PATH) ?: return Result.failure()
        val cleanupSession = inputData.getBoolean(KEY_CLEANUP_SESSION, false)
        val sessionDir = File(sessionPath)
        if (!PreferencesManager.isBackgroundOcrIndexingEnabled(applicationContext) ||
            !OcrModelPackManager.get(applicationContext).isInstalled()
        ) {
            if (cleanupSession) sessionDir.deleteRecursively()
            return Result.success()
        }
        val targetPath = inputData.getString(KEY_TARGET_PATH) ?: run {
            if (cleanupSession) sessionDir.deleteRecursively()
            return Result.failure()
        }
        if (!sessionDir.isDirectory) {
            return if (runAttemptCount < MAX_ATTEMPTS - 1) {
                Result.retry()
            } else {
                if (cleanupSession) sessionDir.deleteRecursively()
                Result.failure()
            }
        }
        var terminal = false
        return try {
            val suppliedLastModified = inputData.getLong(KEY_LAST_MODIFIED, Long.MIN_VALUE)
            val lastModified =
                if (suppliedLastModified != Long.MIN_VALUE) {
                    suppliedLastModified
                } else {
                    com.alexdremov.notate.data.StorageUtils.getOriginInfo(applicationContext, targetPath).first
                }
            val outcome =
                OcrIndexWriter(applicationContext).indexSession(
                    sessionDir,
                    targetPath,
                    lastModified,
                    shouldStop = { isStopped },
                )
            Logger.i("OcrIndexWorker", "Indexed $targetPath: $outcome")
            if (outcome.staleRegions > 0) {
                val dao = OcrIndexDatabase.get(applicationContext).dao()
                dao.recordIndexFailure(outcome.documentId, "${outcome.staleRegions} region(s) could not be indexed", MAX_ATTEMPTS)
                if (dao.getDocument(outcome.documentId)?.status == OcrIndexWriter.STATUS_FAILED) {
                    terminal = true
                    Result.failure()
                } else {
                    Result.retry()
                }
            } else {
                terminal = true
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            terminal = true
            throw cancelled
        } catch (error: Throwable) {
            Logger.e("OcrIndexWorker", "Indexing failed for $targetPath", error)
            val dao = OcrIndexDatabase.get(applicationContext).dao()
            val document = dao.getDocumentByPath(targetPath)
            if (document != null) {
                dao.recordIndexFailure(document.documentId, error.message ?: "Indexing failed", MAX_ATTEMPTS)
            }
            val permanentlyFailed =
                document != null && dao.getDocument(document.documentId)?.status == OcrIndexWriter.STATUS_FAILED
            if (!permanentlyFailed && runAttemptCount < MAX_ATTEMPTS - 1) {
                Result.retry()
            } else {
                terminal = true
                Result.failure()
            }
        } finally {
            if (cleanupSession && terminal) sessionDir.deleteRecursively()
        }
    }

    companion object {
        const val KEY_SESSION_PATH = "ocr_session_path"
        const val KEY_TARGET_PATH = "ocr_target_path"
        const val KEY_LAST_MODIFIED = "ocr_last_modified"
        const val KEY_CLEANUP_SESSION = "ocr_cleanup_session"
        const val TAG = "OcrIndexWorker"
        private const val MAX_ATTEMPTS = 3
    }
}
