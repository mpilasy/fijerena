package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.sports.DAY_HEADING
import org.njarasoa.fijerena.core.ui.sports.GameBells
import org.njarasoa.fijerena.core.ui.sports.GamesState
import org.njarasoa.fijerena.core.ui.sports.TeamsSearchViewModel
import org.njarasoa.fijerena.core.ui.sports.followKey
import org.njarasoa.fijerena.core.ui.sports.gamesByDay
import org.njarasoa.fijerena.core.ui.sports.key
import org.njarasoa.fijerena.core.ui.sports.liveRoute
import org.njarasoa.fijerena.core.ui.sports.rememberGameBells
import org.njarasoa.fijerena.core.ui.sports.rememberPinnedDayKey
import org.njarasoa.fijerena.core.ui.sports.route
import org.njarasoa.fijerena.core.ui.sports.sportLabel
import org.njarasoa.fijerena.core.ui.sports.teamDetails
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.feature.sports.MobileBestTeamCard
import org.njarasoa.fijerena.feature.sports.MobileGameBell
import org.njarasoa.fijerena.feature.sports.MobileGameCard
import org.njarasoa.fijerena.feature.sports.MobileGameSheet
import org.njarasoa.fijerena.feature.sports.MobileSportsDayHeading
import org.njarasoa.fijerena.feature.sports.MobileSportsHeading
import org.njarasoa.fijerena.feature.sports.MobileSportsItemCard
import org.njarasoa.fijerena.feature.sports.sportsCardColors
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaSearchField
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Search's Teams type (the sports teams plan → Screens): the field the other types share, then the
 * best team (the one the user most likely means, `BestTeam`) with its next games, then the other
 * teams and the leagues found, each group under its heading, a star on what the profile follows.
 * The search runs once typing stops and at once on the keyboard's Search.
 *
 * The best team: a card that opens its page ([onTeamSelected]), with its bell and Follow at its end
 * ([MobileBestTeamCard]); it shows at once, its games (up to 10, by day, each with its best channel
 * and bell, as on its page) when they come. A game opens its sheet ([MobileGameSheet]); a channel
 * there opens its mini-player ([onPlay]). Another team opens its page, a league its page
 * ([onLeagueSelected]). Its view model is made the first time the type shows, so another type's
 * search never asks the sports sources.
 */
@Composable
internal fun MobileTeamsSearchPane(
    query: String,
    onQueryChange: (String) -> Unit,
    onTeamSelected: (Screen.SportsTeam) -> Unit,
    onLeagueSelected: (Screen.SportsLeague) -> Unit,
    onPlay: (Screen.CategoryList) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: TeamsSearchViewModel = viewModel(factory = remember { TeamsSearchViewModel.Factory(context.applicationContext) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val followed by viewModel.followed.collectAsStateWithLifecycle()
    val bestTeam by viewModel.bestTeam.collectAsStateWithLifecycle()
    val bestGames by viewModel.bestGames.collectAsStateWithLifecycle()
    val bells = rememberGameBells()
    // The best team's game whose sheet is open (its key saved, so Back from a channel finds it open).
    var openGame by rememberSaveable { mutableStateOf<String?>(null) }
    // Here, not in the list: the list leaves while results load, and Back from a result must find
    // the place the user left (saved with the screen).
    val listState = rememberLazyListState()
    val pinnedDay = rememberPinnedDayKey(listState)
    // The type shown with text it has not searched: at once. Then once typing stops.
    LaunchedEffect(Unit) { viewModel.search(query) }
    LaunchedEffect(query) {
        delay(CinemaAnimation.searchDebounceMs)
        viewModel.search(query)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    CinemaSearchField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        placeholder = stringResource(R.string.search_teams_placeholder),
        leadingIcon = { Icon(imageVector = CinemaIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (state is TeamsSearchViewModel.State.Loading) {
                CinemaSpinner(modifier = Modifier.size(MobileDimensions.iconDefault))
            } else if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        viewModel.search("")
                    },
                ) {
                    Icon(
                        imageVector = CinemaIcons.Close,
                        contentDescription = stringResource(R.string.provider_clear_button),
                        tint = CinemaTheme.colors.textPrimary,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    viewModel.search(query)
                    keyboardController?.hide()
                },
            ),
    )
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        when (val shown = state) {
            TeamsSearchViewModel.State.Idle -> {
                CinemaText(
                    text = stringResource(R.string.search_teams_placeholder),
                    modifier = Modifier.align(Alignment.Center),
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                )
            }

            is TeamsSearchViewModel.State.Loading -> {
                MitadyLoading(modifier = Modifier.align(Alignment.Center), role = TextRole.Title, tone = TextTone.Accent)
            }

            is TeamsSearchViewModel.State.Failed -> {
                RetryWhenOnline(viewModel::retry)
                MobileEmptyState(
                    message = stringResource(R.string.sports_load_error),
                    secondary = shown.raw?.let { "[dev] $it" },
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::retry,
                )
            }

            is TeamsSearchViewModel.State.Loaded -> {
                if (shown.isEmpty) {
                    CinemaText(
                        text = stringResource(R.string.search_no_results_query_format, shown.query),
                        modifier = Modifier.align(Alignment.Center),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                    )
                } else {
                    val cardColors = sportsCardColors()
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        contentPadding = PaddingValues(bottom = Spacing.md + LocalDockInset.current),
                    ) {
                        shown.best?.let { best ->
                            val team = bestTeam?.takeIf { it.id == best.id } ?: best
                            item(key = "best:${best.followKey}", contentType = "best") {
                                MobileBestTeamCard(
                                    team = team,
                                    details = teamDetails(resources, team),
                                    followed = best.followKey in followed,
                                    bells = bells,
                                    onOpen = { onTeamSelected(team.route()) },
                                    onToggleFollow = viewModel::toggleBestFollow,
                                    cardColors = cardColors,
                                )
                            }
                            bestGameCards(bestGames, team, bells, cardColors, pinnedDay, viewModel::retryBestGames) { openGame = it }
                        }
                        if (shown.teams.isNotEmpty()) {
                            item(key = "teams_header", contentType = "header") {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_tab_teams),
                                    count = shown.teams.size,
                                    hiddenCount = 0,
                                )
                            }
                            items(shown.teams, key = { it.followKey }, contentType = { "team" }) { team ->
                                MobileSportsItemCard(
                                    name = team.name,
                                    details = teamDetails(resources, team),
                                    logoUrl = team.logoUrl,
                                    followed = team.followKey in followed,
                                    cardColors = cardColors,
                                    onClick = { onTeamSelected(team.route()) },
                                )
                            }
                        }
                        if (shown.leagues.isNotEmpty()) {
                            item(key = "leagues_header", contentType = "header") {
                                SearchSectionHeader(
                                    title = stringResource(R.string.sports_leagues_heading),
                                    count = shown.leagues.size,
                                    hiddenCount = 0,
                                )
                            }
                            items(shown.leagues, key = { it.followKey }, contentType = { "league" }) { league ->
                                MobileSportsItemCard(
                                    name = league.name,
                                    details = sportLabel(resources, league.sport).orEmpty(),
                                    logoUrl = league.logoUrl,
                                    followed = league.followKey in followed,
                                    cardColors = cardColors,
                                    onClick = { onLeagueSelected(league.route()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    val sheetGames = (bestGames as? GamesState.Loaded)?.takeIf { (state as? TeamsSearchViewModel.State.Loaded)?.best != null }
    val sheetGame = openGame?.let { key -> sheetGames?.games?.firstOrNull { it.key == key } }
    if (sheetGame != null) {
        val nowSec = remember { System.currentTimeMillis() / 1000 }
        MobileGameSheet(
            game = sheetGame,
            channels = sheetGames?.channelsOf(sheetGame),
            onPlay = { channel -> onPlay(channel.liveRoute()) },
            bells = bells,
            onDismiss = { openGame = null },
            actions = { MobileGameBell(sheetGame, bells, nowSec) },
        )
    }
}

/**
 * The best team's games under its card: a spinner while they load, the error with Retry, "No games
 * coming", or the games by day (the same cards as its page: both teams, the best channel, a bell).
 * A game opens its sheet ([onOpenGame] with its key).
 */
private fun LazyListScope.bestGameCards(
    games: GamesState,
    team: SportsTeam,
    bells: GameBells,
    cardColors: CardColors,
    pinnedDay: Any?,
    onRetry: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    when (games) {
        GamesState.Loading -> {
            item(key = "best_loading", contentType = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                    CinemaSpinner()
                }
            }
        }

        is GamesState.Failed -> {
            item(key = "best_failed", contentType = "failed") {
                RetryWhenOnline(onRetry)
                MobileEmptyState(
                    message = stringResource(R.string.sports_load_error),
                    secondary = games.raw?.let { "[dev] $it" },
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = onRetry,
                )
            }
        }

        is GamesState.Loaded -> {
            if (games.games.isEmpty()) {
                item(key = "best_no_games", contentType = "note") {
                    CinemaText(
                        text = stringResource(R.string.sports_no_games),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                        modifier = Modifier.padding(vertical = Spacing.sm),
                    )
                }
            }
            for (day in gamesByDay(games.games)) {
                stickyHeader(key = "best_day:${day.date}", contentType = DAY_HEADING) {
                    MobileSportsDayHeading(
                        DateText.dayLabel(LocalResources.current, day.date),
                        pinned = pinnedDay == "best_day:${day.date}",
                    )
                }
                items(day.games, key = { "game:${it.key}" }, contentType = { "game" }) { game ->
                    val nowSec = remember { System.currentTimeMillis() / 1000 }
                    MobileGameCard(
                        game = game,
                        channels = games.channelsOf(game),
                        team = team,
                        cardColors = cardColors,
                        onClick = { onOpenGame(game.key) },
                        trailing = { MobileGameBell(game, bells, nowSec) },
                    )
                }
            }
        }
    }
}
