package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import org.njarasoa.fijerena.core.ui.sports.MoreChannelsBadge
import org.njarasoa.fijerena.core.ui.sports.canRemind
import org.njarasoa.fijerena.core.ui.sports.channelLetter
import org.njarasoa.fijerena.core.ui.sports.channelNote
import org.njarasoa.fijerena.core.ui.sports.clockOr
import org.njarasoa.fijerena.core.ui.sports.gameLogoTeams
import org.njarasoa.fijerena.core.ui.sports.gameRowTitle
import org.njarasoa.fijerena.core.ui.sports.noChannelNote
import org.njarasoa.fijerena.core.ui.sports.opponentIn
import org.njarasoa.fijerena.core.ui.sports.resultDetails
import org.njarasoa.fijerena.core.ui.sports.resultTitle
import org.njarasoa.fijerena.core.ui.sports.shownChannelName
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.TvIcon
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
                TvIcon(
                    imageVector = CinemaIcons.Star,
                    contentDescription = stringResource(R.string.sports_followed),
                    tone = TextTone.Accent,
                    modifier = Modifier.size(TvDimensions.iconMedium),
                )
            }
        }
    }
}

/**
 * A Teams search's best team, over its games: its logo, its name, [details] (sport, league) and an
 * arrow, as one focus stop whose OK opens the team's page ([onOpen]); then [TvFollowActions] in
 * fixed slots to its right (Right reaches them). [modifier] is the row's own (its focus requester,
 * its Back target).
 */
@Composable
fun TvBestTeamRow(
    team: SportsTeam,
    details: String,
    followed: Boolean,
    bells: GameBells,
    onOpen: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            onClick = onOpen,
            look = TvCardLooks.listRow(),
            modifier =
                modifier
                    .leftToRail()
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CinemaThumbnail(
                    url = team.logoUrl,
                    fallbackLetter = team.name.firstOrNull(Char::isLetterOrDigit),
                    contentType = ThumbnailContentType.LIVE_TV,
                    contentDescription = team.name,
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    CinemaText(text = team.name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                TvIcon(
                    imageVector = CinemaIcons.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.sports_open_team_page),
                    modifier = Modifier.size(TvDimensions.iconMedium),
                )
            }
        }
        TvFollowActions(followed = followed, onToggleFollow = onToggleFollow, bells = bells, team = team)
    }
}

/**
 * Follow (a star) and, for a team ([team] set), the slot of its bell for every game, shown once the
 * profile follows it (the sports teams plan, Reminders). The bell's slot keeps its width while the
 * bell is hidden, so the star never moves and a second press lands on it again. A league page
 * passes no [team]: Follow alone. [modifier] (Follow's) and [bellModifier] are the actions' own
 * (focus requester, focus moves).
 */
@Composable
fun TvFollowActions(
    followed: Boolean,
    onToggleFollow: () -> Unit,
    bells: GameBells,
    team: SportsTeam?,
    modifier: Modifier = Modifier,
    bellModifier: Modifier = Modifier,
) {
    TvIconAction(
        onClick = onToggleFollow,
        icon = if (followed) CinemaIcons.Star else CinemaIcons.StarBorder,
        label = stringResource(if (followed) R.string.sports_unfollow else R.string.sports_follow),
        // A switch, as on the phone: the Accent tone while the team is followed.
        on = followed,
        modifier = modifier,
    )
    if (team != null) {
        Box(modifier = Modifier.widthIn(min = TvButtonStyle.iconButtonSize), contentAlignment = Alignment.Center) {
            if (followed) {
                val on = bells.reminded.hasTeam(team)
                TvIconAction(
                    onClick = { bells.toggleTeam(team) },
                    icon = if (on) CinemaIcons.BellOn else CinemaIcons.Bell,
                    label = stringResource(if (on) R.string.sports_remind_team_set else R.string.sports_remind_team),
                    on = on,
                    modifier = bellModifier,
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
 * A game: both teams, the home team first, with both logos (on a team page, the opponent's alone),
 * the competition and time, and its best channel ([channels]' first: logo, name, "In the guide",
 * "Event channel" or "On ESPN"; "Looking for channels…" while [channels] is null, "Not found on your
 * channels" when it is empty). [trailing]: a fixed slot after the row, outside it, for an action of
 * its own ([TvGameBell]): Right from the row reaches it, Left goes back, Up/Down keep the column.
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
    val title = gameRowTitle(resources, game)
    val logoTeams = gameLogoTeams(game, team)
    val details =
        listOfNotNull(
            game.competition.name.takeIf { it.isNotBlank() },
            clockOr(game, TimeFormat.formatTime(game.startEpochSec)),
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
            GameRowContent(channels, title, logoTeams, details)
        }
        trailing?.invoke(this)
    }
}

/**
 * A result on a team page (the team results plan): the game row's logos, the title with the score
 * and the competition and day. The card takes focus, so the list scrolls to it, and has no action.
 * An empty slot of a bell's width ends the row, so the cards line up with the game rows' ([TvGameBell]).
 */
@Composable
fun TvResultRow(
    game: SportsGame,
    modifier: Modifier = Modifier,
    team: SportsTeam? = null,
) {
    val resources = LocalResources.current
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
            onClick = {},
            look = TvCardLooks.listRow(),
            modifier =
                modifier
                    .leftToRail()
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            GameRowContent(
                channels = null,
                title = resultTitle(resources, game),
                logoTeams = gameLogoTeams(game, team),
                details = resultDetails(resources, game),
                showChannel = false,
            )
        }
        Spacer(modifier = Modifier.width(TvButtonStyle.iconButtonSize))
    }
}

/**
 * A game row's logos (one team at the row's height; two at half of it, one above the other, home on
 * top as in the title; a team's first letter when it has none), its two lines and its best channel
 * ([showChannel]; none for a result).
 */
@Composable
private fun GameRowContent(
    channels: List<GameChannel>?,
    title: String,
    logoTeams: List<SportsTeam>,
    details: String,
    showChannel: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            logoTeams.forEach { team ->
                CinemaThumbnail(
                    url = team.logoUrl,
                    fallbackLetter = team.name.firstOrNull(Char::isLetterOrDigit),
                    contentType = ThumbnailContentType.LIVE_TV,
                    contentDescription = null,
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            CinemaText(text = title, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CinemaText(text = details, role = TextRole.Body, tone = TextTone.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showChannel) TvBestChannel(channels = channels, modifier = Modifier.weight(BEST_CHANNEL_WEIGHT))
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

/** A game's best channel in its row; "Looking for channels…" while the channels are matched, "Not found on your channels" for none. */
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
                fallbackLetter = channelLetter(stream.name),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.height(TvDimensions.iconLarge).aspectRatio(CHANNEL_LOGO_ASPECT),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            best?.stream?.let { stream ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    CinemaText(
                        text = shownChannelName(stream.name),
                        role = TextRole.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    MoreChannelsBadge(channels)
                }
            }
            CinemaText(
                text = best?.let { channelNote(resources, it) } ?: noChannelNote(resources, channels),
                role = TextRole.Body,
                tone = TextTone.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// The best channel's share of a game row's width, beside the game's own text (1): the text holds both team names.
private const val BEST_CHANNEL_WEIGHT = 0.6f

/** A channel logo's frame: wide, drawn whole. */
internal const val CHANNEL_LOGO_ASPECT = 16f / 9f
