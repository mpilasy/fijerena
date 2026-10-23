package org.njarasoa.fijerena.core.network.wanted

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

/**
 * A related title no source has travels as a card that knows its TMDB title, and no catalogue
 * card is taken for one. See the wishlist ux plan, Phase 4.
 */
class TmdbOnlyItemTest {
    @Test
    fun `a TMDB-only card gives back its TMDB title`() {
        val result =
            TmdbRecommendation(
                id = 1170608,
                title = "Dune: Part Three",
                originalTitle = "Dune: Part Three",
                releaseDate = "2026-12-18",
                posterPath = "/p.jpg",
            )

        val title = TmdbOnlyItem.titleOf(TmdbOnlyItem.of(result, MediaType.MOVIE), ContentType.MOVIES)

        assertEquals(TmdbTitle(ContentType.MOVIES, "1170608", "Dune: Part Three", null, 2026, "/p.jpg", null, "2026-12-18"), title)
    }

    @Test
    fun `a catalogue card is not a TMDB-only one`() {
        assertNull(TmdbOnlyItem.titleOf(MediaItem("42", "Dune", MediaType.MOVIE, "c1"), ContentType.MOVIES))
    }
}
