package com.alexdremov.notate.navigation

import android.content.Context
import android.content.Intent

data class CanvasFocusBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class CanvasDestination(
    val path: String,
    val focusBounds: CanvasFocusBounds? = null,
)

data class NotePickerDestination(
    val lockedProjectId: String?,
    val disabledItemUuid: String?,
)

interface NotateNavigator {
    fun canvasIntent(
        context: Context,
        destination: CanvasDestination,
    ): Intent

    fun notePickerIntent(
        context: Context,
        destination: NotePickerDestination,
    ): Intent
}

interface NotateNavigatorOwner {
    val notateNavigator: NotateNavigator
}

fun Context.notateNavigator(): NotateNavigator =
    (applicationContext as? NotateNavigatorOwner)?.notateNavigator
        ?: error("The application has not installed a NotateNavigator")

fun Context.openCanvas(destination: CanvasDestination) {
    startActivity(notateNavigator().canvasIntent(this, destination))
}

fun Intent.readCanvasDestination(): CanvasDestination? {
    val path = getStringExtra(NavigationExtras.CANVAS_PATH) ?: return null
    val bounds =
        if (hasExtra(NavigationExtras.FOCUS_LEFT)) {
            CanvasFocusBounds(
                left = getFloatExtra(NavigationExtras.FOCUS_LEFT, 0f),
                top = getFloatExtra(NavigationExtras.FOCUS_TOP, 0f),
                right = getFloatExtra(NavigationExtras.FOCUS_RIGHT, 0f),
                bottom = getFloatExtra(NavigationExtras.FOCUS_BOTTOM, 0f),
            )
        } else {
            null
        }
    return CanvasDestination(path, bounds)
}

fun Intent.readNotePickerDestination(): NotePickerDestination =
    NotePickerDestination(
        lockedProjectId = getStringExtra(NavigationExtras.LOCKED_PROJECT_ID),
        disabledItemUuid = getStringExtra(NavigationExtras.DISABLED_ITEM_UUID),
    )

fun Intent.writeCanvasDestination(destination: CanvasDestination): Intent =
    apply {
        putExtra(NavigationExtras.CANVAS_PATH, destination.path)
        destination.focusBounds?.let { bounds ->
            putExtra(NavigationExtras.FOCUS_LEFT, bounds.left)
            putExtra(NavigationExtras.FOCUS_TOP, bounds.top)
            putExtra(NavigationExtras.FOCUS_RIGHT, bounds.right)
            putExtra(NavigationExtras.FOCUS_BOTTOM, bounds.bottom)
        }
    }

fun Intent.writeNotePickerDestination(destination: NotePickerDestination): Intent =
    apply {
        putExtra(NavigationExtras.LOCKED_PROJECT_ID, destination.lockedProjectId)
        putExtra(NavigationExtras.DISABLED_ITEM_UUID, destination.disabledItemUuid)
    }

object NavigationExtras {
    const val CANVAS_PATH = "CANVAS_PATH"
    const val FOCUS_LEFT = "OCR_MATCH_LEFT"
    const val FOCUS_TOP = "OCR_MATCH_TOP"
    const val FOCUS_RIGHT = "OCR_MATCH_RIGHT"
    const val FOCUS_BOTTOM = "OCR_MATCH_BOTTOM"
    const val LOCKED_PROJECT_ID = "LOCKED_PROJECT_ID"
    const val DISABLED_ITEM_UUID = "DISABLED_ITEM_UUID"
}
