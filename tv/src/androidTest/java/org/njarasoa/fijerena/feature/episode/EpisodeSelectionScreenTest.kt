package org.njarasoa.fijerena.feature.episode

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.feature.episode.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.feature.episode.fixtures.FakeXtreamEpisodeDao

/**
 * Regression test for docs/plans/episode-selection-fragility-plan.md: playing an episode from a
 * season other than the first, then navigating back, used to silently reset the season tab (and
 * scroll position) to season 1 — twice, both times only caught by a live device repro + logcat,
 * never by a test. [StateRestorationTester] reproduces the exact mechanism (this composable
 * disposed when the player is pushed on top of it, recomposed fresh from the same saved-state
 * registry on return) without needing real navigation or a player.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EpisodeSelectionScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seriesDetail =
        SeriesDetail(
            id = "series-1",
            name = "Test Series",
            seasons = listOf(SeasonInfo(1, "Season 1"), SeasonInfo(2, "Season 2")),
            episodes =
                mapOf(
                    "1" to
                        listOf(
                            EpisodeItem(id = "s1e1", episodeNumber = 1, title = "S1 Episode 1", seasonNumber = 1),
                            EpisodeItem(id = "s1e2", episodeNumber = 2, title = "S1 Episode 2", seasonNumber = 1),
                        ),
                    "2" to
                        listOf(
                            EpisodeItem(id = "s2e1", episodeNumber = 1, title = "S2 Episode 1", seasonNumber = 2),
                            EpisodeItem(id = "s2e2", episodeNumber = 2, title = "S2 Episode 2", seasonNumber = 2),
                        ),
                ),
        )

    @Test
    fun seasonSelectionSurvivesSimulatedNavigationDisposal() {
        val restorationTester = StateRestorationTester(composeTestRule)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mediaRepository =
            MediaRepository(
                context = context,
                providerId = 999L,
                watchStateDao = FakeWatchStateDao(),
                episodeDao = FakeXtreamEpisodeDao(),
            )

        restorationTester.setContent {
            EpisodeListContent(
                seriesDetail = seriesDetail,
                relatedTitles = RelatedTitles(),
                tmdbTitle = null,
                logoUrl = null,
                alternateStreams = emptyList(),
                seriesName = "Test Series",
                categoryId = "cat1",
                mediaRepository = mediaRepository,
                initialEpisodeId = null,
                isFavorite = false,
                categoryName = null,
                isRefreshing = false,
                onToggleFavorite = {},
                onEpisodeSelected = { _, _, _, _ -> },
                onCategorySelected = {},
                onRefresh = {},
                onBack = {},
                onRelatedTitleSelected = {},
                onAlternateStreamSelected = {},
            )
        }

        // Manually pick season 2 and play one of its episodes — the exact interaction that broke:
        // a season the user picked in-session, not one named by a route argument. The episode
        // card sits below the hero section, off-screen until scrolled — LazyColumn only composes
        // what's near the viewport, so it has to be scrolled to before it can be clicked.
        val season2Label = context.getString(R.string.series_season_label, 2)
        composeTestRule.onNodeWithText(season2Label).performClick()
        composeTestRule
            .onNodeWithTag("episode_list")
            .performScrollToNode(hasTestTag("episode_s2e2"))

        // androidx.tv.material3.Card is D-pad-first: it fires onClick on a focused DirectionCenter
        // press, not on a raw touch tap the way a plain Modifier.clickable does (performClick()
        // worked fine on the season tab above, which is a plain clickable) — so drive it the same
        // way a real remote does, not with performClick().
        composeTestRule.onNodeWithTag("episode_s2e2").requestFocus()
        composeTestRule.onNodeWithTag("episode_s2e2").performKeyInput { pressKey(Key.DirectionCenter) }

        val playLabel = context.getString(R.string.series_play_episode_action)
        composeTestRule.onNodeWithText(playLabel, substring = true).requestFocus()
        composeTestRule.onNodeWithText(playLabel, substring = true).performKeyInput { pressKey(Key.DirectionCenter) }

        // Simulates exactly what navigating to the player and coming back does: this composable
        // disposed, then recomposed fresh from the same saved-state registry.
        restorationTester.emulateSavedInstanceStateRestore()

        // Bug behavior: season silently reset to 1, so neither of these would be on screen.
        val continueWatchingLabel = context.getString(R.string.series_continue_watching_badge)
        composeTestRule.onNodeWithText(continueWatchingLabel).assertExists()
        composeTestRule.onNodeWithText("S2 Episode 2").assertExists()
    }
}
