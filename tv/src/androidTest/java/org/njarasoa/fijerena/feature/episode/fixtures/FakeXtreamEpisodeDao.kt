package org.njarasoa.fijerena.feature.episode.fixtures

import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity

/**
 * In-memory stand-in for [XtreamEpisodeDao], for the episode-selection Compose UI tests.
 * `EpisodeListContent` only ever reaches [getSiblingCompletedEpisodeIds] (TMDB sibling-completion
 * dedup, via `MediaRepository.getSiblingCompletedEpisodeIds`) — the fixture data these tests use
 * has no catalogue rows to dedup against, so every other method here is unreachable and just
 * returns an empty/inert result rather than reproducing the real queries.
 */
class FakeXtreamEpisodeDao : XtreamEpisodeDao {
    override fun getEpisodes(
        providerId: Long,
        seriesId: Int,
    ): List<XtreamEpisodeEntity> = emptyList()

    override fun insertAll(episodes: List<XtreamEpisodeEntity>) = Unit

    override fun deleteAll(providerId: Long) = Unit

    override fun deleteBySeriesId(
        providerId: Long,
        seriesId: Int,
    ) = Unit

    override fun getEpisodeHashes(
        providerId: Long,
        seriesId: Int,
    ): Map<String, Int> = emptyMap()

    override fun countEpisodes(providerId: Long): Int = 0

    override suspend fun getEpisode(
        providerId: Long,
        episodeId: String,
    ): XtreamEpisodeEntity? = null

    override suspend fun getNextEpisode(
        providerId: Long,
        seriesId: Int,
        season: Int,
        episodeNum: Int,
    ): XtreamEpisodeEntity? = null

    override suspend fun getSeriesIdForEpisode(
        providerId: Long,
        episodeId: String,
    ): Int? = null

    override fun countEpisodesBySeries(providerId: Long): Map<Int, Int> = emptyMap()

    override suspend fun getSiblingCompletedEpisodeIds(
        providerId: Long,
        profileId: String,
        seriesId: Int,
    ): List<String> = emptyList()

    override suspend fun getSiblingCompletedCountsBySeries(
        providerId: Long,
        profileId: String,
    ): Map<Int, Int> = emptyMap()

    override suspend fun clearGroupCompletion(
        providerId: Long,
        profileId: String,
        itemId: String,
        now: Long,
    ) = Unit

    override fun updateOverviewIfBlank(
        providerId: Long,
        id: String,
        plot: String,
        fetchedAt: Long,
    ) = Unit
}
