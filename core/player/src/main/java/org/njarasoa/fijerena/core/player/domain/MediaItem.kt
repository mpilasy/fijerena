package org.njarasoa.fijerena.core.player.domain

import androidx.compose.runtime.Immutable

enum class MediaType {
    LIVE_CHANNEL,
    MOVIE,
    SERIES,
    EPISODE,
    VIDEO_FILE,
}

data class MediaMetadata(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val contentRating: String? = null,
    val duration: String? = null,
    val durationSecs: Int? = null,
    val bitrate: Int? = null,
    val airDate: String? = null,
    val tmdbId: String? = null,
    val year: Int? = null,
    /** Ready-to-open trailer URL, already normalized by [trailerUrl]. */
    val trailerUrl: String? = null,
    /** TMDB's trailer, played when [trailerUrl] (the source's) is no longer on YouTube. */
    val tmdbTrailerUrl: String? = null,
) {
    /** With TMDB's trailer [url]: the trailer when the source sent none, else its fallback. */
    fun withTmdbTrailer(url: String): MediaMetadata =
        when (trailerUrl) {
            null -> copy(trailerUrl = url)
            url -> this
            else -> copy(tmdbTrailerUrl = url)
        }
}

private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

/**
 * Turns whatever a provider calls a trailer into a URL a browser can open, or null when there is
 * nothing usable. Xtream's `youtube_trailer` is a bare video id about as often as it is a link,
 * and both empty strings and scheme-less hosts show up; Jellyfin sends full URLs.
 */
fun trailerUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    return when {
        value.isEmpty() -> null
        value.startsWith("http://") || value.startsWith("https://") -> value
        YOUTUBE_ID.matches(value) -> "https://www.youtube.com/watch?v=$value"
        value.startsWith("www.") || value.contains('/') -> "https://$value"
        else -> null
    }
}

/**
 * `providerData` is a `Map`, which the Compose compiler cannot prove immutable, so without this
 * annotation the whole class infers unstable and every list row in the app loses its skip. Every
 * construction site builds the map with `mapOf`/`buildMap` and every read site only indexes into
 * it — nothing mutates it after construction, so the promise holds. Keep it that way.
 */
@Immutable
data class MediaItem(
    val id: String,
    val name: String,
    val mediaType: MediaType,
    val categoryId: String,
    val thumbnailUrl: String? = null,
    val streamUri: String? = null,
    val metadata: MediaMetadata = MediaMetadata(),
    val providerData: Map<String, String> = emptyMap(),
    /**
     * What selecting this row opens, when the row knows something its id alone doesn't say — a
     * history row that stands for a whole show, a row that stands for a category. Null on a plain
     * catalogue row, whose content type already answers it; read it through
     * [browseTarget][org.njarasoa.fijerena.core.player.domain.browseTarget] rather than directly.
     */
    val target: BrowseTarget? = null,
    /**
     * The source this row belongs to, so a card can open its route on it; 0 = the list's own
     * source (see [sourceOr]). Set on the rows a `MediaRepository` builds itself (Recent,
     * favourites); catalogue rows leave it 0 until a list puts them on their source. See docs/plans/20261012_multi-source-search-history-plan.md → Phase 1.
     */
    val providerId: Long = 0L,
    /**
     * A Recent row's last play (epoch ms), 0 elsewhere: what merges Recent across sources, newest
     * first (docs/plans/20261012_multi-source-search-history-plan.md → Phase 4).
     */
    val lastPlayedAt: Long = 0L,
) {
    /** The series ID associated with this media item if it represents a series or episode. */
    val seriesId: String?
        get() =
            (target as? BrowseTarget.Series)?.seriesId?.raw
                ?: (target as? BrowseTarget.Episode)?.seriesId?.raw
}

/**
 * The row's key across sources: the same id on two sources is two rows (multi-source plan → Q1).
 * For list keys and dedup; rows of one list all carry a source or all leave it 0.
 */
val MediaItem.sourceKey: String
    get() = sourceKey(providerId, id)

/** [MediaItem.sourceKey] of row [id] on [providerId]: the key of per-row state maps (stars, progress). */
fun sourceKey(
    providerId: Long,
    id: String,
): String = "$providerId/$id"

/**
 * Whether this row is the item played last, [id] on [source] (0: any source — a channel the
 * player hands back, whose source the list already narrows): a list's current-row marker, which
 * must not light the same id on another source (multi-source plan, Phase 6).
 */
fun MediaItem.isLastPlayed(
    id: String?,
    source: Long,
): Boolean = id != null && this.id == id && (source == 0L || providerId == source)

/** The row's source: [MediaItem.providerId], or [listSource] (the list's own) for a row that leaves it 0. */
fun MediaItem.sourceOr(listSource: Long): Long = if (providerId != 0L) providerId else listSource

/**
 * A heading dressed as a row: IPTV catalogues separate their channel lists with fake channels
 * named `##### 4K #####`, `#### GÉNÉRAL HD/4K ####` or `## NOW TV SPORT ##` — a run of `#` at
 * both ends. They carry no stream worth a guide row.
 */
val MediaItem.isCategoryMarker: Boolean
    get() = isCategoryMarkerName(name)

fun isCategoryMarkerName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.length >= 2 && trimmed.first() == '#' && trimmed.last() == '#'
}
