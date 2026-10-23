package org.njarasoa.fijerena.core.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DisplayDateTest {
    @Test
    fun `an ISO day is written the UK way`() {
        assertEquals("17 Feb 2028", displayDate("2028-02-17", Locale.UK))
    }

    @Test
    fun `a time after the day is dropped`() {
        assertEquals("17 Feb 2028", displayDate("2028-02-17 20:00:00", Locale.UK))
    }

    @Test
    fun `other text is shown as it is`() {
        assertEquals("1999", displayDate("1999", Locale.UK))
        assertEquals("Feb 2028", displayDate("Feb 2028", Locale.UK))
    }
}
