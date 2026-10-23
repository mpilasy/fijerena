package org.njarasoa.fijerena.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteByIdTest {
    @Test
    fun `a theme is found by its id`() {
        assertEquals(GraphitePalette, paletteById("graphite"))
    }

    @Test
    fun `a profile that had Teal gets Aurora`() {
        assertEquals(AuroraPalette, paletteById("teal"))
    }

    @Test
    fun `an unknown theme is Deep night`() {
        assertEquals(DeepNightPalette, paletteById("nope"))
    }
}
