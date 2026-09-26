package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleLanguageTest {
    @Test
    fun `language prefix becomes badge`() {
        assertEquals(ParsedTitle("Breaking Bad", "EN"), parseDisplayTitle("EN - Breaking Bad"))
    }

    @Test
    fun `D+ and A+ prefixes become uppercase badges in either case`() {
        assertEquals(ParsedTitle("Loki", "D+"), parseDisplayTitle("D+ - Loki"))
        assertEquals(ParsedTitle("Loki", "D+"), parseDisplayTitle("d+: Loki"))
        assertEquals(ParsedTitle("Severance", "A+"), parseDisplayTitle("A+ - Severance"))
        assertEquals(ParsedTitle("Severance", "A+"), parseDisplayTitle("a+ - Severance"))
    }

    @Test
    fun `D+ and A+ suffixes become badges`() {
        assertEquals(ParsedTitle("Loki", "D+"), parseDisplayTitle("Loki (D+)"))
        assertEquals(ParsedTitle("Severance", "A+"), parseDisplayTitle("Severance (a+)"))
    }

    @Test
    fun `ordinary title casing is not badged`() {
        assertEquals(ParsedTitle("A-Team", null), parseDisplayTitle("A-Team"))
    }
}
