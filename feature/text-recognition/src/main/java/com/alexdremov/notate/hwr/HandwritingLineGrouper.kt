package com.alexdremov.notate.hwr

import android.graphics.RectF
import com.alexdremov.notate.model.Stroke
import com.alexdremov.notate.model.StrokeOrigin
import com.alexdremov.notate.model.StrokeType
import kotlin.math.max

data class GroupedHandwritingLine(
    val strokes: List<Stroke>,
    val bounds: RectF,
)

/** Deterministic horizontal line grouping shared by export and manual review. */
object HandwritingLineGrouper {
    fun automaticLines(strokes: List<Stroke>): List<GroupedHandwritingLine> =
        group(
            strokes.filter {
                it.style != StrokeType.HIGHLIGHTER &&
                    it.origin != StrokeOrigin.PERFECTED_SHAPE
            },
        )

    fun group(strokes: List<Stroke>): List<GroupedHandwritingLine> {
        if (strokes.isEmpty()) return emptyList()
        val ordered = strokes.sortedWith(compareBy<Stroke> { it.bounds.centerY() }.thenBy { it.bounds.left })
        val lines = mutableListOf<MutableList<Stroke>>()
        val bounds = mutableListOf<RectF>()
        ordered.forEach { stroke ->
            val index =
                bounds.indexOfFirst { lineBounds ->
                    val tolerance = 0.55f * max(lineBounds.height(), stroke.bounds.height()).coerceAtLeast(stroke.width)
                    kotlin.math.abs(lineBounds.centerY() - stroke.bounds.centerY()) <= tolerance
                }
            if (index < 0) {
                lines += mutableListOf(stroke)
                bounds += RectF(stroke.bounds)
            } else {
                lines[index] += stroke
                bounds[index].union(stroke.bounds)
            }
        }
        return lines.indices
            .map { index ->
                GroupedHandwritingLine(
                    strokes = lines[index].sortedBy { it.bounds.left },
                    bounds = RectF(bounds[index]),
                )
            }.sortedWith(compareBy<GroupedHandwritingLine> { it.bounds.top }.thenBy { it.bounds.left })
    }
}
