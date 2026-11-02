package org.njarasoa.fijerena.core.player.domain

import org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo
import java.util.Locale

/**
 * The second line of a subtitle track's row in the player's picker: its language and its format in
 * words ("French · SRT"), each only when the track's label does not say it already. Null when
 * there is nothing to add, so the row shows its label alone; never a MIME type
 * (`application/x-media3-cues`, the player's own format for subtitles it reads ahead).
 */
fun subtitleDetail(
    track: SubtitleTrackInfo,
    locale: Locale = Locale.getDefault(),
): String? {
    val label = track.label
    val languageName =
        Locale
            .forLanguageTag(track.language)
            .getDisplayLanguage(locale)
            .takeIf { it.isNotBlank() && !it.equals(track.language, ignoreCase = true) && !label.contains(it, ignoreCase = true) }
    val format = subtitleFormatName(track.mimeType)?.takeIf { !label.contains(it, ignoreCase = true) }
    return listOfNotNull(languageName, format).joinToString(" · ").ifEmpty { null }
}

/** The name viewers know a subtitle format by, from its MIME type; null for one with no such name. */
fun subtitleFormatName(mimeType: String): String? =
    when (mimeType.lowercase()) {
        "text/vtt", "application/x-mp4-vtt" -> "WebVTT"
        "application/x-subrip" -> "SRT"
        "text/x-ssa" -> "SSA"
        "application/pgs" -> "PGS"
        "application/vobsub" -> "VobSub"
        "application/dvbsubs" -> "DVB"
        "application/ttml+xml" -> "TTML"
        "application/cea-608", "application/x-mp4-cea-608" -> "CEA-608"
        "application/cea-708" -> "CEA-708"
        "application/x-quicktime-tx3g" -> "TX3G"
        else -> null
    }
