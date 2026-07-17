package com.alexdremov.notate.ui

import android.view.MotionEvent
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerTouchRouterTest {
    private val event = mockk<MotionEvent>(relaxed = true)

    @Test
    fun `palm rejection consumes only after three-finger detection`() {
        val calls = mutableListOf<String>()
        val router =
            FingerTouchRouter(
                onThreeFingerEvent = { calls += "three" },
                onTwoFingerEvent = { calls += "two" },
            )

        val consumed = router.route(event, isReadOnly = false, palmRejectionEnabled = true)

        assertTrue(consumed)
        assertEquals(listOf("three"), calls)
    }

    @Test
    fun `normal routing checks three-finger before two-finger detection`() {
        val calls = mutableListOf<String>()
        val router =
            FingerTouchRouter(
                onThreeFingerEvent = { calls += "three" },
                onTwoFingerEvent = { calls += "two" },
            )

        val consumed = router.route(event, isReadOnly = false, palmRejectionEnabled = false)

        assertFalse(consumed)
        assertEquals(listOf("three", "two"), calls)
    }

    @Test
    fun `read-only routing skips gesture detection`() {
        val calls = mutableListOf<String>()
        val router =
            FingerTouchRouter(
                onThreeFingerEvent = { calls += "three" },
                onTwoFingerEvent = { calls += "two" },
            )

        val consumed = router.route(event, isReadOnly = true, palmRejectionEnabled = false)

        assertFalse(consumed)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `active stylus stroke suppresses three-finger callback`() {
        val calls = mutableListOf<String>()
        val router =
            FingerTouchRouter(
                onThreeFingerEvent = { calls += "three" },
                onTwoFingerEvent = { calls += "two" },
            )

        val consumed =
            router.route(
                event,
                isReadOnly = false,
                palmRejectionEnabled = true,
                stylusStrokeActive = true,
            )

        assertTrue(consumed)
        assertTrue(calls.isEmpty())
    }
}
