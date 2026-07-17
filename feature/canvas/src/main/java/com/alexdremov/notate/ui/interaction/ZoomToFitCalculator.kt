package com.alexdremov.notate.ui.interaction

import android.graphics.RectF
import com.alexdremov.notate.config.CanvasConfig
import kotlin.math.min

data class ZoomToFitResult(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
)

/** Pure zoom-to-fit geometry shared by fixed pages and infinite canvases. */
object ZoomToFitCalculator {
    fun calculate(
        bounds: RectF?,
        viewportWidth: Int,
        viewportHeight: Int,
        insetFraction: Float = 0.05f,
        minScale: Float = CanvasConfig.MIN_SCALE,
        maxScale: Float = CanvasConfig.MAX_SCALE,
    ): ZoomToFitResult {
        if (bounds == null || bounds.isEmpty || viewportWidth <= 0 || viewportHeight <= 0) {
            return ZoomToFitResult(scale = 1f, translationX = 0f, translationY = 0f)
        }

        val horizontalInset = viewportWidth * insetFraction
        val verticalInset = viewportHeight * insetFraction
        val usableWidth = (viewportWidth - horizontalInset * 2f).coerceAtLeast(1f)
        val usableHeight = (viewportHeight - verticalInset * 2f).coerceAtLeast(1f)
        val scale =
            min(usableWidth / bounds.width(), usableHeight / bounds.height())
                .coerceIn(minScale, maxScale)

        return ZoomToFitResult(
            scale = scale,
            translationX = viewportWidth / 2f - bounds.centerX() * scale,
            translationY = viewportHeight / 2f - bounds.centerY() * scale,
        )
    }
}
