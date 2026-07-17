package com.alexdremov.notate.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.alexdremov.notate.feature.canvas.R
import com.alexdremov.notate.config.CanvasConfig
import com.alexdremov.notate.model.BackgroundStyle
import com.alexdremov.notate.ui.export.ExportAction
import com.alexdremov.notate.ui.pxToMm
import com.alexdremov.notate.ui.settings.InputSettingsPanel
import com.alexdremov.notate.ui.settings.InputSettingsState
import com.alexdremov.notate.ui.settings.InterfaceSettingsPanel
import com.alexdremov.notate.ui.settings.InterfaceSettingsState
import com.alexdremov.notate.ui.settings.SettingsToggle
import com.alexdremov.notate.ui.theme.NotateTheme
import com.alexdremov.notate.vm.DrawingViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsSidebarController(
    private val context: Context,
    private val container: ViewGroup,
    private val viewModel: DrawingViewModel,
    private val getCurrentStyle: () -> BackgroundStyle,
    private val isFixedPageMode: () -> Boolean,
    private val onStyleUpdate: (BackgroundStyle) -> Unit,
    private val onExportRequest: (ExportAction) -> Unit,
    private val onEditToolbar: () -> Unit,
    private val onZoomToFit: () -> Unit,
    private val onGeneratePatterns: (com.alexdremov.notate.util.PatternGenerator.PatternType, Float) -> Unit,
    private val onPalmRejectionChanged: (Boolean) -> Unit,
    private val onFixedPagePreferencesChanged: () -> Unit,
    private val onPagePreviewRailPreferencesChanged: () -> Unit,
    private val onDebugThreeFingerTap: () -> Unit,
    private val onTwoFingerTapActionChange: (com.alexdremov.notate.model.TwoFingerTapAction) -> Unit,
    private val onStylusButtonActionChange: (com.alexdremov.notate.model.StylusButtonAction) -> Unit,
) {
    private val wrapperView: View = LayoutInflater.from(context).inflate(R.layout.sidebar_layout_wrapper, container, false)
    private val contentFrame: FrameLayout = wrapperView.findViewById(R.id.sidebar_content)
    private val tvTitle: TextView = wrapperView.findViewById(R.id.tv_sidebar_title)
    private val btnBack: ImageButton = wrapperView.findViewById(R.id.btn_sidebar_back)

    init {
        container.addView(wrapperView)
        showMainMenu()

        btnBack.setOnClickListener {
            showMainMenu()
        }
    }

    fun showMainMenu() {
        contentFrame.removeAllViews()
        val mainMenuView = LayoutInflater.from(context).inflate(R.layout.sidebar_main_menu, contentFrame, false)
        contentFrame.addView(mainMenuView)

        tvTitle.text = "Settings"
        btnBack.visibility = View.GONE

        mainMenuView.findViewById<View>(R.id.menu_item_background).setOnClickListener {
            showBackgroundSettings()
        }

        mainMenuView.findViewById<View>(R.id.menu_item_writing).setOnClickListener {
            showWritingMenu()
        }

        val docMenuItem = mainMenuView.findViewById<View>(R.id.menu_item_document)
        val docDivider = mainMenuView.findViewById<View>(R.id.divider_document)
        if (isFixedPageMode()) {
            docMenuItem.visibility = View.VISIBLE
            docDivider?.visibility = View.VISIBLE
            docMenuItem.setOnClickListener {
                showDocumentMenu()
            }
        } else {
            docMenuItem.visibility = View.GONE
            docDivider?.visibility = View.GONE
        }

        mainMenuView.findViewById<View>(R.id.menu_item_export).setOnClickListener {
            showExportMenu()
        }

        mainMenuView.findViewById<View>(R.id.menu_item_text_recognition).setOnClickListener {
            showTextRecognitionMenu()
        }

        mainMenuView.findViewById<View>(R.id.menu_item_edit_toolbar).setOnClickListener {
            onEditToolbar()
        }

        mainMenuView.findViewById<View>(R.id.menu_item_zoom_to_fit).setOnClickListener {
            onZoomToFit()
        }

        mainMenuView.findViewById<View>(R.id.menu_item_debug).setOnClickListener {
            showDebugMenu()
        }
    }

    private fun showDocumentMenu() {
        contentFrame.removeAllViews()

        tvTitle.text = "Document"
        btnBack.visibility = View.VISIBLE

        val composeView =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    NotateTheme {
                        Surface(color = Color.White) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                            ) {
                                val isFixedPage by viewModel.isFixedPageMode.collectAsState()
                                val isCentered by viewModel.isFixedPageCenterHorizontal.collectAsState()

                                androidx.compose.material3.Text(
                                    text = "Scrolling",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        androidx.compose.material3.Text(
                                            text = "Force horizontal centering",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        androidx.compose.material3.Text(
                                            text = "Restricts horizontal scrolling in fixed-page mode",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                        )
                                    }
                                    androidx.compose.material3.Switch(
                                        checked = isCentered,
                                        onCheckedChange = { viewModel.setFixedPageCenterHorizontal(it) },
                                        enabled = isFixedPage,
                                    )
                                }
                            }
                        }
                    }
                }
            }

        contentFrame.addView(composeView)
    }

    private fun showWritingMenu() {
        contentFrame.removeAllViews()

        tvTitle.text = "Writing"
        btnBack.visibility = View.VISIBLE

        val composeView =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    NotateTheme {
                        Surface(color = Color.White) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                            ) {
                                val collapsible by viewModel.isCollapsibleToolbar.collectAsState()
                                val timeout by viewModel.toolbarCollapseTimeout.collectAsState()

                                val scribbleEnabled =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .isScribbleToEraseEnabled(context)
                                val shapeEnabled =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .isShapePerfectionEnabled(context)
                                val angleSnapping =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .isAngleSnappingEnabled(context)
                                val axisLocking =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .isAxisLockingEnabled(context)
                                val shapeDelay =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .getShapePerfectionDelay(context)
                                        .toFloat()
                                val shapeRotationCorrection =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .isShapeRotationCorrectionEnabled(context)
                                val shapeRotationPreset =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .getShapeRotationSnapPreset(context)
                                val fixedPagePinch =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .isFixedPagePinchZoomEnabled(context)
                                val fixedPageRotation =
                                    com.alexdremov.notate.data.PreferencesManager
                                        .isFixedPageObjectRotationEnabled(context)

                                val (localScribble, setScribble) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            scribbleEnabled,
                                        )
                                    }
                                val (localShape, setShape) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            shapeEnabled,
                                        )
                                    }
                                val (localAngle, setAngle) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            angleSnapping,
                                        )
                                    }
                                val (localAxis, setAxis) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            axisLocking,
                                        )
                                    }
                                val (localShapeDelay, setShapeDelay) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime
                                            .mutableFloatStateOf(
                                                shapeDelay,
                                            )
                                    }
                                val (localTapAction, setTapAction) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            com.alexdremov.notate.data.PreferencesManager
                                                .getTwoFingerTapAction(context),
                                        )
                                    }
                                val (localButtonAction, setButtonAction) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            com.alexdremov.notate.data.PreferencesManager
                                                .getStylusButtonAction(context),
                                        )
                                    }

                                val (localPalm, setLocalPalm) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            com.alexdremov.notate.data.PreferencesManager
                                                .isPalmRejectionEnabled(context),
                                        )
                                    }
                                val (localShapeRotation, setLocalShapeRotation) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(shapeRotationCorrection)
                                    }
                                val (localShapePreset, setLocalShapePreset) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(shapeRotationPreset.name)
                                    }
                                val (localFixedPinch, setLocalFixedPinch) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(fixedPagePinch)
                                    }
                                val (localFixedRotation, setLocalFixedRotation) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(fixedPageRotation)
                                    }

                                InputSettingsPanel(
                                    state =
                                        InputSettingsState(
                                            localScribble,
                                            localShape,
                                            localAngle,
                                            localAxis,
                                            localShapeDelay,
                                            localPalm,
                                            localTapAction,
                                            localButtonAction,
                                            localShapeRotation,
                                            localShapePreset,
                                            localFixedPinch,
                                            localFixedRotation,
                                        ),
                                    onScribbleChange = {
                                        setScribble(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setScribbleToEraseEnabled(context, it)
                                    },
                                    onPalmRejectionChange = {
                                        setLocalPalm(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setPalmRejectionEnabled(context, it)
                                        onPalmRejectionChanged(it)
                                    },
                                    onShapeChange = {
                                        setShape(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setShapePerfectionEnabled(context, it)
                                    },
                                    onAngleChange = {
                                        setAngle(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setAngleSnappingEnabled(context, it)
                                    },
                                    onAxisChange = {
                                        setAxis(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setAxisLockingEnabled(context, it)
                                    },
                                    onShapeDelayChange = { setShapeDelay(it) },
                                    onShapeDelayFinished = {
                                        com.alexdremov.notate.data.PreferencesManager.setShapePerfectionDelay(
                                            context,
                                            localShapeDelay.toLong(),
                                        )
                                    },
                                    onTwoFingerTapChange = {
                                        setTapAction(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setTwoFingerTapAction(context, it)
                                        onTwoFingerTapActionChange(it)
                                    },
                                    onStylusButtonChange = {
                                        setButtonAction(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setStylusButtonAction(context, it)
                                        onStylusButtonActionChange(it)
                                    },
                                    onShapeRotationCorrectionChange = {
                                        setLocalShapeRotation(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setShapeRotationCorrectionEnabled(context, it)
                                    },
                                    onShapeRotationPresetChange = {
                                        setLocalShapePreset(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setShapeRotationSnapPreset(
                                                context,
                                                com.alexdremov.notate.data.ShapeRotationSnapPreset.valueOf(it),
                                            )
                                    },
                                    onFixedPagePinchZoomChange = {
                                        setLocalFixedPinch(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setFixedPagePinchZoomEnabled(context, it)
                                        onFixedPagePreferencesChanged()
                                    },
                                    onFixedPageObjectRotationChange = {
                                        setLocalFixedRotation(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setFixedPageObjectRotationEnabled(context, it)
                                        onFixedPagePreferencesChanged()
                                    },
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )

                                val (localTimeout, setLocalTimeout) =
                                    androidx.compose.runtime.remember(timeout) {
                                        androidx.compose.runtime.mutableFloatStateOf(timeout.toFloat())
                                    }
                                val (localRailMode, setLocalRailMode) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            com.alexdremov.notate.data.PreferencesManager
                                                .getPagePreviewRailMode(context)
                                                .name,
                                        )
                                    }
                                val (localRailSide, setLocalRailSide) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            com.alexdremov.notate.data.PreferencesManager
                                                .getPagePreviewRailSide(context)
                                                .name,
                                        )
                                    }
                                val (localRailSize, setLocalRailSize) =
                                    androidx.compose.runtime.remember {
                                        androidx.compose.runtime.mutableStateOf(
                                            com.alexdremov.notate.data.PreferencesManager
                                                .getPagePreviewRailSize(context)
                                                .name,
                                        )
                                    }

                                InterfaceSettingsPanel(
                                    state =
                                        InterfaceSettingsState(
                                            collapsible,
                                            localTimeout,
                                            localRailMode,
                                            localRailSide,
                                            localRailSize,
                                        ),
                                    onCollapsibleChange = { viewModel.setCollapsibleToolbar(it) },
                                    onTimeoutChange = { setLocalTimeout(it) },
                                    onTimeoutFinished = { viewModel.setToolbarCollapseTimeout(localTimeout.toLong()) },
                                    onPagePreviewRailModeChange = {
                                        setLocalRailMode(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setPagePreviewRailMode(
                                                context,
                                                com.alexdremov.notate.data.PagePreviewRailMode.valueOf(it),
                                            )
                                        onPagePreviewRailPreferencesChanged()
                                    },
                                    onPagePreviewRailSideChange = {
                                        setLocalRailSide(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setPagePreviewRailSide(
                                                context,
                                                com.alexdremov.notate.data.PagePreviewRailSide.valueOf(it),
                                            )
                                        onPagePreviewRailPreferencesChanged()
                                    },
                                    onPagePreviewRailSizeChange = {
                                        setLocalRailSize(it)
                                        com.alexdremov.notate.data.PreferencesManager
                                            .setPagePreviewRailSize(
                                                context,
                                                com.alexdremov.notate.data.PagePreviewRailSize.valueOf(it),
                                            )
                                        onPagePreviewRailPreferencesChanged()
                                    },
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )

                                val distractionFree by viewModel.isDistractionFree.collectAsState()
                                SettingsToggle(
                                    title = "Distraction-free mode",
                                    checked = distractionFree,
                                    onCheckedChange = { viewModel.setDistractionFree(it) },
                                )
                            }
                        }
                    }
                }
            }

        contentFrame.addView(composeView)
    }

    private fun showExportMenu() {
        contentFrame.removeAllViews()
        val exportView = LayoutInflater.from(context).inflate(R.layout.sidebar_export_menu, contentFrame, false)
        contentFrame.addView(exportView)

        tvTitle.text = "Export"
        btnBack.visibility = View.VISIBLE

        val rgMode: RadioGroup = exportView.findViewById(R.id.rg_export_mode)
        val btnExport: Button = exportView.findViewById(R.id.btn_export_action)
        val btnShare: Button = exportView.findViewById(R.id.btn_share_action)
        val composeSettings: ComposeView = exportView.findViewById(R.id.compose_pdf_settings)
        exportView.findViewById<Switch>(R.id.switch_embed_recognized_handwriting).apply {
            isChecked =
                com.alexdremov.notate.data.PreferencesManager
                    .shouldEmbedRecognizedHandwriting(context)
            setOnCheckedChangeListener { _, checked ->
                com.alexdremov.notate.data.PreferencesManager
                    .setEmbedRecognizedHandwriting(context, checked)
            }
        }
        exportView.findViewById<Switch>(R.id.switch_review_recognition_export).apply {
            isChecked =
                com.alexdremov.notate.data.PreferencesManager
                    .shouldReviewRecognitionBeforeExport(context)
            setOnCheckedChangeListener { _, checked ->
                com.alexdremov.notate.data.PreferencesManager
                    .setReviewRecognitionBeforeExport(context, checked)
            }
        }

        val updateVisibility = {
            val isBitmap = rgMode.checkedRadioButtonId == R.id.rb_bitmap
            composeSettings.visibility = View.VISIBLE
        }

        rgMode.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        updateVisibility()

        composeSettings.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeSettings.setContent {
            NotateTheme {
                Surface(color = Color.White) {
                    val initialScale =
                        com.alexdremov.notate.data.PreferencesManager
                            .getPdfExportScale(context)
                    val (scale, setScale) = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(initialScale) }

                    com.alexdremov.notate.ui.settings.PdfSettingsPanel(
                        state =
                            com.alexdremov.notate.ui.settings
                                .PdfSettingsState(scale),
                        onScaleChange = { setScale(it) },
                        onScaleFinished = {
                            com.alexdremov.notate.data.PreferencesManager
                                .setPdfExportScale(context, scale)
                        },
                    )
                }
            }
        }

        btnExport.setOnClickListener {
            val isVector = rgMode.checkedRadioButtonId == R.id.rb_vector
            onExportRequest(ExportAction.Export(isVector))
        }

        btnShare.setOnClickListener {
            val isVector = rgMode.checkedRadioButtonId == R.id.rb_vector
            onExportRequest(ExportAction.Share(isVector))
        }
    }

    private fun showTextRecognitionMenu() {
        contentFrame.removeAllViews()
        tvTitle.text = "Text recognition & search"
        btnBack.visibility = View.VISIBLE

        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
        root.addView(TextView(context).apply { text = "Handwriting recognition provider" })
        val providers = com.alexdremov.notate.data.RecognitionProviderId.values()
        val providerSpinner =
            Spinner(context).apply {
                adapter =
                    ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_item,
                        listOf("PP-OCR", "ML Kit Digital Ink"),
                    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(
                    providers.indexOf(
                        com.alexdremov.notate.data.PreferencesManager
                            .getDefaultRecognitionProvider(context),
                    ),
                )
                onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            com.alexdremov.notate.data.PreferencesManager
                                .setDefaultRecognitionProvider(context, providers[position])
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
            }
        root.addView(providerSpinner)

        root.addView(TextView(context).apply { text = "Recognition mode" })
        val modes = com.alexdremov.notate.data.RecognitionMode.values()
        root.addView(
            Spinner(context).apply {
                adapter =
                    ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_item,
                        listOf("Use Default Provider", "Compare Installed Providers"),
                    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(
                    modes.indexOf(
                        com.alexdremov.notate.data.PreferencesManager
                            .getRecognitionMode(context),
                    ),
                )
                onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            com.alexdremov.notate.data.PreferencesManager
                                .setRecognitionMode(context, modes[position])
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
            },
        )

        val language =
            EditText(context).apply {
                hint = "Language tag (for example en-US or zh-CN)"
                setText(
                    com.alexdremov.notate.data.PreferencesManager
                        .getMlKitLanguageTag(context),
                )
            }
        root.addView(language)
        root.addView(
            Button(context).apply {
                text = "Download selected provider model"
                setOnClickListener {
                    val languageTag = language.text.toString().trim()
                    if (languageTag.isEmpty()) return@setOnClickListener
                    com.alexdremov.notate.data.PreferencesManager.setMlKitLanguageTag(context, languageTag)
                    val provider =
                        com.alexdremov.notate.ocr.TextRecognitionFeature
                            .get(context)
                            .providers
                            .default()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        runCatching { provider.downloadModel(languageTag) }
                            .onSuccess {
                                Toast.makeText(context, "Model ready for offline recognition", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Model download failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
            },
        )
        contentFrame.addView(root)
    }

    private fun showDebugMenu() {
        contentFrame.removeAllViews()
        val debugView = LayoutInflater.from(context).inflate(R.layout.sidebar_debug_menu, contentFrame, false)
        contentFrame.addView(debugView)

        tvTitle.text = "Debug"
        btnBack.visibility = View.VISIBLE

        debugView.findViewById<Switch>(R.id.switch_debug_simple_renderer).apply {
            isChecked = CanvasConfig.DEBUG_USE_SIMPLE_RENDERER
            setOnCheckedChangeListener { _, isChecked ->
                CanvasConfig.DEBUG_USE_SIMPLE_RENDERER = isChecked
                com.alexdremov.notate.data.PreferencesManager
                    .setDebugSimpleRendererEnabled(context, isChecked)
            }
        }

        debugView.findViewById<Switch>(R.id.switch_debug_ram_usage).apply {
            isChecked = CanvasConfig.DEBUG_SHOW_RAM_USAGE
            setOnCheckedChangeListener { _, isChecked ->
                CanvasConfig.DEBUG_SHOW_RAM_USAGE = isChecked
                com.alexdremov.notate.data.PreferencesManager
                    .setDebugRamUsageEnabled(context, isChecked)
            }
        }

        debugView.findViewById<Switch>(R.id.switch_debug_show_tiles).apply {
            isChecked = CanvasConfig.DEBUG_SHOW_TILES
            setOnCheckedChangeListener { _, isChecked ->
                CanvasConfig.DEBUG_SHOW_TILES = isChecked
                com.alexdremov.notate.data.PreferencesManager
                    .setDebugShowTilesEnabled(context, isChecked)
            }
        }

        debugView.findViewById<Switch>(R.id.switch_debug_show_regions).apply {
            isChecked = CanvasConfig.DEBUG_SHOW_REGIONS
            setOnCheckedChangeListener { _, isChecked ->
                CanvasConfig.DEBUG_SHOW_REGIONS = isChecked
                com.alexdremov.notate.data.PreferencesManager
                    .setDebugShowRegionsEnabled(context, isChecked)
            }
        }

        debugView.findViewById<Switch>(R.id.switch_debug_bounding_box).apply {
            isChecked = CanvasConfig.DEBUG_SHOW_BOUNDING_BOX
            setOnCheckedChangeListener { _, isChecked ->
                CanvasConfig.DEBUG_SHOW_BOUNDING_BOX = isChecked
                com.alexdremov.notate.data.PreferencesManager
                    .setDebugBoundingBoxEnabled(context, isChecked)
            }
        }

        debugView.findViewById<Switch>(R.id.switch_debug_profiling).apply {
            isChecked = CanvasConfig.DEBUG_ENABLE_PROFILING
            setOnCheckedChangeListener { _, isChecked ->
                CanvasConfig.DEBUG_ENABLE_PROFILING = isChecked
                com.alexdremov.notate.data.PreferencesManager
                    .setDebugProfilingEnabled(context, isChecked)
            }
        }

        debugView.findViewById<Switch>(R.id.switch_debug_palm_rejection).apply {
            isChecked =
                com.alexdremov.notate.data.PreferencesManager
                    .isPalmRejectionEnabled(context)
            setOnCheckedChangeListener { _, isChecked ->
                com.alexdremov.notate.data.PreferencesManager
                    .setPalmRejectionEnabled(context, isChecked)
                onPalmRejectionChanged(isChecked)
            }
        }

        debugView.findViewById<Switch>(R.id.switch_debug_gesture_logging).apply {
            isChecked = CanvasConfig.DEBUG_LOG_GESTURES
            setOnCheckedChangeListener { _, isChecked ->
                CanvasConfig.DEBUG_LOG_GESTURES = isChecked
                com.alexdremov.notate.data.PreferencesManager
                    .setDebugGestureLoggingEnabled(context, isChecked)
            }
        }

        debugView.findViewById<View>(R.id.btn_debug_three_finger_tap).setOnClickListener {
            onDebugThreeFingerTap()
        }

        val highlighterStrategies = com.alexdremov.notate.data.HighlighterCommitStrategy.values()
        debugView.findViewById<Spinner>(R.id.spinner_debug_highlighter_strategy).apply {
            adapter =
                ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_item,
                    highlighterStrategies.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
            setSelection(
                highlighterStrategies.indexOf(
                    com.alexdremov.notate.data.PreferencesManager
                        .getHighlighterCommitStrategy(context),
                ),
            )
            onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        com.alexdremov.notate.data.PreferencesManager
                            .setHighlighterCommitStrategy(context, highlighterStrategies[position])
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
        }
        val highlighterMetrics =
            com.alexdremov.notate.util.HighlighterCommitMetrics
                .snapshot()
        debugView.findViewById<TextView>(R.id.tv_debug_highlighter_metrics).text =
            if (highlighterMetrics.instrumentationAvailable) {
                "Session: last %.1f ms · avg %.1f ms · worst %.1f ms\nTiles: last %d · total %d"
                    .format(
                        highlighterMetrics.lastMilliseconds,
                        highlighterMetrics.averageMilliseconds,
                        highlighterMetrics.worstMilliseconds,
                        highlighterMetrics.lastRegeneratedTiles,
                        highlighterMetrics.totalRegeneratedTiles,
                    )
            } else {
                "Metrics hidden: instrumentation overhead exceeded 1 ms."
            }

        debugView.findViewById<Switch>(R.id.switch_debug_recognition).apply {
            isChecked =
                com.alexdremov.notate.data.PreferencesManager
                    .isRecognitionDebugEnabled(context)
            setOnCheckedChangeListener { _, isChecked ->
                com.alexdremov.notate.data.PreferencesManager
                    .setRecognitionDebugEnabled(context, isChecked)
            }
        }
        debugView.findViewById<View>(R.id.btn_debug_clear_recognition).setOnClickListener {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                com.alexdremov.notate.ocr.TextRecognitionFeature
                    .get(context)
                    .diagnostics
                    .clear()
                Toast.makeText(context, "Recognition diagnostics cleared", Toast.LENGTH_SHORT).show()
            }
        }

        val spinnerLogLevel: Spinner = debugView.findViewById(R.id.spinner_debug_log_level)
        val levels =
            com.alexdremov.notate.util.Logger.Level
                .values()
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, levels.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLogLevel.adapter = adapter

        val currentLevel =
            com.alexdremov.notate.util.Logger
                .getMinLogLevelToShow()
        spinnerLogLevel.setSelection(levels.indexOf(currentLevel))

        spinnerLogLevel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val level = levels[position]
                    com.alexdremov.notate.util.Logger
                        .setMinLogLevelToShow(level)
                    com.alexdremov.notate.data.PreferencesManager
                        .setMinLogLevel(context, level.priority)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        debugView.findViewById<View>(R.id.btn_debug_pattern_generator).setOnClickListener {
            showPatternGeneratorMenu()
        }
    }

    private fun showPatternGeneratorMenu() {
        contentFrame.removeAllViews()
        val patternView = LayoutInflater.from(context).inflate(R.layout.sidebar_pattern_generator, contentFrame, false)
        contentFrame.addView(patternView)

        tvTitle.text = "Pattern Generator"
        btnBack.visibility = View.VISIBLE

        val rgType: RadioGroup = patternView.findViewById(R.id.rg_pattern_type)
        val seekArea: SeekBar = patternView.findViewById(R.id.seekbar_pattern_area)
        val tvAreaValue: TextView = patternView.findViewById(R.id.tv_pattern_area_value)
        val btnGenerate: Button = patternView.findViewById(R.id.btn_generate_pattern)

        seekArea.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val p = progress / 100f
                    val label =
                        when {
                            p < 0.3f -> "Small / Low Complexity"
                            p < 0.7f -> "Medium / Moderate Complexity"
                            else -> "Large / High Complexity"
                        }
                    tvAreaValue.text = label
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            },
        )

        btnGenerate.setOnClickListener {
            val type =
                when (rgType.checkedRadioButtonId) {
                    R.id.rb_pattern_fractal -> com.alexdremov.notate.util.PatternGenerator.PatternType.FRACTAL
                    R.id.rb_pattern_squares -> com.alexdremov.notate.util.PatternGenerator.PatternType.SQUARES
                    R.id.rb_pattern_handwriting -> com.alexdremov.notate.util.PatternGenerator.PatternType.HANDWRITING
                    else -> com.alexdremov.notate.util.PatternGenerator.PatternType.FRACTAL
                }
            val intensity = seekArea.progress / 100f
            onGeneratePatterns(type, intensity)
            // Optional: Close sidebar? Or stay to generate more? Let's stay.
            Toast.makeText(context, "Generating pattern...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBackgroundSettings() {
        contentFrame.removeAllViews()
        val bgView = LayoutInflater.from(context).inflate(R.layout.dialog_background_settings, contentFrame, false)
        contentFrame.addView(bgView)

        tvTitle.text = "Background"
        btnBack.visibility = View.VISIBLE

        bindBackgroundSettings(bgView)
    }

    private fun bindBackgroundSettings(view: View) {
        com.alexdremov.notate.ui.settings.BackgroundSettingsBinder(
            context,
            view,
            getCurrentStyle,
            isFixedPageMode(),
            onStyleUpdate,
        )
    }
}
