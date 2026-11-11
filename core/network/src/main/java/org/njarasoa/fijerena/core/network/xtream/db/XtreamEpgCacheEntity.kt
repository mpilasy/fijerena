package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity

/**
 * Per-stream EPG payload cache, in SQLite: a full live catalogue has tens of thousands of entries,
 * and SharedPreferences kept them all parsed in RAM and rewrote the whole file (84 MB) on each
 * commit.
 */
@Entity(
    tableName = "xtream_epg_cache",
    primaryKeys = ["providerId", "streamId"],
)
data class XtreamEpgCacheEntity(
    val providerId: Long,
    val streamId: Int,
    val payload: String,
    val updatedAt: Long,
)
