package org.njarasoa.fijerena.core.player.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AudioTrackNamesTest {
    private fun name(
        label: String?,
        language: String? = "en",
        mime: String? = "audio/vnd.dts.hd",
        channels: Int = 8,
        titles: List<String> = emptyList(),
    ) = audioTrackName(label, language, mime, null, channels, titles, "Mono", "Stereo", "Track", Locale.ENGLISH)

    @Test
    fun `a label that is the file's name gives way to language, codec and channels`() {
        assertEquals("English · DTS-HD · 7.1", name("The.Matrix.1999.REMASTERED.1080p.BluRay.x264-GROUP"))
        assertEquals("English · DTS-HD · 7.1", name("The Matrix (1999).mkv"))
        assertEquals("English · DTS-HD · 7.1", name("The Matrix", titles = listOf("The Matrix")))
    }

    @Test
    fun `a real label stays`() {
        assertEquals("Director's commentary", name("Director's commentary"))
    }

    @Test
    fun `no label builds the name, with the words for one and two channels`() {
        assertEquals("French · AC-3 · Stereo", name(null, language = "fr", mime = "audio/ac3", channels = 2))
        assertEquals("AAC · Mono", name(null, language = "und", mime = "audio/mp4a-latm", channels = 1))
        assertEquals("Track", name(null, language = null, mime = null, channels = 0))
    }

    @Test
    fun `file names are told from track names`() {
        assertTrue(isFileName("Movie.2020.1080p", emptyList()))
        assertTrue(isFileName("movie.mkv", emptyList()))
        assertFalse(isFileName("English 5.1", emptyList()))
        assertFalse(isFileName("Commentary", listOf("The Matrix")))
    }
}
