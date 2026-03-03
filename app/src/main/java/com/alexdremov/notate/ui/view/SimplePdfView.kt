package com.alexdremov.notate.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.alexdremov.notate.util.Logger
import com.onyx.android.sdk.api.device.EpdDeviceManager
import java.io.File

class SimplePdfView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : ScrollView(context, attrs, defStyleAttr) {
        private val container: LinearLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setBackgroundColor(Color.WHITE)
            }

        private var scaleFactor = 1.0f
        private val scaleDetector: ScaleGestureDetector

        init {
            addView(container)

            scaleDetector =
                ScaleGestureDetector(
                    context,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            EpdDeviceManager.enterAnimationUpdate(true)
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            scaleFactor *= detector.scaleFactor
                            scaleFactor = scaleFactor.coerceIn(0.5f, 4.0f)

                            container.scaleX = scaleFactor
                            container.scaleY = scaleFactor

                            // Since container is inside ScrollView, we need to adjust its pivot
                            // based on the current scroll position to keep the zoom centered
                            container.pivotX = detector.focusX + scrollX
                            container.pivotY = detector.focusY + scrollY

                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            EpdDeviceManager.exitAnimationUpdate(true)
                            invalidate()
                        }
                    },
                )
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(ev)
            if (scaleDetector.isInProgress) {
                return true
            }
            return super.dispatchTouchEvent(ev)
        }

        fun setPdfFile(file: File) {
            container.removeAllViews()
            scaleFactor = 1.0f
            container.scaleX = 1.0f
            container.scaleY = 1.0f

            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)

                    // Render at a higher base resolution to allow for zooming without immediate blur
                    val targetWidth = 1500
                    val targetHeight = (targetWidth * (page.height.toFloat() / page.width)).toInt()

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val imageView =
                        ImageView(context).apply {
                            layoutParams =
                                LinearLayout
                                    .LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                    ).apply {
                                        setMargins(0, 0, 0, 16)
                                    }
                            adjustViewBounds = true
                            setImageBitmap(bitmap)
                        }

                    container.addView(imageView)
                    page.close()
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                Logger.e("SimplePdfView", "Failed to load PDF: ${file.path}", e)
            }
        }
    }
