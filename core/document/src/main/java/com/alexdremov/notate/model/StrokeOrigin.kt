package com.alexdremov.notate.model

import kotlinx.serialization.Serializable

/**
 * Semantic source of a persisted stroke. V3 documents deserialize without this field and are
 * treated as [UNKNOWN], which remains eligible for automatic handwriting recognition.
 */
@Serializable
enum class StrokeOrigin {
    FREEHAND,
    PERFECTED_SHAPE,
    UNKNOWN,
}
