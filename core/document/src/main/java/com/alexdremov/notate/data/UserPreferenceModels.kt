package com.alexdremov.notate.data

enum class ShapeRotationSnapPreset(
    val thresholdDegrees: Float,
) {
    TIGHT(2f),
    NORMAL(4f),
    LOOSE(6f),
}

enum class PagePreviewRailMode {
    OFF,
    AUTO,
    PINNED,
}

enum class PagePreviewRailSide {
    LEFT,
    RIGHT,
}

enum class PagePreviewRailSize(
    val widthDp: Int,
) {
    COMPACT(112),
    LARGE(168),
}

enum class HighlighterCommitStrategy {
    LEGACY,
    OPTIMIZED,
}

enum class RecognitionProviderId {
    PP_OCR,
    ML_KIT_DIGITAL_INK,
}

enum class RecognitionMode {
    DEFAULT_PROVIDER,
    COMPARE_INSTALLED,
}
