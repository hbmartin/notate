@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.alexdremov.notate.data

import android.graphics.Matrix
import android.graphics.RectF
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class TranscriptionState {
    ACCEPTED,
    STALE,
}

@Serializable
data class HandwritingLineGeometry(
    @ProtoNumber(1) val left: Float,
    @ProtoNumber(2) val top: Float,
    @ProtoNumber(3) val right: Float,
    @ProtoNumber(4) val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(right >= left && bottom >= top)
    }

    fun toRectF(): RectF = RectF(left, top, right, bottom)

    fun transformed(matrix: Matrix): HandwritingLineGeometry {
        val mapped = toRectF()
        matrix.mapRect(mapped)
        return HandwritingLineGeometry(mapped.left, mapped.top, mapped.right, mapped.bottom)
    }
}

@Serializable
data class RecognitionProvenance(
    @ProtoNumber(1) val providerId: String,
    @ProtoNumber(2) val providerRevision: String,
    @ProtoNumber(3) val modelId: String = "",
    @ProtoNumber(4) val languageTag: String = "",
)

@Serializable
data class HandwritingLine(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val geometry: HandwritingLineGeometry,
    @ProtoNumber(3) val sourceStrokeIds: List<String>,
    @ProtoNumber(4) val sourceFingerprint: String,
    @ProtoNumber(5) val acceptedText: String,
    @ProtoNumber(6) val provenance: RecognitionProvenance,
    @ProtoNumber(7) val confidence: Float? = null,
    @ProtoNumber(8) val userEdited: Boolean = false,
    @ProtoNumber(9) val state: TranscriptionState = TranscriptionState.ACCEPTED,
) {
    init {
        require(id.isNotBlank())
        require(sourceStrokeIds.isNotEmpty())
        require(sourceStrokeIds.none(String::isBlank))
        require(acceptedText.isNotBlank())
        require(confidence == null || (confidence.isFinite() && confidence in 0f..1f))
    }
}

@Serializable
data class RecognitionDocument(
    @ProtoNumber(1) val version: Int = 1,
    @ProtoNumber(2) val lines: List<HandwritingLine> = emptyList(),
)
