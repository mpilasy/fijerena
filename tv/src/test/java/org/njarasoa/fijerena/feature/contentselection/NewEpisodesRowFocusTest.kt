package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeEntity
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.newepisodes.LocalNewEpisodes
import org.njarasoa.fijerena.core.ui.newepisodes.NewEpisodeShow
import org.njarasoa.fijerena.core.ui.newepisodes.NewEpisodesState
import org.njarasoa.fijerena.core.ui.newepisodes.statusLine
import org.njarasoa.fijerena.feature.contentselection.components.TvFavoritesRow
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's New Episodes row (the new episode alerts plan), as Home builds it from `TvFavoritesRow`:
 * one card per show with its New badge and its second line, Right along the row and staying on the
 * last card, Left back. The hand-off when a card leaves is `cardAfterChange` (CardAfterChangeTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp")
class NewEpisodesRowFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)

    private fun show(
        id: String,
        episodes: List<NewEpisodeEntity>,
        newSeason: Boolean = false,
    ) = NewEpisodeShow(MediaItem(id, "Show $id", MediaType.SERIES, "c", providerId = 1L), episodes, newSeason)

    private fun episode(
        id: String,
        season: Int,
        number: Int,
    ) = NewEpisodeEntity(1L, 1, id, season, number, 1L)

    @Test
    fun `the cards in a row, each with its New badge and what is new`() {
        val shows =
            listOf(
                show("1", listOf(episode("a", 2, 5))),
                show("2", listOf(episode("b", 1, 1), episode("c", 1, 2), episode("d", 1, 3))),
                show("3", listOf(episode("e", 4, 1)), newSeason = true),
            )
        rule.setContent {
            FirstVideoPlayerTheme {
                CompositionLocalProvider(LocalNewEpisodes provides NewEpisodesState(shows)) {
                    val first = remember { FocusRequester() }
                    val resources = LocalResources.current
                    Column {
                        TvFavoritesRow(
                            title = "New Episodes",
                            items = shows.map { it.item },
                            thumbnailType = ThumbnailContentType.TV_SHOW,
                            onItemSelected = {},
                            statusOf = { item -> shows.first { it.key == item.sourceKey }.statusLine(resources) },
                            onOpenActions = null,
                            firstItemFocus = first,
                            listState = rememberLazyListState(),
                        )
                    }
                    LaunchedEffect(Unit) { first.requestFocusWithRetry() }
                }
            }
        }

        walk.awaitFocused("S / New / Show 1 / S02E05")
        walk.run(
            right("S / New / Show 2 / 3 new episodes"),
            right("S / New / Show 3 / New season"),
            right("S / New / Show 3 / New season"),
            left("S / New / Show 2 / 3 new episodes"),
        )
    }

    @Test
    fun `a focused card leaving hands focus to the card that took its place, else the next row`() {
        val oldKeys = listOf("1:1", "1:2")
        val rows = HomeShelf.entries.associateWith { emptyList<String>() }

        assertEquals(
            HomeCard(HomeShelf.NEW_EPISODES, "1:2"),
            cardAfterChange(HomeShelf.NEW_EPISODES, oldKeys, "1:1", rows + (HomeShelf.NEW_EPISODES to listOf("1:2"))),
        )
        assertEquals(
            HomeCard(HomeShelf.FAVORITE_SHOWS, "1:9"),
            cardAfterChange(HomeShelf.NEW_EPISODES, listOf("1:1"), "1:1", rows + (HomeShelf.FAVORITE_SHOWS to listOf("1:9"))),
        )
    }
}
