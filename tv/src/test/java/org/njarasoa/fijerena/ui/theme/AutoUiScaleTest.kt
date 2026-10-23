package org.njarasoa.fijerena.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Text & grid size on Auto follows the screen's best resolution. */
class AutoUiScaleTest {
    @Test
    fun `a 4K screen gets 4K, 1080p HD, smaller SD`() {
        assertEquals(0.6f, uiScaleForScreen(3840))
        assertEquals(0.6f, uiScaleForScreen(7680))
        assertEquals(0.8f, uiScaleForScreen(1920))
        assertEquals(1.0f, uiScaleForScreen(1280))
    }

    @Test
    fun `a screen that says nothing gets HD`() {
        assertEquals(0.8f, uiScaleForScreen(0))
    }
}
