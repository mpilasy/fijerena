package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.history.LocalShowSourceBadges
import org.robolectric.RobolectricTestRunner

/**
 * A card's badge line is one line of one height: the source, the rating, then the tags, and the
 * tags that do not fit become one "+N" badge. Home's cards show no tags.
 */
@RunWith(RobolectricTestRunner::class)
class CardLinesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class Card(
        val title: String,
        val rating: String? = null,
        val titleBadges: Boolean = true,
    )

    private fun setCards(
        vararg cards: Card,
        admin: Boolean = true,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalShowSourceBadges provides admin) {
                Box {
                    cards.forEachIndexed { i, card ->
                        Box(Modifier.testTag("card$i")) {
                            CardLines(
                                card.title,
                                subtitle = "2024",
                                sourceName = "XS4K",
                                providerId = 1L,
                                width = 120.dp,
                                titleBadges = card.titleBadges,
                                rating = card.rating,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun heightOf(tag: String) =
        composeTestRule
            .onNodeWithTag(tag)
            .getBoundsInRoot()
            .let { it.bottom - it.top }
            .value

    private fun leftOf(text: String) = composeTestRule.onNodeWithText(text).getBoundsInRoot().left

    // The texts on screen: a badge measured but left out is not placed.
    private fun texts(): List<String> =
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced }
            .flatMap { it.config[SemanticsProperties.Text] }
            .map { it.text }

    private fun moreBadges() = texts().filter { MORE.matches(it) }

    @Test
    fun `many long tags keep one line and show a +N badge`() {
        assertTrue(parseDisplayTitle(LONG_TAGS).tags.size >= 3)
        setCards(Card(LONG_TAGS), Card("Plain"))
        assertEquals(1, moreBadges().size)
        // The same height as a card with no tags: the badges did not wrap to more lines.
        assertEquals(heightOf("card1"), heightOf("card0"), 0.5f)
    }

    @Test
    fun `few tags show them all, the resolution first`() {
        setCards(Card("D+ - Predator 4K (1987)"))
        composeTestRule.onNodeWithText("4K").assertExists()
        composeTestRule.onNodeWithText("D+").assertExists()
        assertEquals(emptyList<String>(), moreBadges())
        assertTrue(leftOf("4K") < leftOf("D+"))
    }

    @Test
    fun `the source ends the title line, the rating is never dropped for the tags`() {
        setCards(Card(LONG_TAGS, rating = "7.84"))
        composeTestRule.onNodeWithText("★ 7.8").assertExists()
        // The source is the badge at the end of the title line, above the rating's line.
        val source = composeTestRule.onNodeWithContentDescription("XS4K", substring = true).getBoundsInRoot()
        val rating = composeTestRule.onNodeWithText("★ 7.8").getBoundsInRoot()
        assertTrue(source.bottom <= rating.top)
        assertTrue(source.left > leftOf("★ 7.8"))
        assertEquals(1, moreBadges().size)
        assertTrue(leftOf("★ 7.8") < leftOf(moreBadges().single()))
    }

    @Test
    fun `a rating of 0 does not show`() {
        setCards(Card("Plain", rating = "0"))
        assertTrue(texts().none { it.startsWith("★") })
    }

    @Test
    fun `a Home card shows the clean name, the source and the rating, without tags`() {
        setCards(Card(LONG_TAGS, rating = "7.8", titleBadges = false))
        composeTestRule.onNodeWithText("Aventure Malgache (2024)").assertExists()
        composeTestRule.onNodeWithText("★ 7.8").assertExists()
        assertTrue(texts().none { it == "EN" || it == "4K" })
        assertEquals(emptyList<String>(), moreBadges())
    }

    @Test
    fun `without source badge and rating the line keeps its height`() {
        setCards(Card("Plain", titleBadges = false), Card("Rated", rating = "7.8", titleBadges = false), admin = false)
        assertEquals(heightOf("card1"), heightOf("card0"), 0.5f)
    }

    private companion object {
        const val LONG_TAGS = "EN - Aventure Malgache 4K HEVC HDR (2024) (FRENCH AUDIO) (ENG-SUB) (MULTI SUB)"
        val MORE = Regex("""\+\d+""")
    }
}
