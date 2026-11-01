package org.njarasoa.fijerena.feature.sports

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.sports.DAY_HEADING
import org.njarasoa.fijerena.core.ui.sports.GamesState
import org.njarasoa.fijerena.core.ui.sports.SportsSectionViewModel
import org.njarasoa.fijerena.core.ui.sports.gamesByDay
import org.njarasoa.fijerena.core.ui.sports.key
import org.njarasoa.fijerena.core.ui.sports.liveRoute
import org.njarasoa.fijerena.core.ui.sports.rememberGameBells
import org.njarasoa.fijerena.core.ui.sports.rememberPinnedDayKey
import org.njarasoa.fijerena.core.ui.sports.route
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.MobileErrorState
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import java.text.Collator

/**
 * The phone's Sports tab (the sports teams plan → Sports section, SP-D7): under the tab's top bar
 * ([sourceTopBar], whose Search opens the Teams search), the teams and leagues the profile follows
 * as a row of logos, each opening its page, then "Next Games": the games of all of them in the next
 * 7 days by day, each once, with its best channel and its bell. A game opens its sheet
 * ([MobileGameSheet]); a channel there opens its mini-player ([onPlay]). Nothing followed (the tab
 * stays while it is open after the last unfollow): a short text and Find Teams ([onFindTeams]).
 */
@Composable
fun MobileSportsSectionScreen(
    sourceTopBar: @Composable () -> Unit,
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
    var openGame by rememberSaveable { mutableStateOf<String?>(null) }
    val loaded = games as? GamesState.Loaded
    val days = remember(loaded?.games) { loaded?.games?.let { gamesByDay(it) }.orEmpty() }
    val cardColors = sportsCardColors()
    val followedSorted =
        remember(followed) {
            followed
                ?.let { f ->
                    (
                        f.teams.map { FollowedEntry("team:${it.source.id}:${it.id}", it.name, it.logoUrl) { onTeamSelected(it.route()) } } +
                            f.leagues.map {
                                FollowedEntry(
                                    "league:${it.source.id}:${it.id}",
                                    it.name,
                                    it.logoUrl,
                                ) { onLeagueSelected(it.route()) }
                            }
                    ).sortedWith(compareBy(byName) { it.name })
                }.orEmpty()
        }
    Scaffold(containerColor = CinemaTheme.colors.clear, topBar = sourceTopBar) { padding ->
        val follows = followed
        when {
            follows == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CinemaSpinner() }
            }

            follows.isEmpty -> {
                MobileEmptyState(
                    message = stringResource(R.string.sports_nothing_followed),
                    icon = CinemaIcons.Sports,
                    actionLabel = stringResource(R.string.sports_find_teams),
                    onAction = onFindTeams,
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                val listState = rememberLazyListState()
                val pinnedDay = rememberPinnedDayKey(listState)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                    contentPadding =
                        PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.md + LocalDockInset.current),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    item(key = "followed_heading") { MobileSportsHeading(stringResource(R.string.sports_following_heading)) }
                    item(key = "followed") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                            items(followedSorted, key = { it.key }) { item ->
                                FollowedCard(item.name, item.logoUrl, item.onClick)
                            }
                        }
                    }
                    item(key = "games_heading") {
                        MobileSportsHeading(
                            stringResource(R.string.sports_next_games_heading),
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    when (val state = games) {
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
                                        role = TextRole.Body,
                                        tone = TextTone.Secondary,
                                        modifier = Modifier.padding(vertical = Spacing.md),
                                    )
                                }
                            }
                            for (day in days) {
                                stickyHeader(
                                    key = "day:${day.date}",
                                    contentType = DAY_HEADING,
                                ) {
                                    MobileSportsDayHeading(
                                        DateText.dayLabel(resources, day.date),
                                        pinned = pinnedDay == "day:${day.date}",
                                    )
                                }
                                items(day.games, key = { "game:${it.key}" }) { game ->
                                    MobileGameCard(
                                        game = game,
                                        channels = state.channelsOf(game),
                                        cardColors = cardColors,
                                        onClick = { openGame = game.key },
                                        trailing = { MobileGameBell(game, bells, nowSec) },
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

/** A followed team or league in the tab's row: its logo (its first letter when it has none) over its name. */
@Composable
private fun FollowedCard(
    name: String,
    logoUrl: String?,
    onClick: () -> Unit,
) {
    CinemaCard(onClick = onClick, modifier = Modifier.width(MobileDimensions.followedCardWidth), colors = sportsCardColors()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CinemaSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            CinemaThumbnail(
                url = logoUrl,
                fallbackLetter = name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.size(MobileDimensions.iconXLarge),
            )
            CinemaText(text = name, role = TextRole.BodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** A followed team or league in the tab's row. */
private class FollowedEntry(
    val key: String,
    val name: String,
    val logoUrl: String?,
    val onClick: () -> Unit,
)

/** Followed teams and leagues together in alphabetical order, accents and case ignored. */
private val byName: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
