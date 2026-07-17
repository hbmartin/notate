package com.alexdremov.notate.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

/**
 * Review requires an explicit Keep or Replace action. Neither outcome is preselected.
 */
class HandwritingReviewDialog(
    context: Context,
    initialText: String,
    candidates: List<Pair<String, String>>,
    providerSummary: String,
    sharedStrokeWarning: Boolean,
    private val onKeep: (String, Int) -> Unit,
    private val onReplace: (String, Int) -> Unit,
    private val onRetry: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    private val root =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
        }
    private val editText =
        EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setText(initialText)
            setSelection(text.length)
        }
    private var selectedCandidate = -1

    private val dialog: AlertDialog

    init {
        root.addView(
            TextView(context).apply {
                text =
                    buildString {
                        append(providerSummary)
                        if (sharedStrokeWarning) {
                            append("\nSome strokes belong to multiple accepted lines; each line remains independent.")
                        }
                    }
            },
        )
        if (candidates.isNotEmpty()) {
            val spinner =
                Spinner(context).apply {
                    prompt = "Recognition alternatives"
                    adapter =
                        ArrayAdapter(
                            context,
                            android.R.layout.simple_spinner_item,
                            candidates.map { it.first },
                        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                }
            var ignoreInitialCallback = true
            spinner.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        if (ignoreInitialCallback) {
                            ignoreInitialCallback = false
                            return
                        }
                        selectedCandidate = position
                        editText.setText(candidates[position].second)
                        editText.setSelection(editText.text.length)
                    }

                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                }
            root.addView(spinner)
        }
        root.addView(editText)

        val actionRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    Button(context).apply {
                        text = "Keep handwriting"
                        setOnClickListener {
                            val text = editText.text.toString().trim()
                            if (text.isNotEmpty()) {
                                dialog.dismiss()
                                onKeep(text, selectedCandidate)
                            }
                        }
                    },
                )
                addView(
                    Button(context).apply {
                        text = "Replace with text"
                        setOnClickListener {
                            val text = editText.text.toString().trim()
                            if (text.isNotEmpty()) {
                                dialog.dismiss()
                                onReplace(text, selectedCandidate)
                            }
                        }
                    },
                )
                addView(
                    Button(context).apply {
                        text = "Retry / provider"
                        setOnClickListener {
                            dialog.dismiss()
                            onRetry()
                        }
                    },
                )
            }
        root.addView(actionRow)

        dialog =
            AlertDialog.Builder(context)
                .setTitle("Review handwriting recognition")
                .setView(root)
                .setNegativeButton("Cancel", null)
                .create()
    }

    fun show() = dialog.show()
}
