package com.alexdremov.notate.hwr

import android.graphics.RectF

data class HwrPoint(
    val x: Float,
    val y: Float,
    val timestampMillis: Long? = null,
) {
    init {
        require(x.isFinite() && y.isFinite())
    }
}

data class HwrStroke(
    val id: String,
    val points: List<HwrPoint>,
    val width: Float,
) {
    init {
        require(id.isNotBlank())
        require(points.isNotEmpty())
        require(width.isFinite() && width > 0f)
    }
}

data class HandwritingLineRequest(
    val strokes: List<HwrStroke>,
    val languageTag: String,
) {
    init {
        require(strokes.isNotEmpty())
        require(languageTag.isNotBlank())
    }

    val bounds: RectF
        get() {
            val result = RectF()
            strokes.forEach { stroke ->
                stroke.points.forEach { point ->
                    if (result.isEmpty) {
                        result.set(point.x, point.y, point.x, point.y)
                    } else {
                        result.union(point.x, point.y)
                    }
                }
            }
            return result
        }
}

data class RecognitionCandidate(
    val text: String,
    val confidence: Float?,
    val bounds: RectF,
) {
    init {
        require(text.isNotBlank())
        require(confidence == null || (confidence.isFinite() && confidence in 0f..1f))
    }
}

data class RecognitionProviderCapabilities(
    val offlineAfterDownload: Boolean,
    val supportsConfidence: Boolean,
    val supportedLanguageTags: Set<String>,
)

interface HandwritingRecognitionProvider {
    val id: String
    val displayName: String
    val revision: String
    val capabilities: RecognitionProviderCapabilities

    suspend fun isModelAvailable(languageTag: String): Boolean

    suspend fun downloadModel(languageTag: String)

    suspend fun removeModel(languageTag: String)

    suspend fun recognizeLine(request: HandwritingLineRequest): List<RecognitionCandidate>
}
