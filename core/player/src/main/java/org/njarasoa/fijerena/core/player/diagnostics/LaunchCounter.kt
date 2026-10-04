package org.njarasoa.fijerena.core.player.diagnostics

import android.util.Log
import java.io.File

/**
 * Counts launches that never reached a healthy state, in a plain text file (one epoch-ms per
 * line) — not SharedPreferences or Room, since either may be the very thing crashing the app.
 * [recordLaunch] runs at process start; [markHealthy] empties the file once the app has proved it
 * can run. Launches older than [WINDOW_MS] are forgotten, so a crash now and then never adds up.
 *
 * Never throws: an unreadable or corrupt file counts as no unfinished launches, and a write that
 * fails only loses this launch's entry. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-10.
 */
class LaunchCounter(
    private val file: File,
    private val clock: () -> Long,
) {
    /**
     * Records this launch. True when at least [THRESHOLD] earlier launches within [WINDOW_MS]
     * never got as far as [markHealthy] — this one should start in safe mode.
     */
    fun recordLaunch(): Boolean {
        val now = clock()
        // A timestamp in the future means the clock went back; keeping it would hold a stale
        // launch in the window for as long as the clock is behind.
        val unfinished = readLaunches().filter { it in (now - WINDOW_MS)..now }.takeLast(THRESHOLD)
        write(unfinished + now)
        return unfinished.size >= THRESHOLD
    }

    /** This launch ran fine: forget every unfinished one. */
    fun markHealthy() {
        try {
            file.delete()
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            Log.w(TAG, "Couldn't reset the launch counter", e)
        }
    }

    private fun readLaunches(): List<Long> =
        try {
            if (file.isFile) file.readLines().mapNotNull { it.trim().toLongOrNull() } else emptyList()
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            Log.w(TAG, "Couldn't read the launch counter", e)
            emptyList()
        }

    private fun write(launches: List<Long>) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(launches.joinToString("\n"))
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            Log.w(TAG, "Couldn't write the launch counter", e)
        }
    }

    companion object {
        private const val TAG = "LaunchCounter"

        /** Unfinished launches in a row that put the next one in safe mode. */
        const val THRESHOLD = 3

        /** How far back an unfinished launch still counts. */
        const val WINDOW_MS = 10 * 60 * 1000L
    }
}
