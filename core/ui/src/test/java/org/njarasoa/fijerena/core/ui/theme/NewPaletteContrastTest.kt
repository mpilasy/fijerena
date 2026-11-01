package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Console, Meadow, Meadow dusk, Slate, Crimson, Forest, Aurora, Royal and Clay: text reads on every surface and on the painted sky (4.5:1). */
class NewPaletteContrastTest {
    private val palettes =
        listOf(
            ConsolePalette,
            MeadowPalette,
            MeadowDuskPalette,
            SlatePalette,
            CrimsonPalette,
            ForestPalette,
            AuroraPalette,
            RoyalPalette,
            ClayPalette,
        )

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private fun assertReads(
        what: String,
        text: Color,
        ground: Color,
    ) {
        val ratio = contrast(text, ground)
        assertTrue("$what: $ratio", ratio >= 4.5f)
    }

    @Test
    fun `text reads on the surfaces`() {
        palettes.forEach { p ->
            listOf(p.background, p.surface, p.surfaceVariant, p.surfaceLight).forEach { ground ->
                assertReads("${p.id} primary", p.textPrimary, ground)
            }
            listOf(p.background, p.surface, p.surfaceVariant).forEach { ground ->
                assertReads("${p.id} secondary", p.textSecondary, ground)
            }
        }
    }

    @Test
    fun `labels read on the accent and on the focus fill`() {
        palettes.forEach { p ->
            assertReads("${p.id} on accent", p.onAccent, p.accent)
            val roles = CinemaColorRoles.from(p)
            assertReads("${p.id} on focus", roles.onFocusFill, roles.focusFill)
        }
    }

    @Test
    fun `the streaming looks' accent text reads on the ground`() {
        listOf(CrimsonPalette, ForestPalette, AuroraPalette, RoyalPalette, ClayPalette).forEach { p ->
            val ratio = contrast(p.accent, p.background)
            assertTrue("${p.id} accent on ground: $ratio", ratio >= 3.9f)
        }
    }

    @Test
    fun `headings read on the painted sky`() {
        palettes.forEach { p ->
            val sky = p.scenery as? Scenery.Meadow ?: return@forEach
            listOf(sky.skyTop, sky.skyBottom).plus(sky.hills).forEach { ground ->
                assertReads("${p.id} text on sky", p.textPrimary, ground)
            }
        }
    }
}
