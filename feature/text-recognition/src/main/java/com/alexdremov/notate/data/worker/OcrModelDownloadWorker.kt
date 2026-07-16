package com.alexdremov.notate.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alexdremov.notate.data.PreferencesManager
import com.alexdremov.notate.ocr.OcrModelPackManager
import com.alexdremov.notate.util.Logger
import kotlinx.coroutines.CancellationException

class OcrModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        try {
            OcrModelPackManager.get(applicationContext).install()
            if (PreferencesManager.isBackgroundOcrIndexingEnabled(applicationContext)) {
                OcrBackfillScheduler.schedule(applicationContext, replace = true)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e("OcrModelDownload", "Unable to download OCR model pack", error)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }

    companion object {
        const val UNIQUE_NAME = "OcrModelDownloadWorker"
        private const val MAX_RETRIES = 3
    }
}

object OcrModelDownloadScheduler {
    fun enqueue(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<OcrModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            OcrModelDownloadWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
