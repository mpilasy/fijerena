package org.njarasoa.fijerena.core.player.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeSeekTargetTest {
    private val duration = 600_000L

    @Test
    fun `with no seek pending it counts from the player's position`() {
        assertEquals(70_000L, relativeSeekTarget(null, 60_000L, 10_000L, duration))
        assertEquals(50_000L, relativeSeekTarget(null, 60_000L, -10_000L, duration))
    }

    @Test
    fun `while a seek is pending it counts from the target, not the old position`() {
        // The player still buffers at 70 s after the first tap; the second tap goes to 80 s.
        assertEquals(80_000L, relativeSeekTarget(70_000L, 60_000L, 10_000L, duration))
    }

    @Test
    fun `three taps in a burst add up to 30 s`() {
        var pending: Long? = null
        repeat(3) { pending = relativeSeekTarget(pending, 60_000L, 10_000L, duration) }
        assertEquals(90_000L, pending)
    }

    @Test
    fun `the target stays inside the film`() {
        assertEquals(0L, relativeSeekTarget(5_000L, 60_000L, -10_000L, duration))
        assertEquals(duration, relativeSeekTarget(595_000L, 60_000L, 10_000L, duration))
    }
}
