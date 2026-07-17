package com.alexdremov.notate.ui.interaction

import android.content.Context
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import com.alexdremov.notate.config.CanvasConfig
import com.alexdremov.notate.util.EpdFastModeController
import kotlin.math.hypot

/**
 * Handles Panning and Zooming interactions for the Canvas.
 * Replaces the fragmented logic in OnyxCanvasView.
 */
class ViewportInteractor(
    context: Context,
    private val matrix: Matrix,
    private val invalidateCallback: () -> Unit,
    private val onScaleChanged: () -> Unit,
    private val onInteractionStart: () -> Unit,
    private val onInteractionEnd: () -> Unit,
    private val lastStrokeEndTimeProvider: () -> Long,
) {
    // State
    private var currentScale = 1.0f
    private var isPanning = false
    private var isInteracting = false
    private var hasPerformedScale = false
    private var isFastModeActive = false
    private var allowPinchZoom = true
    private val activePointerIds = linkedSetOf<Int>()

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Smoothing
    private var filteredDx = 0f
    private var filteredDy = 0f
    private val SMOOTHING_FACTOR = 0.5f // Simple EMA smoothing

    // Config
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val POST_STROKE_SETTLING_MS = 150L // Ignore touch right after pen release to prevent jitter

    // Document Centering
    private var isFixedPageCenterHorizontal = false
    private var canvasWidth = 0f
    private var viewWidth = 0

    // Scale Detector
    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    hasPerformedScale = true
                    startInteraction()
                    return super.onScaleBegin(detector)
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    handleScale(detector)
                    return true
                }
            },
        )

    fun onTouchEvent(event: MotionEvent): Boolean =
        com.alexdremov.notate.util.PerformanceProfiler.trace("ViewportInteractor.onTouchEvent") {
            // Palm Rejection / Settling check
            val timeSinceStroke = System.currentTimeMillis() - lastStrokeEndTimeProvider()
            if (timeSinceStroke < POST_STROKE_SETTLING_MS) {
                return@trace false
            }

            if (allowPinchZoom) {
                scaleDetector.onTouchEvent(event)
            }

            // Handle Panning
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerIds.clear()
                    activePointerIds.add(event.getPointerId(event.actionIndex))
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isPanning = false
                    isInteracting = true
                    hasPerformedScale = false
                    filteredDx = 0f
                    filteredDy = 0f
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    activePointerIds.add(event.getPointerId(event.actionIndex))
                    updateFocusPoint(event)
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    val liftedId = event.getPointerId(event.actionIndex)
                    updateFocusPoint(event, liftedId)
                    activePointerIds.remove(liftedId)
                }

                MotionEvent.ACTION_MOVE -> {
                    val focusX = getFocusX(event)
                    val focusY = getFocusY(event)

                    if (!isInteracting) {
                        lastTouchX = focusX
                        lastTouchY = focusY
                        isInteracting = true
                    }

                    val rawDx = focusX - lastTouchX
                    val rawDy = focusY - lastTouchY

                    if (!isPanning) {
                        if (hypot(rawDx, rawDy) > touchSlop) {
                            isPanning = true
                            startInteraction()
                        }
                    }

                    if (isPanning) {
                        // Apply simple smoothing to reduce EPD noise
                        filteredDx = filteredDx * SMOOTHING_FACTOR + rawDx * (1 - SMOOTHING_FACTOR)
                        filteredDy = filteredDy * SMOOTHING_FACTOR + rawDy * (1 - SMOOTHING_FACTOR)

                        val dx = if (isFixedPageCenterHorizontal) 0f else filteredDx
                        matrix.postTranslate(dx, filteredDy)

                        if (isFixedPageCenterHorizontal) {
                            enforceCentering()
                        }

                        com.alexdremov.notate.util.PerformanceProfiler.trace("ViewportInteractor.invalidateCallback") {
                            invalidateCallback()
                        }
                        lastTouchX = focusX
                        lastTouchY = focusY
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isPanning || isInteracting) {
                        endInteraction()
                    }
                    isPanning = false
                    isInteracting = false
                    activePointerIds.clear()
                }
            }

            isPanning || isInteracting
        }

    private fun handleScale(detector: ScaleGestureDetector) {
        com.alexdremov.notate.util.PerformanceProfiler.trace("ViewportInteractor.handleScale") {
            var scaleFactor = detector.scaleFactor
            val newScale = currentScale * scaleFactor

            // Clamp scale
            if (newScale < CanvasConfig.MIN_SCALE) {
                scaleFactor = CanvasConfig.MIN_SCALE / currentScale
                currentScale = CanvasConfig.MIN_SCALE
            } else if (newScale > CanvasConfig.MAX_SCALE) {
                scaleFactor = CanvasConfig.MAX_SCALE / currentScale
                currentScale = CanvasConfig.MAX_SCALE
            } else {
                currentScale = newScale
            }

            matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)

            if (isFixedPageCenterHorizontal) {
                enforceCentering()
            }

            com.alexdremov.notate.util.PerformanceProfiler.trace("ViewportInteractor.invalidateCallback") {
                invalidateCallback()
            }
            onScaleChanged()
        }
    }

    private fun enforceCentering() {
        if (viewWidth <= 0 || canvasWidth <= 0f) return

        // Calculate current translation
        val values = FloatArray(9)
        matrix.getValues(values)
        val currentTx = values[Matrix.MTRANS_X]

        // Target TX = (viewWidth - canvasWidth * currentScale) / 2
        val targetTx = (viewWidth - canvasWidth * currentScale) / 2f
        val deltaX = targetTx - currentTx

        matrix.postTranslate(deltaX, 0f)
    }

    private fun updateFocusPoint(
        event: MotionEvent,
        excludedPointerId: Int? = null,
    ) {
        lastTouchX = getFocusX(event, excludedPointerId)
        lastTouchY = getFocusY(event, excludedPointerId)
    }

    private fun getFocusX(
        event: MotionEvent,
        excludedPointerId: Int? = null,
    ): Float {
        var sum = 0f
        var div = 0
        activePointerIds.forEach { pointerId ->
            if (pointerId == excludedPointerId) return@forEach
            val index = event.findPointerIndex(pointerId)
            if (index < 0) return@forEach
            sum += event.getX(index)
            div++
        }
        return if (div > 0) sum / div else event.x
    }

    private fun getFocusY(
        event: MotionEvent,
        excludedPointerId: Int? = null,
    ): Float {
        var sum = 0f
        var div = 0
        activePointerIds.forEach { pointerId ->
            if (pointerId == excludedPointerId) return@forEach
            val index = event.findPointerIndex(pointerId)
            if (index < 0) return@forEach
            sum += event.getY(index)
            div++
        }
        return if (div > 0) sum / div else event.y
    }

    private fun startInteraction() {
        if (isFastModeActive) return
        isFastModeActive = true
        onInteractionStart()
        EpdFastModeController.enterFastMode()
    }

    private fun endInteraction() {
        if (!isFastModeActive) return
        isFastModeActive = false
        onInteractionEnd()
        EpdFastModeController.exitFastMode()
    }

    fun getCurrentScale() = currentScale

    fun setScale(scale: Float) {
        currentScale = scale
        if (isFixedPageCenterHorizontal) {
            enforceCentering()
        }
    }

    fun isInteracting() = isPanning || isInteracting

    fun isBusy() = isPanning || hasPerformedScale

    fun setFixedPageCenterHorizontal(enabled: Boolean) {
        isFixedPageCenterHorizontal = enabled
        if (enabled) {
            enforceCentering()
            invalidateCallback()
        }
    }

    fun setCanvasWidth(width: Float) {
        canvasWidth = width
        if (isFixedPageCenterHorizontal) {
            enforceCentering()
            invalidateCallback()
        }
    }

    fun setViewWidth(width: Int) {
        viewWidth = width
        if (isFixedPageCenterHorizontal) {
            enforceCentering()
            invalidateCallback()
        }
    }

    fun setAllowPinchZoom(enabled: Boolean) {
        allowPinchZoom = enabled
    }
}
