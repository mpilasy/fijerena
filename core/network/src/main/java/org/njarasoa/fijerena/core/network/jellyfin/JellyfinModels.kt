package org.njarasoa.fijerena.core.network.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyfinAuthResponse(
    @SerialName("User") val user: JellyfinUser,
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String
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
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false
)

@Serializable
data class JellyfinUserData(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null
)

@Serializable
data class JellyfinPlaybackProgress(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean
)

@Serializable
data class JellyfinAuthBody(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String
)
