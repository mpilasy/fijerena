@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

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

@Serializable
data class XtreamCategory(
    // Every field of an Xtream answer may come as null: coerceInputValues turns a null into the
    // default, and an entry it leaves without an id is dropped where the list is read.
    @SerialName("category_id")
    val categoryId: String = "",
    @SerialName("category_name")
    val categoryName: String = "",
    @SerialName("parent_id")
    @Serializable(with = LenientIntSerializer::class)
    val parentId: Int = 0,
)

@Serializable
data class XtreamStream(
    @SerialName("num")
    @Serializable(with = LenientIntSerializer::class)
    val num: Int = 0,
    // Defaults, so one entry with `"name": null` (a panel-c film) does not fail the whole list:
    // coerceInputValues turns a null into the default. [withName] then names the entry from its
    // title, or drops it.
    @SerialName("name")
    val name: String = "",
    // Raw, so a server that sends it as anything but text does not fail the list. [titleText] reads
    // it.
    @SerialName("title")
    val title: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("stream_type")
    val streamType: String = "",
    // 0 when the panel sent none: [withName] drops the entry.
    @SerialName("stream_id")
    @Serializable(with = LenientIntSerializer::class)
    val streamId: Int = 0,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    @SerialName("epg_channel_id")
    val epgChannelId: String? = null,
    @SerialName("added")
    val added: String? = null,
    @SerialName("category_id")
    val categoryId: String = "",
    @SerialName("custom_sid")
    val customSid: String? = null,
    @SerialName("tv_archive")
    @Serializable(with = LenientIntSerializer::class)
    val tvArchive: Int = 0,
    @SerialName("direct_source")
    val directSource: String? = null,
    @SerialName("tv_archive_duration")
    @Serializable(with = LenientIntSerializer::class)
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
    @SerialName("tmdb")
    val tmdb: kotlinx.serialization.json.JsonElement? = null,
)

/** Series use series_id, not stream_id. */
@Serializable
data class XtreamSeries(
    @SerialName("num")
    @Serializable(with = LenientIntOrNullSerializer::class)
    val num: Int? = null,
    // See XtreamStream: a null name does not fail the list.
    @SerialName("name")
    val name: String = "",
    // Raw, so a server that sends it as anything but text does not fail the list. [titleText] reads
    // it.
    @SerialName("title")
    val title: kotlinx.serialization.json.JsonElement? = null,
    // See XtreamStream.streamId.
    @SerialName("series_id")
    @Serializable(with = LenientIntSerializer::class)
    val seriesId: Int = 0,
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
    @Serializable(with = LenientDoubleOrNullSerializer::class)
    val rating5based: Double? = null,
    @SerialName("backdrop_path")
    @Serializable(with = LenientTextListSerializer::class)
    val backdropPath: List<String?>? = null,
    @SerialName("youtube_trailer")
    val youtubeTrailer: String? = null,
    @SerialName("episode_run_time")
    val episodeRunTime: kotlinx.serialization.json.JsonElement? = null,
    // See XtreamStream: a null category id (seen in a series list) does not fail the list.
    @SerialName("category_id")
    val categoryId: String = "",
    @SerialName("tmdb")
    val tmdb: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info")
    val userInfo: XtreamUserInfo = XtreamUserInfo(),
    // A refused login is just {"user_info":{"auth":0}}: no server_info and no username, password
    // or status, so these default rather than failing the parse and hiding "wrong login".
    @SerialName("server_info")
    val serverInfo: XtreamServerInfo? = null,
)

@Serializable
data class XtreamUserInfo(
    @SerialName("username")
    val username: String = "",
    @SerialName("password")
    val password: String = "",
    @SerialName("message")
    val message: String? = null,
    @SerialName("auth")
    @Serializable(with = LenientIntSerializer::class)
    val auth: Int = 0,
    @SerialName("status")
    val status: String = "",
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
    @Serializable(with = LenientTextListSerializer::class)
    val allowedOutputFormats: List<String?>? = null,
)

@Serializable
data class XtreamServerInfo(
    @SerialName("url")
    val url: String = "",
    @SerialName("port")
    val port: String = "",
    @SerialName("https_port")
    val httpsPort: String? = null,
    @SerialName("server_protocol")
    val serverProtocol: String = "",
    @SerialName("rtmp_port")
    val rtmpPort: String? = null,
    @SerialName("timezone")
    val timezone: String? = null,
    @SerialName("timestamp_now")
    @Serializable(with = LenientLongOrNullSerializer::class)
    val timestampNow: Long? = null,
    @SerialName("time_now")
    val timeNow: String? = null,
)

@Serializable
data class SeriesInfo(
    @SerialName("info")
    @Serializable(with = SeriesDetailsOrNullSerializer::class)
    val info: SeriesDetails? = null,
    @SerialName("seasons")
    @Serializable(with = LenientSeasonListSerializer::class)
    val seasons: List<Season?> = emptyList(),
    @Serializable(with = EpisodesMapSerializer::class)
    @SerialName("episodes")
    val episodes: Map<String, List<Episode>> = emptyMap(),
)

@Serializable
data class SeriesDetails(
    // Nullable: some Xtream servers leave it out of `info`, or send it as `title`. It is only a
    // display fallback (the series list has the name), so a missing one must not fail the whole
    // episode list.
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
    @Serializable(with = LenientDoubleOrNullSerializer::class)
    val rating5based: Double? = null,
    @SerialName("youtube_trailer")
    val youtubeTrailer: String? = null,
    @SerialName("episode_run_time")
    val episodeRunTime: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("backdrop_path")
    @Serializable(with = LenientTextListSerializer::class)
    val backdropPath: List<String?>? = null,
    @SerialName("category_id")
    val categoryId: String? = null,
    @SerialName("tmdb")
    val tmdb: kotlinx.serialization.json.JsonElement? = null,
    // Some panels send the country of origin as text ("United States, France") in the info.
    @SerialName("country")
    val country: String? = null,
)

@Serializable
data class Season(
    @SerialName("season_number")
    @Serializable(with = LenientIntSerializer::class)
    val seasonNumber: Int = 0,
    @SerialName("name")
    val name: String = "",
    @SerialName("episode_count")
    @Serializable(with = LenientIntOrNullSerializer::class)
    val episodeCount: Int? = null,
    @SerialName("cover")
    val cover: String? = null,
)

@Serializable
data class Episode(
    // Blank when the panel sent none: [EpisodesMapSerializer] drops an episode without an id or a
    // container extension, since its stream URL is built from both.
    @SerialName("id")
    val id: String = "",
    @SerialName("episode_num")
    @Serializable(with = LenientIntSerializer::class)
    val episodeNum: Int = 0,
    @SerialName("title")
    val title: String = "",
    @SerialName("container_extension")
    val containerExtension: String = "",
    @SerialName("info")
    val info: EpisodeInfo? = null,
    @SerialName("season")
    @Serializable(with = LenientIntOrNullSerializer::class)
    val season: Int? = null,
)

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
    @Serializable(with = LenientIntOrNullSerializer::class)
    val durationSecs: Int? = null,
    @SerialName("bitrate")
    @Serializable(with = LenientIntOrNullSerializer::class)
    val bitrate: Int? = null,
    @SerialName("rating")
    val rating: kotlinx.serialization.json.JsonElement? = null,
    @JsonNames("tmdb_id", "tmdb")
    @SerialName("tmdb_id")
    val tmdbId: String? = null,
)

@Serializable
data class VodInfo(
    @SerialName("info")
    @Serializable(with = MovieInfoOrNullSerializer::class)
    val info: MovieInfo? = null,
    @SerialName("movie_data")
    @Serializable(with = MovieDataOrNullSerializer::class)
    val movieData: MovieData? = null,
)

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
    @JsonNames("tmdb_id", "tmdb")
    @SerialName("tmdb_id")
    val tmdbId: String? = null,
    @SerialName("youtube_trailer")
    val youtubeTrailer: String? = null,
    // See SeriesDetails.country.
    @SerialName("country")
    val country: String? = null,
)

@Serializable
data class MovieData(
    @SerialName("stream_id")
    @Serializable(with = LenientIntOrNullSerializer::class)
    val streamId: Int? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("container_extension")
    val containerExtension: String? = null,
)

@Serializable
data class VideoInfo(
    @SerialName("width")
    @Serializable(with = LenientIntOrNullSerializer::class)
    val width: Int? = null,
    @SerialName("height")
    @Serializable(with = LenientIntOrNullSerializer::class)
    val height: Int? = null,
    @SerialName("codec_name")
    val codecName: String? = null,
)

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
 * Episodes are decoded one at a time: with the generated serializer on the whole map, one episode
 * without a required field (`title`, `container_extension`) failed the decode and the show read as
 * empty. An episode we cannot read is dropped alone; one without `container_extension` cannot play
 * anyway, as the stream URL is built from it.
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
                            jsonDecoder.json
                                .decodeFromJsonElement(Episode.serializer(), raw)
                                .takeIf { it.id.isNotBlank() && it.containerExtension.isNotBlank() }
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
 * VideoInfo from an object or an array: some APIs send an empty array [] in place of null or an
 * object.
 */
object VideoInfoSerializer : KSerializer<VideoInfo?> {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("VideoInfo")

    override fun deserialize(decoder: Decoder): VideoInfo? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()

        return if (element.toString().startsWith("[")) {
            null
        } else {
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
 * AudioInfo from an object or an array: some APIs send an empty array [] in place of null or an
 * object.
 */
object AudioInfoSerializer : KSerializer<AudioInfo?> {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AudioInfo")

    override fun deserialize(decoder: Decoder): AudioInfo? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()

        return if (element.toString().startsWith("[")) {
            null
        } else {
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

/**
 * This entry with a name to show: its own, else its title. Null when it has neither (nothing to
 * list it under) or no id (nothing to play), so one bad entry does not fail the whole list.
 */
fun XtreamStream.withName(): XtreamStream? {
    if (streamId == 0) return null
    if (name.isNotBlank()) return this
    val fromTitle = titleText(title) ?: return null
    return copy(name = fromTitle)
}

/** See [XtreamStream.withName]. */
fun XtreamSeries.withName(): XtreamSeries? {
    if (seriesId == 0) return null
    if (name.isNotBlank()) return this
    val fromTitle = titleText(title) ?: return null
    return copy(name = fromTitle)
}

/** A `title` that is non-blank text, or null (absent, null, a number, an object…). */
private fun titleText(title: kotlinx.serialization.json.JsonElement?): String? =
    (title as? kotlinx.serialization.json.JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.takeIf { it.isNotBlank() }
