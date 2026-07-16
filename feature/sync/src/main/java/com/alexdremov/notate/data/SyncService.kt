package com.alexdremov.notate.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** Stable entry point used by UI features instead of constructing sync internals. */
interface SyncService {
    val globalProgress: StateFlow<Map<String, Pair<Int, String>>>

    suspend fun syncProject(
        projectId: String,
        progressCallback: ((Int, String) -> Unit)? = null,
    )

    suspend fun findProjectForFile(filePath: String): String?

    suspend fun deleteFromRemote(
        projectId: String,
        relativePath: String,
    ): Boolean

    fun takeInterruptedProjects(): Set<String>

    fun setCanvasActive(active: Boolean)
}

interface SyncServiceOwner {
    val syncService: SyncService
}

fun Context.syncService(): SyncService =
    (applicationContext as? SyncServiceOwner)?.syncService
        ?: SyncManager(applicationContext, CanvasRepository(applicationContext))
