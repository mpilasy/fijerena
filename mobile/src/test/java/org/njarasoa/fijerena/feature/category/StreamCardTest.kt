package org.njarasoa.fijerena.feature.category

import androidx.compose.material3.CardDefaults
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
 * A channel row is one accessibility node that keeps the card's tap and long-press: with an
 * accessibility service on, Compose diffs every node on each scroll step (on the TV, the biggest
 * cost of a frame). See docs/plans/20260914_codebase-robustness-plan.md, Phase 4. Checked on the
 * merged tree, which like the accessibility walk leaves out what sits under
 * `clearAndSetSemantics`.
 */
@RunWith(RobolectricTestRunner::class)
class StreamCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setRow() {
        composeTestRule.setContent {
            StreamCard(
                item = MediaItem(id = "1", name = "PT: RTP 1 HD", mediaType = MediaType.LIVE_CHANNEL, categoryId = "pt"),
                nowPlayingProgram = EpgProgram(id = "p", title = "Jornal da Tarde", start = "0", end = "0"),
                catchupDays = 3,
                // Not the playing row: its title would scroll for ever, and the test never idles.
                isCurrent = true,
                cardColors = CardDefaults.cardColors(),
                onClick = {},
                onLongClick = {},
                onLongClickLabel = "Actions",
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
    fun `the row reads its parts in order and keeps tap and long-press`() {
        setRow()
        composeTestRule
            .onNodeWithContentDescription("Catch-up: 3 days, PT, RTP 1 HD, Now: Jornal da Tarde")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
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
