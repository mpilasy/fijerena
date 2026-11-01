package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.sports.DAY_HEADING
import org.njarasoa.fijerena.core.ui.sports.GameBells
import org.njarasoa.fijerena.core.ui.sports.GamesState
import org.njarasoa.fijerena.core.ui.sports.TeamsSearchViewModel
import org.njarasoa.fijerena.core.ui.sports.dayHeadingBand
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
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.feature.sports.GameSheetOverlay
import org.njarasoa.fijerena.feature.sports.TvBestTeamRow
import org.njarasoa.fijerena.feature.sports.TvGameBell
import org.njarasoa.fijerena.feature.sports.TvGameRow
import org.njarasoa.fijerena.feature.sports.TvSportsHeading
import org.njarasoa.fijerena.feature.sports.TvSportsItemRow
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvSearchField
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Search's Teams type (the sports teams plan → Screens): the field the other types share, then
 * the best team (the one the user most likely means, `BestTeam`) with its next games, then the
 * other teams and the leagues found, each group under a heading that takes no focus, a star on
 * what the profile follows. The search runs once typing stops and on the keyboard's Search; only
 * the keyboard's Search moves focus to the first result. Each result records itself in
 * [returnFocus], so Back from the page it opened lands on it again.
 *
 * The best team: a row whose OK opens its page ([onTeamSelected]), Follow and its bell to the
 * right ([TvBestTeamRow]); its header shows at once, its games (up to 10, by day, each with its
 * best channel and bell, as on its page) when they come. A game opens its sheet
 * ([GameSheetOverlay]) over the pane; a channel there opens its preview ([onPlay]).
 *
 * Focus: Down from the field goes to the first result (the best team, else the first team or
 * league), then down through the best team's games, the other teams and the leagues; Up goes back
 * the same way to the field. A league opens its page ([onLeagueSelected]). Its view model is made
 * the first time the type shows, so another type's search never asks the sports sources.
 * [holdFocus]: a type switch keeps focus on the type selector.
 */
@Composable
internal fun TeamsSearchPane(
    query: String,
    onQueryChange: (String) -> Unit,
    holdFocus: Boolean,
    fieldModifier: Modifier,
    returnFocus: NavReturnFocus,
    onTeamSelected: (Screen.SportsTeam) -> Unit,
    onLeagueSelected: (Screen.SportsLeague) -> Unit,
    onPlay: (Screen.CategoryList) -> Unit,
    viewModel: TeamsSearchViewModel = teamsSearchViewModel(),
) {
    val resources = LocalResources.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val followed by viewModel.followed.collectAsStateWithLifecycle()
    val bestTeam by viewModel.bestTeam.collectAsStateWithLifecycle()
    val bestGames by viewModel.bestGames.collectAsStateWithLifecycle()
    val bells = rememberGameBells()
    val fieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    // The best team's game whose sheet is open (its key saved, so Back from a channel finds it open).
    var openGame by rememberSaveable { mutableStateOf<String?>(null) }
    var closedGame by remember { mutableStateOf<String?>(null) }
    // Set by the keyboard's Search: the next results take focus.
    var jumpToResults by remember { mutableStateOf(false) }
    // As the People type: straight into the keyboard for a first search, else the field.
    var editing by remember { mutableStateOf(!holdFocus && returnFocus.key == null && query.isEmpty()) }
    LaunchedEffect(Unit) {
        if (returnFocus.key == null && !holdFocus && !editing) fieldFocus.requestFocusWithRetry()
    }
    // The type shown with text it has not searched: at once. Then once typing stops.
    LaunchedEffect(Unit) { viewModel.search(query) }
    LaunchedEffect(query) {
        delay(CinemaAnimation.searchDebounceMs)
        viewModel.search(query)
    }
    val loaded = state as? TeamsSearchViewModel.State.Loaded
    LaunchedEffect(loaded) {
        if (loaded != null && jumpToResults) {
            jumpToResults = false
            if (!loaded.isEmpty) firstResultFocus.requestFocusWithRetry()
        }
    }
    LaunchedEffect(closedGame) {
        closedGame?.let { returnFocus.requesterOf(it).requestFocusWithRetry(fallback = firstResultFocus) }
        closedGame = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TvSearchField(
                modifier =
                    (if (loaded?.isEmpty == false) Modifier.focusProperties { down = firstResultFocus } else Modifier)
                        .then(fieldModifier),
                query = query,
                onQueryChange = onQueryChange,
                onSearchSubmit = {
                    // The results on screen already are this query's: none come to jump to (Down reaches them).
                    jumpToResults = (state as? TeamsSearchViewModel.State.Loaded)?.query != query.trim()
                    viewModel.search(query)
                },
                onClear = {
                    onQueryChange("")
                    viewModel.search("")
                },
                placeholder = stringResource(R.string.search_teams_placeholder),
                focusRequester = fieldFocus,
                editing = editing,
                onEditingChange = { editing = it },
                restingFieldModifier = Modifier.leftToRail(),
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            when (val shown = state) {
                TeamsSearchViewModel.State.Idle -> {
                    TvEmptyState(message = stringResource(R.string.search_teams_placeholder), icon = CinemaIcons.Search)
                }

                is TeamsSearchViewModel.State.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MitadyLoading(role = TextRole.Display, tone = TextTone.Accent)
                    }
                }

                is TeamsSearchViewModel.State.Failed -> {
                    TvErrorState(
                        title = stringResource(R.string.sports_load_error),
                        message = shown.raw?.let { "[dev] $it" }.orEmpty(),
                        onRetry = viewModel::retry,
                    )
                }

                is TeamsSearchViewModel.State.Loaded -> {
                    if (shown.isEmpty) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CinemaText(
                                text = stringResource(R.string.search_no_results_query_format_tv, shown.query),
                                tone = TextTone.Secondary,
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val pinnedDay = rememberPinnedDayKey(listState)
                        NavReturnFocusEffect(returnFocus, listState = listState)
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            shown.best?.let { best ->
                                val team = bestTeam?.takeIf { it.id == best.id } ?: best
                                val bestKey = "best:${best.followKey}"
                                item(key = bestKey, contentType = "best") {
                                    TvBestTeamRow(
                                        team = team,
                                        details = teamDetails(resources, team),
                                        followed = best.followKey in followed,
                                        bells = bells,
                                        onOpen = {
                                            returnFocus.leaveFrom(bestKey, listState)
                                            onTeamSelected(team.route())
                                        },
                                        onToggleFollow = viewModel::toggleBestFollow,
                                        modifier =
                                            Modifier
                                                .focusRequester(firstResultFocus)
                                                .navReturnFocusTarget(returnFocus, bestKey),
                                    )
                                }
                                bestGameRows(bestGames, team, bells, returnFocus, pinnedDay, viewModel::retryBestGames) { openGame = it }
                            }
                            teamAndLeagueRows(shown, followed, returnFocus, listState, firstResultFocus, onTeamSelected, onLeagueSelected)
                        }
                    }
                }
            }
        }
        val sheetGames = (bestGames as? GamesState.Loaded)?.takeIf { loaded?.best != null }
        val sheetGame = openGame?.let { key -> sheetGames?.games?.firstOrNull { it.key == key } }
        if (sheetGame != null) {
            val nowSec = remember { System.currentTimeMillis() / 1000 }
            GameSheetOverlay(
                game = sheetGame,
                channels = sheetGames?.channelsOf(sheetGame),
                returnFocus = returnFocus,
                onPlay = { channel -> onPlay(channel.liveRoute()) },
                bells = bells,
                onClose = {
                    closedGame = "game:${sheetGame.key}"
                    openGame = null
                },
                // The search screen has its safe margins already.
                contentPadding = PaddingValues(),
                actions = { TvGameBell(sheetGame, bells, nowSec) },
            )
        }
    }
}

/** The pane's view model, made the first time the Teams type shows. */
@Composable
private fun teamsSearchViewModel(): TeamsSearchViewModel {
    val context = LocalContext.current
    return viewModel(factory = remember { TeamsSearchViewModel.Factory(context.applicationContext) })
}

/**
 * The best team's games under its row: a spinner while they load, the error with Retry, "No games
 * coming", or the games by day (the same rows as its page: both teams, the best channel, a bell).
 * A game opens its sheet ([onOpenGame] with its key).
 */
private fun LazyListScope.bestGameRows(
    games: GamesState,
    team: SportsTeam,
    bells: GameBells,
    returnFocus: NavReturnFocus,
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
                TvErrorState(
                    title = stringResource(R.string.sports_load_error),
                    message = games.raw?.let { "[dev] $it" }.orEmpty(),
                    onRetry = onRetry,
                )
            }
        }

        is GamesState.Loaded -> {
            if (games.games.isEmpty()) {
                item(key = "best_no_games", contentType = "note") {
                    CinemaText(
                        text = stringResource(R.string.sports_no_games),
                        tone = TextTone.Secondary,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
            }
            for (day in gamesByDay(games.games)) {
                stickyHeader(key = "best_day:${day.date}", contentType = DAY_HEADING) {
                    TvSportsHeading(
                        DateText.dayLabel(LocalResources.current, day.date),
                        modifier =
                            Modifier.dayHeadingBand(pinnedDay == "best_day:${day.date}"),
                    )
                }
                for (game in day.games) {
                    val key = "game:${game.key}"
                    item(key = key, contentType = "game") {
                        val nowSec = remember { System.currentTimeMillis() / 1000 }
                        TvGameRow(
                            game = game,
                            channels = games.channelsOf(game),
                            team = team,
                            onClick = { onOpenGame(game.key) },
                            modifier = Modifier.navReturnFocusTarget(returnFocus, key),
                            trailing = { TvGameBell(game, bells, nowSec) },
                        )
                    }
                }
            }
        }
    }
}

/** The other teams, then the leagues, each group under its heading; each row opens its page. */
private fun LazyListScope.teamAndLeagueRows(
    shown: TeamsSearchViewModel.State.Loaded,
    followed: Set<String>,
    returnFocus: NavReturnFocus,
    listState: LazyListState,
    firstResultFocus: FocusRequester,
    onTeamSelected: (Screen.SportsTeam) -> Unit,
    onLeagueSelected: (Screen.SportsLeague) -> Unit,
) {
    if (shown.teams.isNotEmpty()) {
        item(key = "teams_header", contentType = "header") {
            SearchSectionHeader(
                title = stringResource(R.string.search_tab_teams),
                count = shown.teams.size,
                hiddenCount = 0,
            )
        }
        itemsIndexed(shown.teams, key = { _, team -> team.followKey }, contentType = { _, _ -> "team" }) { index, team ->
            val returnKey = team.followKey
            TvSportsItemRow(
                name = team.name,
                details = teamDetails(LocalResources.current, team),
                logoUrl = team.logoUrl,
                followed = team.followKey in followed,
                onClick = {
                    returnFocus.leaveFrom(returnKey, listState)
                    onTeamSelected(team.route())
                },
                modifier =
                    (if (index == 0 && shown.best == null) Modifier.focusRequester(firstResultFocus) else Modifier)
                        .navReturnFocusTarget(returnFocus, returnKey),
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
        itemsIndexed(shown.leagues, key = { _, league -> league.followKey }, contentType = { _, _ -> "league" }) { index, league ->
            val returnKey = league.followKey
            TvSportsItemRow(
                name = league.name,
                details = sportLabel(LocalResources.current, league.sport).orEmpty(),
                logoUrl = league.logoUrl,
                followed = league.followKey in followed,
                onClick = {
                    returnFocus.leaveFrom(returnKey, listState)
                    onLeagueSelected(league.route())
                },
                modifier =
                    (if (index == 0 && shown.best == null && shown.teams.isEmpty()) Modifier.focusRequester(firstResultFocus) else Modifier)
                        .navReturnFocusTarget(returnFocus, returnKey),
            )
        }
    }
}
