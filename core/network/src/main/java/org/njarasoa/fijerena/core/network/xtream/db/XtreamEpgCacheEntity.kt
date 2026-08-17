package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity

/**
 * Per-stream EPG payload cache.
 *
 * Lives in SQLite rather than SharedPreferences because a full live catalogue is tens of
 * thousands of entries: SharedPreferences keeps every value parsed in RAM for the lifetime of
 * the process and rewrites the whole file on each commit, which grew to an 84 MB xml file and
 * an equivalent permanent heap cost.
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
