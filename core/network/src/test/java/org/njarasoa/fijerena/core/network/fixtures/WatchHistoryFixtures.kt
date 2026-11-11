package org.njarasoa.fijerena.core.network.fixtures

import org.njarasoa.fijerena.core.network.WatchedItem
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.SeriesId

/**
 * The watch-history row shapes that exist on real devices, so a test picks one and does not invent
 * a convenient row. A helper that always set `episodeId` once hid a bug (an episode id sent to the
 * series screen) behind a green suite: [anonymousEpisode] is the shape that breaks.
 *
 * Counts and percentages below are from a test phone (12 TV_SHOWS rows, providers 2/8/9).
 */
object WatchHistoryFixtures {
    /**
     * What a completed write looks like: the player knew which episode of which show it was
     * playing. Six of the twelve rows, all of them 2.08 % watched or more.
     */
    fun episode(
        id: String,
        seriesId: String,
        position: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false,
    ) = WatchedItem(
        itemId = id,
        itemName = id,
        categoryId = "cat1",
        contentType = ContentType.TV_SHOWS,
        playbackPosition = position,
        duration = duration,
        isCompleted = isCompleted,
        episodeId = EpisodeId(id),
        episodeExtension = "mkv",
        seriesId = SeriesId(seriesId),
        seriesName = "Series $seriesId",
    )

    /**
     * A row with a position and no identity at all — no episode id, no series id, no extension.
     * The other six rows, every one of them under 1.33 % watched: `savePlaybackPosition` created
     * the row while the write that carried the metadata sat behind a 2 % gate.
     *
     * The write side is fixed, but rows in this shape stay on devices, and Recent must still open
     * them at the player, not at a series screen.
     */
    fun anonymousEpisode(
        id: String,
        position: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false,
    ) = WatchedItem(
        itemId = id,
        itemName = id,
        categoryId = "cat1",
        contentType = ContentType.TV_SHOWS,
        playbackPosition = position,
        duration = duration,
        isCompleted = isCompleted,
    )

    /**
     * An episode that knows itself but not its show. No row on the test phone is in this shape,
     * but it is what a partially-repaired [anonymousEpisode] becomes: replaying one of those rows
     * fills in the episode id from `itemId` and leaves the series unknown.
     */
    fun seriesUnknownEpisode(
        id: String,
        position: Long = 0L,
        duration: Long = 0L,
    ) = WatchedItem(
        itemId = id,
        itemName = id,
        categoryId = "cat1",
        contentType = ContentType.TV_SHOWS,
        playbackPosition = position,
        duration = duration,
        episodeId = EpisodeId(id),
    )

    fun movie(
        id: String,
        position: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false,
    ) = WatchedItem(
        itemId = id,
        itemName = id,
        categoryId = "cat1",
        contentType = ContentType.MOVIES,
        playbackPosition = position,
        duration = duration,
        isCompleted = isCompleted,
    )

    /** Live rows never carry a position — `savePlaybackPosition` returns early for LIVE_TV. */
    fun channel(id: String) =
        WatchedItem(
            itemId = id,
            itemName = id,
            categoryId = "cat1",
            contentType = ContentType.LIVE_TV,
        )

    /**
     * Rows exactly as they sit in `shared_prefs`, for anything that has to survive the file rather
     * than the model — a schema change, a serializer swap.
     */
    object Stored {
        /** Provider 9, full metadata. */
        const val LAW_AND_ORDER =
            """{"itemId":"242136","itemName":"EN - Law & Order - S06E18","categoryId":"156",""" +
                """"contentType":"TV_SHOWS","timestamp":1787181459349,"playbackPosition":37365,""" +
                """"duration":2811558,"isCompleted":false,"episodeId":"242136","episodeExtension":null,""" +
                """"seriesId":"4080","seriesName":"EN - Law & Order (1990) (US)","audioTrackIndex":0,""" +
                """"subtitleTrackIndex":-1}"""

        /** Provider 9, watched 0.08 % — the anonymous shape, as stored. */
        const val ANONYMOUS =
            """{"itemId":"749050","itemName":"FR - From - S01E01 - Long Day's Journey Into Night",""" +
                """"categoryId":"153","contentType":"TV_SHOWS","timestamp":1786935468926,""" +
                """"playbackPosition":2656,"duration":3172160,"isCompleted":false}"""
    }
}
