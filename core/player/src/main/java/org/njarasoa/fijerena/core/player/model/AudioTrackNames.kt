package org.njarasoa.fijerena.core.player.model

import androidx.media3.common.Player
import java.util.Locale

/**
 * The name an audio track shows in the player's audio list, the same on both apps (phone walk A,
 * item 12). The track's own [label] when it is a real name ("Director's commentary"); else, when
 * it has none or it is only the file's or the stream's name (many files name every track
 * "The.Matrix.1999.REMASTERED.1080p.BluRay…"), a name built from what the track is: its
 * language, its codec and its channels ("English · DTS-HD · 7.1"). [fallback] when nothing is
 * known.
 *
 * [mono] and [stereo] are the words for one and two channels in the app language; [titles] are the
 * names of what plays (its title, its file name), which a label equal to one of them is not a name.
 */
fun audioTrackName(
    label: String?,
    language: String?,
    mimeType: String?,
    codecs: String?,
    channelCount: Int,
    titles: List<String>,
    mono: String,
    stereo: String,
    fallback: String,
    locale: Locale = Locale.getDefault(),
): String {
    val own = label?.trim()?.takeIf { it.isNotEmpty() && !isFileName(it, titles) }
    val built =
        listOfNotNull(
            languageName(language, locale),
            audioCodecName(mimeType, codecs),
            channelsName(channelCount, mono, stereo),
        ).joinToString(" · ")
    return own ?: built.ifEmpty { label?.trim()?.takeIf { it.isNotEmpty() } ?: fallback }
}

/**
 * [label] is a file's or a release's name, not a track's: one of [titles] (with or without its
 * extension), a name with a video file's extension, or words joined by dots with no space
 * ("The.Matrix.1999.1080p").
 */
internal fun isFileName(
    label: String,
    titles: List<String>,
): Boolean {
    val bare = label.substringBeforeLast('.', label)
    val sameAsTitle =
        titles.any { title ->
            val t = title.trim()
            t.isNotEmpty() && (t.equals(label, ignoreCase = true) || t.substringBeforeLast('.', t).equals(bare, ignoreCase = true))
        }
    val hasVideoExtension = label.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    val dottedWords = ' ' !in label && label.count { it == '.' } >= 2
    return sameAsTitle || hasVideoExtension || dottedWords
}

/** The language's own name in [locale] ("English", "Français"), null when unknown. */
private fun languageName(
    language: String?,
    locale: Locale,
): String? {
    val tag = language?.trim()?.takeIf { it.isNotEmpty() && it != "und" && it != "unknown" } ?: return null
    val name = Locale.forLanguageTag(tag).getDisplayLanguage(locale)
    return name.takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }?.replaceFirstChar { it.titlecase(locale) } ?: tag
}

/** The codec's common name ("DTS-HD", "E-AC-3", "AAC"), null when unknown. */
internal fun audioCodecName(
    mimeType: String?,
    codecs: String?,
): String? {
    val codec = codecs?.lowercase().orEmpty()
    return when (mimeType?.lowercase()) {
        "audio/vnd.dts.hd", "audio/vnd.dts.uhd" -> "DTS-HD"
        "audio/vnd.dts" -> "DTS"
        "audio/true-hd" -> "TrueHD"
        "audio/eac3-joc" -> "E-AC-3 Atmos"
        "audio/eac3" -> "E-AC-3"
        "audio/ac3" -> "AC-3"
        "audio/ac4" -> "AC-4"
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg", "audio/mpeg-l2", "audio/mpeg-l1" -> "MP3"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        "audio/alac" -> "ALAC"
        "audio/raw" -> "PCM"
        else -> if (codec.startsWith("mp4a")) "AAC" else null
    }
}

/** "Mono", "Stereo", "5.1", "7.1", or the count of channels; null when unknown. */
private fun channelsName(
    count: Int,
    mono: String,
    stereo: String,
): String? =
    when {
        count <= 0 -> null
        count == 1 -> mono
        count == 2 -> stereo
        count == 6 -> "5.1"
        count == 8 -> "7.1"
        else -> "${count}ch"
    }

/** The names of what [player] plays: its titles and its file's name, for [audioTrackName]. */
fun playingTitles(player: Player): List<String> {
    val item = player.currentMediaItem
    return listOfNotNull(
        player.mediaMetadata.title,
        player.mediaMetadata.displayTitle,
        item?.mediaMetadata?.title,
        item?.localConfiguration?.uri?.lastPathSegment,
        item?.requestMetadata?.mediaUri?.lastPathSegment,
    ).map { it.toString() }
        .filter { it.isNotBlank() }
        .distinct()
}

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "ts", "m2ts", "webm", "wmv", "mpg", "mpeg", "flv")
