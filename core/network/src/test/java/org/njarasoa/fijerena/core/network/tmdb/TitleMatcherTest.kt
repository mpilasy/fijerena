package org.njarasoa.fijerena.core.network.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wrong match here produces a recommendation row pointing at a title the provider does not
 * carry, which is worse than no row at all — so the near-miss cases matter more than the hits.
 */
class TitleMatcherTest {
    @Test
    fun `strips provider prefixes`() {
        assertEquals("dune part two", TitleMatcher.normalize("EN - Dune: Part Two").text)
        assertEquals("dune part two", TitleMatcher.normalize("4K - Dune: Part Two").text)
        assertEquals("dune part two", TitleMatcher.normalize("[VIP] Dune: Part Two").text)
        assertEquals("dune part two", TitleMatcher.normalize("|FR| Dune: Part Two").text)
    }

    @Test
    fun `strips stacked provider prefixes`() {
        assertEquals("dune part two", TitleMatcher.normalize("EN - 4K - Dune: Part Two").text)
        assertEquals("dune part two", TitleMatcher.normalize("[VIP] MULTI - Dune: Part Two").text)
    }

    @Test
    fun `removes a bracketed year outright`() {
        assertEquals(NormalizedTitle("dune", "dune", 2021), TitleMatcher.normalize("Dune (2021)"))
        assertEquals(NormalizedTitle("dune", "dune", 2021), TitleMatcher.normalize("EN - Dune [2021]"))
    }

    @Test
    fun `keeps a bare trailing year in the text but records it`() {
        assertEquals(NormalizedTitle("dune 2021", "dune", 2021), TitleMatcher.normalize("Dune 2021"))
        assertEquals(
            NormalizedTitle("blade runner 2049", "blade runner", 2049),
            TitleMatcher.normalize("Blade Runner 2049"),
        )
    }

    @Test
    fun `leaves a number that cannot be a year alone`() {
        assertEquals(NormalizedTitle("ocean s 11", "ocean s 11", null), TitleMatcher.normalize("Ocean's 11"))
    }

    @Test
    fun `matches a bare trailing year against a title without one`() {
        assertTrue(TitleMatcher.matches("Dune 2021", null, "Dune", 2021))
    }

    @Test
    fun `does not let a numbered title answer to its prefix`() {
        // "Blade Runner 2049" only equals "Blade Runner" once the trailing number is treated as a
        // year, so the years have to agree — and 1982 is not 2049.
        assertFalse(TitleMatcher.matches("Blade Runner 2049", null, "Blade Runner", 1982))
        assertFalse(TitleMatcher.matches("Blade Runner 2049", null, "Blade Runner", null))
        assertTrue(TitleMatcher.matches("Blade Runner 2049", 2017, "Blade Runner 2049", 2017))
    }

    @Test
    fun `does not treat a colon as a tag separator`() {
        assertEquals("dune part two", TitleMatcher.normalize("Dune: Part Two").text)
        assertEquals("x men", TitleMatcher.normalize("X-Men").text)
    }

    @Test
    fun `strips accents and punctuation`() {
        assertEquals("amelie", TitleMatcher.normalize("Amélie").text)
        assertEquals("wall e", TitleMatcher.normalize("WALL·E").text)
        assertEquals("spider man no way home", TitleMatcher.normalize("Spider-Man: No Way Home").text)
    }

    @Test
    fun `normalizes a bare title to itself`() {
        val normalized = TitleMatcher.normalize("Dune")
        assertEquals("dune", normalized.text)
        assertNull(normalized.year)
    }

    @Test
    fun `matches the same title across provider decoration`() {
        assertTrue(TitleMatcher.matches("EN - Dune: Part Two (2024)", null, "Dune: Part Two", 2024))
    }

    @Test
    fun `rejects a title that merely contains the other`() {
        assertFalse(TitleMatcher.matches("Dune", null, "Dune: Part Two", 2024))
        assertFalse(TitleMatcher.matches("Dune: Part Two", null, "Dune", 2021))
    }

    @Test
    fun `accepts a one year disagreement`() {
        assertTrue(TitleMatcher.matches("Shogun", 2024, "Shogun", 2023))
    }

    @Test
    fun `rejects a remake years apart`() {
        assertFalse(TitleMatcher.matches("Dune", 1984, "Dune", 2021))
    }

    @Test
    fun `accepts when either side has no year`() {
        assertTrue(TitleMatcher.matches("Dune", null, "Dune", 2021))
        assertTrue(TitleMatcher.matches("Dune", 2021, "Dune", null))
    }

    @Test
    fun `prefers the provider metadata year over one in the title`() {
        // The title says 1984, the provider's metadata says 2021 — metadata wins, so this matches.
        assertTrue(TitleMatcher.matches("Dune (1984)", 2021, "Dune", 2021))
    }

    @Test
    fun `rejects a blank title`() {
        assertFalse(TitleMatcher.matches("", null, "", null))
        assertFalse(TitleMatcher.matches("[VIP]", null, "Dune", 2021))
    }

    @Test
    fun `strips a provider prefix for display without touching the rest`() {
        assertEquals("The Title", TitleMatcher.stripProviderPrefix("NF - The Title"))
        assertEquals("The Title", TitleMatcher.stripProviderPrefix("EN - The Title"))
        assertEquals("Dune: Part Two", TitleMatcher.stripProviderPrefix("4K - Dune: Part Two"))
        assertEquals("Vengeance (2022)", TitleMatcher.stripProviderPrefix("UNV - Vengeance (2022)"))
    }

    @Test
    fun `strips the compound tags real catalogues use`() {
        assertEquals("Vengeance (2026)", TitleMatcher.stripProviderPrefix("4K-AMZ - Vengeance (2026)"))
        assertEquals("Fistful of Vengeance (2022)", TitleMatcher.stripProviderPrefix("KU-S - Fistful of Vengeance (2022)"))
        assertEquals("Gundam: Requiem for Vengeance", TitleMatcher.stripProviderPrefix("AR-SUBS - Gundam: Requiem for Vengeance"))
    }

    @Test
    fun `display strip keeps a title that only looks like a tag`() {
        // "Alien" is five characters, past the cap, so the spaced dash is not read as a separator.
        assertEquals("Alien - Covenant", TitleMatcher.stripProviderPrefix("Alien - Covenant"))
        assertEquals("X-Men", TitleMatcher.stripProviderPrefix("X-Men"))
        assertEquals("Dune: Part Two", TitleMatcher.stripProviderPrefix("Dune: Part Two"))
    }

    @Test
    fun `display strip never returns nothing`() {
        // A name that is only a tag would otherwise leave an empty card label.
        assertEquals("NF -", TitleMatcher.stripProviderPrefix("NF -"))
        assertEquals("[VIP]", TitleMatcher.stripProviderPrefix("[VIP]"))
    }
}
