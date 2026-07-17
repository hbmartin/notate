package com.alexdremov.notate.ocr

import android.content.Context
import android.graphics.RectF
import com.alexdremov.notate.data.worker.OcrBackfillScheduler
import com.alexdremov.notate.data.worker.OcrModelDownloadScheduler
import com.alexdremov.notate.data.HandwritingLine
import com.alexdremov.notate.data.HandwritingLineGeometry
import com.alexdremov.notate.data.RecognitionProvenance
import com.alexdremov.notate.model.Stroke
import com.alexdremov.notate.hwr.HandwritingLineGrouper
import com.alexdremov.notate.hwr.HandwritingProviderRegistry
import com.alexdremov.notate.hwr.HandwritingLineRequest
import com.alexdremov.notate.hwr.HwrStrokeMapper
import com.alexdremov.notate.hwr.RecognitionCandidate
import com.alexdremov.notate.hwr.RecognitionDiagnosticsStore
import com.alexdremov.notate.ocr.index.OcrSearchRepository
import com.alexdremov.notate.ocr.index.OcrTiledRecognizer
import java.util.UUID

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
    val providers: HandwritingProviderRegistry = HandwritingProviderRegistry(appContext)
    val diagnostics: RecognitionDiagnosticsStore = RecognitionDiagnosticsStore.get(appContext)

    suspend fun recognize(strokes: List<Stroke>): List<OcrBlock> =
        OcrTiledRecognizer.recognize(strokes, PaddleOcrProvider.get(appContext))

    suspend fun recognizeCandidates(
        strokes: List<Stroke>,
        providerId: String? = null,
        languageTag: String = "zh-CN",
    ): List<RecognitionCandidate> {
        if (strokes.isEmpty()) return emptyList()
        val provider =
            providerId?.let(providers::get) ?: providers.default()
        requireNotNull(provider) { "Unknown handwriting recognition provider: $providerId" }
        val candidates = provider.recognizeLine(
            HandwritingLineRequest(
                strokes = strokes.map(HwrStrokeMapper::fromNotate),
                languageTag = languageTag,
            ),
        )
        diagnostics.record(provider, candidates)
        return candidates
    }

    /**
     * Recognizes eligible horizontal lines and returns only results allowed by the automatic
     * acceptance policy. Review UI may call [recognizeCandidates] directly to expose every result.
     */
    suspend fun automaticallyAcceptedLines(
        strokes: List<Stroke>,
        providerId: String? = null,
        languageTag: String = "zh-CN",
    ): List<HandwritingLine> {
        val provider = providerId?.let(providers::get) ?: providers.default()
        requireNotNull(provider) { "Unknown handwriting recognition provider: $providerId" }
        return HandwritingLineGrouper.automaticLines(strokes).mapNotNull { group ->
            val candidates =
                provider.recognizeLine(
                        HandwritingLineRequest(
                            strokes = group.strokes.map(HwrStrokeMapper::fromNotate),
                            languageTag = languageTag,
                        ),
                    )
            val candidate = candidates.firstOrNull() ?: return@mapNotNull null
            if (candidate.confidence != null && candidate.confidence < 0.5f) {
                diagnostics.record(provider, candidates)
                return@mapNotNull null
            }
            diagnostics.record(provider, candidates, candidate.text)
            HandwritingLine(
                id = UUID.randomUUID().toString(),
                geometry =
                    HandwritingLineGeometry(
                        group.bounds.left,
                        group.bounds.top,
                        group.bounds.right,
                        group.bounds.bottom,
                    ),
                sourceStrokeIds = group.strokes.map(Stroke::strokeId),
                sourceFingerprint = HwrStrokeMapper.fingerprint(group.strokes),
                acceptedText = candidate.text,
                provenance =
                    RecognitionProvenance(
                        providerId = provider.id,
                        providerRevision = provider.revision,
                        languageTag = languageTag,
                    ),
                confidence = candidate.confidence,
            )
        }
    }

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
