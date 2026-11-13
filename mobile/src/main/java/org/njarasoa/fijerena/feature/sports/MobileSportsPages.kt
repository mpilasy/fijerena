package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.sports.AFTER_DAYS
import org.njarasoa.fijerena.core.ui.sports.DAY_HEADING
import org.njarasoa.fijerena.core.ui.sports.GamesState
import org.njarasoa.fijerena.core.ui.sports.SportsLeagueViewModel
import org.njarasoa.fijerena.core.ui.sports.SportsTeamViewModel
import org.njarasoa.fijerena.core.ui.sports.endOfDays
import org.njarasoa.fijerena.core.ui.sports.gamesByDay
import org.njarasoa.fijerena.core.ui.sports.key
import org.njarasoa.fijerena.core.ui.sports.liveRoute
import org.njarasoa.fijerena.core.ui.sports.rememberEndedDayKey
import org.njarasoa.fijerena.core.ui.sports.rememberGameBells
import org.njarasoa.fijerena.core.ui.sports.rememberPinnedDayKey
import org.njarasoa.fijerena.core.ui.sports.route
import org.njarasoa.fijerena.core.ui.sports.sportLabel
import org.njarasoa.fijerena.core.ui.sports.teamDetails
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.MobileDayHeading
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.MobileErrorState
import org.njarasoa.fijerena.ui.components.MobileScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A team's page on the phone (the sports teams plan → Team page): the logo, the name, the sport
 * and league, Follow at the end of the top bar (an icon, its label in a long-press tooltip; once
 * followed, a bell for every game of the team to its left, so Follow never moves), then the team's
 * coming games (up to 10) by day in
 * the device's time zone, each with its bell. A game opens its sheet
 * ([MobileGameSheet]); a channel there opens its mini-player ([onPlay], as a guide result).
 */
@Composable
fun MobileSportsTeamScreen(
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
    val results by viewModel.results.collectAsStateWithLifecycle()
    MobileSportsPage(
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
        // Every game of the team, once the profile follows it (the sports teams plan → Reminders).
        teamBell = shown,
        // The results of the last 30 days above the games, oldest first (the team results plan,
        // Phase 3): the page opens on the next game, the results a scroll up. Nothing at all when there are none.
        beforeCount = if (results.isEmpty()) 0 else results.size + 1,
        before = {
            if (results.isNotEmpty()) {
                item(key = "results_heading") { MobileSportsHeading(stringResource(R.string.sports_results_heading)) }
                items(
                    results.asReversed(),
                    key = { "result:${it.source.id}:${it.id}" },
                ) { game -> MobileResultCard(game = game, team = shown) }
            }
        },
    )
}

/**
 * A league's page on the phone (the sports teams plan → League page): the logo, the name, the
 * sport, Follow in the top bar, the league's games of the next 7 days by day (both teams), then
 * its teams, each opening its team page ([onTeamSelected]).
 */
@Composable
fun MobileSportsLeagueScreen(
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
    MobileSportsPage(
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
        extra = {
            val list = teams.orEmpty()
            if (list.isNotEmpty()) {
                endOfDays(key = "teams_end_of_days")
                item(key = "teams_heading", contentType = AFTER_DAYS) {
                    MobileSportsHeading(stringResource(R.string.search_tab_teams), modifier = Modifier.padding(top = Spacing.md))
                }
                items(list, key = { "team:${it.source.id}:${it.id}" }, contentType = { AFTER_DAYS }) { team ->
                    MobileSportsItemCard(
                        name = team.name,
                        details = teamDetails(resources, team),
                        logoUrl = team.logoUrl,
                        followed = false,
                        onClick = { onTeamSelected(team.route()) },
                    )
                }
            }
        },
    )
}

/**
 * The page both share: the top bar with Follow, the header, the games by day, [extra] cards after
 * them, and the game sheet while one is open (its game's key saved, so Back from a channel finds it
 * open again).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSportsPage(
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
    teamBell: SportsTeam? = null,
    beforeCount: Int = 0,
    before: LazyListScope.() -> Unit = {},
    extra: LazyListScope.() -> Unit = {},
) {
    val resources = LocalResources.current
    val bells = rememberGameBells()
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val listState = rememberLazyListState()
    val pinnedDay = rememberPinnedDayKey(listState)
    val endedDay = rememberEndedDayKey(listState)
    var openGame by rememberSaveable { mutableStateOf<String?>(null) }
    val loaded = games as? GamesState.Loaded
    val days = remember(loaded?.games) { loaded?.games?.let { gamesByDay(it) }.orEmpty() }
    val cardColors = sportsCardColors()
    // With rows above the games ([before]: a team's results), the page opens on the games' heading.
    LaunchedEffect(beforeCount > 0, loaded != null) {
        if (beforeCount > 0 && loaded != null && listState.firstVisibleItemIndex == 0) listState.scrollToItem(1 + beforeCount)
    }
    Scaffold(
        topBar = {
            MobileScreenHeader(title = title, onBack = onBack) {
                MobileFollowActions(followed = followed, onToggleFollow = onToggleFollow, bells = bells, team = teamBell)
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding =
                PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = Spacing.sm,
                    bottom =
                        Spacing.md + LocalDockInset.current,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "header") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                ) {
                    CinemaThumbnail(
                        url = logoUrl,
                        fallbackLetter = title.firstOrNull(Char::isLetterOrDigit),
                        contentType = ThumbnailContentType.LIVE_TV,
                        contentDescription = null,
                        modifier = Modifier.size(MobileDimensions.iconXLarge),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        CinemaText(text = title, role = TextRole.Heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (subtitle.isNotEmpty()) CinemaText(text = subtitle, role = TextRole.BodySmall, tone = TextTone.Secondary)
                    }
                }
            }
            before()
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
                        MobileErrorState(
                            title = stringResource(R.string.sports_load_error),
                            message = games.raw?.let { "[dev] $it" }.orEmpty(),
                            onRetry = onRetry,
                        )
                    }
                }

                is GamesState.Loaded -> {
                    if (games.games.isEmpty()) {
                        item(key = "no_games") {
                            MobileEmptyState(
                                message = stringResource(R.string.sports_no_games),
                                icon = CinemaIcons.DateRange,
                            )
                        }
                    } else if (beforeCount > 0) {
                        // Under the results, a heading tells the coming games from them.
                        item(key = "games_heading") { MobileSportsHeading(stringResource(R.string.sports_next_games_heading)) }
                    }
                    for (day in days) {
                        stickyHeader(
                            key = "day:${day.date}",
                            contentType = DAY_HEADING,
                        ) {
                            MobileDayHeading(
                                DateText.dayLabel(resources, day.date),
                                pinned = pinnedDay == "day:${day.date}",
                                modifier = Modifier.alpha(if (endedDay == "day:${day.date}") 0f else 1f),
                            )
                        }
                        items(day.games, key = { "game:${it.key}" }) { game ->
                            MobileGameCard(
                                game = game,
                                channels = games.channelsOf(game),
                                team = team,
                                cardColors = cardColors,
                                onClick = { openGame = game.key },
                                trailing = { MobileGameBell(game, bells, nowSec) },
                            )
                        }
                    }
                }
            }
            extra()
        }
    }
    val sheetGame = openGame?.let { key -> loaded?.games?.firstOrNull { it.key == key } }
    if (sheetGame != null) {
        MobileGameSheet(
            game = sheetGame,
            channels = loaded?.channelsOf(sheetGame),
            onPlay = { channel -> onPlay(channel.liveRoute()) },
            bells = bells,
            onDismiss = { openGame = null },
            actions = { MobileGameBell(sheetGame, bells, nowSec) },
        )
    }
}
