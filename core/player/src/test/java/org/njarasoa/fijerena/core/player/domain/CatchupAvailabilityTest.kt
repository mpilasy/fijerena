package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.EpgProgram

class CatchupAvailabilityTest {
    private val now = 1_791_652_504L
    private val hour = 3_600L
    private val day = 86_400L

    private fun program(
        start: Long,
        end: Long,
        hasArchive: Int? = null,
    ) = EpgProgram(id = "p$start", title = "P", start = start.toString(), end = end.toString(), hasArchive = hasArchive)

    @Test
    fun `an ended programme inside the archive days plays`() {
        assertTrue(CatchupAvailability.isPlayable(3, program(now - 2 * hour, now - hour), now))
    }

    @Test
    fun `a channel without an archive plays nothing`() {
        assertFalse(CatchupAvailability.isPlayable(0, program(now - 2 * hour, now - hour, hasArchive = 1), now))
    }

    @Test
    fun `older than the archive days does not play when the guide says nothing`() {
        assertFalse(CatchupAvailability.isPlayable(3, program(now - 3 * day - hour, now - 3 * day), now))
    }

    @Test
    fun `the panel's own flag wins over the days`() {
        // bears: a 3-day channel still lists programmes from 72.6 h ago as in the archive.
        assertTrue(CatchupAvailability.isPlayable(3, program(now - 3 * day - hour, now - 3 * day, hasArchive = 1), now))
        assertFalse(CatchupAvailability.isPlayable(3, program(now - 10 * hour, now - 9 * hour, hasArchive = 0), now))
    }

    @Test
    fun `a 0 that may come from a guide cached while the programme was on air is not trusted`() {
        assertTrue(CatchupAvailability.isPlayable(3, program(now - 2 * hour, now - hour, hasArchive = 0), now))
    }

    @Test
    fun `the programme on air plays from its start, whatever its flag`() {
        val onAir = program(now - 20 * 60, now + 40 * 60, hasArchive = 0)
        assertTrue(CatchupAvailability.isPlayable(3, onAir, now))
        assertTrue(CatchupAvailability.isOnAir(onAir, now))
    }

    @Test
    fun `a programme still to come does not play`() {
        val later = program(now + hour, now + 2 * hour)
        assertFalse(CatchupAvailability.isPlayable(7, later, now))
        assertFalse(CatchupAvailability.isOnAir(later, now))
    }

    @Test
    fun `a programme without times does not play`() {
        assertFalse(CatchupAvailability.isPlayable(7, program(0, 0), now))
    }

    @Test
    fun `the window starts two minutes early on a whole minute and ends five minutes late`() {
        // 15:15:30 → 15:45:00
        val start = 1_791_645_330L
        val window = CatchupAvailability.window(program(start, start + 29 * 60 + 30))
        assertEquals(1_791_645_180L, window.startEpochSec) // 15:13:00
        assertEquals(150L, window.programOffsetSec)
        assertEquals(37 * 60L, window.durationSec) // 15:13 to 15:50
    }
}
