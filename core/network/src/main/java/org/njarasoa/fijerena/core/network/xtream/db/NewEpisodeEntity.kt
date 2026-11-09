package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity

/**
 * An episode that a source added to a show that a profile of this device follows, found when a new
 * episode list of the show was compared with the stored one (see `NewEpisodeCompare` and the new
 * episode alerts plan). One row for each episode. Catalogue data, local to the device and not
 * synced: each device finds new episodes on its own. "New for a profile" is worked out at read
 * time from `watch_state`, so this table has no profile.
 *
 * [seriesId] is the Xtream id of the show, [episodeId] the id of the episode at the source. [season]
 * can be null, as in `xtream_episodes`. [foundAt] is when the episode was found. A row goes after 30
 * days, and with its show or its source.
 */
@Entity(
    tableName = "new_episode",
    primaryKeys = ["providerId", "seriesId", "episodeId"],
)
data class NewEpisodeEntity(
    val providerId: Long,
    val seriesId: Int,
    val episodeId: String,
    val season: Int?,
    val episodeNum: Int,
    val foundAt: Long,
)
