package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
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
)

data class EpgBrowserMatchedStream(
    val streamId: Int,
    val streamName: String,
    val categoryId: String,
    val excluded: Boolean = false,
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

fun formatAiringTime(
    context: Context,
    startEpoch: Long,
    endEpoch: Long,
): String {
    val startText = TimeFormat.formatTime(context, startEpoch)
    val endText = TimeFormat.formatTime(context, endEpoch)
    return "$startText – $endText"
}

fun formatFileSize(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

fun formatCount(count: Int): String =
    when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }

fun freshnessLabel(
    oldestIngestedAtMs: Long?,
    nowEpoch: Long,
    staleSourceCount: Int,
): String {
    if (oldestIngestedAtMs == null) return "No EPG sources"
    if (oldestIngestedAtMs == 0L) return "Never refreshed"
    val ageSec = nowEpoch - oldestIngestedAtMs / 1000L
    val ageLabel =
        when {
            ageSec < 60 -> "just now"
            ageSec < 3600 -> "${ageSec / 60}m ago"
            ageSec < 86_400 -> "${ageSec / 3600}h ago"
            else -> "${ageSec / 86_400}d ago"
        }
    val suffix = if (staleSourceCount > 0) " • $staleSourceCount stale" else ""
    return "Updated $ageLabel$suffix"
}
