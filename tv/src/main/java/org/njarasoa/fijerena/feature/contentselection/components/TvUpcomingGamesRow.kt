package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.sports.MoreChannelsBadge
import org.njarasoa.fijerena.core.ui.sports.UpcomingGame
import org.njarasoa.fijerena.core.ui.sports.channelLetter
import org.njarasoa.fijerena.core.ui.sports.gameDayLabel
import org.njarasoa.fijerena.core.ui.sports.key
import org.njarasoa.fijerena.core.ui.sports.shownChannelName
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import java.time.Instant
import java.time.ZoneId

/**
 * Home's Upcoming Games row (the sports teams plan → Home row): a card per game of the followed
 * teams and leagues in the next 48 hours. A card has no actions menu; OK opens the game's sheet.
 */
@Composable
fun TvUpcomingGamesRow(
    title: String,
    games: List<UpcomingGame>,
    onGameSelected: (UpcomingGame) -> Unit,
    firstItemFocus: FocusRequester,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    restoreFocus: FocusRequester? = null,
    itemModifier: (UpcomingGame) -> Modifier = { Modifier },
) {
    HomeRow(
        title = title,
        items = games,
        key = { it.game.key },
        listState = listState,
        firstItemFocus = firstItemFocus,
        restoreFocus = restoreFocus,
        modifier = modifier,
    ) { game, rowModifier ->
        TvUpcomingGameCard(game = game, onClick = { onGameSelected(game) }, modifier = itemModifier(game).then(rowModifier))
    }
}

/** A game: both teams' logos, each team's name on its own line (home first), its day and time, and its best channel (logo, name). */
@Composable
private fun TvUpcomingGameCard(
    game: UpcomingGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val start = game.game.startEpochSec
    val day = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault()).toLocalDate()
    val best = game.bestChannel
    TvCard(onClick = onClick, look = TvCardLooks.shelf(), modifier = modifier.width(TvDimensions.continueWatchingCardWidth)) {
        Column(modifier = Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOfNotNull(game.game.home, game.game.away).forEach { team -> TeamLogo(team) }
            }
            // Each team on its own line, home first: one line of both was cut before the second team.
            listOfNotNull(game.game.home, game.game.away).forEach { team ->
                CinemaText(text = team.name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CinemaText(
                text = "${gameDayLabel(resources, day)} · ${TimeFormat.formatTime(start)}",
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                best?.stream?.let { stream ->
                    CinemaThumbnail(
                        url = stream.iconUrl,
                        fallbackLetter = channelLetter(stream.name),
                        contentType = ThumbnailContentType.LIVE_TV,
                        contentDescription = null,
                        modifier = Modifier.height(TvDimensions.iconMedium).aspectRatio(CHANNEL_LOGO_ASPECT),
                    )
                }
                CinemaText(
                    // A line even with no channel, so the cards in the row line up.
                    text = best?.stream?.name?.let(::shownChannelName) ?: stringResource(R.string.sports_not_found),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                MoreChannelsBadge(game.channels)
            }
        }
    }
}

@Composable
private fun TeamLogo(team: SportsTeam) {
    CinemaThumbnail(
        url = team.logoUrl,
        fallbackLetter = team.name.firstOrNull(Char::isLetterOrDigit),
        contentType = ThumbnailContentType.LIVE_TV,
        contentDescription = null,
        modifier = Modifier.size(TvDimensions.iconXLarge),
    )
}

// A channel logo's frame: wide, drawn whole.
private const val CHANNEL_LOGO_ASPECT = 16f / 9f
