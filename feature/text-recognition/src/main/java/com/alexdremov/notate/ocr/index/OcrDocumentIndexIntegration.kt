package com.alexdremov.notate.ocr.index

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import com.alexdremov.notate.data.DocumentIndexIntegration
import com.alexdremov.notate.data.DocumentIndexSnapshot
import com.alexdremov.notate.data.PathRelations
import com.alexdremov.notate.data.PreferencesManager
import com.alexdremov.notate.data.worker.OcrBackfillScheduler
import com.alexdremov.notate.data.worker.OcrIndexWorker
import java.util.concurrent.TimeUnit

/** Text-recognition implementation of the document indexing bridge. */
class OcrDocumentIndexIntegration(
    context: Context,
) : DocumentIndexIntegration {
    private val appContext = context.applicationContext

    override val isIndexingEnabled: Boolean
        get() = PreferencesManager.isBackgroundOcrIndexingEnabled(appContext)

    override fun createIndexWork(snapshot: DocumentIndexSnapshot): OneTimeWorkRequest? {
        if (!isIndexingEnabled) return null

        val data =
            Data.Builder()
                .putString(OcrIndexWorker.KEY_SESSION_PATH, snapshot.sessionPath)
                .putString(OcrIndexWorker.KEY_TARGET_PATH, snapshot.targetPath)
                .apply {
                    snapshot.lastModified?.let { putLong(OcrIndexWorker.KEY_LAST_MODIFIED, it) }
                    putBoolean(OcrIndexWorker.KEY_CLEANUP_SESSION, snapshot.cleanupSession)
                }.build()

        return OneTimeWorkRequest.Builder(OcrIndexWorker::class.java)
            .setInputData(data)
            .addTag(OcrIndexWorker.TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun invalidatePaths(
        rootPath: String,
        rebuild: Boolean,
    ) {
        val dao = OcrIndexDatabase.get(appContext).dao()
        dao.getAllDocuments()
            .filter { PathRelations.contains(rootPath, it.path) }
            .forEach { document ->
                dao.deleteDocumentBlocks(document.documentId)
                dao.deleteDocument(document.documentId)
            }
        if (rebuild) OcrBackfillScheduler.schedule(appContext, replace = true)
    }
}
