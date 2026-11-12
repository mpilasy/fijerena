package org.njarasoa.fijerena.core.player.domain

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.AudioTrackInfo
import org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo

/** The tracks that a profile's track languages pick (the preferred track languages plan). */
class TrackLanguageTest {
    private fun audio(
        code: String?,
        label: String = "",
        channels: Int = 2,
        roleFlags: Int = 0,
        selectionFlags: Int = 0,
        selected: Boolean = false,
    ) = AudioTrackInfo(
        groupIndex = 0,
        trackIndex = 0,
        language = code ?: "Unknown",
        label = label,
        channelCount = channels,
        sampleRate = 48_000,
        bitrate = 0,
        isSelected = selected,
        languageCode = code,
        roleFlags = roleFlags,
        selectionFlags = selectionFlags,
    )

    private fun subtitle(
        code: String?,
        label: String = "",
        roleFlags: Int = 0,
        selectionFlags: Int = 0,
    ) = SubtitleTrackInfo(
        groupIndex = 0,
        trackIndex = 0,
        language = code ?: "Unknown",
        label = label,
        mimeType = "text/vtt",
        isSelected = false,
        languageCode = code,
        roleFlags = roleFlags,
        selectionFlags = selectionFlags,
    )

    private val noItem = ItemLanguages()

    // A track's language

    @Test
    fun `codes are normalised and empty codes say nothing`() {
        assertEquals("fr", normalisedLanguage("fre"))
        assertEquals("fr", normalisedLanguage("fra"))
        assertEquals("de", normalisedLanguage("ger"))
        assertEquals("en", normalisedLanguage("en-US"))
        assertEquals(ORIGINAL_LANGUAGE_CODE, normalisedLanguage("qaa"))
        assertNull(normalisedLanguage("und"))
        assertNull(normalisedLanguage("mul"))
        assertNull(normalisedLanguage("zxx"))
        assertNull(normalisedLanguage(""))
        assertNull(normalisedLanguage(null))
    }

    @Test
    fun `an und track takes the language of its label`() {
        assertEquals("fr", audioLanguage(audio("und", "VFF"), onlyTrack = false, noItem))
        assertEquals("fr", audioLanguage(audio(null, "TrueFrench 5.1"), onlyTrack = false, noItem))
        assertEquals(ORIGINAL_LANGUAGE_CODE, audioLanguage(audio("und", "VO"), onlyTrack = false, noItem))
    }

    @Test
    fun `labels name a language by a code or a name`() {
        assertEquals("en", labelLanguage("ENG"))
        assertEquals("fr", labelLanguage("fre"))
        assertEquals("fr", labelLanguage("Français"))
        assertEquals("de", labelLanguage("Deutsch DTS-HD"))
        assertEquals("en", labelLanguage("Original English"))
    }

    @Test
    fun `codec words and small words are not languages`() {
        assertNull(labelLanguage("HE-AAC Stereo"))
        assertNull(labelLanguage("DTS-ES 6.1"))
        assertNull(labelLanguage("Version de luxe"))
        assertNull(labelLanguage("Stereo"))
    }

    @Test
    fun `the only track takes the title's code`() {
        val item = ItemLanguages(title = "fr")
        assertEquals("fr", audioLanguage(audio(null), onlyTrack = true, item))
        assertNull(audioLanguage(audio(null), onlyTrack = false, item))
    }

    @Test
    fun `a title that names subtitles has its audio in the original`() {
        val name = "FR - Parasite (2019) VOSTFR"
        assertTrue(titleNamesSubtitles(name))
        assertTrue(titleNamesSubtitles("Cocorico 2 (2026) (FRENCH ENG-SUB)"))
        assertFalse(titleNamesSubtitles("FR - Dune (2021)"))
        val item = ItemLanguages(original = "ko", title = "fr", titleNamesSubtitles = true)
        assertEquals(ORIGINAL_LANGUAGE_CODE, audioLanguage(audio(null), onlyTrack = true, item))
        assertEquals("ko", playingAudioLanguage(listOf(audio(null, selected = true)), null, item))
    }

    // Audio

    @Test
    fun `the profile's language wins and its commentary is never picked`() {
        val tracks =
            listOf(
                audio("en", "English", channels = 6),
                audio("fr", "Commentaire", channels = 6, roleFlags = C.ROLE_FLAG_COMMENTARY),
                audio("fr", "Français", channels = 6),
            )
        assertEquals(2, pickAudioTrack(tracks, "fr", noItem))
    }

    @Test
    fun `a commentary named only by its label is never picked`() {
        val tracks = listOf(audio("fr", "Commentary"), audio("en"), audio("fr", "Main"))
        assertEquals(2, pickAudioTrack(tracks, "fr", noItem))
    }

    @Test
    fun `among tracks of one language the default flag wins, then more channels`() {
        val tracks =
            listOf(
                audio("fr", channels = 2),
                audio("fr", channels = 6),
                audio("fr", channels = 2, selectionFlags = C.SELECTION_FLAG_DEFAULT),
            )
        assertEquals(2, pickAudioTrack(tracks, "fr", noItem))
        assertEquals(1, pickAudioTrack(tracks.take(2), "fr", noItem))
    }

    @Test
    fun `audio description is never picked`() {
        val tracks =
            listOf(
                audio("en"),
                audio("fr", "AD"),
                audio("qad"),
                audio("fr", "Audio description"),
                audio("fr", roleFlags = C.ROLE_FLAG_DESCRIBES_VIDEO),
            )
        assertEquals(0, pickAudioTrack(tracks, "fr", noItem))
    }

    @Test
    fun `without the profile's language the original plays, from TMDB first`() {
        val tracks = listOf(audio("de"), audio("qaa"), audio("ja"), audio("fr", selectionFlags = C.SELECTION_FLAG_DEFAULT))
        assertEquals(2, pickAudioTrack(tracks, "mg", ItemLanguages(original = "ja")))
    }

    @Test
    fun `without TMDB the original version track plays`() {
        val tracks = listOf(audio("de"), audio("und", "VO"), audio("fr", selectionFlags = C.SELECTION_FLAG_DEFAULT))
        assertEquals(1, pickAudioTrack(tracks, "mg", noItem))
    }

    @Test
    fun `without any of them the stream's default track plays`() {
        val tracks = listOf(audio("de"), audio("fr", selectionFlags = C.SELECTION_FLAG_DEFAULT))
        assertEquals(1, pickAudioTrack(tracks, "mg", noItem))
        assertEquals(0, pickAudioTrack(listOf(audio("de"), audio("fr")), "mg", noItem))
        assertNull(pickAudioTrack(emptyList(), "mg", noItem))
    }

    // Subtitles

    private val subtitles =
        listOf(
            subtitle("fr", "Forced", selectionFlags = C.SELECTION_FLAG_FORCED),
            subtitle("fr", "SDH", roleFlags = C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND),
            subtitle("fr", "Français"),
            subtitle("en", "English"),
        )
    private val french = TrackLanguagePreference(subtitle = "fr")

    @Test
    fun `audio in another language gets a full track in the language`() {
        assertEquals(SubtitlePick.Track(2), pickSubtitleTrack(subtitles, french, "en"))
    }

    @Test
    fun `audio in the language gets only its forced track`() {
        assertEquals(SubtitlePick.Track(0), pickSubtitleTrack(subtitles, french, "fr"))
        assertEquals(SubtitlePick.Off, pickSubtitleTrack(subtitles.drop(1), french, "fr"))
    }

    @Test
    fun `audio in an unknown language gets no subtitles`() {
        assertEquals(SubtitlePick.Off, pickSubtitleTrack(subtitles, french, null))
    }

    @Test
    fun `with the switch off the language always shows`() {
        val always = french.copy(subtitlesOtherAudioOnly = false)
        assertEquals(SubtitlePick.Track(2), pickSubtitleTrack(subtitles, always, "fr"))
        assertEquals(SubtitlePick.Track(2), pickSubtitleTrack(subtitles, always, null))
    }

    @Test
    fun `off keeps only a forced track in the audio's language`() {
        val off = TrackLanguagePreference(subtitle = TrackLanguagePreference.SUBTITLES_OFF)
        assertEquals(SubtitlePick.Track(0), pickSubtitleTrack(subtitles, off, "fr"))
        assertEquals(SubtitlePick.Off, pickSubtitleTrack(subtitles, off, "en"))
        assertEquals(SubtitlePick.Off, pickSubtitleTrack(subtitles, off, null))
    }

    @Test
    fun `a track for the deaf is picked only when no other has the language`() {
        val sdhOnly = listOf(subtitle("en"), subtitle("fr", "SDH"))
        assertEquals(SubtitlePick.Track(1), pickSubtitleTrack(sdhOnly, french, "en"))
        assertTrue(isSdh(subtitle("fr", "Français (malentendants)")))
    }

    @Test
    fun `no track in the language gets no subtitles`() {
        assertEquals(SubtitlePick.Off, pickSubtitleTrack(listOf(subtitle("en")), french, "de"))
    }

    @Test
    fun `no subtitle language keeps the stream's default`() {
        assertEquals(SubtitlePick.Keep, pickSubtitleTrack(subtitles, TrackLanguagePreference(), "en"))
    }
}
