package com.alexdremov.notate.ui

import android.view.MotionEvent

/**
 * Routes finger events that must remain available while palm rejection is enabled.
 */
internal class FingerTouchRouter(
    private val onThreeFingerEvent: (MotionEvent) -> Unit,
    private val onTwoFingerEvent: (MotionEvent) -> Unit,
) {
    /** Returns true when palm rejection consumed the event before standard touch handling. */
    fun route(
        event: MotionEvent,
        isReadOnly: Boolean,
        palmRejectionEnabled: Boolean,
    ): Boolean {
        if (!isReadOnly) onThreeFingerEvent(event)

        if (palmRejectionEnabled) return true

        if (!isReadOnly) onTwoFingerEvent(event)
        return false
    }
}
