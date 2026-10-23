package org.njarasoa.fijerena.feature.category.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.robolectric.RobolectricTestRunner

/**
 * A channel row is one accessibility node: with an accessibility service on (Button Mapper on a
 * Shield), Compose diffs every node on each scroll step, and a node per text and badge made that
 * the biggest cost of a frame. See docs/plans/20260914_codebase-robustness-plan.md, Phase 4.
 *
 * Checked on the merged tree: like the accessibility walk (`SemanticsNode.replacedChildren`), it
 * leaves out what sits under `clearAndSetSemantics`; the test's unmerged tree still lists it.
 */
@RunWith(RobolectricTestRunner::class)
class StreamItemTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val channel =
        MediaItem(id = "1", name = "PT: RTP 1 HD", mediaType = MediaType.LIVE_CHANNEL, categoryId = "pt")

    private fun setRow() {
        composeTestRule.setContent {
            StreamItem(
                item = channel,
                isFavorite = true,
                nowPlayingProgram = EpgProgram(id = "p", title = "Jornal da Tarde", start = "0", end = "0"),
                catchupDays = 3,
                onClick = {},
                onOpenActions = {},
                cardStyle = streamCardStyle(),
            )
        }
    }

    @Test
    fun `the row's texts and badges are not nodes of their own`() {
        setRow()
        composeTestRule.onAllNodesWithText("RTP 1 HD").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Now: Jornal da Tarde").assertCountEquals(0)
    }

    @Test
    fun `the row reads its parts in order, as one description`() {
        setRow()
        composeTestRule
            .onNodeWithContentDescription("★, Catch-up: 3 days, RTP 1, PT, HD, Now: Jornal da Tarde")
            .assertExists()
    }

    @Test
    fun `nothing inside the row adds a node`() {
        setRow()
        val described =
            composeTestRule
                .onRoot()
                .fetchSemanticsNode()
                .let { root -> generateSequence(listOf(root)) { level -> level.flatMap { it.children }.ifEmpty { null } }.flatten() }
                .count { SemanticsProperties.ContentDescription in it.config || SemanticsProperties.Text in it.config }
        assertEquals(1, described)
    }
}
