package org.njarasoa.fijerena.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.tmdb.CastMember
import org.njarasoa.fijerena.core.network.tmdb.TitleCredits
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner

/** The Cast tab's More card: OK shows the rest of the cast, and the 11th actor keeps the focus. */
@RunWith(RobolectricTestRunner::class)
class TvCreditsTabTest {
    @get:Rule
    val rule = createComposeRule()

    private val credits =
        TitleCredits(
            cast = (1..12).map { CastMember(it.toString(), "Actor $it", null, null) },
            crew = emptyList(),
        )

    @Test
    fun `the More card shows the rest of the cast and its place keeps the focus`() {
        rule.setContent {
            FirstVideoPlayerTheme {
                TvCreditsTabContent(
                    credits = credits,
                    section = ThumbnailContentType.MOVIE,
                    returnFocus = rememberNavReturnFocus(),
                    onPersonSelected = { _, _, _ -> },
                )
            }
        }
        val row = rule.onNode(hasScrollToIndexAction())
        row.performScrollToIndex(10)
        rule.onNodeWithText("Actor 11").assertDoesNotExist()
        val more = rule.onNodeWithText("+2 more")
        more.performSemanticsAction(SemanticsActions.RequestFocus)
        more.assertIsFocused()

        // OK on the remote.
        more.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        rule.onNodeWithText("+2 more").assertDoesNotExist()
        rule.onNodeWithText("Actor 11").assertIsFocused()
    }
}
