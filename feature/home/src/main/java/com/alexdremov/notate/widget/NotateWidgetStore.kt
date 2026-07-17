package com.alexdremov.notate.widget

import android.content.Context
import com.alexdremov.notate.data.CanvasItem
import com.alexdremov.notate.data.PreferencesManager
import com.alexdremov.notate.data.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class NotateWidgetMode {
    AUTOMATIC,
    PINNED,
}

data class NotateWidgetConfig(
    val mode: NotateWidgetMode = NotateWidgetMode.AUTOMATIC,
    val count: Int = 4,
    val projectId: String? = null,
    val pinnedUuid: String? = null,
    val pinnedPath: String? = null,
    val pinnedName: String? = null,
)

object NotateWidgetStore {
    private const val PREFS = "notate_widget_preferences"

    fun load(
        context: Context,
        widgetId: Int,
    ): NotateWidgetConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = "widget_${widgetId}_"
        return NotateWidgetConfig(
            mode =
                runCatching {
                    NotateWidgetMode.valueOf(prefs.getString("${prefix}mode", null) ?: "")
                }.getOrDefault(NotateWidgetMode.AUTOMATIC),
            count = prefs.getInt("${prefix}count", 4).coerceIn(1, 12),
            projectId = prefs.getString("${prefix}project", null),
            pinnedUuid = prefs.getString("${prefix}pinned_uuid", null),
            pinnedPath = prefs.getString("${prefix}pinned_path", null),
            pinnedName = prefs.getString("${prefix}pinned_name", null),
        )
    }

    fun save(
        context: Context,
        widgetId: Int,
        config: NotateWidgetConfig,
    ) {
        val prefix = "widget_${widgetId}_"
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("${prefix}mode", config.mode.name)
            .putInt("${prefix}count", config.count.coerceIn(1, 12))
            .putString("${prefix}project", config.projectId)
            .putString("${prefix}pinned_uuid", config.pinnedUuid)
            .putString("${prefix}pinned_path", config.pinnedPath)
            .putString("${prefix}pinned_name", config.pinnedName)
            .apply()
    }

    fun delete(
        context: Context,
        widgetId: Int,
    ) {
        val prefix = "widget_${widgetId}_"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach(::remove)
        }.apply()
    }

    /** Device-local, notebook-owned representative page selection. */
    fun getPreviewPage(
        context: Context,
        notebookUuid: String,
    ): Int =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("preview_page_$notebookUuid", 1)
            .coerceAtLeast(1)

    fun setPreviewPage(
        context: Context,
        notebookUuid: String,
        page: Int,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("preview_page_$notebookUuid", page.coerceAtLeast(1))
            .apply()
    }

    fun clearPreviewPage(
        context: Context,
        notebookUuid: String,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("preview_page_$notebookUuid")
            .apply()
    }
}

data class WidgetNotebook(
    val item: CanvasItem,
    val projectId: String,
)

object WidgetNotebookResolver {
    suspend fun all(
        context: Context,
        projectId: String? = null,
    ): List<WidgetNotebook> =
        withContext(Dispatchers.IO) {
            val notebooks = mutableListOf<WidgetNotebook>()
            PreferencesManager
                .getProjects(context)
                .filter { projectId == null || it.id == projectId }
                .forEach { project ->
                    val items =
                        runCatching {
                            val repository = ProjectRepository(context, project.uri)
                            repository.refreshIndex()
                            repository.getAllIndexedFiles()
                        }.getOrDefault(emptyList())
                    notebooks += items.map { WidgetNotebook(it, project.id) }
                }
            notebooks
                .distinctBy { it.item.uuid ?: it.item.path }
                .sortedByDescending { it.item.lastModified }
        }

    suspend fun automatic(
        context: Context,
        config: NotateWidgetConfig,
    ): List<WidgetNotebook> {
        val available = all(context, config.projectId)
        val recents = PreferencesManager.getRecents(context)
        val rank = recents.withIndex().associate { it.value to it.index }
        return available
            .sortedWith(
                compareBy<WidgetNotebook> {
                    minOf(
                        rank[it.item.uuid] ?: Int.MAX_VALUE,
                        rank[it.item.path] ?: Int.MAX_VALUE,
                    )
                }.thenByDescending { it.item.lastModified },
            ).take(config.count)
    }

    suspend fun pinned(
        context: Context,
        config: NotateWidgetConfig,
    ): WidgetNotebook? =
        all(context).firstOrNull {
            (config.pinnedUuid != null && it.item.uuid == config.pinnedUuid) ||
                it.item.path == config.pinnedPath
        }
}
