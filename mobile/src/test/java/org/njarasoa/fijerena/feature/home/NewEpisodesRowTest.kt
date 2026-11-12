package org.njarasoa.fijerena.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeEntity
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.newepisodes.LocalNewEpisodes
import org.njarasoa.fijerena.core.ui.newepisodes.NewEpisodeShow
import org.njarasoa.fijerena.core.ui.newepisodes.NewEpisodesState
import org.robolectric.RobolectricTestRunner

/** The phone's New Episodes row (the new episode alerts plan): its cards, badge and actions. */
@RunWith(RobolectricTestRunner::class)
class NewEpisodesRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        id: String,
        season: Int,
        vararg numbers: Int,
        newSeason: Boolean = false,
    ) = NewEpisodeShow(
        MediaItem(id, "Show $id", MediaType.SERIES, "c", providerId = 1L),
        numbers.map { NewEpisodeEntity(1L, id.toInt(), "$id-$it", season, it, 100L) },
        newSeason,
    )

    private var opened: NewEpisodeShow? = null
    private var menu: NewEpisodeShow? = null

    private fun setRow(vararg shows: NewEpisodeShow) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalNewEpisodes provides NewEpisodesState(shows.toList())) {
                NewEpisodesRow(shows.toList(), emptyMap(), onOpen = { opened = it }, onMenu = { menu = it })
            }
        }
    }

    @Test
    fun `each card has its New badge and says what is new`() {
        setRow(show("1", 2, 5), show("2", 1, 1, 2, 3), show("3", 4, 1, 2, newSeason = true))

        composeTestRule.onNodeWithText("New Episodes").assertIsDisplayed()
        composeTestRule.onNodeWithText("S02E05").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 new episodes").assertIsDisplayed()
        composeTestRule.onNodeWithText("New season").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("New").assertCountEquals(3)
    }

    @Test
    fun `the title keeps its room beside the New badge and the source`() {
        val shows = listOf(show("1", 2, 5))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalNerd provides true, LocalNewEpisodes provides NewEpisodesState(shows)) {
                NewEpisodesRow(shows, mapOf(1L to "BEARS"), onOpen = {}, onMenu = {})
            }
        }

        composeTestRule.onNodeWithText("Show 1").assertWidthIsAtLeast(30.dp)
    }

    @Test
    fun `the second line has no rating beside it`() {
        val rated = show("1", 2, 5).let { it.copy(item = it.item.copy(metadata = MediaMetadata(rating = "7.7"))) }
        setRow(rated)

        composeTestRule.onNodeWithText("S02E05").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("7.7", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a tap opens the show, a long press its menu`() {
        val show = show("7", 3, 1)
        setRow(show)

        composeTestRule.onNodeWithText("Show 7").performClick()
        assertEquals(show, opened)
        composeTestRule.onNodeWithText("Show 7").performTouchInput { longClick() }
        assertEquals(show, menu)
    }

    @Test
    fun `no new episodes, no row`() {
        setRow()

        composeTestRule.onAllNodesWithText("New Episodes").assertCountEquals(0)
    }
}
