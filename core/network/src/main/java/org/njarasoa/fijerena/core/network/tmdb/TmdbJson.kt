package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.json.Json

/**
 * The Json settings that read every TMDB answer. [TmdbApiService] and the tests use this one value,
 * so a test reads an answer as the client does.
 *
 * TMDB adds keys the models do not know, and sends `null` for a missing poster, runtime or list: a
 * null takes the field's default.
 */
internal val TmdbJson =
    Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
