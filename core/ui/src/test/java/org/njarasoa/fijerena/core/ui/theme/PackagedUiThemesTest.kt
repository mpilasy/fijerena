package org.njarasoa.fijerena.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.UiThemes
import org.njarasoa.fijerena.core.ui.R

/** Every packaged UI theme names a colour theme and a Look and Feel the app has. */
class PackagedUiThemesTest {
    @Test
    fun `each packaged UI theme names a real colour theme and Look`() {
        UiThemes.packaged.forEach {
            assertEquals(it.id, it.themeId, paletteById(it.themeId).id)
            assertEquals(it.id, it.uiStyleId, styleById(it.uiStyleId).id)
        }
    }

    @Test
    fun `a UI theme with a light colour theme is Expert only`() {
        UiThemes.packaged.filter { paletteById(it.themeId).isLight }.forEach { assertEquals(it.id, true, it.expertOnly) }
    }

    @Test
    fun `a packaged UI theme takes its colour theme's or its Look's name, Custom its own`() {
        assertEquals(R.string.ui_style_sharp, uiThemeNameRes("sharp"))
        assertEquals(R.string.theme_meadow_dusk, uiThemeNameRes("meadow_dusk"))
        assertEquals(R.string.ui_theme_custom, uiThemeNameRes(UiThemes.CUSTOM))
    }
}
