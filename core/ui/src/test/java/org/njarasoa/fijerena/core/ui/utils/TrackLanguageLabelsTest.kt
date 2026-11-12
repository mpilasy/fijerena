package org.njarasoa.fijerena.core.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The languages of the Audio Language and Subtitle Language pickers (the preferred track languages plan, PL-Q6). */
class TrackLanguageLabelsTest {
    @Test
    fun `English, French and Malagasy come first, then the others by name`() {
        val codes = trackLanguageCodes("", Locale.ENGLISH)
        assertEquals(listOf("en", "fr", "mg"), codes.take(3))
        assertEquals(
            listOf("ar", "zh", "nl", "de", "el", "hi", "it", "ja", "ko", "pl", "pt", "ro", "ru", "es", "sv", "tr", "uk"),
            codes.drop(3),
        )
        assertEquals(20, codes.size)
    }

    @Test
    fun `the others follow their names in the app language`() {
        // French: allemand, anglais… so German comes first after the three.
        assertEquals("de", trackLanguageCodes("", Locale.FRENCH)[3])
    }

    @Test
    fun `a code the list does not have is offered too`() {
        assertTrue("fil" in trackLanguageCodes("fil", Locale.ENGLISH))
        assertEquals(20, trackLanguageCodes("off", Locale.ENGLISH).size)
        assertEquals(20, trackLanguageCodes("fr", Locale.ENGLISH).size)
    }

    @Test
    fun `no audio setting shows English`() {
        assertEquals("en", shownAudioLanguage(""))
        assertEquals("fr", shownAudioLanguage("fr"))
    }

    @Test
    fun `the switch shows only for a language`() {
        assertTrue(isSubtitleLanguage("fr"))
        assertFalse(isSubtitleLanguage(""))
        assertFalse(isSubtitleLanguage("off"))
    }
}
