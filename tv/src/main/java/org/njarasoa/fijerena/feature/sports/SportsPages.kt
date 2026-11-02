package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.sports.DAY_HEADING
import org.njarasoa.fijerena.core.ui.sports.GamesState
import org.njarasoa.fijerena.core.ui.sports.SportsLeagueViewModel
import org.njarasoa.fijerena.core.ui.sports.SportsTeamViewModel
import org.njarasoa.fijerena.core.ui.sports.dayHeadingBand
import org.njarasoa.fijerena.core.ui.sports.gamesByDay
import org.njarasoa.fijerena.core.ui.sports.key
import org.njarasoa.fijerena.core.ui.sports.liveRoute
import org.njarasoa.fijerena.core.ui.sports.rememberGameBells
import org.njarasoa.fijerena.core.ui.sports.rememberPinnedDayKey
import org.njarasoa.fijerena.core.ui.sports.route
import org.njarasoa.fijerena.core.ui.sports.sportLabel
import org.njarasoa.fijerena.core.ui.sports.teamDetails
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.ui.components.TvDayHeading
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * A team's page on TV (the sports teams plan → Team page): the logo, the name, the sport and
 * league, a Follow icon action (and, once followed, a bell for every game of the team in a slot
 * to its right that keeps its place, so Follow never moves), then the
 * team's coming games (up to 10) by day in the device's time zone, each with its bell. A game opens its sheet ([GameSheetOverlay]); a channel there opens its preview
 * ([onPlay], as a guide result).
 *
 * Focus: the first game once the games come (Follow while they load or when there is none); Up
 * from the first game reaches Follow; Left from a row or Follow opens the rail. Back closes the
 * sheet, then leaves; Back from a channel returns to its row in the sheet.
 */
@Composable
fun SportsTeamScreen(
    team: SportsTeam,
    onPlay: (Screen.CategoryList) -> Unit,
    onBack: () -> Unit,
    viewModel: SportsTeamViewModel =
        viewModel(
            key = "sports_team_${team.source.id}_${team.id}",
            factory = SportsTeamViewModel.Factory(LocalContext.current, team),
        ),
) {
    val resources = LocalResources.current
    val shown by viewModel.team.collectAsStateWithLifecycle()
    val followed by viewModel.followed.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    TvSportsPage(
        title = shown.name,
        subtitle = teamDetails(resources, shown),
        logoUrl = shown.logoUrl,
        followed = followed,
        onToggleFollow = viewModel::toggleFollow,
        games = games,
        team = shown,
        onRetry = viewModel::retry,
        onPlay = onPlay,
        onBack = onBack,
        // Every game of the team, once the profile follows it (the sports teams plan → Reminders); its slot always.
        teamBell = shown,
    )
}

/**
 * A league's page on TV (the sports teams plan → League page): the logo, the name, the sport, a
 * Follow icon action, the league's games of the next 7 days by day (both teams), then its teams,
 * each opening its team page ([onTeamSelected]). Focus as [SportsTeamScreen]: the first game, else
 * the first team, else Follow; Back from a team page returns to its row.
 */
@Composable
fun SportsLeagueScreen(
    league: SportsLeague,
    onTeamSelected: (Screen.SportsTeam) -> Unit,
    onPlay: (Screen.CategoryList) -> Unit,
    onBack: () -> Unit,
    viewModel: SportsLeagueViewModel =
        viewModel(
            key = "sports_league_${league.source.id}_${league.id}",
            factory = SportsLeagueViewModel.Factory(LocalContext.current, league),
        ),
) {
    val resources = LocalResources.current
    val followed by viewModel.followed.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val teams by viewModel.teams.collectAsStateWithLifecycle()
    val firstTeamFocus = remember { FocusRequester() }
    TvSportsPage(
        title = league.name,
        subtitle = sportLabel(resources, league.sport).orEmpty(),
        logoUrl = league.logoUrl,
        followed = followed,
        onToggleFollow = viewModel::toggleFollow,
        games = games,
        team = null,
        onRetry = viewModel::retry,
        onPlay = onPlay,
        onBack = onBack,
        secondFocus = firstTeamFocus.takeIf { !teams.isNullOrEmpty() },
        extra = { returnFocus, listState ->
            val list = teams.orEmpty()
            if (list.isNotEmpty()) {
                item(key = "teams_heading") {
                    TvSportsHeading(stringResource(R.string.search_tab_teams), modifier = Modifier.padding(top = Spacing.lg))
                }
                itemsIndexed(list, key = { _, team -> "team:${team.source.id}:${team.id}" }) { index, team ->
                    val key = "team:${team.source.id}:${team.id}"
                    TvSportsItemRow(
                        name = team.name,
                        details = teamDetails(resources, team),
                        logoUrl = team.logoUrl,
                        followed = false,
                        onClick = {
                            returnFocus.leaveFrom(key, listState)
                            onTeamSelected(team.route())
                        },
                        modifier =
                            (if (index == 0) Modifier.focusRequester(firstTeamFocus) else Modifier)
                                .navReturnFocusTarget(returnFocus, key),
                    )
                }
            }
        },
    )
}

/**
 * The page both share: the header with Follow, the games by day, [extra] rows after them, and the
 * game sheet over the page while one is open (its game's key saved, so Back from a channel finds
 * it open). [secondFocus]: the entry focus when there is no game. [teamBell]: the team whose every
 * game the header's bell reminds of once followed, in a slot after Follow that keeps its width
 * so Follow never moves ([TvFollowActions]); none on a league page.
 */
@Composable
private fun TvSportsPage(
    title: String,
    subtitle: String,
    logoUrl: String?,
    followed: Boolean,
    onToggleFollow: () -> Unit,
    games: GamesState,
    team: SportsTeam?,
    onRetry: () -> Unit,
    onPlay: (Screen.CategoryList) -> Unit,
    onBack: () -> Unit,
    secondFocus: FocusRequester? = null,
    teamBell: SportsTeam? = null,
    extra: LazyListScope.(NavReturnFocus, LazyListState) -> Unit = { _, _ -> },
) {
    val resources = LocalResources.current
    val bells = rememberGameBells()
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val listState = rememberLazyListState()
    val pinnedDay = rememberPinnedDayKey(listState)
    val returnFocus = rememberNavReturnFocus()
    val followFocus = remember { FocusRequester() }
    val firstGameFocus = remember { FocusRequester() }
    var followHasFocus by remember { mutableStateOf(false) }
    var openGame by rememberSaveable { mutableStateOf<String?>(null) }
    var closedGame by remember { mutableStateOf<String?>(null) }
    val loaded = games as? GamesState.Loaded
    val days = remember(loaded?.games) { loaded?.games?.let { gamesByDay(it) }.orEmpty() }
    val currentSecond by rememberUpdatedState(secondFocus)

    // Follow while the games load; then the first game, unless the user has moved on.
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!returnFocus.isReturn && !entered) followFocus.requestFocusWithRetry()
    }
    LaunchedEffect(loaded != null, secondFocus != null, followHasFocus) {
        if (loaded == null || entered || returnFocus.isReturn || !followHasFocus) return@LaunchedEffect
        val target = if (loaded.games.isNotEmpty()) firstGameFocus else currentSecond
        if (target != null) {
            entered = true
            target.requestFocusWithRetry()
            // The header stays in view: the focused row's own scroll into view runs after the focus
            // move, and a scroll to the top made at once was undone by it (the header half off the
            // screen). Wait for that scroll to end, then go back to the top.
            withFrameNanos { }
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            listState.scrollToItem(0)
        }
    }
    NavReturnFocusEffect(
        returnFocus,
        listState = listState,
        fallback = followFocus,
        // The rows come with the games.
        prepare = { snapshotFlow { games is GamesState.Loaded }.first { it } },
    )
    LaunchedEffect(closedGame) {
        closedGame?.let { returnFocus.requesterOf(it).requestFocusWithRetry(fallback = followFocus) }
        closedGame = null
    }

    Box(modifier = Modifier.fillMaxSize().panel(PanelFill.Ground)) {
        LazyColumn(
            state = listState,
            // Back on the root, before the focused control: see NAVIGATION_GUIDE → TV Back on Detail Screens.
            modifier =
                Modifier.fillMaxSize().focusable().onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        onBack()
                        true
                    } else {
                        false
                    }
                },
            contentPadding = PaddingValues(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Down from the header's actions: the first game (else the first of the extra rows), not
            // the bell under them.
            val headerDown = if (loaded?.games?.isNotEmpty() == true) firstGameFocus else secondFocus
            val downToRows = Modifier.focusProperties { headerDown?.let { down = it } }
            item(key = "header") {
                TvScreenHeader(
                    title = title,
                    subtitle = subtitle.takeIf { it.isNotEmpty() },
                    leading = {
                        CinemaThumbnail(
                            url = logoUrl,
                            fallbackLetter = title.firstOrNull(Char::isLetterOrDigit),
                            contentType = ThumbnailContentType.LIVE_TV,
                            contentDescription = null,
                            modifier = Modifier.size(TvDimensions.iconXLarge),
                        )
                    },
                ) {
                    TvFollowActions(
                        followed = followed,
                        onToggleFollow = onToggleFollow,
                        bells = bells,
                        team = teamBell,
                        modifier =
                            Modifier
                                .focusRequester(followFocus)
                                .onFocusChanged { followHasFocus = it.hasFocus }
                                .leftToRail()
                                .then(downToRows),
                        bellModifier = downToRows,
                    )
                }
            }
            when (games) {
                GamesState.Loading -> {
                    item(key = "loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                            CinemaSpinner()
                        }
                    }
                }

                is GamesState.Failed -> {
                    item(key = "failed") {
                        TvErrorState(
                            title = stringResource(R.string.sports_load_error),
                            message = games.raw?.let { "[dev] $it" }.orEmpty(),
                            onRetry = onRetry,
                        )
                    }
                }

                is GamesState.Loaded -> {
                    if (games.games.isEmpty()) {
                        item(key = "no_games") {
                            TvEmptyState(
                                message = stringResource(R.string.sports_no_games),
                                icon = CinemaIcons.DateRange,
                                takeFocus = false,
                            )
                        }
                    } else {
                        item(key = "games_heading") { TvSportsHeading(stringResource(R.string.sports_next_games_heading)) }
                    }
                    var first = true
                    for (day in days) {
                        stickyHeader(key = "day:${day.date}", contentType = DAY_HEADING) {
                            TvDayHeading(
                                text = DateText.dayLabel(resources, day.date),
                                pinned = pinnedDay == "day:${day.date}",
                            )
                        }
                        for (game in day.games) {
                            val key = "game:${game.key}"
                            val isFirst = first
                            first = false
                            item(key = key) {
                                TvGameRow(
                                    game = game,
                                    channels = games.channelsOf(game),
                                    team = team,
                                    onClick = { openGame = game.key },
                                    modifier =
                                        (if (isFirst) Modifier.focusRequester(firstGameFocus) else Modifier)
                                            .navReturnFocusTarget(returnFocus, key),
                                    trailing = { TvGameBell(game, bells, nowSec) },
                                )
                            }
                        }
                    }
                }
            }
            extra(returnFocus, listState)
        }
        val sheetGame = openGame?.let { key -> loaded?.games?.firstOrNull { it.key == key } }
        if (sheetGame != null) {
            GameSheetOverlay(
                game = sheetGame,
                channels = loaded?.channelsOf(sheetGame),
                returnFocus = returnFocus,
                onPlay = { channel: GameChannel -> onPlay(channel.liveRoute()) },
                bells = bells,
                onClose = {
                    closedGame = "game:${sheetGame.key}"
                    openGame = null
                },
                actions = { TvGameBell(sheetGame, bells, nowSec) },
            )
        }
    }
}
