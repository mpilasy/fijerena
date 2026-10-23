package org.njarasoa.fijerena.core.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** English is UK English; the other languages keep their own tag. */
class LocaleManagerTest {
    @Test
    fun `English is UK English`() {
        assertEquals(Locale.UK, LocaleManager.localeFor("en"))
    }

    @Test
    fun `French and Malagasy keep their tag`() {
        assertEquals(Locale.forLanguageTag("fr"), LocaleManager.localeFor("fr"))
        assertEquals(Locale.forLanguageTag("mg"), LocaleManager.localeFor("mg"))
    }
}
