package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

/**
 * Durable watch state: playback position and completion, per item, kept forever.
 *
 * Replaces the `watch_history_v3` SharedPreferences blob, which truncated to
 * `providerSettings.watchHistorySize` entries on every write (the watch state durable storage
 * plan). Not `xtream_`-prefixed: `MediaRepository` backs Xtream, SMB, Local and Remote M3U through
 * it. Jellyfin keeps this state on its server.
 *
 * `updatedAt` is this row's last-modified stamp. `lastPlayedAt` is set by playback only and drives
 * the Recent row; it stays null for a row created by a manual watched/unwatched mark (Phase 6).
 *
 * `profileId` scopes the row to one person (the live sync plan → User profiles). It is part of the
 * primary key and comes after `providerId` in every index, since every read is for one provider
 * *and* one profile.
 */
@Entity(
    tableName = "watch_state",
    primaryKeys = ["providerId", "profileId", "itemId", "contentType"],
    indices = [
        Index(value = ["providerId", "profileId", "contentType", "lastPlayedAt"]),
        Index(value = ["providerId", "profileId", "seriesId"]),
    ],
)
data class WatchStateEntity(
    val providerId: Long,
    val profileId: String,
    val itemId: String,
    val contentType: String,
    val itemName: String,
    val categoryId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val updatedAt: Long,
    val lastPlayedAt: Long? = null,
    val seriesId: String? = null,
    val episodeId: String? = null,
    val seriesName: String? = null,
    val episodeExtension: String? = null,
    val audioTrackIndex: Int? = null,
    val subtitleTrackIndex: Int? = null,
)
