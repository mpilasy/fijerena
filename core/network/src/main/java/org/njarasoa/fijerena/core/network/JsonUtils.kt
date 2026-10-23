package org.njarasoa.fijerena.core.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun JsonElement?.asString(): String? {
    if (this == null) return null
    if (this is JsonPrimitive) {
        return this.contentOrNull
    }
    return this.toString()
}

/**
 * Xtream panels send an empty string, or the literal `"0"`, for a title with no TMDB match — not
 * `null`. Both pass a plain `IS NOT NULL`/non-null check, so every unmatched title ends up sharing
 * one fake "tmdbId" and gets treated as the same catalogue title by the TMDB-dedup joins in
 * `XtreamStreamDao`/`XtreamSeriesDao`/`XtreamEpisodeDao` (see `docs/DATABASE_SCHEMA.md` "TMDB
 * dedup"). Apply this wherever a provider-supplied tmdbId is about to be persisted or compared.
 */
fun String?.normalizeTmdbId(): String? = this?.takeIf { it.isNotBlank() && it != "0" }

fun String?.toJsonPrimitive(): kotlinx.serialization.json.JsonPrimitive? {
    if (this == null) return null
    return kotlinx.serialization.json.JsonPrimitive(this)
}
