package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

/**
 * Durable watch state: playback position and completion, per item, kept forever.
 *
 * Replaces the `watch_history_v3` SharedPreferences blob, which truncated to
 * `providerSettings.watchHistorySize` entries on every write and silently evicted anything older —
 * see `docs/plans/watch-state-durable-storage-plan.md`. Covers Xtream and the other local-blob
 * providers (SMB, Local, Remote M3U); not `xtream_`-prefixed because `MediaRepository` backs all
 * of them, not just Xtream. Jellyfin is out of scope: it owns this state server-side.
 *
 * `updatedAt` is this row's last-modified stamp. `lastPlayedAt` is set by playback only and drives
 * the Recent row; it stays null for a row created by a manual watched/unwatched mark (Phase 6).
 */
@Entity(
    tableName = "watch_state",
    primaryKeys = ["providerId", "itemId", "contentType"],
    indices = [
        Index(value = ["providerId", "contentType", "lastPlayedAt"]),
        Index(value = ["providerId", "seriesId"]),
    ],
)
data class WatchStateEntity(
    val providerId: Long,
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
