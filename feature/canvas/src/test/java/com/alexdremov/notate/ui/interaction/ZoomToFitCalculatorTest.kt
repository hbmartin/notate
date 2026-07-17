package com.alexdremov.notate.ui.interaction

import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZoomToFitCalculatorTest {
    @Test
    fun `applies five percent inset on every viewport edge`() {
        val result = ZoomToFitCalculator.calculate(RectF(0f, 0f, 100f, 100f), 1000, 500)

        assertThat(result.scale).isWithin(0.0001f).of(4.5f)
        assertThat(result.translationX).isWithin(0.0001f).of(275f)
        assertThat(result.translationY).isWithin(0.0001f).of(25f)
    }

    @Test
    fun `empty infinite canvas resets to identity`() {
        assertThat(ZoomToFitCalculator.calculate(null, 1000, 700))
            .isEqualTo(ZoomToFitResult(1f, 0f, 0f))
        assertThat(ZoomToFitCalculator.calculate(RectF(), 1000, 700))
            .isEqualTo(ZoomToFitResult(1f, 0f, 0f))
    }

    @Test
    fun `scale is clamped and content remains centered`() {
        val result =
            ZoomToFitCalculator.calculate(
                bounds = RectF(100f, 200f, 101f, 201f),
                viewportWidth = 1000,
                viewportHeight = 500,
                maxScale = 8f,
            )

        assertThat(result.scale).isEqualTo(8f)
        assertThat(result.translationX).isWithin(0.0001f).of(-304f)
        assertThat(result.translationY).isWithin(0.0001f).of(-1354f)
    }
}
