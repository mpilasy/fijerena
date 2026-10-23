package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A packaged UI theme forces the colour theme and the Look and Feel; Custom leaves them free; the poster background is always the profile's. */
class UiThemesTest {
    private val own = ProfileLook(themeId = "forest", uiStyleId = "cinema", posterBackdrop = true)
    private val packaged = listOf(PackagedUiTheme("meadow_dusk", themeId = "meadow_dusk", uiStyleId = "meadow"))

    @Test
    fun `custom keeps the profile's own look`() {
        assertEquals(own, UiThemes.resolveLook(UiThemes.CUSTOM, own, packaged))
    }

    @Test
    fun `a packaged UI theme forces the colour theme and the Look, not the poster background`() {
        assertEquals(ProfileLook("meadow_dusk", "meadow", true), UiThemes.resolveLook("meadow_dusk", own, packaged))
    }

    @Test
    fun `an unknown id counts as custom`() {
        assertNull(UiThemes.packaged("from-a-newer-build", packaged))
        assertEquals(own, UiThemes.resolveLook("from-a-newer-build", own, packaged))
    }
}
