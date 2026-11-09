package org.njarasoa.fijerena.core.ui.newepisodes

import android.content.res.Resources
import org.njarasoa.fijerena.core.network.NewEpisodeFacts
import org.njarasoa.fijerena.core.network.xtream.NEW_EPISODE_TTL_MS
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeEntity
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.history.MergedLists

/** A followed show with new episodes for the profile in use, one card of Home's New Episodes row. */
data class NewEpisodeShow(
    val item: MediaItem,
    /** The new episodes, first episode first. */
    val episodes: List<NewEpisodeEntity>,
    /** A new season number is in the list. */
    val newSeason: Boolean,
) {
    val key: String get() = item.sourceKey

    /** The newest find of the show. */
    val foundAt: Long get() = episodes.maxOf { it.foundAt }

    /** The season of the first new episode: where the card opens the show page. */
    val season: Int? get() = episodes.first().season

    val episodeIds: List<String> get() = episodes.map { it.episodeId }
}

/** The New Episodes of the profile in use, read over its [MergedLists]. See the new episode alerts plan. */
object NewEpisodes {
    /** At most this many cards on Home's New Episodes row. */
    const val MAX_CARDS = 20

    /**
     * Every source's new episodes for the profile: one entry per show, newest find first.
     * A show gives a card only when the profile follows it from before the find (Favourites, or a
     * show it is watching) and the source gives it a new episode that it has not started or finished
     * (NE-D7). Each source's [NewEpisodeFacts] is already filtered as Favourite Shows (content
     * filters and the kids' filter). The row takes [MAX_CARDS] of this list.
     */
    suspend fun forProfile(
        lists: MergedLists,
        now: Long = System.currentTimeMillis(),
    ): List<NewEpisodeShow> =
        lists
            .newEpisodeFacts(now)
            .flatMap { showsOf(it, now) }
            .sortedWith(compareByDescending<NewEpisodeShow> { it.foundAt }.thenBy { it.key })

    private fun showsOf(
        facts: NewEpisodeFacts,
        now: Long,
    ): List<NewEpisodeShow> {
        val bySeries = facts.rows.groupBy { it.seriesId }
        return facts.shows.mapNotNull { show ->
            val since = show.followedSince
            val seriesRows =
                show.item.id
                    .toIntOrNull()
                    ?.let { bySeries[it] }
                    .orEmpty()
            val episodes =
                if (since == null) {
                    emptyList()
                } else {
                    seriesRows
                        .filter { row ->
                            row.foundAt >= since && now - row.foundAt < NEW_EPISODE_TTL_MS && row.episodeId !in facts.seenEpisodeIds
                        }.sortedWith(compareBy({ it.season ?: 0 }, { it.episodeNum }))
                }
            if (episodes.isEmpty()) null else NewEpisodeShow(show.item, episodes, episodes.any { it.season in show.newSeasons })
        }
    }
}

/**
 * A New Episodes card's second line: "New season" when a new season number is in the list, "S02E05"
 * for one new episode, "3 new episodes" for more.
 */
fun NewEpisodeShow.statusLine(resources: Resources): String =
    when {
        newSeason -> {
            resources.getString(R.string.new_episodes_new_season)
        }

        episodes.size == 1 -> {
            val episode = episodes.single()
            resources.getString(R.string.new_episodes_code, episode.season ?: 0, episode.episodeNum)
        }

        else -> {
            resources.getQuantityString(R.plurals.new_episodes_count, episodes.size, episodes.size)
        }
    }
