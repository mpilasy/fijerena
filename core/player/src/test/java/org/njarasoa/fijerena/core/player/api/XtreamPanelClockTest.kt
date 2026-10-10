package org.njarasoa.fijerena.core.player.api

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.XtreamServerInfo
import java.time.ZoneId
import java.time.ZoneOffset

class XtreamPanelClockTest {
    private fun info(
        timezone: String?,
        timestampNow: Long?,
        timeNow: String?,
    ) = XtreamServerInfo(
        url = "panel",
        port = "80",
        serverProtocol = "http",
        timezone = timezone,
        timestampNow = timestampNow,
        timeNow = timeNow,
    )

    // bears, 2026-10-10 (docs/plans/archive/20261010_catchup-plan.md → "Facts measured").
    private val bears = info("Europe/Amsterdam", 1791652504, "2026-10-10 19:15:04")

    @Test
    fun `a name that agrees with the measured offset is kept`() {
        assertEquals(ZoneId.of("Europe/Amsterdam"), XtreamPanelClock.from(bears).zone)
    }

    @Test
    fun `the start is written in the panel's wall clock`() {
        // The RTP 1 programme of 15:15 UTC (1791645300) is 17:15 in Amsterdam.
        assertEquals("2026-10-10:17-15", XtreamPanelClock.from(bears).timeshiftStart(1791645300))
    }

    @Test
    fun `a programme before the DST change keeps its summer offset`() {
        // Logged in after the clocks went back (CET, +1), replaying 24 October 20:00 UTC (CEST, +2).
        val winterLogin = info("Europe/Amsterdam", 1793041200, "2026-10-26 20:00:00")
        assertEquals("2026-10-24:22-00", XtreamPanelClock.from(winterLogin).timeshiftStart(1792872000))
    }

    @Test
    fun `a wrong name gives way to the measured offset`() {
        val clock = XtreamPanelClock.from(info("UTC", 1791652504, "2026-10-10 19:15:04"))
        assertEquals(ZoneOffset.ofHours(2), clock.zone)
    }

    @Test
    fun `a missing name uses the measured offset, rounded to a quarter hour`() {
        val clock = XtreamPanelClock.from(info(null, 1791652504, "2026-10-10 19:15:51"))
        assertEquals(ZoneOffset.ofHours(2), clock.zone)
    }

    @Test
    fun `without a clock reading the name is used`() {
        assertEquals(ZoneId.of("America/New_York"), XtreamPanelClock.from(info("America/New_York", null, null)).zone)
    }

    @Test
    fun `a panel that says nothing is taken as UTC`() {
        assertEquals(ZoneOffset.UTC, XtreamPanelClock.from(info(null, null, null)).zone)
        assertEquals(ZoneOffset.UTC, XtreamPanelClock.from(info("Not/AZone", null, "garbage")).zone)
        assertEquals(ZoneOffset.UTC, XtreamPanelClock.from(null).zone)
    }
}
