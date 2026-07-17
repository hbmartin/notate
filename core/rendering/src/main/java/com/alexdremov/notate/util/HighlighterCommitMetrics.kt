package com.alexdremov.notate.util

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class HighlighterMetricsSnapshot(
    val count: Int,
    val lastMilliseconds: Double,
    val averageMilliseconds: Double,
    val worstMilliseconds: Double,
    val lastRegeneratedTiles: Int,
    val totalRegeneratedTiles: Int,
    val instrumentationAvailable: Boolean,
)

/** Current-process metrics only; no notebook or coordinate data is retained. */
object HighlighterCommitMetrics {
    private val count = AtomicInteger(0)
    private val totalNanos = AtomicLong(0)
    private val lastNanos = AtomicLong(0)
    private val worstNanos = AtomicLong(0)
    private val lastTiles = AtomicInteger(0)
    private val totalTiles = AtomicInteger(0)
    private val worstSchedulingOverheadNanos = AtomicLong(0)

    fun recordSchedulingOverhead(nanos: Long) {
        worstSchedulingOverheadNanos.updateAndGet { maxOf(it, nanos) }
    }

    fun record(
        durationNanos: Long,
        regeneratedTiles: Int,
    ) {
        count.incrementAndGet()
        totalNanos.addAndGet(durationNanos)
        lastNanos.set(durationNanos)
        worstNanos.updateAndGet { maxOf(it, durationNanos) }
        lastTiles.set(regeneratedTiles)
        totalTiles.addAndGet(regeneratedTiles)
    }

    fun snapshot(): HighlighterMetricsSnapshot {
        val samples = count.get()
        val toMs = { nanos: Long -> nanos / 1_000_000.0 }
        return HighlighterMetricsSnapshot(
            count = samples,
            lastMilliseconds = toMs(lastNanos.get()),
            averageMilliseconds = if (samples == 0) 0.0 else toMs(totalNanos.get()) / samples,
            worstMilliseconds = toMs(worstNanos.get()),
            lastRegeneratedTiles = lastTiles.get(),
            totalRegeneratedTiles = totalTiles.get(),
            instrumentationAvailable = worstSchedulingOverheadNanos.get() < 1_000_000L,
        )
    }
}
