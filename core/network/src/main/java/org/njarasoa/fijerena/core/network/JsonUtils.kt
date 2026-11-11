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
 * Xtream panels send an empty string or the literal `"0"`, not `null`, for a title with no TMDB
 * match. Both pass an `IS NOT NULL` check, so every unmatched title would share one fake tmdbId in
 * the TMDB-dedup joins of `XtreamStreamDao`/`XtreamSeriesDao`/`XtreamEpisodeDao` (see
 * `docs/DATABASE_SCHEMA.md` "TMDB dedup"). Apply this wherever a provider's tmdbId is stored or
 * compared.
 */
fun String?.normalizeTmdbId(): String? = this?.takeIf { it.isNotBlank() && it != "0" }

fun String?.toJsonPrimitive(): kotlinx.serialization.json.JsonPrimitive? {
    if (this == null) return null
    return kotlinx.serialization.json.JsonPrimitive(this)
}
