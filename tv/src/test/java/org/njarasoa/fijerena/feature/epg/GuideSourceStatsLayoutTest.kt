package org.njarasoa.fijerena.feature.epg

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TV Settings → Guide Sources in Malagasy, on a 1920×1080 screen: the stat labels of a guide
 * source's card are long, and the last ones were squeezed to a letter per line. Each label keeps
 * a readable width: at most two lines. Native graphics, so text is measured as on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+mg")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideSourceStatsLayoutTest : SettingsFocusTest() {
    @Test
    fun `each stat label of a guide source's card is at most two lines in Malagasy`() {
        seed(SettingsFixture.ADMIN)
        show { TvEpgManagementScreen(providerId = 0L, onBack = {}) }
        val labels =
            listOf(
                R.string.epg_source_stat_label,
                R.string.epg_source_stat_download,
                R.string.epg_source_stat_ingest,
                R.string.epg_source_stat_index,
                R.string.epg_source_stat_latest,
                R.string.epg_source_stat_channels,
                R.string.epg_source_stat_programmes,
            ).map(context::getString)
        // The card loads off the main thread.
        rule.waitUntil(LOAD_TIMEOUT_MS) {
            rule.onAllNodesWithText(labels.first(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        labels.forEach { label ->
            val nodes = rule.onAllNodesWithText(label, useUnmergedTree = true)
            val count = nodes.fetchSemanticsNodes().size
            assertTrue("no label \"$label\"", count > 0)
            repeat(count) { index ->
                val layouts = mutableListOf<TextLayoutResult>()
                nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val lines = layouts.single().lineCount
                assertTrue("\"$label\" takes $lines lines", lines <= 2)
            }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
