package org.njarasoa.fijerena.core.network.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JellyfinAuthResponse(
    @SerialName("User") val user: JellyfinUser,
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String? = null
)

@Serializable
data class JellyfinUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String
)

@Serializable
data class JellyfinItemsResponse(
    @SerialName("Items") val items: List<JellyfinItem>,
    @SerialName("TotalRecordCount") val totalRecordCount: Int
)

@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Type") val type: String,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CommunityRating") val communityRating: Float? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Studios") val studios: List<JellyfinStudio> = emptyList(),
    @SerialName("People") val people: List<JellyfinPerson> = emptyList(),
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSource> = emptyList(),
    @SerialName("UserData") val userData: JellyfinUserData? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("Container") val container: String? = null
)

@Serializable
data class JellyfinStudio(
    @SerialName("Name") val name: String,
    @SerialName("Id") val id: String? = null
)

@Serializable
data class JellyfinPerson(
    @SerialName("Name") val name: String,
    @SerialName("Id") val id: String? = null,
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null
)

@Serializable
data class JellyfinMediaSource(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Bitrate") val bitrate: Int? = null,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStream> = emptyList()
)

@Serializable
data class JellyfinMediaStream(
    @SerialName("Type") val type: String,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("VideoDoViTitle") val videoDoViTitle: String? = null,
    @SerialName("VideoRange") val videoRange: String? = null,
    @SerialName("VideoRangeType") val videoRangeType: String? = null
)

@Serializable
data class JellyfinUserData(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null
)

// ---- Playback reporting ----

@Serializable
data class JellyfinPlaybackProgress(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null
)

@Serializable
data class JellyfinPlaybackStopped(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null
)

@Serializable
data class JellyfinPlaybackStart(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null
)

// ---- Auth ----

@Serializable
data class JellyfinAuthBody(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String
)

// ---- Quick Connect ----

@Serializable
data class JellyfinQuickConnectResult(
    @SerialName("Secret") val secret: String,
    @SerialName("Code") val code: String,
    @SerialName("Authenticated") val authenticated: Boolean = false
)

@Serializable
data class JellyfinQuickConnectAuthBody(
    @SerialName("Secret") val secret: String
)

// ---- Capabilities ----

@Serializable
data class JellyfinClientCapabilities(
    @SerialName("PlayableMediaTypes") val playableMediaTypes: List<String> = listOf("Audio", "Video"),
    @SerialName("SupportedCommands") val supportedCommands: List<String> = emptyList(),
    @SerialName("SupportsMediaControl") val supportsMediaControl: Boolean = false,
    @SerialName("SupportsContentUploading") val supportsContentUploading: Boolean = false,
    @SerialName("SupportsPersistentIdentifier") val supportsPersistentIdentifier: Boolean = true
)

// ---- PlaybackInfo negotiation ----

@Serializable
data class JellyfinPlaybackInfoRequest(
    @SerialName("UserId") val userId: String,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long = 140_000_000,
    @SerialName("StartTimeTicks") val startTimeTicks: Long = 0,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("DeviceProfile") val deviceProfile: JsonElement
)

@Serializable
data class JellyfinPlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<JellyfinPlaybackMediaSource> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null
)

/**
 * MediaSource entry returned by /Items/{id}/PlaybackInfo.
 * Distinct from [JellyfinMediaSource] (used for catalog browsing) — this one
 * carries transcoding decision fields.
 */
@Serializable
data class JellyfinPlaybackMediaSource(
    @SerialName("Id") val id: String,
    @SerialName("Container") val container: String? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("DirectStreamUrl") val directStreamUrl: String? = null
)
