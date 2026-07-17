package com.alexdremov.notate.hwr

import android.content.Context
import com.alexdremov.notate.data.PreferencesManager
import com.alexdremov.notate.data.RecognitionProviderId

class HandwritingProviderRegistry(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val providers: Map<String, HandwritingRecognitionProvider> =
        listOf(
            PpOcrHandwritingProvider(appContext),
            MlKitDigitalInkProvider(appContext),
        ).associateBy(HandwritingRecognitionProvider::id)

    fun all(): List<HandwritingRecognitionProvider> = providers.values.toList()

    fun get(id: String): HandwritingRecognitionProvider? = providers[id]

    fun default(): HandwritingRecognitionProvider =
        when (PreferencesManager.getDefaultRecognitionProvider(appContext)) {
            RecognitionProviderId.PP_OCR -> checkNotNull(get(PpOcrHandwritingProvider.ID))
            RecognitionProviderId.ML_KIT_DIGITAL_INK -> checkNotNull(get(MlKitDigitalInkProvider.ID))
        }
}
