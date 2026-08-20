package org.njarasoa.fijerena.core.player.api

import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo

/**
 * What a provider's answer to a single-item lookup — `get_series_info`, `get_vod_info` — actually
 * turned out to be.
 *
 * These servers, proxies especially, have several ways of saying "nothing" that all deserialize
 * happily: a bare `[]` where an object belongs, a well-formed object with no payload in it, a
 * field missing that the model called required. Each one used to be recognised (or missed) at
 * whichever call site tripped over it first, so the same non-answer read as an empty show on one
 * screen and an error on another. Classifying once, here, is what keeps those readings the same.
 *
 * Callers get four cases and no raw text, so "the provider gave us nothing usable" is a thing they
 * match on rather than a shape they re-sniff.
 */
sealed interface XtreamResponse<out T> {
    /** A response with something in it. */
    data class Ok<T>(val value: T) : XtreamResponse<T>

    /**
     * The provider has no such id, or nothing to say about it: `[]`, or an object whose every
     * field came back empty. Usually a catalogue id that changed since the local sync, sometimes
     * a proxy answering before it has the data.
     */
    data class Unavailable(val itemId: Int, val action: String) : XtreamResponse<Nothing>

    /** A response that could not be read as the expected shape at all. */
    data class Malformed(val itemId: Int, val action: String, val cause: Throwable) : XtreamResponse<Nothing>

    /** The call itself did not complete — no network, not authenticated, server error. */
    data class Failed(val cause: Throwable) : XtreamResponse<Nothing>
}

/** The throwable to report when a response that is not [XtreamResponse.Ok] has to surface as one. */
fun XtreamResponse<*>.asThrowable(): Throwable =
    when (this) {
        is XtreamResponse.Ok -> IllegalStateException("Ok is not a failure")
        is XtreamResponse.Unavailable -> XtreamItemUnavailableException(itemId, action)
        is XtreamResponse.Malformed -> cause
        is XtreamResponse.Failed -> cause
    }

/**
 * Reads one item response, treating both "not an object" and "an object with nothing in it" as
 * [XtreamResponse.Unavailable] — the caller cannot tell those apart from the outside, and neither
 * can a viewer looking at the screen.
 *
 * [carriesNothing] is what "nothing in it" means for this particular payload.
 */
inline fun <reified T> Json.parseItemResponse(
    raw: String,
    itemId: Int,
    action: String,
    carriesNothing: (T) -> Boolean,
): XtreamResponse<T> {
    // An array where an object belongs is how these servers say "no such id".
    if (raw.trim().startsWith("[")) return XtreamResponse.Unavailable(itemId, action)

    val value =
        try {
            decodeFromString<T>(raw)
        } catch (e: Exception) {
            return XtreamResponse.Malformed(itemId, action, e)
        }

    return if (carriesNothing(value)) XtreamResponse.Unavailable(itemId, action) else XtreamResponse.Ok(value)
}

/** No episodes and no info — a series object with nothing a screen could show. */
fun SeriesInfo.carriesNothing(): Boolean = info == null && episodes.values.all { it.isEmpty() }

/** Neither the movie's metadata nor its stream data came back. */
fun VodInfo.carriesNothing(): Boolean = info == null && movieData == null
