package com.alexdremov.notate.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.alexdremov.notate.MainActivity
import com.alexdremov.notate.data.CanvasRepository
import com.alexdremov.notate.data.CanvasType
import com.alexdremov.notate.data.NotebookChangeNotifier
import com.alexdremov.notate.feature.home.R
import com.alexdremov.notate.model.InfiniteCanvasModel
import com.alexdremov.notate.util.ThumbnailGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class NotateWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { NotateWidgetStore.delete(context, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        update(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == NotebookChangeNotifier.ACTION_NOTEBOOK_CHANGED) {
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_OPEN_WIDGET_NOTE = "com.alexdremov.notate.action.OPEN_WIDGET_NOTE"
        const val EXTRA_NOTEBOOK_PATH = "widget_notebook_path"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NotateWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { update(context, manager, it) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_notebook_list)
        }

        fun update(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
        ) {
            val config = NotateWidgetStore.load(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_notate)
            views.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            if (config.mode == NotateWidgetMode.AUTOMATIC) {
                views.setViewVisibility(R.id.widget_notebook_list, View.VISIBLE)
                views.setViewVisibility(R.id.widget_pinned_container, View.GONE)
                val serviceIntent =
                    Intent(context, NotateWidgetService::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        .setData(android.net.Uri.parse("notate-widget://automatic/$widgetId"))
                views.setRemoteAdapter(R.id.widget_notebook_list, serviceIntent)
                views.setEmptyView(R.id.widget_notebook_list, R.id.widget_empty)
                views.setPendingIntentTemplate(
                    R.id.widget_notebook_list,
                    notebookPendingIntent(context, widgetId),
                )
                manager.updateAppWidget(widgetId, views)
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_notebook_list)
                return
            }

            views.setViewVisibility(R.id.widget_notebook_list, View.GONE)
            views.setViewVisibility(R.id.widget_pinned_container, View.VISIBLE)
            val notebook = runBlocking(Dispatchers.IO) { WidgetNotebookResolver.pinned(context, config) }
            if (notebook == null) {
                views.setTextViewText(
                    R.id.widget_pinned_name,
                    "Notebook unavailable—tap to reconfigure.",
                )
                views.setImageViewResource(R.id.widget_pinned_preview, R.drawable.ic_notebook_widget)
                val configure =
                    Intent(context, NotateWidgetConfigureActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                views.setOnClickPendingIntent(
                    R.id.widget_pinned_container,
                    PendingIntent.getActivity(
                        context,
                        widgetId + 10_000,
                        configure,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                views.setTextViewText(R.id.widget_pinned_name, notebook.item.name)
                decodePreview(
                    runBlocking(Dispatchers.IO) { previewBase64(context, notebook) },
                )?.let { views.setImageViewBitmap(R.id.widget_pinned_preview, it) }
                    ?: views.setImageViewResource(R.id.widget_pinned_preview, R.drawable.ic_notebook_widget)
                views.setOnClickPendingIntent(
                    R.id.widget_pinned_container,
                    openNotebookPendingIntent(context, widgetId, notebook.item.path),
                )
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun notebookPendingIntent(
            context: Context,
            requestCode: Int,
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_WIDGET_NOTE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

        private fun openNotebookPendingIntent(
            context: Context,
            requestCode: Int,
            path: String,
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_OPEN_WIDGET_NOTE)
                    .putExtra(EXTRA_NOTEBOOK_PATH, path),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        internal suspend fun previewBase64(
            context: Context,
            notebook: WidgetNotebook,
        ): String? {
            val uuid = notebook.item.uuid ?: return notebook.item.thumbnail
            val requestedPage = NotateWidgetStore.getPreviewPage(context, uuid)
            if (requestedPage <= 1) return notebook.item.thumbnail

            val repository = CanvasRepository(context)
            val session = repository.openCanvasSession(notebook.item.path) ?: return notebook.item.thumbnail
            return try {
                session.waitForInitialization()
                val model = InfiniteCanvasModel()
                model.initializeSession(session.regionManager)
                model.loadFromCanvasData(session.metadata)
                val valid =
                    session.metadata.canvasType == CanvasType.FIXED_PAGES &&
                        requestedPage <= model.getTotalPages()
                if (!valid) {
                    NotateWidgetStore.clearPreviewPage(context, uuid)
                    notebook.item.thumbnail
                } else {
                    ThumbnailGenerator.generateBase64(
                        session.regionManager,
                        session.metadata,
                        context,
                        fixedPageIndex = requestedPage - 1,
                    )
                }
            } finally {
                repository.releaseCanvasSession(session)
            }
        }

        internal fun decodePreview(encoded: String?): Bitmap? =
            runCatching {
                encoded?.let {
                    val bytes = Base64.decode(it, Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.getOrNull()
    }
}

class NotateWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return NotateWidgetFactory(applicationContext, widgetId)
    }
}

private class NotateWidgetFactory(
    private val context: Context,
    private val widgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var notebooks: List<WidgetNotebook> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        notebooks =
            runBlocking(Dispatchers.IO) {
                WidgetNotebookResolver.automatic(context, NotateWidgetStore.load(context, widgetId))
            }
    }

    override fun onDestroy() {
        notebooks = emptyList()
    }

    override fun getCount(): Int = notebooks.size

    override fun getViewAt(position: Int): RemoteViews? {
        val notebook = notebooks.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_notebook_row).apply {
            setTextViewText(R.id.widget_row_name, notebook.item.name)
            NotateWidgetProvider.decodePreview(
                runBlocking(Dispatchers.IO) {
                    NotateWidgetProvider.previewBase64(context, notebook)
                },
            )?.let { setImageViewBitmap(R.id.widget_row_preview, it) }
                ?: setImageViewResource(R.id.widget_row_preview, R.drawable.ic_notebook_widget)
            setOnClickFillInIntent(
                R.id.widget_row_root,
                Intent().putExtra(NotateWidgetProvider.EXTRA_NOTEBOOK_PATH, notebook.item.path),
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        notebooks.getOrNull(position)?.item?.let { (it.uuid ?: it.path).hashCode().toLong() } ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
