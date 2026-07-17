package com.alexdremov.notate.hwr

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitDigitalInkProvider(
    context: Context,
) : HandwritingRecognitionProvider {
    @Suppress("unused")
    private val appContext = context.applicationContext
    private val modelManager = RemoteModelManager.getInstance()

    override val id: String = ID
    override val displayName: String = "ML Kit Digital Ink"
    override val revision: String = "ml-kit-digital-ink:19.0.0"
    override val capabilities =
        RecognitionProviderCapabilities(
            offlineAfterDownload = true,
            supportsConfidence = false,
            supportedLanguageTags = emptySet(),
        )

    override suspend fun isModelAvailable(languageTag: String): Boolean =
        await { continuation ->
            modelManager
                .isModelDownloaded(model(languageTag))
                .addOnSuccessListener(continuation::resume)
                .addOnFailureListener(continuation::resumeWithException)
        }

    override suspend fun downloadModel(languageTag: String) {
        awaitUnit { onSuccess, onFailure ->
            modelManager
                .download(model(languageTag), DownloadConditions.Builder().build())
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener(onFailure)
        }
    }

    override suspend fun removeModel(languageTag: String) {
        awaitUnit { onSuccess, onFailure ->
            modelManager
                .deleteDownloadedModel(model(languageTag))
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener(onFailure)
        }
    }

    override suspend fun recognizeLine(request: HandwritingLineRequest): List<RecognitionCandidate> {
        val recognitionModel = model(request.languageTag)
        if (!isModelAvailable(request.languageTag)) {
            throw IllegalStateException("ML Kit model is not downloaded for ${request.languageTag}")
        }
        val recognizer =
            DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(recognitionModel).build(),
            )
        return try {
            val inkBuilder = Ink.builder()
            request.strokes.forEach { stroke ->
                val strokeBuilder = Ink.Stroke.builder()
                stroke.points.forEach { point ->
                    val inkPoint =
                        if (point.timestampMillis == null) {
                            Ink.Point.create(point.x, point.y)
                        } else {
                            Ink.Point.create(point.x, point.y, point.timestampMillis)
                        }
                    strokeBuilder.addPoint(inkPoint)
                }
                inkBuilder.addStroke(strokeBuilder.build())
            }
            val result =
                await { continuation ->
                    recognizer
                        .recognize(inkBuilder.build())
                        .addOnSuccessListener(continuation::resume)
                        .addOnFailureListener(continuation::resumeWithException)
                }
            result.candidates.mapNotNull { candidate ->
                candidate.text.trim().takeIf(String::isNotEmpty)?.let {
                    RecognitionCandidate(it, null, request.bounds)
                }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun model(languageTag: String): DigitalInkRecognitionModel {
        val identifier =
            try {
                DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            } catch (error: MlKitException) {
                throw IllegalArgumentException("Invalid ML Kit language tag: $languageTag", error)
            } ?: throw IllegalArgumentException("ML Kit has no handwriting model for $languageTag")
        return DigitalInkRecognitionModel.builder(identifier).build()
    }

    private suspend fun <T> await(
        register: (kotlin.coroutines.Continuation<T>) -> Unit,
    ): T =
        suspendCancellableCoroutine { continuation ->
            register(continuation)
        }

    private suspend fun awaitUnit(
        register: (() -> Unit, (Exception) -> Unit) -> Unit,
    ) {
        suspendCancellableCoroutine { continuation ->
            register(
                { continuation.resume(Unit) },
                continuation::resumeWithException,
            )
        }
    }

    companion object {
        const val ID = "ml-kit-digital-ink"
    }
}
