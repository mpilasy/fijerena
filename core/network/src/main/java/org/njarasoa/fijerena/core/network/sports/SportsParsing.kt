package org.njarasoa.fijerena.core.network.sports

import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * The JSON reader of every sports parser. None of the sports APIs is documented for outside use,
 * so unknown fields are skipped and a wrong-typed value takes the field's default.
 */
internal val sportsJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

/**
 * Epoch seconds of an ISO time: with an offset ("2026-11-01T16:30Z", "2026-11-03T17:45:00Z") or
 * without one, read as UTC ("2026-04-06T10:30:00"). Null when [text] is blank or not a time.
 */
internal fun isoEpochSec(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(text).toEpochSecond()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(text).toEpochSecond(ZoneOffset.UTC)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/** [text] trimmed, or null when it is blank. */
internal fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
