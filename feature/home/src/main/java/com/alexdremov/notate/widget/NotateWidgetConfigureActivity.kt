package com.alexdremov.notate.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.alexdremov.notate.data.CanvasRepository
import com.alexdremov.notate.data.CanvasType
import com.alexdremov.notate.data.PreferencesManager
import com.alexdremov.notate.ui.theme.NotateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class NotateWidgetConfigureActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            NotateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val existing = remember { NotateWidgetStore.load(this, widgetId) }
                    var mode by remember { mutableStateOf(existing.mode) }
                    var count by remember { mutableFloatStateOf(existing.count.toFloat()) }
                    var projectId by remember { mutableStateOf(existing.projectId) }
                    var selected by remember { mutableStateOf<WidgetNotebook?>(null) }
                    var notebooks by remember { mutableStateOf<List<WidgetNotebook>>(emptyList()) }
                    var pageCount by remember { mutableStateOf(1) }
                    var previewPage by remember { mutableFloatStateOf(1f) }
                    val projects = remember { PreferencesManager.getProjects(this) }

                    LaunchedEffect(Unit) {
                        notebooks = WidgetNotebookResolver.all(this@NotateWidgetConfigureActivity)
                        selected =
                            notebooks.firstOrNull {
                                (existing.pinnedUuid != null && it.item.uuid == existing.pinnedUuid) ||
                                    it.item.path == existing.pinnedPath
                            }
                    }
                    LaunchedEffect(selected?.item?.path) {
                        val notebook = selected
                        if (notebook != null) {
                            pageCount = notebookPageCount(notebook)
                            previewPage =
                                notebook.item.uuid
                                    ?.let { NotateWidgetStore.getPreviewPage(this@NotateWidgetConfigureActivity, it) }
                                    ?.coerceIn(1, pageCount)
                                    ?.toFloat() ?: 1f
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text("Configure Notate widget", style = MaterialTheme.typography.headlineSmall)
                        }
                        item {
                            ChoiceRow("Automatic recents", mode == NotateWidgetMode.AUTOMATIC) {
                                mode = NotateWidgetMode.AUTOMATIC
                            }
                            ChoiceRow("Pinned notebook", mode == NotateWidgetMode.PINNED) {
                                mode = NotateWidgetMode.PINNED
                            }
                        }

                        if (mode == NotateWidgetMode.AUTOMATIC) {
                            item {
                                Text("Configured entries: ${count.roundToInt()}")
                                Slider(
                                    value = count,
                                    onValueChange = { count = it },
                                    valueRange = 1f..12f,
                                    steps = 10,
                                )
                                Text("Resizing may show fewer entries without changing this count.")
                            }
                            item {
                                Text("Project filter", style = MaterialTheme.typography.titleMedium)
                                ChoiceRow("All Projects", projectId == null) { projectId = null }
                                projects.forEach { project ->
                                    ChoiceRow(project.name, projectId == project.id) { projectId = project.id }
                                }
                            }
                        } else {
                            item {
                                Text("Notebook", style = MaterialTheme.typography.titleMedium)
                            }
                            items(notebooks, key = { it.item.uuid ?: it.item.path }) { notebook ->
                                ChoiceRow(notebook.item.name, notebook.item.path == selected?.item?.path) {
                                    selected = notebook
                                }
                            }
                            if (selected != null) {
                                item {
                                    HorizontalDivider()
                                    Text("Widget preview page: ${previewPage.roundToInt()}")
                                    Slider(
                                        value = previewPage,
                                        onValueChange = { previewPage = it },
                                        valueRange = 1f..pageCount.coerceAtLeast(1).toFloat(),
                                        steps = (pageCount - 2).coerceAtLeast(0),
                                        enabled = pageCount > 1,
                                    )
                                }
                            }
                        }

                        item {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = mode == NotateWidgetMode.AUTOMATIC || selected != null,
                                onClick = {
                                    val notebook = selected
                                    val config =
                                        NotateWidgetConfig(
                                            mode = mode,
                                            count = count.roundToInt().coerceIn(1, 12),
                                            projectId = projectId,
                                            pinnedUuid = notebook?.item?.uuid,
                                            pinnedPath = notebook?.item?.path,
                                            pinnedName = notebook?.item?.name,
                                        )
                                    NotateWidgetStore.save(this@NotateWidgetConfigureActivity, widgetId, config)
                                    notebook?.item?.uuid?.let {
                                        NotateWidgetStore.setPreviewPage(
                                            this@NotateWidgetConfigureActivity,
                                            it,
                                            previewPage.roundToInt().coerceIn(1, pageCount),
                                        )
                                    }
                                    NotateWidgetProvider.update(
                                        this@NotateWidgetConfigureActivity,
                                        AppWidgetManager.getInstance(this@NotateWidgetConfigureActivity),
                                        widgetId,
                                    )
                                    setResult(
                                        RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                                    )
                                    finish()
                                },
                            ) {
                                Text("Add widget")
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun notebookPageCount(notebook: WidgetNotebook): Int =
        withContext(Dispatchers.IO) {
            val repository = CanvasRepository(this@NotateWidgetConfigureActivity)
            val session = repository.openCanvasSession(notebook.item.path) ?: return@withContext 1
            try {
                session.waitForInitialization()
                if (session.metadata.canvasType != CanvasType.FIXED_PAGES) {
                    1
                } else {
                    val bounds = session.regionManager.getContentBounds()
                    if (bounds.isEmpty) {
                        1
                    } else {
                        val stride = session.metadata.pageHeight + com.alexdremov.notate.config.CanvasConfig.PAGE_SPACING
                        (kotlin.math.floor(bounds.bottom / stride).toInt() + 1).coerceAtLeast(1)
                    }
                }
            } finally {
                repository.releaseCanvasSession(session)
            }
        }
}

@androidx.compose.runtime.Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
