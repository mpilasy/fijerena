package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

/** A chosen channel goes into Recent after the watch delay; a zapped one only once it settles. */
class RecentRecordDelayTest {
    @Test
    fun `a chosen channel waits the watch delay`() {
        assertEquals(10_000L, recentRecordDelayMs(watchDelaySeconds = 10, zapped = false))
        assertEquals(0L, recentRecordDelayMs(watchDelaySeconds = 0, zapped = false))
    }

    @Test
    fun `a zapped channel waits the settle time`() {
        assertEquals(ZAP_SETTLE_MS, recentRecordDelayMs(watchDelaySeconds = 10, zapped = true))
        assertEquals(ZAP_SETTLE_MS, recentRecordDelayMs(watchDelaySeconds = 0, zapped = true))
    }

    @Test
    fun `a watch delay longer than the settle time wins for a zapped channel`() {
        assertEquals(120_000L, recentRecordDelayMs(watchDelaySeconds = 120, zapped = true))
    }
}
