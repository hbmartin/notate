package com.alexdremov.notate.export

import android.content.Context
import com.alexdremov.notate.data.CanvasSession
import com.alexdremov.notate.model.InfiniteCanvasModel
import java.io.OutputStream

/** Public PDF feature boundary for UI export and sync composition. */
class PdfService(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun exportSession(
        session: CanvasSession,
        outputStream: OutputStream,
        isVector: Boolean,
        bitmapScale: Float,
        onProgress: ((Int, String) -> Unit)? = null,
    ) {
        val model = InfiniteCanvasModel()
        model.initializeSession(session.regionManager)
        model.loadFromCanvasData(session.metadata)
        exportModel(model, outputStream, isVector, bitmapScale, onProgress)
    }

    suspend fun exportModel(
        model: InfiniteCanvasModel,
        outputStream: OutputStream,
        isVector: Boolean,
        bitmapScale: Float,
        onProgress: ((Int, String) -> Unit)? = null,
    ) {
        PdfExporter.export(
            context = appContext,
            model = model,
            outputStream = outputStream,
            isVector = isVector,
            callback =
                onProgress?.let { callback ->
                    object : PdfExporter.ProgressCallback {
                        override fun onProgress(
                            progress: Int,
                            message: String,
                        ) = callback(progress, message)
                    }
                },
            bitmapScale = bitmapScale,
        )
    }
}
