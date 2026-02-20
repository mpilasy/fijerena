package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "xtream_streams",
    primaryKeys = ["streamId", "providerId", "type"],
    indices = [
        Index(value = ["providerId", "type"]),
        Index(value = ["categoryId", "providerId"])
    ]
)
data class XtreamStreamEntity(
    val streamId: Int,
    val providerId: Long,
    val type: String, // LIVE, VOD

    val num: Int,
    val name: String,
    val streamType: String,
    val streamIcon: String? = null,
    val epgChannelId: String? = null,
    val added: String? = null,
    val categoryId: String,
    val customSid: String? = null,
    val tvArchive: Int = 0,
    val directSource: String? = null,
    val tvArchiveDuration: Int = 0,
    val contentHash: Int = 0
) {
    companion object {
        const val TYPE_LIVE = "LIVE"
        const val TYPE_VOD = "VOD"
    }
}
