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

    @Test
    fun `4K catalogue tags become one badge`() {
        assertEquals(ParsedTitle("The Perfect Lie (2026)", "4K-NF"), parseDisplayTitle("4K-NF - The Perfect Lie (2026) (NL)"))
        assertEquals(ParsedTitle("Silo (2023)", "4K-A+"), parseDisplayTitle("4K-A+ - Silo (2023) (US)"))
        assertEquals(ParsedTitle("Loki", "4K-D+"), parseDisplayTitle("4K-D+ - Loki"))
        assertEquals(ParsedTitle("Neagley (2026)", "4K-AMZ"), parseDisplayTitle("4K-AMZ - Neagley (2026) (US)"))
        assertEquals(ParsedTitle("Dune", "4K-FR-HDR"), parseDisplayTitle("4K-FR-HDR - Dune"))
        assertEquals(ParsedTitle("Dune", "4K-OSN+"), parseDisplayTitle("4K-OSN+ - Dune"))
        assertEquals(ParsedTitle("Dune", "4K-FR"), parseDisplayTitle("4k-FR - Dune"))
        assertEquals(ParsedTitle("Dune", "4K-DE"), parseDisplayTitle("4K-DE  - Dune"))
    }

    @Test
    fun `a title starting with 4K- but no tag separator is not badged`() {
        assertEquals(ParsedTitle("4K-Ultra Nature", null), parseDisplayTitle("4K-Ultra Nature"))
    }
}
