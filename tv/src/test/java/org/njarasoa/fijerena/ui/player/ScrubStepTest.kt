package org.njarasoa.fijerena.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.PlaybackState

class ScrubStepTest {
    @Test
    fun `tap moves ten seconds`() {
        assertEquals(10_000L, scrubStepMs(repeatCount = 0, heldMs = 0L))
    }

    @Test
    fun `hold ramps by held time, not repeat count`() {
        // A high repeatCount early in the hold must not jump to the big steps.
        assertEquals(3_000L, scrubStepMs(repeatCount = 30, heldMs = 1_900L))
        assertEquals(10_000L, scrubStepMs(repeatCount = 40, heldMs = 2_000L))
        assertEquals(10_000L, scrubStepMs(repeatCount = 90, heldMs = 4_999L))
        assertEquals(30_000L, scrubStepMs(repeatCount = 100, heldMs = 5_000L))
    }

    @Test
    fun `holding for ten seconds at 20 repeats per second stays controllable`() {
        // First repeat at 400ms, then every 50ms (Android defaults).
        var total = scrubStepMs(0, 0L)
        var t = 400L
        var repeat = 1
        while (t <= 10_000L) {
            total += scrubStepMs(repeat++, t)
            t += 50L
        }
        // ~1.5 min + 10 min + ~50 min: a 2h film's end is reachable in ~15s, but no
        // 40-min-per-second runaway like the old repeatCount tiers.
        assertTrue("total=$total", total in 3_000_000L..4_000_000L)
    }

    @Test
    fun `a tap during the previous tap's seek still counts`() {
        assertTrue(canScrub(PlaybackState.Buffering, durationMs = 60_000L))
        assertTrue(canScrub(PlaybackState.Playing(0L, 60_000L), durationMs = 60_000L))
        assertTrue(canScrub(PlaybackState.Paused(0L, 60_000L), durationMs = 60_000L))
    }

    @Test
    fun `no scrub without a known duration or after the end`() {
        assertFalse(canScrub(PlaybackState.Buffering, durationMs = 0L))
        assertFalse(canScrub(PlaybackState.Playing(0L, 0L), durationMs = 0L))
        assertFalse(canScrub(PlaybackState.Idle, durationMs = 60_000L))
        assertFalse(canScrub(PlaybackState.Ended(60_000L), durationMs = 60_000L))
    }
}
