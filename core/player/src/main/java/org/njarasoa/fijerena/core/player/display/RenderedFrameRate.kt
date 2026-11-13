package org.njarasoa.fijerena.core.player.display

/**
 * Counts the frames that the player shows in each [WINDOW_MS] of the clock: the "measured" rate of
 * Stats for Nerds when the format and the frame times give no known rate (a 10 fps film, say).
 * The frame metadata listener calls [onFrame] for each frame about to show, so the count changes
 * during playback. (Media3 gives its frame processing offsets only when the renderer stops.) The
 * frames are released on display refreshes (16.7 ms at 60 Hz), so a window of 2 seconds keeps the
 * result within about 1 % (10 fps reads 9.9 to 10.1).
 *
 * A gap longer than [MAX_GAP_MS] between two frames (a pause, a seek, a stall, a new stream)
 * starts a new window, so the gap does not make the rate lower.
 *
 * Not thread-safe: one thread (the playback thread) calls it.
 */
class RenderedFrameRate {
    private var windowStartMs = NO_TIME
    private var lastFrameMs = NO_TIME
    private var frames = 0

    /** Adds the frame shown at [nowMs] (elapsed realtime) and returns the rate when a window ends, else null. */
    fun onFrame(nowMs: Long): Float? {
        if (lastFrameMs == NO_TIME || nowMs - lastFrameMs > MAX_GAP_MS) {
            windowStartMs = nowMs
            frames = 0
        } else {
            frames++
        }
        lastFrameMs = nowMs
        val elapsedMs = nowMs - windowStartMs
        return if (elapsedMs >= WINDOW_MS) {
            val rate = frames * MS_PER_SECOND / elapsedMs
            windowStartMs = nowMs
            frames = 0
            rate
        } else {
            null
        }
    }

    companion object {
        /** The longest time between two frames of one run of playback: 500 ms, 2 fps. */
        const val MAX_GAP_MS = 500L

        /** The time that one rate covers. */
        const val WINDOW_MS = 2_000L

        private const val MS_PER_SECOND = 1_000f
        private const val NO_TIME = Long.MIN_VALUE
    }
}
