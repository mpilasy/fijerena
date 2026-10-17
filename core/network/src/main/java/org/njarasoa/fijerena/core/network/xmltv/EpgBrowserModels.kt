package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.player.model.TimeFormat

data class EpgBrowserProgram(
    val id: String,
    val title: String,
    val description: String?,
    val category: String?,
    val airings: List<EpgBrowserAiring>,
)

data class EpgBrowserAiring(
    val channelId: String,
    val channelName: String,
    val channelIconUrl: String?,
    val startEpoch: Long,
    val endEpoch: Long,
    val sourceId: Long = 0,
    val matchedStream: EpgBrowserMatchedStream? = null,
    /** The source (provider) whose guide source lists this airing. */
    val providerId: Long = 0L,
    /** That source's name, for its badge, when Search the guide covers more than one source. */
    val sourceName: String? = null,
)

data class EpgBrowserMatchedStream(
    val streamId: Int,
    val streamName: String,
    val categoryId: String,
    val excluded: Boolean = false,
    /** The source the stream is on: opening it opens Live TV there. */
    val providerId: Long = 0L,
)

data class EpgBrowserDateGroup(
    val dateLabel: String,
    val dayStartEpoch: Long,
    val programs: List<EpgBrowserProgram>,
)

/** Keeps only programs/airings that matched a stream, dropping any date group left empty. */
fun filterMatchedOnly(dateGroups: List<EpgBrowserDateGroup>): List<EpgBrowserDateGroup> =
    dateGroups.mapNotNull { group ->
        val filteredPrograms =
            group.programs.mapNotNull { program ->
                val matchedAirings = program.airings.filter { it.matchedStream != null }
                if (matchedAirings.isEmpty()) null else program.copy(airings = matchedAirings)
            }
        if (filteredPrograms.isEmpty()) null else group.copy(programs = filteredPrograms)
    }

/**
 * A TV Guide's channels (GD5, "In <category> only"): their stream ids, and the same streams by
 * the guide channel (xmltv id, lowercased) the grid shows for them. A search result is matched to
 * one stream only, often another one on the same guide channel (PL: CANAL+ UHD for FR: CANAL+ 4K,
 * which has no guide id and is matched by name), so the guide channel is what finds the guide's
 * own stream.
 */
data class GuideChannels(
    val streamIds: Set<String>,
    val byEpgChannelId: Map<String, EpgBrowserMatchedStream> = emptyMap(),
    /** The guide's own source: only its airings are kept (multi-source plan, Phase 7). */
    val providerId: Long = 0L,
)

/**
 * Keeps only airings on one of [channels] — a TV Guide's channels, on the guide's own source —
 * dropping programmes and date groups left empty. An airing matched to another stream on the same
 * guide channel is kept and pointed at the guide's stream, so it shows and plays the channel the
 * guide shows.
 */
fun filterToStreams(
    dateGroups: List<EpgBrowserDateGroup>,
    channels: GuideChannels,
): List<EpgBrowserDateGroup> = filterToStreams(dateGroups, listOf(channels))

/**
 * [filterToStreams] for a guide whose channels are on several sources (a merged Recent or
 * Favourites, one [GuideChannels] per source): each airing is kept on its own source's channels.
 */
fun filterToStreams(
    dateGroups: List<EpgBrowserDateGroup>,
    channels: List<GuideChannels>,
): List<EpgBrowserDateGroup> =
    dateGroups.mapNotNull { group ->
        val programs =
            group.programs.mapNotNull { program ->
                val airings =
                    program.airings.mapNotNull { airing ->
                        val own = channels.firstOrNull { it.providerId == airing.providerId }
                        when {
                            own == null -> null
                            airing.matchedStream?.streamId?.toString() in own.streamIds -> airing
                            else -> own.byEpgChannelId[airing.channelId.lowercase()]?.let { airing.copy(matchedStream = it) }
                        }
                    }
                if (airings.isEmpty()) null else program.copy(airings = airings)
            }
        if (programs.isEmpty()) null else group.copy(programs = programs)
    }

/**
 * "18:25 – 19:30", or "18:25 – Mon 15:00" when it ends on another day: guides list overnight blocks
 * and live events that run into the next day, and the end without its day read as ending before it
 * started. See docs/plans/archive/20261012_guide-overlaps-plan.md.
 */
fun formatAiringTime(
    context: Context,
    startEpoch: Long,
    endEpoch: Long,
): String {
    val startText = TimeFormat.formatTime(context, startEpoch)
    val endText = TimeFormat.formatTime(context, endEpoch)
    val endDay = endDayLabel(startEpoch, endEpoch, java.time.ZoneId.systemDefault(), java.util.Locale.getDefault())
    return if (endDay != null) "$startText – $endDay $endText" else "$startText – $endText"
}

/** The end's short weekday ("Mon") when [endEpoch] falls on another day than [startEpoch] in [zone], else null. */
internal fun endDayLabel(
    startEpoch: Long,
    endEpoch: Long,
    zone: java.time.ZoneId,
    locale: java.util.Locale,
): String? {
    val end =
        java.time.Instant
            .ofEpochSecond(endEpoch)
            .atZone(zone)
    if (java.time.Instant
            .ofEpochSecond(startEpoch)
            .atZone(zone)
            .toLocalDate() == end.toLocalDate()
    ) {
        return null
    }
    return end.format(
        java.time.format.DateTimeFormatter
            .ofPattern("EEE", locale),
    )
}

fun formatFileSize(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

fun formatCount(count: Int): String =
    when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }

/**
 * How current the EPG is, in one line.
 *
 * [oldestIngestedAtMs] is the stalest source that has actually run; a source that has never run
 * is not a freshness figure but a count, and arrives in [neverRunSourceCount]; folding the two
 * together would read one never-run source as "Never refreshed" while everything else was minutes old.
 * [hasOffSources]: with no source on ([oldestIngestedAtMs] null), whether there are guide sources
 * that are switched off, which is not the same as having none.
 */
fun freshnessLabel(
    context: Context,
    oldestIngestedAtMs: Long?,
    nowEpoch: Long,
    staleSourceCount: Int,
    neverRunSourceCount: Int = 0,
    hasOffSources: Boolean = false,
): String {
    if (oldestIngestedAtMs == null) {
        return context.getString(if (hasOffSources) R.string.epg_freshness_all_off else R.string.epg_freshness_no_sources)
    }
    if (oldestIngestedAtMs == 0L) return context.getString(R.string.epg_freshness_never_refreshed)
    val ageSec = nowEpoch - oldestIngestedAtMs / 1000L
    val ageLabel =
        when {
            ageSec < 60 -> context.getString(R.string.epg_freshness_just_now)
            ageSec < 3600 -> context.getString(R.string.epg_freshness_minutes_ago_format, ageSec / 60)
            ageSec < 86_400 -> context.getString(R.string.epg_freshness_hours_ago_format, ageSec / 3600)
            else -> context.getString(R.string.epg_freshness_days_ago_format, ageSec / 86_400)
        }
    val staleSuffix =
        if (staleSourceCount > 0) context.getString(R.string.epg_freshness_stale_suffix_format, staleSourceCount) else ""
    val neverRunSuffix =
        if (neverRunSourceCount > 0) {
            context.getString(R.string.epg_freshness_never_run_suffix_format, neverRunSourceCount)
        } else {
            ""
        }
    return context.getString(R.string.epg_freshness_updated_format, ageLabel) + staleSuffix + neverRunSuffix
}
