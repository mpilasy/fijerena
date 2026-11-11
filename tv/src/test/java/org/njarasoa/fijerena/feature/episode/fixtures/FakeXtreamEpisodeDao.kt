package org.njarasoa.fijerena.feature.episode.fixtures

import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity

/**
 * In-memory [XtreamEpisodeDao] for the episode-selection Compose UI tests. `EpisodeListContent`
 * only reaches [getSiblingCompletedEpisodeIds] (TMDB sibling dedup, through
 * `MediaRepository.getSiblingCompletedEpisodeIds`); the fixture data has no catalogue rows, so every
 * other method returns an empty result.
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
