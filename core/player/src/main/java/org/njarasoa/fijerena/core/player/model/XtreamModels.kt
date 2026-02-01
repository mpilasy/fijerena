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

/**
 * Represents authentication response from Xtream API
 */
@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info")
    val userInfo: XtreamUserInfo,

    @SerialName("server_info")
    val serverInfo: XtreamServerInfo
)

/**
 * User information from authentication response
 */
@Serializable
data class XtreamUserInfo(
    @SerialName("username")
    val username: String,

    @SerialName("password")
    val password: String,

    @SerialName("message")
    val message: String? = null,

    @SerialName("auth")
    val auth: Int,

    @SerialName("status")
    val status: String,

    @SerialName("exp_date")
    val expDate: String? = null,

    @SerialName("is_trial")
    val isTrial: String? = null,

    @SerialName("active_cons")
    val activeCons: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("max_connections")
    val maxConnections: String? = null,

    @SerialName("allowed_output_formats")
    val allowedOutputFormats: List<String>? = null
)

/**
 * Server information from authentication response
 */
@Serializable
data class XtreamServerInfo(
    @SerialName("url")
    val url: String,

    @SerialName("port")
    val port: String,

    @SerialName("https_port")
    val httpsPort: String? = null,

    @SerialName("server_protocol")
    val serverProtocol: String,

    @SerialName("rtmp_port")
    val rtmpPort: String? = null,

    @SerialName("timezone")
    val timezone: String? = null,

    @SerialName("timestamp_now")
    val timestampNow: Long? = null,

    @SerialName("time_now")
    val timeNow: String? = null
)
