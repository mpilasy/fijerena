package org.njarasoa.fijerena.core.network.xtream

import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeDao
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity

/** How long a profile counts as watching a show after it played an episode: 60 days. */
const val FOLLOWED_WATCHED_WINDOW_MS = 60L * 24 * 60 * 60 * 1000

/** How long a [NewEpisodeEntity] stays: 30 days. */
const val NEW_EPISODE_TTL_MS = 30L * 24 * 60 * 60 * 1000

/**
 * The compare step of the new episode alerts plan: the episode list that a source sent, against
 * the list stored from before. Shared by the check after a catalogue sync and by the open of a show
 * page, so an open never swallows an alert.
 */
internal object NewEpisodeCompare {
    data class Change(
        val added: List<NewEpisodeEntity>,
        val removedIds: List<String>,
    )

    /**
     * An episode of [incoming] is new when neither its id nor its (season, episode number) pair is
     * in [stored]: a source that renumbers or uploads an episode again gives a new id to an old
     * pair (Risk 4). With no [stored] list, [incoming] is the baseline: no alert. A row of
     * [pending] whose episode is no longer in [incoming] goes.
     */
    fun compare(
        providerId: Long,
        seriesId: Int,
        stored: List<XtreamEpisodeEntity>,
        incoming: List<XtreamEpisodeEntity>,
        pending: List<NewEpisodeEntity>,
        now: Long,
    ): Change {
        val added =
            if (stored.isEmpty()) {
                emptyList()
            } else {
                val knownIds = stored.mapTo(HashSet()) { it.id }
                val knownPairs = stored.mapTo(HashSet()) { it.season to it.episodeNum }
                incoming
                    .filter { it.id !in knownIds && (it.season to it.episodeNum) !in knownPairs }
                    .distinctBy { it.season to it.episodeNum }
                    .map { NewEpisodeEntity(providerId, seriesId, it.id, it.season, it.episodeNum, now) }
            }
        val incomingIds = incoming.mapTo(HashSet()) { it.id }
        return Change(added, pending.map { it.episodeId }.filter { it !in incomingIds })
    }

    /** The compare of a show's list and its result in `new_episode`. Call it before [stored] is replaced. */
    fun record(
        dao: NewEpisodeDao,
        providerId: Long,
        seriesId: Int,
        stored: List<XtreamEpisodeEntity>,
        incoming: List<XtreamEpisodeEntity>,
        now: Long,
    ) {
        val change = compare(providerId, seriesId, stored, incoming, dao.getForSeries(providerId, seriesId), now)
        if (change.added.isNotEmpty()) dao.insertAll(change.added)
        change.removedIds.chunked(SQLITE_MAX_IDS).forEach { dao.deleteEpisodes(providerId, seriesId, it) }
    }

    private const val SQLITE_MAX_IDS = 900
}

/** The shows that any profile of this device follows on [providerId]: favourites, and shows it is watching. */
internal fun followedShowIds(
    dao: NewEpisodeDao,
    providerId: Long,
    now: Long,
): Set<Int> =
    (dao.getFavoriteSeriesIds(providerId) + dao.getRecentlyWatchedSeriesIds(providerId, now - FOLLOWED_WATCHED_WINDOW_MS))
        .mapNotNullTo(HashSet()) { it.toIntOrNull() }
