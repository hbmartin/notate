package com.alexdremov.notate.ocr.index

import android.graphics.RectF
import com.alexdremov.notate.model.Stroke
import com.alexdremov.notate.ocr.OcrBlock
import com.alexdremov.notate.ocr.PaddleOcrEngine
import com.alexdremov.notate.ocr.StrokeOcrRasterizer
import kotlin.math.floor

/** Shared manual/background tiling so equivalent strokes use the same OCR resolution. */
object OcrTiledRecognizer {
    private const val TILE_SIZE = 960f
    private const val TILE_OVERLAP = 96f
    private const val TILE_STRIDE = TILE_SIZE - TILE_OVERLAP

    suspend fun recognize(
        strokes: List<Stroke>,
        engine: PaddleOcrEngine,
    ): List<OcrBlock> {
        if (strokes.isEmpty()) return emptyList()
        val contentBounds = RectF(strokes.first().bounds)
        strokes.drop(1).forEach { contentBounds.union(it.bounds) }
        val firstX = floor(contentBounds.left / TILE_STRIDE).toInt() * TILE_STRIDE
        val firstY = floor(contentBounds.top / TILE_STRIDE).toInt() * TILE_STRIDE
        val results = mutableListOf<OcrBlock>()
        var y = firstY
        while (y < contentBounds.bottom) {
            var x = firstX
            while (x < contentBounds.right) {
                val tile = RectF(x, y, x + TILE_SIZE, y + TILE_SIZE)
                if (strokes.any { RectF.intersects(it.bounds, tile) }) {
                    StrokeOcrRasterizer.render(strokes, tile)?.let { raster ->
                        try {
                            results += engine.recognize(raster.bitmap).map(raster::toWorld)
                        } finally {
                            raster.bitmap.recycle()
                        }
                    }
                }
                x += TILE_STRIDE
            }
            y += TILE_STRIDE
        }
        return OcrBlockDeduplicator.deduplicate(results)
    }
}
