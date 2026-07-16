package com.alexdremov.notate.ocr

import android.content.Context
import android.graphics.RectF
import com.alexdremov.notate.data.worker.OcrBackfillScheduler
import com.alexdremov.notate.data.worker.OcrModelDownloadScheduler
import com.alexdremov.notate.model.Stroke
import com.alexdremov.notate.ocr.index.OcrSearchRepository
import com.alexdremov.notate.ocr.index.OcrTiledRecognizer

/**
 * Public entry point for the text-recognition feature.
 *
 * UI modules depend on this facade instead of assembling the model runtime, index, workers,
 * and conversion utilities themselves.
 */
class TextRecognitionFeature private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext

    val modelInfo: OcrModelInfo = OcrModelInfo()
    val modelManager: OcrModelPackManager = OcrModelPackManager.get(appContext)
    val searchRepository: OcrSearchRepository = OcrSearchRepository.get(appContext)

    suspend fun recognize(strokes: List<Stroke>): List<OcrBlock> =
        OcrTiledRecognizer.recognize(strokes, PaddleOcrProvider.get(appContext))

    fun orderedText(blocks: List<OcrBlock>): String = OcrConversionPlanner.orderedText(blocks)

    fun insertionY(
        selection: RectF,
        text: String,
        fontSize: Float,
        fixedPageHeight: Float?,
        pageSpacing: Float,
        gap: Float = 24f,
    ): Float =
        OcrConversionPlanner.insertionY(
            selection = selection,
            text = text,
            fontSize = fontSize,
            fixedPageHeight = fixedPageHeight,
            pageSpacing = pageSpacing,
            gap = gap,
        )

    fun enqueueModelDownload() = OcrModelDownloadScheduler.enqueue(appContext)

    fun scheduleBackfill(replace: Boolean = false) = OcrBackfillScheduler.schedule(appContext, replace)

    suspend fun rebuildIndex() {
        searchRepository.clear()
        scheduleBackfill(replace = true)
    }

    companion object {
        @Volatile private var instance: TextRecognitionFeature? = null

        fun get(context: Context): TextRecognitionFeature =
            instance ?: synchronized(this) {
                instance ?: TextRecognitionFeature(context).also { instance = it }
            }
    }
}
