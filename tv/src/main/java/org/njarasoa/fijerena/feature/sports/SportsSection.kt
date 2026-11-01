package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.sports.DAY_HEADING
import org.njarasoa.fijerena.core.ui.sports.GamesState
import org.njarasoa.fijerena.core.ui.sports.SportsSectionViewModel
import org.njarasoa.fijerena.core.ui.sports.dayHeadingBand
import org.njarasoa.fijerena.core.ui.sports.gamesByDay
import org.njarasoa.fijerena.core.ui.sports.key
import org.njarasoa.fijerena.core.ui.sports.liveRoute
import org.njarasoa.fijerena.core.ui.sports.rememberGameBells
import org.njarasoa.fijerena.core.ui.sports.rememberPinnedDayKey
import org.njarasoa.fijerena.core.ui.sports.route
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import java.text.Collator

/** A team or a league the profile follows, as the section's row of logos shows it. */

/** Followed teams and leagues together in alphabetical order, accents and case ignored. */
private val byName: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

private data class FollowedItem(
    val key: String,
    val name: String,
    val logoUrl: String?,
    val route: Screen,
)

/**
 * The Sports section on TV (the sports teams plan → Sports section; the rail's Sports): the teams
 * and leagues the profile follows as a row of logos, each opening its page, then "Next Games": the
 * games of all of them in the next 7 days by day, each once, with its best channel and its bell. A
 * game opens its sheet over the section ([GameSheetOverlay]); a channel there opens its preview
 * ([onPlay]). Nothing followed: a short text and Find Teams ([onFindTeams], Search on its Teams
 * type), which has focus.
 *
 * Focus: the first logo on opening, and whenever focus comes in from outside (Right from the rail
 * before the follows were read); Down to the games, Up from the games to the logo last focused (the
 * first one at first), Up from the logos to the header's Find Teams, which Right leaves alone; Left
 * from the first logo, a game row or Find Teams opens the rail. Back from a page, a channel or Search
 * returns to the control that opened it; Back on the section goes to the rail (the nav host).
 */
@Composable
fun SportsSectionScreen(
    onTeamSelected: (Screen.SportsTeam) -> Unit,
    onLeagueSelected: (Screen.SportsLeague) -> Unit,
    onFindTeams: () -> Unit,
    onPlay: (Screen.CategoryList) -> Unit,
    viewModel: SportsSectionViewModel =
        viewModel(key = "sports_section", factory = SportsSectionViewModel.Factory(LocalContext.current)),
) {
    val resources = LocalResources.current
    val followed by viewModel.followed.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val bells = rememberGameBells()
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val listState = rememberLazyListState()
    val pinnedDay = rememberPinnedDayKey(listState)
    val returnFocus = rememberNavReturnFocus()
    val firstItemFocus = remember { FocusRequester() }
    val findTeamsFocus = remember { FocusRequester() }
    var openGame by rememberSaveable { mutableStateOf<String?>(null) }
    var closedGame by remember { mutableStateOf<String?>(null) }
    val loaded = games as? GamesState.Loaded
    val days = remember(loaded?.games) { loaded?.games?.let { gamesByDay(it) }.orEmpty() }
    val items =
        remember(followed) {
            followed?.let { f ->
                (
                    f.teams.map { FollowedItem("followed:team:${it.source.id}:${it.id}", it.name, it.logoUrl, it.route()) } +
                        f.leagues.map { FollowedItem("followed:league:${it.source.id}:${it.id}", it.name, it.logoUrl, it.route()) }
                ).sortedWith(compareBy(byName) { it.name })
            }
        }
    // The effects below wait for the follows: they read this state, not [items] as it was when they started
    // (null on a first open, when the follows come from the database after the first frame).
    val itemsState = rememberUpdatedState(items)
    val leave: (String, () -> Unit) -> Unit = { key, go ->
        returnFocus.leaveFrom(key, listState)
        go()
    }

    // The first logo once the follows are read; Find Teams when there is none.
    LaunchedEffect(Unit) {
        if (returnFocus.isReturn) return@LaunchedEffect
        val read = snapshotFlow { itemsState.value }.first { it != null }.orEmpty()
        (if (read.isEmpty()) findTeamsFocus else firstItemFocus).requestFocusWithRetry()
    }
    NavReturnFocusEffect(
        returnFocus,
        listState = listState,
        fallback = firstItemFocus,
        // The logos come with the follows, the game rows with the games.
        prepare = { key ->
            snapshotFlow { itemsState.value }.first { it != null }
            if (key.startsWith("game") && itemsState.value?.isNotEmpty() == true) {
                snapshotFlow { games is GamesState.Loaded }.first { it }
            }
        },
    )
    LaunchedEffect(closedGame) {
        closedGame?.let { returnFocus.requesterOf(it).requestFocusWithRetry(fallback = firstItemFocus) }
        closedGame = null
    }

    Box(modifier = Modifier.fillMaxSize().panel(PanelFill.Ground)) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    // Focus coming in from outside (the rail's Right) lands on the entry item, not on whatever is nearest.
                    .focusProperties {
                        onEnter = {
                            val list = items
                            // A focus request on a row (Back to it) is left alone; only a D-pad move from outside is sent on.
                            if (requestedFocusDirection != FocusDirection.Enter && list != null) {
                                (if (list.isEmpty()) findTeamsFocus else firstItemFocus).requestFocus()
                            }
                        }
                    }.focusGroup(),
            contentPadding = PaddingValues(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "header") {
                TvScreenHeader(title = stringResource(R.string.sports_title)) {
                    if (!items.isNullOrEmpty()) {
                        TvIconAction(
                            onClick = { leave(RETURN_FIND_TEAMS, onFindTeams) },
                            icon = CinemaIcons.Search,
                            label = stringResource(R.string.sports_find_teams),
                            // Down: the first logo, not a game's bell under the header's end; Right: nothing (no bell).
                            modifier =
                                Modifier
                                    .navReturnFocusTarget(returnFocus, RETURN_FIND_TEAMS)
                                    .focusProperties {
                                        down = firstItemFocus
                                        right = FocusRequester.Cancel
                                    },
                        )
                    }
                }
            }
            val list = items
            when {
                list == null -> {
                    item(key = "reading") { Loading() }
                }

                list.isEmpty() -> {
                    item(key = "nothing_followed") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            CinemaText(
                                text = stringResource(R.string.sports_nothing_followed),
                                role = TextRole.Title,
                                textAlign = TextAlign.Center,
                            )
                            CinemaPrimaryButton(
                                onClick = { leave(RETURN_FIND_TEAMS, onFindTeams) },
                                text = stringResource(R.string.sports_find_teams),
                                modifier =
                                    Modifier
                                        .focusRequester(findTeamsFocus)
                                        .navReturnFocusTarget(returnFocus, RETURN_FIND_TEAMS)
                                        .leftToRail(),
                            )
                        }
                    }
                }

                else -> {
                    item(key = "followed_heading") { TvSportsHeading(stringResource(R.string.sports_following_heading)) }
                    item(key = "followed") {
                        // Up from the games comes back to the logo last focused, the first one at first.
                        LazyRow(
                            modifier = Modifier.focusRestorer(firstItemFocus),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        ) {
                            itemsIndexed(list, key = { _, item -> item.key }) { index, item ->
                                FollowedCard(
                                    item = item,
                                    onClick = {
                                        leave(item.key) {
                                            when (val route = item.route) {
                                                is Screen.SportsTeam -> onTeamSelected(route)
                                                is Screen.SportsLeague -> onLeagueSelected(route)
                                                else -> Unit
                                            }
                                        }
                                    },
                                    modifier =
                                        (if (index == 0) Modifier.focusRequester(firstItemFocus).leftToRail() else Modifier)
                                            .navReturnFocusTarget(returnFocus, item.key)
                                            .focusProperties { if (index == list.lastIndex) right = FocusRequester.Cancel },
                                )
                            }
                        }
                    }
                    item(key = "games_heading") {
                        TvSportsHeading(stringResource(R.string.sports_next_games_heading), modifier = Modifier.padding(top = Spacing.md))
                    }
                    when (val state = games) {
                        GamesState.Loading -> {
                            item(key = "loading") { Loading() }
                        }

                        is GamesState.Failed -> {
                            item(key = "failed") {
                                TvErrorState(
                                    title = stringResource(R.string.sports_load_error),
                                    message = state.raw?.let { "[dev] $it" }.orEmpty(),
                                    onRetry = viewModel::retry,
                                )
                            }
                        }

                        is GamesState.Loaded -> {
                            if (state.games.isEmpty()) {
                                item(key = "no_games") {
                                    CinemaText(
                                        text = stringResource(R.string.sports_no_games),
                                        tone = TextTone.Secondary,
                                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg),
                                    )
                                }
                            }
                            for (day in days) {
                                stickyHeader(key = "day:${day.date}", contentType = DAY_HEADING) {
                                    CinemaText(
                                        text = DateText.dayLabel(resources, day.date),
                                        tone = TextTone.Secondary,
                                        modifier =
                                            Modifier
                                                .dayHeadingBand(pinnedDay == "day:${day.date}")
                                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                    )
                                }
                                for (game in day.games) {
                                    val key = "game:${game.key}"
                                    item(key = key) {
                                        TvGameRow(
                                            game = game,
                                            channels = state.channelsOf(game),
                                            onClick = { openGame = game.key },
                                            modifier = Modifier.navReturnFocusTarget(returnFocus, key),
                                            trailing = { TvGameBell(game, bells, nowSec) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

/** A followed team or league in the section's row: its logo (its first letter when it has none) over its name. */
@Composable
private fun FollowedCard(
    item: FollowedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(onClick = onClick, look = TvCardLooks.shelf(), modifier = modifier.width(TvDimensions.followedCardWidth)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            CinemaThumbnail(
                url = item.logoUrl,
                fallbackLetter = item.name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.size(TvDimensions.followedLogoSize),
            )
            CinemaText(text = item.name, role = TextRole.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        CinemaSpinner()
    }
}

// The key of Find Teams (the header's icon or the empty section's button) — see rememberNavReturnFocus.
private const val RETURN_FIND_TEAMS = "sports:find-teams"
