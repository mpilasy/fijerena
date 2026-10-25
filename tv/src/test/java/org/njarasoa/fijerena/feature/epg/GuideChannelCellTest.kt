package org.njarasoa.fijerena.feature.epg

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The TV Guide's channel column shows a channel as the Live TV list does: the clean name, and the
 * tags the provider put in the name as a badge. Its rows grow when the text is taller, so the name
 * is not cut off.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideChannelCellTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the channel cell shows the clean name and the country as a badge`() {
        val channel = MediaItem(id = "1", name = "US: BBC WORLD", mediaType = MediaType.LIVE_CHANNEL, categoryId = "news")
        rule.setContent {
            ChannelCell(
                channel = channel,
                sourceName = null,
                cardStyle = TvCardLooks.guideCell(),
                onClick = {},
                onLongClick = {},
            )
        }
        rule.onNodeWithText("BBC WORLD", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("US", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithText("US: BBC WORLD", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a row is taller with a larger font, never shorter than the base height`() {
        var normal: Dp? = null
        var large: Dp? = null
        rule.setContent {
            val density = LocalDensity.current
            normal = guideRowHeight()
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                large = guideRowHeight()
            }
        }
        rule.waitForIdle()
        assertTrue("normal $normal", normal!! >= TvDimensions.epgRowHeight)
        assertTrue("large $large, normal $normal", large!! > normal!!)
    }
}
