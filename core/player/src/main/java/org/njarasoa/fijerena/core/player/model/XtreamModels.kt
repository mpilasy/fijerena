package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

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
    val parentId: Int = 0,
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
    val tvArchiveDuration: Int = 0,
    @JsonNames("description", "plot", "overview")
    @SerialName("description")
    val description: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("cast")
    val cast: String? = null,
    @SerialName("director")
    val director: String? = null,
    @SerialName("genre")
    val genre: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("rating")
    val rating: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("duration")
    val duration: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("youtube_trailer")
    val youtubeTrailer: String? = null,
)

/**
 * Represents a series (TV show) listing from Xtream API
 * Note: Series use series_id instead of stream_id
 */
@Serializable
data class XtreamSeries(
    @SerialName("num")
    val num: Int? = null,
    @SerialName("name")
    val name: String,
    @SerialName("series_id")
    val seriesId: Int,
    @JsonNames("cover", "movie_image")
    @SerialName("cover")
    val cover: String? = null,
    @JsonNames("plot", "description", "overview")
    @SerialName("plot")
    val plot: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("cast")
    val cast: String? = null,
    @SerialName("director")
    val director: String? = null,
    @SerialName("genre")
    val genre: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null,
    @SerialName("last_modified")
    val lastModified: String? = null,
    @SerialName("rating")
    val rating: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("rating_5based")
    val rating5based: Double? = null,
    @SerialName("backdrop_path")
    val backdropPath: List<String?>? = null,
    @SerialName("youtube_trailer")
    val youtubeTrailer: String? = null,
    @SerialName("episode_run_time")
    val episodeRunTime: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("category_id")
    val categoryId: String,
)

/**
 * Represents authentication response from Xtream API
 */
@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info")
    val userInfo: XtreamUserInfo,
    @SerialName("server_info")
    val serverInfo: XtreamServerInfo,
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
    val allowedOutputFormats: List<String>? = null,
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
    val timeNow: String? = null,
)

/**
 * Represents detailed information about a series including seasons and episodes
 */
@Serializable
data class SeriesInfo(
    @SerialName("info")
    val info: SeriesDetails? = null,
    @SerialName("seasons")
    val seasons: List<Season> = emptyList(),
    @Serializable(with = EpisodesMapSerializer::class)
    @SerialName("episodes")
    val episodes: Map<String, List<Episode>> = emptyMap(),
)

/**
 * Details about a series
 */
@Serializable
data class SeriesDetails(
    // Nullable: some Xtream servers omit it from `info` entirely, or send it as `title`. It is
    // only a display fallback (the series list already carries the name), so a missing one must
    // not fail the whole episode list — which is what a required field did.
    @JsonNames("name", "title")
    @SerialName("name")
    val name: String? = null,
    @JsonNames("cover", "movie_image")
    @SerialName("cover")
    val cover: String? = null,
    @JsonNames("plot", "description", "overview")
    @SerialName("plot")
    val plot: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("cast")
    val cast: String? = null,
    @SerialName("director")
    val director: String? = null,
    @SerialName("genre")
    val genre: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("rating")
    val rating: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("rating_5based")
    val rating5based: Double? = null,
    @SerialName("youtube_trailer")
    val youtubeTrailer: String? = null,
    @SerialName("episode_run_time")
    val episodeRunTime: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("backdrop_path")
    val backdropPath: List<String>? = null,
    @SerialName("category_id")
    val categoryId: String? = null,
    @SerialName("tmdb")
    val tmdb: kotlinx.serialization.json.JsonElement? = null,
)

/**
 * Represents a season in a series
 */
@Serializable
data class Season(
    @SerialName("season_number")
    val seasonNumber: Int,
    @SerialName("name")
    val name: String,
    @SerialName("episode_count")
    val episodeCount: Int? = null,
    @SerialName("cover")
    val cover: String? = null,
)

/**
 * Represents an episode in a series
 */
@Serializable
data class Episode(
    @SerialName("id")
    val id: String,
    @SerialName("episode_num")
    val episodeNum: Int,
    @SerialName("title")
    val title: String,
    @SerialName("container_extension")
    val containerExtension: String,
    @SerialName("info")
    val info: EpisodeInfo? = null,
    @SerialName("season")
    val season: Int? = null,
)

/**
 * Additional information about an episode
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class EpisodeInfo(
    @SerialName("name")
    val name: String? = null,
    @JsonNames("overview", "summary")
    @SerialName("overview")
    val overview: kotlinx.serialization.json.JsonElement? = null,
    @JsonNames("plot", "synopsis", "description")
    @SerialName("plot")
    val plot: kotlinx.serialization.json.JsonElement? = null,
    @JsonNames("air_date", "release_date", "releasedate")
    @SerialName("air_date")
    val airDate: String? = null,
    @JsonNames("movie_image", "cover", "thumbnail")
    @SerialName("movie_image")
    val movieImage: String? = null,
    @SerialName("cover_big")
    val coverBig: String? = null,
    @SerialName("duration")
    val duration: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("duration_secs")
    val durationSecs: Int? = null,
    @SerialName("bitrate")
    val bitrate: Int? = null,
    @SerialName("rating")
    val rating: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("tmdb_id")
    val tmdbId: String? = null,
)

/**
 * Represents detailed information about a VOD movie
 */
@Serializable
data class VodInfo(
    @SerialName("info")
    val info: MovieInfo? = null,
    @SerialName("movie_data")
    val movieData: MovieData? = null,
)

/**
 * Detailed information about a movie
 */
@Serializable
data class MovieInfo(
    @SerialName("name")
    val name: String? = null,
    @SerialName("cover_big")
    val coverBig: String? = null,
    @SerialName("movie_image")
    val movieImage: String? = null,
    @JsonNames("plot", "description", "overview")
    @SerialName("plot")
    val plot: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("cast")
    val cast: String? = null,
    @SerialName("director")
    val director: String? = null,
    @SerialName("genre")
    val genre: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("rating")
    val rating: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("duration")
    val duration: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("video")
    @Serializable(with = VideoInfoSerializer::class)
    val video: VideoInfo? = null,
    @SerialName("audio")
    @Serializable(with = AudioInfoSerializer::class)
    val audio: AudioInfo? = null,
    @SerialName("tmdb_id")
    val tmdbId: String? = null,
    @SerialName("youtube_trailer")
    val youtubeTrailer: String? = null,
)

/**
 * Movie stream data
 */
@Serializable
data class MovieData(
    @SerialName("stream_id")
    val streamId: Int? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("container_extension")
    val containerExtension: String? = null,
)

/**
 * Video technical information
 */
@Serializable
data class VideoInfo(
    @SerialName("width")
    val width: Int? = null,
    @SerialName("height")
    val height: Int? = null,
    @SerialName("codec_name")
    val codecName: String? = null,
)

/**
 * Audio technical information
 */
@Serializable
data class AudioInfo(
    @SerialName("codec_name")
    val codecName: String? = null,
    @SerialName("language")
    val language: String? = null,
)

/**
 * Custom serializer for the episodes map: some servers answer `[]` where the object belongs, and
 * some send an episode the model cannot read.
 *
 * Episodes are decoded one at a time. Handing the whole map to the generated serializer meant one
 * episode missing a required field — `title`, `container_extension` — aborted the decode and cost
 * the show every other episode it had, leaving a series that read as empty for a reason nothing
 * reported. An episode we cannot read is dropped on its own; a missing `container_extension` in
 * particular is unplayable anyway, since the stream URL is built from it.
 */
object EpisodesMapSerializer : KSerializer<Map<String, List<Episode>>> {
    private val mapSerializer = MapSerializer(String.serializer(), ListSerializer(Episode.serializer()))

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun deserialize(decoder: Decoder): Map<String, List<Episode>> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyMap()
        val element = jsonDecoder.decodeJsonElement()

        // An array (e.g. []) where the object belongs means the show has no episodes.
        if (element !is JsonObject) return emptyMap()

        return buildMap {
            for ((season, seasonElement) in element) {
                val rawEpisodes = seasonElement as? JsonArray ?: continue
                val episodes =
                    rawEpisodes.mapNotNull { raw ->
                        try {
                            jsonDecoder.json.decodeFromJsonElement(Episode.serializer(), raw)
                        } catch (_: Exception) {
                            null
                        }
                    }
                if (episodes.isNotEmpty()) put(season, episodes)
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: Map<String, List<Episode>>,
    ) {
        encoder.encodeSerializableValue(mapSerializer, value)
    }
}

/**
 * Custom serializer for VideoInfo that handles both object and array responses
 * Some APIs return empty arrays [] instead of null/object for missing video info
 */
object VideoInfoSerializer : KSerializer<VideoInfo?> {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("VideoInfo")

    override fun deserialize(decoder: Decoder): VideoInfo? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()

        // If it's an array (empty or not), return null
        return if (element.toString().startsWith("[")) {
            null
        } else {
            // Try to decode as VideoInfo object
            try {
                jsonDecoder.json.decodeFromJsonElement(VideoInfo.serializer(), element)
            } catch (e: Exception) {
                null
            }
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: VideoInfo?,
    ) {
        if (value != null) {
            encoder.encodeSerializableValue(VideoInfo.serializer(), value)
        } else {
            encoder.encodeNull()
        }
    }
}

/**
 * Custom serializer for AudioInfo that handles both object and array responses
 * Some APIs return empty arrays [] instead of null/object for missing audio info
 */
object AudioInfoSerializer : KSerializer<AudioInfo?> {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AudioInfo")

    override fun deserialize(decoder: Decoder): AudioInfo? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()

        // If it's an array (empty or not), return null
        return if (element.toString().startsWith("[")) {
            null
        } else {
            // Try to decode as AudioInfo object
            try {
                jsonDecoder.json.decodeFromJsonElement(AudioInfo.serializer(), element)
            } catch (e: Exception) {
                null
            }
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: AudioInfo?,
    ) {
        if (value != null) {
            encoder.encodeSerializableValue(AudioInfo.serializer(), value)
        } else {
            encoder.encodeNull()
        }
    }
}
