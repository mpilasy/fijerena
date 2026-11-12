package org.njarasoa.fijerena.core.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.domain.TrackLanguagePreference
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.viewmodels.languageLabel
import java.text.Collator
import java.util.Locale

// The values of the profile's Audio Language and Subtitle Language rows and their pickers, the
// same on TV and phone (the preferred track languages plan, PL-Q6).

/** First in the pickers, in this order. */
private val FIRST_TRACK_LANGUAGES = listOf("en", "fr", "mg")

/** Then these, by name in the app language. */
private val OTHER_TRACK_LANGUAGES =
    listOf("ar", "de", "el", "es", "hi", "it", "ja", "ko", "nl", "pl", "pt", "ro", "ru", "sv", "tr", "uk", "zh")

/**
 * The language codes the pickers offer: English, French and Malagasy, then the others by their name
 * in [locale]. A [current] code that the list does not have (set on a newer version) is offered too,
 * so the picker can start on it.
 */
fun trackLanguageCodes(
    current: String,
    locale: Locale,
): List<String> {
    val listed = FIRST_TRACK_LANGUAGES + OTHER_TRACK_LANGUAGES
    val extra = listOf(current).filter { it.isNotBlank() && it != TrackLanguagePreference.SUBTITLES_OFF && it !in listed }
    val collator = Collator.getInstance(locale)
    return FIRST_TRACK_LANGUAGES + (OTHER_TRACK_LANGUAGES + extra).sortedWith(compareBy(collator) { languageLabel(it, locale) })
}

/** The audio language a profile has: its own, else English (PL-Q1 b). */
fun shownAudioLanguage(audio: String): String = audio.ifBlank { PlayerConfigFactory.DEFAULT_AUDIO_LANGUAGE }

/** A track language value in words, in the app language: a language's name, "Stream default" for blank, "Off". */
@Composable
fun trackLanguageLabel(value: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (value) {
        "" -> stringResource(R.string.settings_track_language_stream_default)
        TrackLanguagePreference.SUBTITLES_OFF -> stringResource(R.string.player_subtitles_off)
        else -> languageLabel(value, locale)
    }
}

/** The Audio Language picker's choices, label to code. */
@Composable
fun audioLanguageChoices(current: String): List<Pair<String, String>> {
    val locale = LocalConfiguration.current.locales[0]
    return trackLanguageCodes(current, locale).map { languageLabel(it, locale) to it }
}

/** The Subtitle Language picker's choices, label to value: Stream default, Off, then the languages. */
@Composable
fun subtitleLanguageChoices(current: String): List<Pair<String, String>> {
    val locale = LocalConfiguration.current.locales[0]
    return listOf(
        stringResource(R.string.settings_track_language_stream_default) to "",
        stringResource(R.string.player_subtitles_off) to TrackLanguagePreference.SUBTITLES_OFF,
    ) + trackLanguageCodes(current, locale).map { languageLabel(it, locale) to it }
}

/** A subtitle value that is a language: the "Only When the Audio Is in Another Language" switch shows. */
fun isSubtitleLanguage(value: String): Boolean = value.isNotBlank() && value != TrackLanguagePreference.SUBTITLES_OFF
