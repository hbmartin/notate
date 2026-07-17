package com.alexdremov.notate.ui

import android.view.MotionEvent

/**
 * Routes finger events that must remain available while palm rejection is enabled.
 */
internal class FingerTouchRouter(
    private val onThreeFingerEvent: (MotionEvent) -> Unit,
    private val onTwoFingerEvent: (MotionEvent) -> Unit,
) {
    private var reservedThreeFingerSequence = false

    /** Returns true when palm rejection consumed the event before standard touch handling. */
    fun route(
        event: MotionEvent,
        isReadOnly: Boolean,
        palmRejectionEnabled: Boolean,
        stylusStrokeActive: Boolean = false,
    ): Boolean {
        if (event.pointerCount >= 3) reservedThreeFingerSequence = true

        if (!isReadOnly && !stylusStrokeActive) onThreeFingerEvent(event)

        val consume = palmRejectionEnabled || reservedThreeFingerSequence
        if (!consume && !isReadOnly) onTwoFingerEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            reservedThreeFingerSequence = false
        }
        return consume
    }
}
