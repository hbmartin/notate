package com.alexdremov.notate.data

import android.content.Context
import androidx.work.OneTimeWorkRequest

/**
 * Optional bridge from document persistence into the installed text-recognition feature.
 *
 * Document storage owns when a consistent snapshot exists. The feature owns whether
 * indexing is enabled, how work is constructed, and how its private index is repaired.
 */
data class DocumentIndexSnapshot(
    val sessionPath: String,
    val targetPath: String,
    val lastModified: Long? = null,
    val cleanupSession: Boolean = false,
)

interface DocumentIndexIntegration {
    val isIndexingEnabled: Boolean

    fun createIndexWork(snapshot: DocumentIndexSnapshot): OneTimeWorkRequest?

    suspend fun invalidatePaths(
        rootPath: String,
        rebuild: Boolean,
    )
}

interface DocumentIndexIntegrationOwner {
    val documentIndexIntegration: DocumentIndexIntegration
}

private object NoOpDocumentIndexIntegration : DocumentIndexIntegration {
    override val isIndexingEnabled: Boolean = false

    override fun createIndexWork(snapshot: DocumentIndexSnapshot): OneTimeWorkRequest? = null

    override suspend fun invalidatePaths(
        rootPath: String,
        rebuild: Boolean,
    ) = Unit
}

fun Context.documentIndexIntegration(): DocumentIndexIntegration =
    (applicationContext as? DocumentIndexIntegrationOwner)?.documentIndexIntegration
        ?: NoOpDocumentIndexIntegration
