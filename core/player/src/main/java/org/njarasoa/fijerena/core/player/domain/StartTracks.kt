package org.njarasoa.fijerena.core.player.domain

import org.njarasoa.fijerena.core.player.model.AudioTrackInfo
import org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo

// Which choice starts an item's audio and subtitles (the preferred track languages plan, "Which
// choice wins"): the item's own, the series', the profile's languages, else the stream's default.

/** Where the start track of one type (audio or subtitles) comes from. */
sealed interface TrackStart {
    /** The item's own saved choice: an index in the track list, -1 for subtitles off. */
    data class Item(
        val index: Int,
    ) : TrackStart

    /** The series' latest choice, for an episode with no choice of its own. */
    data class Series(
        val index: Int,
    ) : TrackStart

    /** The profile's track languages. */
    data object Profile : TrackStart

    /** The stream's default track: nothing to do. */
    data object StreamDefault : TrackStart
}

/** The start of an item's audio and subtitles. */
data class StartTracks(
    val audio: TrackStart = TrackStart.StreamDefault,
    val subtitle: TrackStart = TrackStart.StreamDefault,
) {
    /** The player waits for the tracks only when one type has something to do. */
    val needsTracks: Boolean
        get() = audio != TrackStart.StreamDefault || subtitle != TrackStart.StreamDefault
}

/** One track change the player makes once the tracks are known. A [persist] change is saved as the item's choice. */
sealed interface TrackAction {
    val persist: Boolean

    data class Audio(
        val index: Int,
        override val persist: Boolean,
    ) : TrackAction

    /** [index] -1: subtitles off. */
    data class Subtitle(
        val index: Int,
        override val persist: Boolean,
    ) : TrackAction
}

/**
 * The start of each track type, decided on its own: the item's choice ([itemAudio],
 * [itemSubtitle]); else the series' ([series], audio then subtitles), which an episode takes only
 * when it has no audio and no subtitle choice; else [preference] when it sets that type (null: the
 * item does not use the profile's languages); else the stream's default.
 */
fun startTracks(
    itemAudio: Int?,
    itemSubtitle: Int?,
    series: Pair<Int?, Int?>?,
    preference: TrackLanguagePreference?,
): StartTracks {
    val fromSeries = series?.takeIf { itemAudio == null && itemSubtitle == null }
    return StartTracks(
        audio = trackStart(itemAudio, fromSeries?.first, preference?.audio?.isNotBlank() == true),
        subtitle = trackStart(itemSubtitle, fromSeries?.second, preference?.subtitle?.isNotBlank() == true),
    )
}

/**
 * The track changes for [start] on the item's [audio] and [subtitles] tracks. A saved index is
 * applied and saved again, as before: thus a series' choice becomes the episode's own. A profile
 * pick is never saved, and is made only when it is not the track that plays.
 */
fun startTrackActions(
    start: StartTracks,
    preference: TrackLanguagePreference,
    item: ItemLanguages,
    audio: List<AudioTrackInfo>,
    subtitles: List<SubtitleTrackInfo>,
): List<TrackAction> {
    val audioIndex =
        when (val type = start.audio) {
            is TrackStart.Item -> type.index
            is TrackStart.Series -> type.index
            TrackStart.Profile -> pickAudioTrack(audio, preference.audio, item)
            TrackStart.StreamDefault -> null
        }
    val audioAction =
        when (start.audio) {
            is TrackStart.Item, is TrackStart.Series -> {
                audioIndex?.let { TrackAction.Audio(it, persist = true) }
            }

            TrackStart.Profile -> {
                audioIndex
                    ?.takeIf {
                        audio
                            .getOrNull(
                                it,
                            )?.isSelected == false
                    }?.let { TrackAction.Audio(it, persist = false) }
            }

            TrackStart.StreamDefault -> {
                null
            }
        }
    val subtitleAction =
        when (val type = start.subtitle) {
            is TrackStart.Item -> {
                TrackAction.Subtitle(type.index, persist = true)
            }

            is TrackStart.Series -> {
                TrackAction.Subtitle(type.index, persist = true)
            }

            TrackStart.Profile -> {
                profileSubtitle(
                    pickSubtitleTrack(subtitles, preference, playingAudioLanguage(audio, audioIndex, item)),
                    subtitles,
                )
            }

            TrackStart.StreamDefault -> {
                null
            }
        }
    return listOfNotNull(audioAction, subtitleAction)
}

private fun trackStart(
    item: Int?,
    series: Int?,
    profile: Boolean,
): TrackStart =
    when {
        item != null -> TrackStart.Item(item)
        series != null -> TrackStart.Series(series)
        profile -> TrackStart.Profile
        else -> TrackStart.StreamDefault
    }

/** The unsaved change for a profile's subtitle [pick]; none when it is what plays already. */
private fun profileSubtitle(
    pick: SubtitlePick,
    subtitles: List<SubtitleTrackInfo>,
): TrackAction? =
    when (pick) {
        SubtitlePick.Keep -> {
            null
        }

        SubtitlePick.Off -> {
            TrackAction.Subtitle(-1, persist = false).takeIf { subtitles.any { it.isSelected } }
        }

        is SubtitlePick.Track -> {
            TrackAction.Subtitle(pick.index, persist = false).takeIf {
                subtitles.getOrNull(pick.index)?.isSelected ==
                    false
            }
        }
    }

/**
 * The audio languages for Media3 to prefer when the stream starts, so a well-tagged stream starts
 * on the right track: [preference]'s, then the original. Empty for no audio setting or an item
 * that does not use the profile's languages: the player then prefers English, as before.
 */
fun preferredAudioLanguages(
    preference: TrackLanguagePreference?,
    item: ItemLanguages,
): List<String> =
    preference
        ?.audio
        ?.takeIf { it.isNotBlank() }
        ?.let { listOfNotNull(it, item.original, ORIGINAL_LANGUAGE_CODE).distinct() }
        .orEmpty()
