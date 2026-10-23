package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Search the guide's airing time names the end's day when it ends on another day. See the guide overlaps plan. */
class EndDayLabelTest {
    private val chicago = ZoneId.of("America/Chicago")

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, chicago).toEpochSecond()

    @Test
    fun `a programme ending the same day has no end day`() {
        assertNull(endDayLabel(at(11, 21, 50), at(11, 23, 45), chicago, Locale.UK))
    }

    @Test
    fun `panel-a' 49ers game from Sunday 18-25 to Monday 15-00 names Monday`() {
        assertEquals("Mon", endDayLabel(at(11, 18, 25), at(12, 15), chicago, Locale.UK))
    }

    @Test
    fun `the day is the viewer's, not UTC's`() {
        // 18:00 to 23:00 in Chicago is the same day there, though it ends the next day in UTC.
        assertNull(endDayLabel(at(11, 18), at(11, 23), chicago, Locale.UK))
    }
}
