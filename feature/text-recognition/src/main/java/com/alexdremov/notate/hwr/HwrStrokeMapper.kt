package com.alexdremov.notate.hwr

import com.alexdremov.notate.model.Stroke
import java.security.MessageDigest
import java.util.Locale

object HwrStrokeMapper {
    fun fromNotate(stroke: Stroke): HwrStroke =
        HwrStroke(
            id = stroke.strokeId,
            points =
                stroke.points.map { point ->
                    HwrPoint(point.x, point.y, point.timestamp)
                },
            width = stroke.width.coerceAtLeast(1f),
        )

    fun fingerprint(strokes: List<Stroke>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        strokes.sortedBy(Stroke::strokeId).forEach { stroke ->
            digest.update(stroke.strokeId.toByteArray())
            digest.update(stroke.style.name.toByteArray())
            digest.update(stroke.origin.name.toByteArray())
            digest.update(String.format(Locale.ROOT, "%.4f", stroke.width).toByteArray())
            stroke.points.forEach { point ->
                digest.update(java.lang.Float.floatToIntBits(point.x).toBytes())
                digest.update(java.lang.Float.floatToIntBits(point.y).toBytes())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Int.toBytes(): ByteArray =
        byteArrayOf(
            (this ushr 24).toByte(),
            (this ushr 16).toByte(),
            (this ushr 8).toByte(),
            toByte(),
        )
}
