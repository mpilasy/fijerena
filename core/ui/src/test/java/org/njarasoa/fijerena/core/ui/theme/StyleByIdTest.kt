package org.njarasoa.fijerena.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class StyleByIdTest {
    @Test
    fun `a Look is found by its id`() {
        assertEquals(CinemaStyle, styleById("cinema"))
    }

    @Test
    fun `a profile that had Serif gets Cinema`() {
        assertEquals(CinemaStyle, styleById("serif"))
    }

    @Test
    fun `an unknown Look is Modern`() {
        assertEquals(ModernStyle, styleById("nope"))
    }
}
