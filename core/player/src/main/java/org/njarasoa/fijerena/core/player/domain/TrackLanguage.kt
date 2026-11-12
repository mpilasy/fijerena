package org.njarasoa.fijerena.core.player.domain

import androidx.media3.common.C
import org.njarasoa.fijerena.core.player.model.AudioTrackInfo
import org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo
import java.text.Normalizer
import java.util.Locale

// The audio and subtitle tracks that a profile's track languages pick (the preferred track
// languages plan). Pure: the player gives the tracks it has, the item and the profile's setting.

/** The DVB code of the original version ("VO"). */
const val ORIGINAL_LANGUAGE_CODE = "qaa"

/** The DVB code of audio description. */
const val AUDIO_DESCRIPTION_CODE = "qad"

/** A profile's track languages. */
data class TrackLanguagePreference(
    /** An ISO 639-1 code; blank when the profile has no audio language. */
    val audio: String = "",
    /** An ISO 639-1 code, [SUBTITLES_OFF], or blank for the stream's default. */
    val subtitle: String = "",
    /** Subtitles in [subtitle] only when the audio is in another language. */
    val subtitlesOtherAudioOnly: Boolean = true,
) {
    companion object {
        const val SUBTITLES_OFF = "off"
    }
}

/** What the item tells about its languages, apart from its tracks. */
data class ItemLanguages(
    /** TMDB's original language (ISO 639-1), when it is known. */
    val original: String? = null,
    /** The language of the title's code ("FR - …"), when it names one. */
    val title: String? = null,
    /** The title's tags name subtitles ("VOSTFR", "ENG-SUB"): its audio is the original. */
    val titleNamesSubtitles: Boolean = false,
)

/** The subtitle choice of a profile's setting for the tracks of one item. */
sealed interface SubtitlePick {
    /** No change: the stream's default. */
    data object Keep : SubtitlePick

    /** No subtitles. */
    data object Off : SubtitlePick

    /** The track at [index] in the subtitle track list. */
    data class Track(
        val index: Int,
    ) : SubtitlePick
}

/**
 * The ISO 639-1 code of a 2- or 3-letter code ("en", "eng", "fre"); null when it is not a language's.
 * The bibliographic codes ("fre", "ger") count too.
 */
fun languageOfCode(code: String): String? {
    val lower = code.lowercase(Locale.ROOT)
    return when (lower.length) {
        2 -> lower.takeIf { it in ISO_LANGUAGES }
        3 -> BIBLIOGRAPHIC_LANGUAGES[lower] ?: ISO3_LANGUAGES[lower]
        else -> null
    }
}

/**
 * A track's language code as the stream gives it, normalised to ISO 639-1; [ORIGINAL_LANGUAGE_CODE]
 * and [AUDIO_DESCRIPTION_CODE] stay as they are. Null for no value, `und`, `mul`, `zxx` and any
 * other code that is not a language's.
 */
fun normalisedLanguage(code: String?): String? {
    val lower =
        code
            .orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
            .substringBefore('-')
            .substringBefore('_')
    return when (lower) {
        "", in UNKNOWN_CODES -> null
        ORIGINAL_LANGUAGE_CODE, AUDIO_DESCRIPTION_CODE -> lower
        else -> languageOfCode(lower)
    }
}

/**
 * The language that a track's label names: a release word ("VFF", "TrueFrench", "VO"), a language
 * name in English, in French or in the language itself ("French", "Français", "Deutsch"), or a 2- or
 * 3-letter code ("ENG", "fre"). A code counts only in capitals or as the whole label, so a word of
 * a sentence ("de", "it") is not taken for a language. A language wins over the original version
 * ("Original English" is English). Null when it names none.
 */
fun labelLanguage(label: String): String? {
    val words = labelWords(label)
    val single = words.size == 1
    val languages = words.mapNotNull { wordLanguage(it, single) }
    return languages.firstOrNull { it != ORIGINAL_LANGUAGE_CODE } ?: languages.firstOrNull()
}

/** The tags of a provider's [name] name subtitles ("VOSTFR", "FRENCH ENG-SUB", "NAPISY"): its audio is the original. */
fun titleNamesSubtitles(name: String): Boolean = parseDisplayTitle(name).tags.any { SUBTITLE_TAG.containsMatchIn(it) }

/** A commentary track: never picked by the profile's setting. */
fun isCommentary(
    roleFlags: Int,
    label: String,
): Boolean = roleFlags and C.ROLE_FLAG_COMMENTARY != 0 || foldedWords(label).any { it in COMMENTARY_WORDS }

/** An audio description track: never picked by the profile's setting. */
fun isAudioDescription(track: AudioTrackInfo): Boolean {
    val words = foldedWords(track.label)
    return track.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0 ||
        normalisedLanguage(track.languageCode) == AUDIO_DESCRIPTION_CODE ||
        words.any { it in AUDIO_DESCRIPTION_WORDS } ||
        ("audio" in words && "description" in words)
}

/** Subtitles for the deaf and hard of hearing: picked only when no other track has the language. */
fun isSdh(track: SubtitleTrackInfo): Boolean =
    track.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0 || foldedWords(track.label).any { it in SDH_WORDS }

/** Forced subtitles: they show only the parts in another language than the audio. */
fun isForced(track: SubtitleTrackInfo): Boolean =
    track.selectionFlags and C.SELECTION_FLAG_FORCED != 0 || foldedWords(track.label).any { it in FORCED_WORDS }

/**
 * An audio track's language: its code, else its label's, else, when it is the item's [onlyTrack],
 * the title's code ([ItemLanguages.title]); a title whose tags name subtitles has its audio in the
 * original ([ORIGINAL_LANGUAGE_CODE]).
 */
fun audioLanguage(
    track: AudioTrackInfo,
    onlyTrack: Boolean,
    item: ItemLanguages,
): String? =
    normalisedLanguage(track.languageCode)
        ?: labelLanguage(track.label)
        ?: onlyTrackLanguage(onlyTrack, item)

/** A subtitle track's language: its code, else its label's. */
fun subtitleLanguage(track: SubtitleTrackInfo): String? = normalisedLanguage(track.languageCode) ?: labelLanguage(track.label)

/**
 * The language of the audio track at [index] (the selected track when [index] is null), with the
 * original version read as [ItemLanguages.original]. Null when it is not known.
 */
fun playingAudioLanguage(
    tracks: List<AudioTrackInfo>,
    index: Int?,
    item: ItemLanguages,
): String? =
    tracks
        .getOrNull(index ?: tracks.indexOfFirst { it.isSelected })
        ?.let { comparable(audioLanguage(it, tracks.size == 1, item), item) }

/**
 * The index of the audio track for [language] (an ISO 639-1 code): the first track in it, else the
 * original (the track in [ItemLanguages.original], else an [ORIGINAL_LANGUAGE_CODE] track), else
 * the stream's default track (its default flag, else the first). Among tracks of one language the
 * default flag wins, then more channels. Commentary and audio description are never picked. Null
 * when no track is left.
 */
fun pickAudioTrack(
    tracks: List<AudioTrackInfo>,
    language: String,
    item: ItemLanguages,
): Int? {
    val onlyTrack = tracks.size == 1
    val candidates =
        tracks.withIndex().filter { (_, track) -> !isCommentary(track.roleFlags, track.label) && !isAudioDescription(track) }
    val languages = candidates.associate { (index, track) -> index to audioLanguage(track, onlyTrack, item) }
    val inLanguage = { code: String? -> candidates.filter { code != null && languages[it.index] == code } }
    val chosen =
        bestAudio(inLanguage(language))
            ?: bestAudio(inLanguage(item.original))
            ?: bestAudio(inLanguage(ORIGINAL_LANGUAGE_CODE))
            ?: candidates.firstOrNull { it.value.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 }
            ?: candidates.firstOrNull()
    return chosen?.index
}

/**
 * The subtitle choice of [preference] when the audio plays in [audioLanguage] (null: not known).
 *
 * - Blank: the stream's default ([SubtitlePick.Keep]).
 * - [TrackLanguagePreference.SUBTITLES_OFF]: no subtitles.
 * - A language, with [TrackLanguagePreference.subtitlesOtherAudioOnly]: a full track in it when the
 *   audio's language is known and another. Audio in that language or in an unknown one gets no
 *   subtitles: a wrong guess would put subtitles on every item in that language.
 * - A language, without the switch: a track in it always.
 *
 * "No subtitles" still shows a forced track in the audio's language: it carries the parts in another
 * language. A full track for the deaf and hard of hearing is taken only when no other has the
 * language.
 */
fun pickSubtitleTrack(
    tracks: List<SubtitleTrackInfo>,
    preference: TrackLanguagePreference,
    audioLanguage: String?,
): SubtitlePick {
    val wanted = preference.subtitle
    val off = forcedTrack(tracks, audioLanguage)?.let { SubtitlePick.Track(it) } ?: SubtitlePick.Off
    val full = fullTrack(tracks, wanted)
    return when {
        wanted.isBlank() -> SubtitlePick.Keep
        wanted == TrackLanguagePreference.SUBTITLES_OFF -> off
        !preference.subtitlesOtherAudioOnly -> (full ?: forcedTrack(tracks, wanted))?.let { SubtitlePick.Track(it) } ?: off
        audioLanguage == null || audioLanguage == wanted -> off
        else -> full?.let { SubtitlePick.Track(it) } ?: off
    }
}

/** The best of tracks of one language: the default flag, then more channels; the first on a tie. */
private fun bestAudio(tracks: List<IndexedValue<AudioTrackInfo>>): IndexedValue<AudioTrackInfo>? =
    tracks.maxWithOrNull(
        compareBy<IndexedValue<AudioTrackInfo>> { it.value.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 }
            .thenBy { it.value.channelCount },
    )

/** A full (not forced) track in [language]: one not for the deaf first, the default flag first. */
private fun fullTrack(
    tracks: List<SubtitleTrackInfo>,
    language: String,
): Int? {
    val full = tracks.withIndex().filter { (_, track) -> !isForced(track) && subtitleLanguage(track) == language }
    val chosen =
        full.firstOrNull { !isSdh(it.value) && it.value.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 }
            ?: full.firstOrNull { !isSdh(it.value) }
            ?: full.firstOrNull()
    return chosen?.index
}

/** A forced track in [language]; null when [language] is not known. */
private fun forcedTrack(
    tracks: List<SubtitleTrackInfo>,
    language: String?,
): Int? =
    tracks
        .withIndex()
        .firstOrNull { (_, track) -> language != null && isForced(track) && subtitleLanguage(track) == language }
        ?.index

/** The only audio track's language from the title: its code, or the original when its tags name subtitles. */
private fun onlyTrackLanguage(
    onlyTrack: Boolean,
    item: ItemLanguages,
): String? =
    when {
        !onlyTrack -> null
        item.titleNamesSubtitles -> ORIGINAL_LANGUAGE_CODE
        else -> item.title
    }

/** A language to compare with a subtitle language: the original version as its language, audio description as unknown. */
private fun comparable(
    code: String?,
    item: ItemLanguages,
): String? =
    when (code) {
        ORIGINAL_LANGUAGE_CODE -> item.original
        AUDIO_DESCRIPTION_CODE -> null
        else -> code
    }

private fun wordLanguage(
    word: String,
    single: Boolean,
): String? {
    val upper = word.uppercase(Locale.ROOT)
    val isCode = word.length in CODE_LENGTHS && (single || (word == upper && upper !in NOT_CODES))
    return RELEASE_WORDS[upper]
        ?: LANGUAGE_NAMES[fold(word)]
        ?: if (isCode) languageOfCode(word) else null
}

private fun labelWords(label: String): List<String> = label.split(LABEL_SEPARATORS).filter { it.isNotEmpty() }

private fun foldedWords(label: String): List<String> = labelWords(label).map(::fold)

/** Lower case, with no accents: "Français" → "francais". */
private fun fold(word: String): String =
    Normalizer
        .normalize(word.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(ACCENTS, "")

private val LABEL_SEPARATORS = Regex("[^\\p{L}\\p{M}]+")
private val ACCENTS = Regex("\\p{Mn}+")
private val SUBTITLE_TAG = Regex("(?<!\\p{L})(SUBS?|VOST\\p{L}*|NAPISY|TEXT)(?!\\p{L})", RegexOption.IGNORE_CASE)
private val CODE_LENGTHS = 2..3

private val UNKNOWN_CODES = setOf("und", "mul", "zxx", "mis")

/** Capital words in labels that are a codec's or a format's, not a language's ("HE-AAC", "DTS-ES", "HI"). */
private val NOT_CODES = setOf("HE", "ES", "HI", "MA", "HD", "TV", "DD", "EX", "LC", "AD", "CC", "SD", "ST", "VO", "VF")

/** Release words in labels: French dubs and the original version. */
private val RELEASE_WORDS =
    mapOf(
        "VF" to "fr",
        "VFF" to "fr",
        "VFQ" to "fr",
        "VFI" to "fr",
        "VQ" to "fr",
        "TRUEFRENCH" to "fr",
        "LATINO" to "es",
        "CASTELLANO" to "es",
        "VO" to ORIGINAL_LANGUAGE_CODE,
        "VOST" to ORIGINAL_LANGUAGE_CODE,
        "OV" to ORIGINAL_LANGUAGE_CODE,
        "ORIGINAL" to ORIGINAL_LANGUAGE_CODE,
        "ORIGINALE" to ORIGINAL_LANGUAGE_CODE,
    )

private val COMMENTARY_WORDS = setOf("commentary", "commentaire", "commentaires", "commentaries")
private val AUDIO_DESCRIPTION_WORDS = setOf("ad", "audiodescription")
private val SDH_WORDS = setOf("sdh", "cc", "malentendants", "malentendant")
private val FORCED_WORDS = setOf("forced", "force", "forces", "forcee", "forcees")

private val ISO_LANGUAGES = Locale.getISOLanguages().toSet()
private val ISO3_LANGUAGES = Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).isO3Language }

/** ISO 639-2 bibliographic codes that differ from the terminology ones Java knows. */
private val BIBLIOGRAPHIC_LANGUAGES =
    mapOf(
        "fre" to "fr",
        "ger" to "de",
        "dut" to "nl",
        "gre" to "el",
        "per" to "fa",
        "alb" to "sq",
        "chi" to "zh",
        "cze" to "cs",
        "rum" to "ro",
        "slo" to "sk",
        "arm" to "hy",
        "geo" to "ka",
        "mac" to "mk",
        "ice" to "is",
    )

/** Names that labels use for another meaning: "Latin" is Latin American Spanish there. */
private val NOT_NAMES = setOf("latin")

/** Each language's one-word name in English, in French and in itself, folded ([fold]). */
private val LANGUAGE_NAMES: Map<String, String> =
    Locale
        .getISOLanguages()
        .flatMap { code ->
            val locale = Locale.forLanguageTag(code)
            listOf(Locale.ENGLISH, Locale.FRENCH, locale).map { fold(locale.getDisplayLanguage(it)) to code }
        }.filter { (name, code) -> name.isNotEmpty() && name != code && name.none { it.isWhitespace() } && name !in NOT_NAMES }
        .toMap()
