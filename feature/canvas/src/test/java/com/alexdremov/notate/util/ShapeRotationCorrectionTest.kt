package com.alexdremov.notate.util

import android.graphics.Path
import android.graphics.PointF
import com.alexdremov.notate.util.ShapeRecognizer.RecognizedShape
import com.alexdremov.notate.util.ShapeRecognizer.RecognitionResult
import com.google.common.truth.Truth.assertThat
import kotlin.math.atan2
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShapeRotationCorrectionTest {
    @Test
    fun `line immediately inside normal threshold snaps to fifteen degrees`() {
        val result = lineAt(18.999f)
        val corrected = ShapeRecognizer.applyRotationCorrection(result, enabled = true, thresholdDegrees = 4f)

        assertThat(angle(corrected)).isWithin(0.01f).of(15f)
    }

    @Test
    fun `line immediately outside normal threshold keeps detected orientation`() {
        val result = lineAt(19.001f)
        val corrected = ShapeRecognizer.applyRotationCorrection(result, enabled = true, thresholdDegrees = 4f)

        assertThat(angle(corrected)).isWithin(0.01f).of(19.001f)
    }

    @Test
    fun `disabled correction preserves orientation`() {
        val result = lineAt(16f)
        val corrected = ShapeRecognizer.applyRotationCorrection(result, enabled = false, thresholdDegrees = 6f)

        assertThat(angle(corrected)).isWithin(0.01f).of(16f)
    }

    private fun lineAt(degrees: Float): RecognitionResult {
        val radians = Math.toRadians(degrees.toDouble())
        val end = PointF((100 * kotlin.math.cos(radians)).toFloat(), (100 * kotlin.math.sin(radians)).toFloat())
        return RecognitionResult(RecognizedShape.LINE, listOf(listOf(PointF(0f, 0f), end)), Path())
    }

    private fun angle(result: RecognitionResult): Float {
        val segment = result.segments.single()
        return Math.toDegrees(
            atan2(
                (segment.last().y - segment.first().y).toDouble(),
                (segment.last().x - segment.first().x).toDouble(),
            ),
        ).toFloat()
    }
}
