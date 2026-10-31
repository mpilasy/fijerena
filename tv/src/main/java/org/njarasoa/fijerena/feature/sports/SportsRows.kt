package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.sports.GameBells
import org.njarasoa.fijerena.core.ui.sports.canRemind
import org.njarasoa.fijerena.core.ui.sports.channelNote
import org.njarasoa.fijerena.core.ui.sports.gameTitle
import org.njarasoa.fijerena.core.ui.sports.isHomeGame
import org.njarasoa.fijerena.core.ui.sports.opponentIn
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.buttons.TvButtonStyle
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

// The sports rows on TV (the sports teams plan → Screens), shared by Search's Teams type, the team
// and league pages and the Sports section. Each row is one focus stop, Left opens the rail; a game
// row's bell is a fixed slot to its right.

/**
 * A team or a league in a list: its logo (its first letter when it has none), its name, [details]
 * (sport, league) and a star when the profile follows it.
 */
@Composable
fun TvSportsItemRow(
    name: String,
    details: String,
    logoUrl: String?,
    followed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        look = TvCardLooks.listRow(),
        modifier =
            modifier
                .leftToRail()
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaThumbnail(
                url = logoUrl,
                fallbackLetter = name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = name,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                CinemaText(text = name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (details.isNotEmpty()) {
                    CinemaText(
                        text = details,
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (followed) {
                Icon(
                    imageVector = CinemaIcons.Star,
                    contentDescription = stringResource(R.string.sports_followed),
                    tint = CinemaTheme.colors.accent,
                    modifier = Modifier.size(TvDimensions.iconMedium),
                )
            }
        }
    }
}

/** A day's heading over its games ("Today", "Saturday 1 November"); it takes no focus. */
@Composable
fun TvSportsHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    CinemaText(
        text = text,
        role = TextRole.Title,
        modifier = modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

/**
 * A game: the logo and name of the side that matters ([team]'s opponent on a team page, else
 * both teams), the competition and time (home or away on a team page), and its best channel
 * ([channels]' first: logo, name, "In the guide" or "On Canal+"; "Looking for channels…" while
 * [channels] is null). [trailing]: a fixed slot after the row, outside it, for an action of its own
 * ([TvGameBell]): Right from the row reaches it, Left goes back, Up/Down keep the column.
 * [modifier] is the row's own (its focus requester, its Back target).
 */
@Composable
fun TvGameRow(
    game: SportsGame,
    channels: List<GameChannel>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    team: SportsTeam? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val resources = LocalResources.current
    val opponent = team?.let { opponentIn(game, it) }
    val title = if (team != null) opponent?.name ?: game.home.name else gameTitle(resources, game)
    val logo = if (team != null) opponent?.logoUrl else game.home.logoUrl
    val details =
        listOfNotNull(
            team?.takeIf { opponent != null }?.let {
                stringResource(if (isHomeGame(game, it)) R.string.sports_home_game else R.string.sports_away_game)
            },
            game.competition.name.takeIf { it.isNotBlank() },
            TimeFormat.formatTime(game.startEpochSec),
        ).joinToString(" · ")
    Row(
        modifier =
            Modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvCard(
            onClick = onClick,
            look = TvCardLooks.listRow(),
            modifier =
                modifier
                    .leftToRail()
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            GameRowContent(channels, title, logo, details)
        }
        trailing?.invoke(this)
    }
}

/** A game row's logo, its two lines and its best channel. */
@Composable
private fun GameRowContent(
    channels: List<GameChannel>?,
    title: String,
    logo: String?,
    details: String,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CinemaThumbnail(
            url = logo,
            fallbackLetter = title.firstOrNull(Char::isLetterOrDigit),
            contentType = ThumbnailContentType.LIVE_TV,
            contentDescription = null,
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            CinemaText(text = title, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CinemaText(text = details, role = TextRole.Body, tone = TextTone.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TvBestChannel(channels = channels, modifier = Modifier.weight(BEST_CHANNEL_WEIGHT))
    }
}

/**
 * A game's reminder bell (the sports teams plan → Reminders): sets or clears its reminder, the
 * icon and label saying which ([GameBells]). Nothing once the game has started ([canRemind]); in a
 * row's trailing slot ([TvGameRow]) the slot keeps its width, so the bells stay in one column.
 */
@Composable
fun TvGameBell(
    game: SportsGame,
    bells: GameBells,
    nowSec: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.widthIn(min = TvButtonStyle.iconButtonSize), contentAlignment = Alignment.Center) {
        if (canRemind(game, nowSec)) {
            val on = bells.reminded.has(game)
            TvIconAction(
                onClick = { bells.toggle(game) },
                icon = if (on) CinemaIcons.BellOn else CinemaIcons.Bell,
                label = stringResource(if (on) R.string.sports_reminder_set else R.string.sports_remind_me),
                on = on,
            )
        }
    }
}

/** A game's best channel in its row; "Looking for channels…" while the channels are matched. */
@Composable
private fun TvBestChannel(
    channels: List<GameChannel>?,
    modifier: Modifier = Modifier,
) {
    val best = channels?.firstOrNull()
    val resources = LocalResources.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        best?.stream?.let { stream ->
            CinemaThumbnail(
                url = stream.iconUrl,
                fallbackLetter = stream.name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.height(TvDimensions.iconLarge).aspectRatio(CHANNEL_LOGO_ASPECT),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            best?.stream?.let { stream ->
                CinemaText(text = stream.name, role = TextRole.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CinemaText(
                text = best?.let { channelNote(resources, it) } ?: stringResource(R.string.sports_matching),
                role = TextRole.Body,
                tone = TextTone.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// The best channel's share of a game row's width, beside the game's own text (1).
private const val BEST_CHANNEL_WEIGHT = 0.8f

/** A channel logo's frame: wide, drawn whole. */
internal const val CHANNEL_LOGO_ASPECT = 16f / 9f
