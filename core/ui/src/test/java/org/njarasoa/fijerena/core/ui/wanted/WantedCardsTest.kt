package org.njarasoa.fijerena.core.ui.wanted

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.wanted.CatalogueHit
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.network.xtream.db.WantedTitleEntity
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * The Wanted list's cards: available titles first, the badge's count, what a banner still has to
 * announce, and where a card goes. See the wanted titles plan, Phase 5.
 */
class WantedCardsTest {
    private val dune = WantedTitleEntity("p", ContentType.MOVIES, "1", "Dune", addedAt = 3)
    private val alien = WantedTitleEntity("p", ContentType.MOVIES, "2", "Alien", addedAt = 2, notifiedAt = 5, seenAt = 6)
    private val bear = WantedTitleEntity("p", ContentType.TV_SHOWS, "3", "The Bear", addedAt = 1)
    private val hit = CatalogueHit(7, "42", "EN - Alien", "films", "2")
    private val state =
        WantedTitles.State(
            titles = listOf(dune, alien, bear),
            available =
                mapOf(
                    WantedTitles.Key(ContentType.MOVIES, "2") to listOf(hit),
                    WantedTitles.Key(ContentType.TV_SHOWS, "3") to listOf(hit.copy(itemId = "9")),
                ),
        )

    @Test
    fun `available titles come first, each part in the list's order`() {
        assertEquals(listOf("Alien", "The Bear", "Dune"), state.cards().map { it.entity.title })
        assertEquals(listOf("Alien", "Dune"), state.cards(ContentType.MOVIES).map { it.entity.title })
    }

    @Test
    fun `titles not out yet come after the available ones, soonest first, then the rest`() {
        val today = java.time.LocalDate.parse("2026-10-22")
        val later = WantedTitleEntity("p", ContentType.MOVIES, "4", "Later", addedAt = 9, releaseDate = "2027-12-01")
        val sooner = WantedTitleEntity("p", ContentType.MOVIES, "5", "Sooner", addedAt = 8, releaseDate = "2027-01-01")
        val withComing = state.copy(titles = listOf(later, dune, sooner) + state.titles.drop(1))

        assertEquals(listOf("Alien", "The Bear", "Sooner", "Later", "Dune"), withComing.cards(today = today).map { it.entity.title })
    }

    @Test
    fun `an available card names its copy's source while the profile has several`() {
        val named = state.copy(sourceNames = mapOf(7L to "panel-a", 8L to "jellyxtream"))

        assertEquals("panel-a", named.cards(ContentType.MOVIES).first().sourceName)
        assertEquals(null, state.cards(ContentType.MOVIES).first().sourceName)
    }

    @Test
    fun `the badge counts available titles not opened yet`() {
        assertEquals(1, state.newCount())
        assertEquals(0, state.newCount(ContentType.MOVIES))
    }

    @Test
    fun `the banner announces available titles it hasn't told about`() {
        assertEquals(listOf("The Bear"), state.toAnnounce().map { it.entity.title })
    }

    @Test
    fun `an available card opens its copy, the others their TMDB page`() {
        val cards = state.cards().associateBy { it.entity.title }
        assertEquals(Screen.MovieDetails("42", "EN - Alien", "films", 7), cards.getValue("Alien").route)
        assertEquals(Screen.TmdbTitle(ContentType.MOVIES, "1", "Dune"), cards.getValue("Dune").route)
    }
}
