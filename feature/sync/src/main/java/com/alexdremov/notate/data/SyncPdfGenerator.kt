package com.alexdremov.notate.data

import android.content.Context
import java.io.OutputStream

/** PDF capability supplied to sync by the application composition root. */
interface SyncPdfGenerator {
    suspend fun generate(
        session: CanvasSession,
        outputStream: OutputStream,
        isVector: Boolean,
        bitmapScale: Float,
        onProgress: ((Int, String) -> Unit)?,
    )
}

interface SyncPdfGeneratorOwner {
    val syncPdfGenerator: SyncPdfGenerator
}

fun Context.syncPdfGenerator(): SyncPdfGenerator =
    (applicationContext as? SyncPdfGeneratorOwner)?.syncPdfGenerator
        ?: error("The application has not installed a SyncPdfGenerator")
