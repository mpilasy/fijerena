package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a live TV category from Xtream API
 */
@Serializable
data class XtreamCategory(
    @SerialName("category_id")
    val categoryId: String,

    @SerialName("category_name")
    val categoryName: String,

    @SerialName("parent_id")
    val parentId: Int = 0
)

/**
 * Represents a live stream from Xtream API
 */
@Serializable
data class XtreamStream(
    @SerialName("num")
    val num: Int,

    @SerialName("name")
    val name: String,

    @SerialName("stream_type")
    val streamType: String,

    @SerialName("stream_id")
    val streamId: Int,

    @SerialName("stream_icon")
    val streamIcon: String? = null,

    @SerialName("epg_channel_id")
    val epgChannelId: String? = null,

    @SerialName("added")
    val added: String? = null,

    @SerialName("category_id")
    val categoryId: String,

    @SerialName("custom_sid")
    val customSid: String? = null,

    @SerialName("tv_archive")
    val tvArchive: Int = 0,

    @SerialName("direct_source")
    val directSource: String? = null,

    @SerialName("tv_archive_duration")
    val tvArchiveDuration: Int = 0
)
