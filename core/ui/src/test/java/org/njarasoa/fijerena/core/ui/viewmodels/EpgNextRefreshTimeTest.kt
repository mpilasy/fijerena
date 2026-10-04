package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EPG management's "next refresh" for a stored refresh time it can't parse: no schedule, not a
 * crash every time the screen opens — a value such as "4:00 AM" synced from another device did
 * that on every linked device. See docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-09.
 */
class EpgNextRefreshTimeTest {
    @Test
    fun `a malformed refresh time means no next run`() {
        listOf("4:00 AM", "noon:30", "12:xx", "").forEach {
            assertEquals(it, 0L, EpgManagementViewModel.calculateNextRefreshTime(it, 24))
        }
    }

    @Test
    fun `a valid refresh time gives a run within one interval`() {
        val now = System.currentTimeMillis()

        val next = EpgManagementViewModel.calculateNextRefreshTime("04:00", 24)

        assertTrue("next run at $next", next >= now && next <= now + 24 * 3600 * 1000L)
    }
}
