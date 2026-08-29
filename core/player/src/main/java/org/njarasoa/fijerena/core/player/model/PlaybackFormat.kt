package org.njarasoa.fijerena.core.player.model

import android.content.Context
import java.util.Locale

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun formatEpochTime(
    context: Context,
    epochSeconds: Long,
): String = TimeFormat.formatTime(context, epochSeconds)

fun formatBitrate(bitrate: Int): String =
    if (bitrate > 0) {
        val kbps = bitrate / 1000
        if (kbps > 1000) {
            // Locale.US, not getDefault(): a device-locale decimal comma here would read as
            // inconsistent next to formatRating()'s always-dot output elsewhere on screen.
            String.format(Locale.US, "%.1f Mbps", kbps / 1000f)
        } else {
            "$kbps Kbps"
        }
    } else {
        "Unknown"
    }

/**
 * Parses a duration string that can be either raw seconds ("7200") or h:mm:ss / m:ss
 * format ("1:23:45"). Returns total seconds, or null if unparseable.
 */
fun parseDurationToSeconds(duration: String): Long? {
    duration.toLongOrNull()?.let { return it }
    val parts = duration.split(":")
    return when (parts.size) {
        3 -> {
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val s = parts[2].toLongOrNull() ?: return null
            h * 3600 + m * 60 + s
        }
        2 -> {
            val m = parts[0].toLongOrNull() ?: return null
            val s = parts[1].toLongOrNull() ?: return null
            m * 60 + s
        }
        else -> null
    }
}

/**
 * Computes "Ends at" wall-clock time from a duration string and optional resume
 * position. Returns a formatted time string like "10:30 PM", or null if the
 * duration is missing/unparseable.
 */
fun computeEndsAt(
    context: Context,
    duration: String?,
    resumePositionMs: Long,
): String? {
    if (duration == null) return null
    val totalSeconds = parseDurationToSeconds(duration) ?: return null
    if (totalSeconds <= 0) return null
    val totalMs = totalSeconds * 1000
    val remainingMs = if (resumePositionMs > 0) (totalMs - resumePositionMs).coerceAtLeast(0) else totalMs
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.MILLISECOND, remainingMs.toInt())
    return TimeFormat.formatClockTime(context, calendar.time)
}

/**
 * Formats a duration string (raw seconds or h:mm:ss / m:ss) into human-readable
 * "2h 0m" form. Returns the input unchanged if unparseable.
 */
fun formatDuration(duration: String): String {
    val seconds = parseDurationToSeconds(duration) ?: return duration
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

/**
 * Trims a star rating to one decimal place. Providers pass TMDB's vote average straight through,
 * which carries three decimals ("6.666") and reads as false precision on a 10-point scale.
 *
 * Returns the input unchanged when it is not a bare number — some providers send the rating
 * combined with a certificate, e.g. "7.9 | PG-13", and that must not be mangled into a number.
 */
fun formatRating(rating: String): String {
    val value = rating.trim().toDoubleOrNull() ?: return rating
    // Locale.US, not the default: on a device set to a comma-decimal locale this rendered "6,4"
    // for one row and "9.0" for another (same list, different providers), which reads as a bug.
    return String.format(Locale.US, "%.1f", value)
}

/**
 * True when [duration] parses to more than zero seconds. Providers sometimes send "0" for an
 * unknown/missing runtime — treat that as absent rather than render it as a bogus "0s".
 */
fun hasMeaningfulDuration(duration: String?): Boolean =
    duration != null && (parseDurationToSeconds(duration) ?: 0) > 0

/** Resolution bucket label, e.g. "4K", "1080p", "720p". */
fun resolutionLabel(
    width: Int,
    height: Int,
): String =
    when {
        width >= 3840 || height >= 2160 -> "4K"
        width >= 2560 || height >= 1440 -> "1440p"
        width >= 1920 || height >= 1080 -> "1080p"
        width >= 1280 || height >= 720 -> "720p"
        width >= 854 || height >= 480 -> "480p"
        else -> "${height}p"
    }

/**
 * Channel-count label, e.g. "5.1", "7.1", "Stereo". [custom] handles any channel
 * count without a well-known name; the named labels default to English but can be
 * overridden (e.g. with string resources) by callers that need localization.
 */
fun channelLabel(
    channels: Int,
    mono: String = "Mono",
    stereo: String = "Stereo",
    surround51: String = "5.1",
    surround71: String = "7.1",
    custom: (Int) -> String = { "${it}ch" },
): String =
    when (channels) {
        1 -> mono
        2 -> stereo
        6 -> surround51
        8 -> surround71
        else -> custom(channels)
    }
