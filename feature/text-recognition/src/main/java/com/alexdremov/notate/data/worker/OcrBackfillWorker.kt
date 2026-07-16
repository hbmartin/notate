package com.alexdremov.notate.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alexdremov.notate.data.CanvasRepository
import com.alexdremov.notate.data.CanvasItem
import com.alexdremov.notate.data.PreferencesManager
import com.alexdremov.notate.data.ProjectRepository
import com.alexdremov.notate.ocr.OcrModelInfo
import com.alexdremov.notate.ocr.OcrModelPackManager
import com.alexdremov.notate.ocr.index.OcrIndexDatabase
import com.alexdremov.notate.ocr.index.OcrIndexWriter
import com.alexdremov.notate.ocr.index.OcrDocumentEntity
import com.alexdremov.notate.util.Logger
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class OcrBackfillWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (!PreferencesManager.isBackgroundOcrIndexingEnabled(applicationContext)) return Result.success()
        if (!OcrModelPackManager.get(applicationContext).isInstalled()) return Result.success()
        val dao = OcrIndexDatabase.get(applicationContext).dao()
        dao.repairInterruptedDocuments()
        dao.deleteOrphans()
        val canvasRepository = CanvasRepository(applicationContext)
        val modelVersion = OcrModelInfo().indexVersion
        val projects = PreferencesManager.getProjects(applicationContext)
        val scans = projects.mapNotNull { project ->
            try {
                val repository = ProjectRepository(applicationContext, project.uri)
                repository.refreshIndex()
                ProjectScan(project.id, repository.getAllIndexedFiles())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e("OcrBackfill", "Unable to scan ${project.name}", error)
                null
            }
        }
        val allFiles = scans.flatMap { scan -> scan.files.map { ScannedItem(scan.projectId, it) } }.distinctBy { it.file.path }
        val scanFailed = scans.size < projects.size

        val documents = dao.getAllDocuments()
        val configuredProjectIds = projects.mapTo(mutableSetOf()) { it.id }
        documents.filter { it.projectId != null && it.projectId !in configuredProjectIds }.forEach { document ->
            dao.deleteDocumentBlocks(document.documentId)
            dao.deleteDocument(document.documentId)
        }
        scans.forEach { scan ->
            val currentPaths = scan.files.mapTo(mutableSetOf()) { it.path }
            documents.filter { it.projectId == scan.projectId && it.path !in currentPaths }.forEach { document ->
                dao.deleteDocumentBlocks(document.documentId)
                dao.deleteDocument(document.documentId)
            }
        }

        val pending = allFiles.filter { item ->
            val indexed = dao.getDocumentByPath(item.file.path)
            val changed = indexed == null || indexed.lastModified != item.file.lastModified || indexed.modelVersion != modelVersion
            changed || indexed?.status !in setOf("INDEXED", "FAILED")
        }
        var attempted = 0
        var scanned = 0
        var retryableFailure = false
        var transientLock = false
        for (item in pending) {
            if (attempted >= BATCH_SIZE || scanned >= MAX_SCANNED_PER_RUN) break
            scanned++
            var session: com.alexdremov.notate.data.CanvasSession? = null
            try {
                session = canvasRepository.openCanvasSession(item.file.path)
                    ?: error("Unable to open canvas session")
                attempted++
                session.waitForInitialization()
                val outcome =
                    OcrIndexWriter(applicationContext).indexSession(
                        session.sessionDir,
                        item.file.path,
                        item.file.lastModified,
                        shouldStop = { isStopped },
                    )
                if (outcome.staleRegions > 0) {
                    dao.recordIndexFailure(outcome.documentId, "${outcome.staleRegions} region(s) could not be indexed", MAX_FAILURES)
                    retryableFailure = retryableFailure || dao.getDocument(outcome.documentId)?.status != "FAILED"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (locked: CanvasRepository.CanvasLockedException) {
                transientLock = true
                Logger.w("OcrBackfill", "Skipping locked note ${item.file.path}")
            } catch (error: Throwable) {
                if (session == null) attempted++
                Logger.e("OcrBackfill", "Unable to index ${item.file.path}", error)
                val document = ensureFailureDocument(dao, item, modelVersion)
                dao.recordIndexFailure(document.documentId, error.message ?: "Indexing failed", MAX_FAILURES)
                retryableFailure = retryableFailure || dao.getDocument(document.documentId)?.status != "FAILED"
            } finally {
                session?.let { canvasRepository.releaseCanvasSession(it) }
            }
        }
        val morePending = pending.size > scanned
        return if (morePending || retryableFailure || ((transientLock || scanFailed) && runAttemptCount < MAX_TRANSIENT_RETRIES)) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private suspend fun ensureFailureDocument(
        dao: com.alexdremov.notate.ocr.index.OcrIndexDao,
        item: ScannedItem,
        modelVersion: String,
    ): OcrDocumentEntity =
        dao.getDocumentByPath(item.file.path) ?: OcrDocumentEntity(
            documentId = item.file.uuid ?: sha256(item.file.path),
            projectId = item.projectId,
            path = item.file.path,
            name = item.file.name,
            lastModified = item.file.lastModified,
            modelVersion = modelVersion,
            status = "STALE",
            errorMessage = "Indexing failed before the document could be opened",
        ).also { dao.upsertDocumentSafely(it) }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val BATCH_SIZE = 3
        private const val MAX_SCANNED_PER_RUN = 12
        private const val MAX_FAILURES = 3
        private const val MAX_TRANSIENT_RETRIES = 2
        const val UNIQUE_NAME = "OcrBackfillWorker"
    }
}

private data class ProjectScan(
    val projectId: String,
    val files: List<CanvasItem>,
)

private data class ScannedItem(
    val projectId: String,
    val file: CanvasItem,
)

object OcrBackfillScheduler {
    fun schedule(
        context: Context,
        replace: Boolean = false,
    ) {
        val request = OneTimeWorkRequestBuilder<OcrBackfillWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiresDeviceIdle(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            OcrBackfillWorker.UNIQUE_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
