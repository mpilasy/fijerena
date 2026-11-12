package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.AudioTrackInfo
import org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo

/** Which choice starts an item's audio and subtitles (the preferred track languages plan). */
class StartTracksTest {
    private val french = TrackLanguagePreference(audio = "fr", subtitle = "fr")

    // startTracks

    @Test
    fun `the item's own choice wins`() {
        assertEquals(StartTracks(TrackStart.Item(1), TrackStart.Item(-1)), startTracks(1, -1, 0 to 0, french))
    }

    @Test
    fun `an episode with no choice takes the series'`() {
        assertEquals(StartTracks(TrackStart.Series(2), TrackStart.Series(0)), startTracks(null, null, 2 to 0, french))
    }

    @Test
    fun `the profile's languages come next`() {
        assertEquals(StartTracks(TrackStart.Profile, TrackStart.Profile), startTracks(null, null, null, french))
    }

    @Test
    fun `with nothing set the stream's default stays`() {
        val start = startTracks(null, null, null, TrackLanguagePreference())
        assertEquals(StartTracks(), start)
        assertFalse(start.needsTracks)
        assertEquals(StartTracks(), startTracks(null, null, null, null))
    }

    @Test
    fun `one type saved and the other not`() {
        // The item's audio is saved: the series is not read, and the subtitles follow the profile.
        assertEquals(StartTracks(TrackStart.Item(1), TrackStart.Profile), startTracks(1, null, 0 to 0, french))
        // The series saved only subtitles: the audio follows the profile.
        assertEquals(StartTracks(TrackStart.Profile, TrackStart.Series(-1)), startTracks(null, null, null to -1, french))
        // A profile with only a subtitle language leaves the audio to the stream.
        val subtitlesOnly = TrackLanguagePreference(subtitle = "fr")
        assertEquals(StartTracks(TrackStart.StreamDefault, TrackStart.Profile), startTracks(null, null, null, subtitlesOnly))
    }

    @Test
    fun `live TV and catch-up do not use the profile`() {
        assertEquals(StartTracks(TrackStart.Item(1), TrackStart.StreamDefault), startTracks(1, null, null, null))
    }

    // startTrackActions

    private fun audio(
        code: String,
        selected: Boolean = false,
    ) = AudioTrackInfo(0, 0, code, code, 2, 48_000, 0, selected, languageCode = code)

    private fun subtitle(
        code: String,
        selected: Boolean = false,
    ) = SubtitleTrackInfo(0, 0, code, code, "text/vtt", selected, languageCode = code)

    private val audioTracks = listOf(audio("en", selected = true), audio("fr"))
    private val subtitleTracks = listOf(subtitle("en"), subtitle("fr"))

    @Test
    fun `a saved choice is applied and saved again`() {
        val actions =
            startTrackActions(
                StartTracks(TrackStart.Series(1), TrackStart.Series(-1)),
                french,
                ItemLanguages(),
                audioTracks,
                subtitleTracks,
            )
        assertEquals(listOf(TrackAction.Audio(1, persist = true), TrackAction.Subtitle(-1, persist = true)), actions)
    }

    @Test
    fun `a profile pick is never saved`() {
        val subtitles = TrackLanguagePreference(audio = "en", subtitle = "fr")
        val start = StartTracks(TrackStart.Profile, TrackStart.Profile)
        val actions =
            startTrackActions(start, subtitles, ItemLanguages(), listOf(audio("fr", selected = true), audio("en")), subtitleTracks)
        assertEquals(listOf(TrackAction.Audio(1, persist = false), TrackAction.Subtitle(1, persist = false)), actions)
        assertTrue(actions.none { it.persist })
    }

    @Test
    fun `a profile pick that already plays changes nothing`() {
        val start = StartTracks(TrackStart.Profile, TrackStart.Profile)
        val tracks = listOf(audio("en"), audio("fr", selected = true))
        // French audio: French subtitles only when the audio is in another language, and none shows.
        assertEquals(emptyList<TrackAction>(), startTrackActions(start, french, ItemLanguages(), tracks, subtitleTracks))
    }

    @Test
    fun `the subtitles follow the audio the profile picks`() {
        val start = StartTracks(TrackStart.Profile, TrackStart.Profile)
        val actions =
            startTrackActions(start, french, ItemLanguages(), audioTracks, listOf(subtitle("en"), subtitle("fr", selected = true)))
        // French audio gets picked, so the French subtitles that show go off.
        assertEquals(listOf(TrackAction.Audio(1, persist = false), TrackAction.Subtitle(-1, persist = false)), actions)
    }

    @Test
    fun `the subtitles follow a saved audio choice`() {
        val start = StartTracks(TrackStart.Item(0), TrackStart.Profile)
        val actions = startTrackActions(start, french, ItemLanguages(), audioTracks, subtitleTracks)
        assertEquals(listOf(TrackAction.Audio(0, persist = true), TrackAction.Subtitle(1, persist = false)), actions)
    }

    @Test
    fun `the stream's default does nothing`() {
        assertEquals(emptyList<TrackAction>(), startTrackActions(StartTracks(), french, ItemLanguages(), audioTracks, subtitleTracks))
    }

    // preferredAudioLanguages

    @Test
    fun `Media3 prefers the profile's language, then the original`() {
        assertEquals(listOf("fr", "ja", ORIGINAL_LANGUAGE_CODE), preferredAudioLanguages(french, ItemLanguages(original = "ja")))
        assertEquals(listOf("fr", ORIGINAL_LANGUAGE_CODE), preferredAudioLanguages(french, ItemLanguages(original = "fr")))
        assertEquals(emptyList<String>(), preferredAudioLanguages(TrackLanguagePreference(subtitle = "fr"), ItemLanguages()))
        assertEquals(emptyList<String>(), preferredAudioLanguages(null, ItemLanguages(original = "ja")))
    }
}
