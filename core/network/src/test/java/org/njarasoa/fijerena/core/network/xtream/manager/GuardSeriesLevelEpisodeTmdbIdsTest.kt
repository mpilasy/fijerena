package org.njarasoa.fijerena.core.network.xtream.manager

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity

/**
 * The mandatory guard ahead of Phase 5 dedup (plans/watch-state-durable-storage-plan.md): a
 * panel-supplied `tmdb_id` copied onto every episode of a series is a series-level value, not
 * episode identity, and must be nulled before it ever reaches the sibling-completion query.
 */
class GuardSeriesLevelEpisodeTmdbIdsTest {
    private fun episode(
        id: String,
        tmdbId: String?,
    ) = XtreamEpisodeEntity(
        id = id,
        seriesId = 1,
        providerId = 1L,
        episodeNum = id.toInt(),
        title = "Episode $id",
        containerExtension = "mkv",
        tmdbId = tmdbId,
    )

    @Test
    fun `a tmdbId shared by more than one episode is nulled on all of them`() {
        val episodes =
            listOf(
                episode("1", tmdbId = "999"),
                episode("2", tmdbId = "999"),
                episode("3", tmdbId = "111"),
            )

        val guarded = guardSeriesLevelEpisodeTmdbIds(episodes)

        assertEquals(null, guarded.single { it.id == "1" }.tmdbId)
        assertEquals(null, guarded.single { it.id == "2" }.tmdbId)
        assertEquals("111", guarded.single { it.id == "3" }.tmdbId)
    }

    @Test
    fun `a tmdbId unique to one episode is left alone`() {
        val episodes = listOf(episode("1", tmdbId = "111"), episode("2", tmdbId = "222"))

        val guarded = guardSeriesLevelEpisodeTmdbIds(episodes)

        assertEquals("111", guarded.single { it.id == "1" }.tmdbId)
        assertEquals("222", guarded.single { it.id == "2" }.tmdbId)
    }

    @Test
    fun `null tmdbId is left alone and does not count toward any repeat`() {
        val episodes = listOf(episode("1", tmdbId = null), episode("2", tmdbId = null))

        val guarded = guardSeriesLevelEpisodeTmdbIds(episodes)

        assertEquals(null, guarded.single { it.id == "1" }.tmdbId)
        assertEquals(null, guarded.single { it.id == "2" }.tmdbId)
    }
}
