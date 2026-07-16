package com.alexdremov.notate.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.alexdremov.notate.config.CanvasConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/**
 * Rasterizes a PDF into page bitmaps for import as locked background images.
 *
 * PdfRenderer is NOT thread-safe and permits only ONE open page at a time. A single global
 * [pdfLock] serializes ALL access across concurrent imports, and pages are opened/rendered/closed
 * strictly one at a time on the caller's (IO) context.
 */
object PdfImportHelper {
    private const val TAG = "PdfImportHelper"

    private val pdfLock = Mutex()

    /** Page-0 dimensions expressed in canvas world units. */
    data class PageSize(
        val widthUnits: Float,
        val heightUnits: Float,
    )

    /**
     * Rasterizes each page of [uri] serially. For each page, [onPage] receives a freshly rendered
     * ARGB_8888 [Bitmap]; the bitmap is recycled by this helper AFTER [onPage] returns, so the
     * callback must fully consume it (e.g. persist it) before returning. Returns page-0 size in
     * canvas units, or null if the PDF could not be opened/decoded.
     *
     * MUST be invoked from a background (IO) coroutine — it performs blocking file + PdfRenderer work.
     */
    suspend fun rasterize(
        context: Context,
        uri: Uri,
        onPage: suspend (index: Int, pageCount: Int, bitmap: Bitmap, page0: PageSize) -> Unit,
    ): PageSize? {
        val tempFile = File(context.cacheDir, "pdf_import_${UUID.randomUUID()}.pdf")
        try {
            val copied =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
            if (!copied) {
                Logger.e(TAG, "Failed to open PDF stream: $uri")
                return null
            }

            return pdfLock.withLock {
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    val pageCount = renderer.pageCount
                    if (pageCount <= 0) return@withLock null

                    val factor = CanvasConfig.PDF_POINTS_TO_CANVAS_UNITS
                    var page0: PageSize? = null

                    for (index in 0 until pageCount) {
                        val page = renderer.openPage(index)
                        val bitmap: Bitmap
                        try {
                            if (index == 0) {
                                page0 = PageSize(page.width * factor, page.height * factor)
                            }
                            val pageW = page.width.toFloat().coerceAtLeast(1f)
                            val pageH = page.height.toFloat().coerceAtLeast(1f)
                            val maxDim = CanvasConfig.PDF_IMPORT_MAX_RENDER_DIMENSION.toFloat()
                            val renderScale = (maxDim / maxOf(pageW, pageH)).coerceAtMost(factor)
                            val bmpW = (pageW * renderScale).toInt().coerceAtLeast(1)
                            val bmpH = (pageH * renderScale).toInt().coerceAtLeast(1)
                            bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        } finally {
                            page.close()
                        }
                        try {
                            onPage(index, pageCount, bitmap, page0!!)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    page0
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to rasterize PDF", e)
                    null
                } finally {
                    renderer?.close()
                    pfd?.close()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "PDF import failed", e)
            return null
        } finally {
            tempFile.delete()
        }
    }
}
