package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** XMLTV times as feeds write them: with or without seconds, with or without a space before the zone. */
class XmltvTimestampTest {
    private val sixPmUtc = Instant.parse("2026-11-11T18:00:00Z").epochSecond

    private fun parse(ts: String) = XmltvParser.parseTimestamp(ts, timezoneOverrideHours = 0)

    @Test
    fun `14 digits with a space before the zone`() {
        assertEquals(sixPmUtc, parse("20261111180000 +0000"))
        assertEquals(sixPmUtc, parse("20261111210000 +0300"))
    }

    @Test
    fun `12 digits, without seconds`() {
        assertEquals(sixPmUtc, parse("202611111800 +0000"))
        assertEquals(sixPmUtc, parse("202611111800"))
        assertEquals(sixPmUtc, parse("202611112100 +0300"))
    }

    @Test
    fun `no space before the zone keeps the zone`() {
        assertEquals(sixPmUtc, parse("20261111210000+0300"))
        assertEquals(sixPmUtc, parse("202611112100+0300"))
        assertEquals(sixPmUtc, parse("20261111123000-05:30"))
    }

    @Test
    fun `no zone is UTC`() {
        assertEquals(sixPmUtc, parse("20261111180000"))
    }

    @Test
    fun `a time without a full date is not read`() {
        assertEquals(0L, parse(""))
        assertEquals(0L, parse("2026111"))
        assertEquals(0L, parse("not a time"))
        assertNull(XmltvParser.splitTimestamp("202611111800000000"))
    }

    @Test
    fun `the zone part is split off with or without a space`() {
        assertEquals("20261111180000" to "+0300", XmltvParser.splitTimestamp("20261111180000 +0300"))
        assertEquals("20261111180000" to "+0300", XmltvParser.splitTimestamp("202611111800+0300"))
        assertEquals("20261111000000" to "", XmltvParser.splitTimestamp("20261111"))
    }
}
