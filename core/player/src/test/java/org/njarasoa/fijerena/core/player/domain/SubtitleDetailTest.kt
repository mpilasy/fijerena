package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo
import java.util.Locale

class SubtitleDetailTest {
    private fun track(
        label: String,
        language: String,
        mimeType: String,
    ) = SubtitleTrackInfo(groupIndex = 0, trackIndex = 0, language = language, label = label, mimeType = mimeType, isSelected = false)

    @Test
    fun `a label that names the language and the format needs no second line`() {
        assertNull(subtitleDetail(track("English-PGS", "en", "application/pgs"), Locale.ENGLISH))
    }

    @Test
    fun `a MIME type with no name is never shown`() {
        assertNull(subtitleDetail(track("English", "en", "application/x-media3-cues"), Locale.ENGLISH))
    }

    @Test
    fun `a bare language code gets the language and the format in words`() {
        assertEquals("French · SRT", subtitleDetail(track("fr", "fr", "application/x-subrip"), Locale.ENGLISH))
    }

    @Test
    fun `an unknown language adds nothing`() {
        assertEquals("WebVTT", subtitleDetail(track("Track 2", "Unknown", "text/vtt"), Locale.ENGLISH))
    }
}
