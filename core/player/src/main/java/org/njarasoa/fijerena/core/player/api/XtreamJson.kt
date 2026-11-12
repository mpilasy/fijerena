package org.njarasoa.fijerena.core.player.api

import kotlinx.serialization.json.Json

/**
 * The Json settings that read every Xtream answer. [XtreamApiService] and the tests use this one
 * value, so a test reads an answer as the client does.
 *
 * A panel sends text without quotes, keys the models do not know, and `null` in any field: a null
 * takes the field's default.
 */
internal val XtreamJson =
    Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
