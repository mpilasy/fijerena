package org.njarasoa.fijerena.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class UiThemeOrderTest {
    @Test
    fun `Custom and Standard lead, the rest by name`() {
        val options = listOf("Standard" to "standard", "Slate" to "slate", "Custom" to "custom", "Clay" to "clay", "Aurora" to "aurora")
        val sorted = options.sortedAsUiThemes({ it.second }, { it.first }).map { it.first }
        assertEquals(listOf("Custom", "Standard", "Aurora", "Clay", "Slate"), sorted)
    }
}
