package com.alexdremov.notate.hwr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.alexdremov.notate.ocr.OcrConversionPlanner
import com.alexdremov.notate.ocr.OcrModelInfo
import com.alexdremov.notate.ocr.OcrModelPackManager
import com.alexdremov.notate.ocr.PaddleOcrProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min

class PpOcrHandwritingProvider(
    context: Context,
) : HandwritingRecognitionProvider {
    private val appContext = context.applicationContext
    private val modelInfo = OcrModelInfo()
    private val modelManager = OcrModelPackManager.get(appContext)

    override val id: String = ID
    override val displayName: String = "PP-OCR"
    override val revision: String = modelInfo.indexVersion
    override val capabilities =
        RecognitionProviderCapabilities(
            offlineAfterDownload = true,
            supportsConfidence = true,
            supportedLanguageTags = setOf("en", "en-US", "zh", "zh-CN", "zh-TW"),
        )

    override suspend fun isModelAvailable(languageTag: String): Boolean = modelManager.isInstalled()

    override suspend fun downloadModel(languageTag: String) {
        modelManager.install()
    }

    override suspend fun removeModel(languageTag: String) {
        modelManager.remove()
    }

    override suspend fun recognizeLine(request: HandwritingLineRequest): List<RecognitionCandidate> =
        withContext(Dispatchers.Default) {
            val raster = rasterize(request) ?: return@withContext emptyList()
            try {
                val blocks = PaddleOcrProvider.get(appContext).recognize(raster.bitmap)
                if (blocks.isEmpty()) return@withContext emptyList()
                val worldBlocks =
                    blocks.map { block ->
                        val mapped = RectF(block.bounds)
                        mapped.offset(raster.bounds.left, raster.bounds.top)
                        block.copy(bounds = mapped)
                    }
                val text = OcrConversionPlanner.orderedText(worldBlocks).trim()
                if (text.isBlank()) return@withContext emptyList()
                val confidence = worldBlocks.map { it.confidence }.average().toFloat()
                listOf(RecognitionCandidate(text, confidence, request.bounds))
            } finally {
                raster.bitmap.recycle()
            }
        }

    private data class Raster(
        val bitmap: Bitmap,
        val bounds: RectF,
    )

    private fun rasterize(request: HandwritingLineRequest): Raster? {
        val bounds = request.bounds
        if (bounds.isEmpty) return null
        bounds.inset(-24f, -24f)
        val scale = min(1f, 960f / maxOf(bounds.width(), bounds.height()))
        val bitmap =
            Bitmap.createBitmap(
                ceil(bounds.width() * scale).toInt().coerceIn(1, 960),
                ceil(bounds.height() * scale).toInt().coerceIn(1, 960),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        canvas.translate(-bounds.left, -bounds.top)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        request.strokes.forEach { stroke ->
            paint.strokeWidth = stroke.width
            val path = Path()
            path.moveTo(stroke.points.first().x, stroke.points.first().y)
            stroke.points.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
            canvas.drawPath(path, paint)
        }
        return Raster(bitmap, bounds)
    }

    companion object {
        const val ID = "pp-ocr"
    }
}
