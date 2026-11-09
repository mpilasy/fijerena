package org.njarasoa.fijerena.core.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.ConsoleStyle
import org.njarasoa.fijerena.core.ui.theme.DialStyle
import org.njarasoa.fijerena.core.ui.theme.ModernStyle
import org.njarasoa.fijerena.core.ui.theme.UiStyle
import java.util.Locale

/** Dial alone sets its chip labels in spaced capitals; a button in Dial and every other Look do not change. */
class ChipLabelTest {
    private fun shownInChip(look: UiStyle) = lookCase("All", TextRole.Label, look.forChipLabel().type.capsLabels, Locale.UK)

    @Test
    fun `a Look with no chip label token keeps the case and the tracking`() {
        assertSame(ModernStyle, ModernStyle.forChipLabel())
        assertSame(ConsoleStyle, ConsoleStyle.forChipLabel())
        assertEquals("All", shownInChip(ModernStyle))
        assertEquals("ALL", shownInChip(ConsoleStyle))
    }

    @Test
    fun `only Dial has a chip label token`() {
        assertEquals(listOf(DialStyle), AllUiStyles.filter { it.type.chipLabel != null })
    }

    @Test
    fun `a chip in Dial is in capitals, a button is not`() {
        assertEquals("ALL", shownInChip(DialStyle))
        assertEquals("All", lookCase("All", TextRole.Label, DialStyle.type.capsLabels, Locale.UK))
    }

    @Test
    fun `a chip in Dial adds its tracking to the label's`() {
        val tracked = TextStyle(letterSpacing = 0.5.sp).trackedForChip(DialStyle.type.chipLabel!!)
        assertEquals(1.3f, tracked.letterSpacing.value, 0.001f)
    }
}
