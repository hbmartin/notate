package com.alexdremov.notate.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import com.alexdremov.notate.R
import com.alexdremov.notate.data.LinkType
import com.alexdremov.notate.util.EpdFastModeController
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode

class InsertLinkDialog(
    context: Context,
    private val onConfirm: (String, String, LinkType) -> Unit,
    private val onBrowse: (onResult: (name: String, uuid: String) -> Unit) -> Unit,
    private val onSelectFile: (onResult: (name: String, path: String) -> Unit) -> Unit,
) : Dialog(context) {
    private lateinit var editName: EditText
    private lateinit var editTarget: EditText
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioInternal: RadioButton
    private lateinit var radioExternal: RadioButton
    private lateinit var radioFile: RadioButton
    private lateinit var btnBrowse: Button
    private lateinit var btnInsert: Button
    private lateinit var btnCancel: Button

    private var selectedType: LinkType = LinkType.INTERNAL_NOTE
    private var targetUuid: String? = null
    private var targetPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_insert_link)

        editName = findViewById(R.id.edit_link_name)
        editTarget = findViewById(R.id.edit_target)
        radioGroup = findViewById(R.id.radio_group_type)
        radioInternal = findViewById(R.id.radio_internal)
        radioExternal = findViewById(R.id.radio_external)
        radioFile = findViewById(R.id.radio_file)
        btnBrowse = findViewById(R.id.btn_browse)
        btnInsert = findViewById(R.id.btn_insert)
        btnCancel = findViewById(R.id.btn_cancel)

        setupListeners()
        updateState()
        validate()
    }

    override fun onStart() {
        super.onStart()
        window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setLayout(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val params = win.attributes
            params.gravity = android.view.Gravity.CENTER
            win.attributes = params
        }
    }

    private fun setupListeners() {
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedType =
                when (checkedId) {
                    R.id.radio_internal -> LinkType.INTERNAL_NOTE
                    R.id.radio_external -> LinkType.EXTERNAL_URL
                    R.id.radio_file -> LinkType.LOCAL_FILE
                    else -> LinkType.INTERNAL_NOTE
                }
            updateState()
            validate()
        }

        val watcher =
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {}

                override fun afterTextChanged(s: Editable?) {
                    validate()
                }
            }
        editName.addTextChangedListener(watcher)
        editTarget.addTextChangedListener(watcher)

        btnBrowse.setOnClickListener {
            if (selectedType == LinkType.INTERNAL_NOTE) {
                onBrowse { name, uuid ->
                    targetUuid = uuid
                    editTarget.setText(name)
                    if (editName.text.isBlank()) {
                        editName.setText(name)
                    }
                    validate()
                }
            } else if (selectedType == LinkType.LOCAL_FILE) {
                onSelectFile { name, path ->
                    targetPath = path
                    editTarget.setText(name)
                    if (editName.text.isBlank()) {
                        editName.setText(name)
                    }
                    validate()
                }
            }
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnInsert.setOnClickListener {
            val name = editName.text.toString().trim()
            val rawTarget = editTarget.text.toString().trim()

            val target =
                when (selectedType) {
                    LinkType.INTERNAL_NOTE -> targetUuid ?: ""
                    LinkType.LOCAL_FILE -> targetPath ?: ""
                    LinkType.EXTERNAL_URL -> rawTarget
                }

            if (name.isNotEmpty() && target.isNotEmpty()) {
                onConfirm(name, target, selectedType)
                dismiss()
            }
        }

        // EPD Optimization: Force refresh on show
        window?.decorView?.post {
            EpdFastModeController.exitFastMode()
            EpdController.invalidate(window?.decorView, UpdateMode.GC)
        }
    }

    private fun validate() {
        val name = editName.text.toString().trim()
        val targetText = editTarget.text.toString().trim()

        val isTargetValid =
            when (selectedType) {
                LinkType.INTERNAL_NOTE -> targetUuid != null && targetText.isNotEmpty()
                LinkType.LOCAL_FILE -> targetPath != null && targetText.isNotEmpty()
                LinkType.EXTERNAL_URL -> targetText.isNotEmpty() && Patterns.WEB_URL.matcher(targetText).matches()
            }

        btnInsert.isEnabled = name.isNotEmpty() && isTargetValid
    }

    private fun updateState() {
        when (selectedType) {
            LinkType.INTERNAL_NOTE -> {
                editTarget.isEnabled = false
                editTarget.hint = "Select a note..."
                btnBrowse.text = "Browse"
                btnBrowse.visibility = View.VISIBLE
                if (targetUuid == null) editTarget.text.clear()
            }

            LinkType.LOCAL_FILE -> {
                editTarget.isEnabled = false
                editTarget.hint = "Select a file..."
                btnBrowse.text = "Select"
                btnBrowse.visibility = View.VISIBLE
                if (targetPath == null) editTarget.text.clear()
            }

            LinkType.EXTERNAL_URL -> {
                editTarget.isEnabled = true
                editTarget.hint = "https://example.com"
                btnBrowse.visibility = View.GONE
                if (targetUuid != null || targetPath != null) {
                    editTarget.text.clear()
                    targetUuid = null
                    targetPath = null
                }
            }
        }
    }
}
