package org.njarasoa.fijerena.core.network.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A TMDB-based list shows the original name in Latin letters, else the English name. */
class TmdbListTitleTest {
    @Test
    fun `an original name in Latin letters is kept, accents and punctuation too`() {
        assertEquals("Le Fabuleux Destin d'Amélie Poulain", tmdbListTitle("Le Fabuleux Destin d'Amélie Poulain", "Amélie"))
        assertEquals("La casa de papel", tmdbListTitle("La casa de papel", "Money Heist"))
        assertEquals("The Godfather Part II", tmdbListTitle("The Godfather Part II", "The Godfather Part II"))
        assertEquals("Blade Runner 2049", tmdbListTitle("Blade Runner 2049", "Blade Runner 2049"))
    }

    @Test
    fun `an original name in another script gives the English name`() {
        assertEquals("Parasite", tmdbListTitle("기생충", "Parasite"))
        assertEquals("Spirited Away", tmdbListTitle("千と千尋の神隠し", "Spirited Away"))
        assertEquals("Leviathan", tmdbListTitle("Левиафан", "Leviathan"))
        assertEquals("Squid Game", tmdbListTitle("오징어 게임 2", "Squid Game"))
    }

    @Test
    fun `a blank original name gives the English name`() {
        assertEquals("Dune", tmdbListTitle(null, "Dune"))
        assertEquals("Dune", tmdbListTitle("  ", "Dune"))
    }

    @Test
    fun `no name gives null, and a lone original name is kept`() {
        assertNull(tmdbListTitle(null, null))
        assertNull(tmdbListTitle("", " "))
        assertEquals("기생충", tmdbListTitle("기생충", null))
    }

    @Test
    fun `a TMDB title reads its film or show fields`() {
        assertEquals("Amélie", TmdbRecommendation(id = 1, title = "Amelie", originalTitle = "Amélie").listTitle)
        assertEquals("Money Heist", TmdbRecommendation(id = 2, name = "Money Heist", originalName = "Каса").listTitle)
    }
}
