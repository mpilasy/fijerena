package org.njarasoa.fijerena.core.ui.performance

import android.os.Debug
import android.view.Choreographer

/**
 * Process-side playback health: heap pressure, GC activity, and dropped UI frames.
 *
 * The player's own stats only see what ExoPlayer reports — buffer level, rebuffers, decoder
 * frame drops. A stutter caused by the *app* starving itself (GC thrash, a background job
 * allocating hard) never touches any of those, so the overlay could truthfully report a
 * perfectly healthy stream while the picture juddered. These are the missing signals.
 */
object AppPerformanceMonitor {
    data class HeapSnapshot(
        val usedMb: Int,
        val maxMb: Int,
        val gcCount: Long,
        val gcTimeMs: Long,
    ) {
        val usedPercent: Int get() = if (maxMb > 0) (usedMb * 100) / maxMb else 0
    }

    fun heapSnapshot(): HeapSnapshot {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        return HeapSnapshot(
            usedMb = (usedBytes / BYTES_PER_MB).toInt(),
            maxMb = (runtime.maxMemory() / BYTES_PER_MB).toInt(),
            gcCount = runtimeStat("art.gc.gc-count"),
            gcTimeMs = runtimeStat("art.gc.gc-time"),
        )
    }

    private fun runtimeStat(name: String): Long = Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L

    /**
     * Counts frames the UI thread missed, via [Choreographer]. Cheap enough to leave running
     * while the stats panel is open; [stop] unregisters the callback.
     */
    class JankMonitor {
        @Volatile
        var skippedFrames: Long = 0
            private set

        private var lastFrameTimeNanos = 0L
        private var running = false

        private val callback =
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!running) return
                    if (lastFrameTimeNanos != 0L) {
                        val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000
                        // Anything beyond two vsyncs is a frame the user did not get.
                        if (deltaMs > FRAME_BUDGET_MS * 2) {
                            skippedFrames += (deltaMs / FRAME_BUDGET_MS) - 1
                        }
                    }
                    lastFrameTimeNanos = frameTimeNanos
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }

        fun start() {
            if (running) return
            running = true
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(callback)
        }

        fun stop() {
            running = false
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    private const val BYTES_PER_MB = 1024L * 1024L

    /** 60Hz budget. Only used to turn a frame gap into a count, so exactness doesn't matter. */
    private const val FRAME_BUDGET_MS = 16
}
