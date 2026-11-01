package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.LiveChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.sports.LocalGameBellStore
import org.njarasoa.fijerena.core.ui.sports.UpcomingGame
import org.njarasoa.fijerena.core.ui.sports.rememberGameBells
import org.njarasoa.fijerena.feature.contentselection.components.TvUpcomingGamesRow
import org.njarasoa.fijerena.feature.sports.GameSheetOverlay
import org.njarasoa.fijerena.feature.sports.SportsSectionFocusTest
import org.njarasoa.fijerena.feature.sports.TvGameBell
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner

/**
 * Home's Upcoming Games row (the sports teams plan → Home row), as Home lays it out: a card per
 * game (both logos' letters, the game, its day and time, its channel), Right along the row and
 * staying on the last card; OK opens the game's sheet over Home on its channel, the bell above it;
 * Back closes the sheet onto the card.
 */
@RunWith(RobolectricTestRunner::class)
class UpcomingGamesRowFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)

    @Test
    fun `the cards in a row, OK opens the sheet with its bell, Back returns to the card`() {
        val bells = SportsSectionFocusTest.FakeBells()
        rule.setContent {
            FirstVideoPlayerTheme {
                CompositionLocalProvider(LocalGameBellStore provides bells) {
                    val first = remember { FocusRequester() }
                    val returnFocus = rememberNavReturnFocus()
                    val gameBells = rememberGameBells()
                    var open by remember { mutableStateOf<UpcomingGame?>(null) }
                    val cards = remember { mutableMapOf<String, FocusRequester>() }
                    var closed by remember { mutableStateOf<String?>(null) }
                    Box {
                        TvUpcomingGamesRow(
                            title = "Upcoming Games",
                            games = GAMES,
                            onGameSelected = { open = it },
                            firstItemFocus = first,
                            listState = rememberLazyListState(),
                            itemModifier = { game -> Modifier.focusRequester(cards.getOrPut(game.game.id) { FocusRequester() }) },
                        )
                        open?.let { game ->
                            GameSheetOverlay(
                                game = game.game,
                                channels = game.channels,
                                returnFocus = returnFocus,
                                onPlay = {},
                                bells = gameBells,
                                onClose = {
                                    closed = game.game.id
                                    open = null
                                },
                                actions = { TvGameBell(game.game, gameBells, NOW_SEC) },
                            )
                        }
                    }
                    LaunchedEffect(Unit) { first.requestFocusWithRetry() }
                    LaunchedEffect(closed) {
                        closed?.let { cards.getValue(it).requestFocusWithRetry() }
                        closed = null
                    }
                }
            }
        }

        walk.awaitFocused("A / C / Arsenal / Chelsea")
        walk.run(
            right("L / A / Liverpool / Arsenal"),
            right("L / A / Liverpool / Arsenal"),
            left("A / C / Arsenal / Chelsea"),
            center("S / Sky Sports Main Event / UK"),
            up("Remind me"),
        )
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Reminder set")
        assertEquals(setOf("espn/1"), bells.reminded.value.games)
        walk.run(down("S / Sky Sports Main Event / UK"), back("A / C / Arsenal / Chelsea"))
    }

    private companion object {
        val ARSENAL = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer")
        val CHELSEA = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
        val LIVERPOOL = SportsTeam(SportsSource.ESPN, "soccer/364", "Liverpool", sport = "soccer")
        val LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
        val CHANNEL = LiveChannel(1, 7, "UK: Sky Sports Main Event", "skysports.uk", "sport")
        const val NOW_SEC = 3_999_990_000L
        val ON_SKY = listOf(GameChannel(CHANNEL, null, null, GameChannelHow.GUIDE))

        val GAMES =
            listOf(
                UpcomingGame(SportsGame(SportsSource.ESPN, "1", 4_000_000_000, ARSENAL, CHELSEA, LEAGUE), ON_SKY),
                UpcomingGame(SportsGame(SportsSource.ESPN, "2", 4_000_090_000, LIVERPOOL, ARSENAL, LEAGUE), ON_SKY),
            )
    }
}
