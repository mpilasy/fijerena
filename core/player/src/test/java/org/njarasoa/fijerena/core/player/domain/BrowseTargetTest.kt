package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The fallback for a row that carries no target of its own — the one place content type is read as intent. */
class BrowseTargetTest {
    private fun item(target: BrowseTarget? = null) =
        MediaItem(
            id = "4080",
            name = "EN - Law & Order (1990) (US)",
            mediaType = MediaType.SERIES,
            categoryId = "156",
            target = target,
        )

    @Test
    fun plainCatalogueRowIsWhateverItsContentTypeSays() {
        assertEquals(BrowseTarget.Series("4080"), item().browseTarget(ContentType.TV_SHOWS))
        assertEquals(BrowseTarget.Movie("4080"), item().browseTarget(ContentType.MOVIES))
        assertEquals(BrowseTarget.Channel("4080"), item().browseTarget(ContentType.LIVE_TV))
    }

    @Test
    fun aRowThatKnowsBetterKeepsItsOwnTarget() {
        // A history row for one episode of a TV show: the content type would say "series", and
        // acting on that is exactly how an episode id once reached the series screen.
        val episode = BrowseTarget.Episode(episodeId = "242136", seriesId = null)

        assertEquals(episode, item(episode).browseTarget(ContentType.TV_SHOWS))
    }
}
