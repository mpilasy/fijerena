package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.sports.GameBells
import org.njarasoa.fijerena.core.ui.sports.MoreChannelsBadge
import org.njarasoa.fijerena.core.ui.sports.UpcomingGame
import org.njarasoa.fijerena.core.ui.sports.canRemind
import org.njarasoa.fijerena.core.ui.sports.channelLetter
import org.njarasoa.fijerena.core.ui.sports.channelNote
import org.njarasoa.fijerena.core.ui.sports.dayHeadingBand
import org.njarasoa.fijerena.core.ui.sports.gameLogoTeams
import org.njarasoa.fijerena.core.ui.sports.gameTitle
import org.njarasoa.fijerena.core.ui.sports.noChannelNote
import org.njarasoa.fijerena.core.ui.sports.opponentIn
import org.njarasoa.fijerena.core.ui.sports.resultDetails
import org.njarasoa.fijerena.core.ui.sports.resultTitle
import org.njarasoa.fijerena.core.ui.sports.shownChannelName
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

// The sports cards on the phone (the sports teams plan → Screens), shared by Search's Teams type,
// the team and league pages, the Sports tab and Home's Upcoming Games row.

/** The cards' colours, as the search results' cards. */
@Composable
fun sportsCardColors(): CardColors = CardDefaults.cardColors(containerColor = CinemaTheme.colors.surface)

/**
 * A team or a league in a list: its logo (its first letter when it has none), its name, [details]
 * (sport, league) and a star when the profile follows it.
 */
@Composable
fun MobileSportsItemCard(
    name: String,
    details: String,
    logoUrl: String?,
    followed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardColors: CardColors = sportsCardColors(),
) {
    CinemaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(MobileDimensions.streamCardHeight),
        colors = cardColors,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(CinemaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            CinemaThumbnail(
                url = logoUrl,
                fallbackLetter = name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = name,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CinemaText(text = name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (details.isNotEmpty()) {
                    CinemaText(
                        text = details,
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (followed) {
                CinemaIcon(
                    imageVector = CinemaIcons.Star,
                    contentDescription = stringResource(R.string.sports_followed),
                    tone = TextTone.Accent,
                    modifier = Modifier.size(MobileDimensions.iconDefault),
                )
            }
        }
    }
}

/**
 * A Teams search's best team, over its games: its logo, its name, [details] (sport, league), then
 * [MobileFollowActions] at its end. A tap on the card opens the team's page ([onOpen]).
 */
@Composable
fun MobileBestTeamCard(
    team: SportsTeam,
    details: String,
    followed: Boolean,
    bells: GameBells,
    onOpen: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
    cardColors: CardColors = sportsCardColors(),
) {
    CinemaCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().height(MobileDimensions.streamCardHeight),
        colors = cardColors,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(CinemaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            CinemaThumbnail(
                url = team.logoUrl,
                fallbackLetter = team.name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = team.name,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CinemaText(text = team.name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (details.isNotEmpty()) {
                    CinemaText(
                        text = details,
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MobileFollowActions(followed = followed, onToggleFollow = onToggleFollow, bells = bells, team = team)
        }
    }
}

/**
 * The team's bell for every game (for a team, [team] set, once the profile follows it), then
 * Follow (a star) last, at the end of its row: the bell comes and goes to the star's left, so the
 * star never moves and a second tap lands on it again. A league page passes no [team]: Follow alone.
 */
@Composable
fun MobileFollowActions(
    followed: Boolean,
    onToggleFollow: () -> Unit,
    bells: GameBells,
    team: SportsTeam?,
) {
    if (team != null && followed) MobileTeamBell(team, bells)
    IconAction(
        onClick = onToggleFollow,
        icon = if (followed) CinemaIcons.Star else CinemaIcons.StarBorder,
        label = stringResource(if (followed) R.string.sports_unfollow else R.string.sports_follow),
        // A switch: the Accent tone while the team is followed, the Primary tone while not.
        on = followed,
    )
}

/** A heading over a group of cards (a day, Teams); it does nothing on a tap. */
@Composable
fun MobileSportsHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    CinemaText(text = text, role = TextRole.Title, modifier = modifier.padding(vertical = Spacing.xs))
}

/**
 * A game: both teams, the home team first, with both logos (on a team page, the opponent's alone), the time and the
 * competition, then its best channel ([channels]' first: logo, name, "In the
 * guide", "Event channel" or "On ESPN"; "Looking for channels…" while [channels] is null, "Not
 * found on your channels" when it is empty). [trailing]: a slot at
 * the end for an action of its own ([MobileGameBell]).
 */
@Composable
fun MobileGameCard(
    game: SportsGame,
    channels: List<GameChannel>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    team: SportsTeam? = null,
    cardColors: CardColors = sportsCardColors(),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val resources = LocalResources.current
    val title = gameTitle(resources, game)
    val details =
        listOfNotNull(
            TimeFormat.formatTime(game.startEpochSec),
            game.competition.name.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    val best = channels?.firstOrNull()
    CinemaCard(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = cardColors) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            GameLogos(gameLogoTeams(game, team))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                // Long team and competition names wrap to a second line rather than being cut.
                CinemaText(text = title, role = TextRole.Heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CinemaText(
                    text = details,
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                    best?.stream?.let { stream ->
                        CinemaThumbnail(
                            url = stream.iconUrl,
                            fallbackLetter = channelLetter(stream.name),
                            contentType = ThumbnailContentType.LIVE_TV,
                            contentDescription = null,
                            modifier = Modifier.height(MobileDimensions.iconDefault).aspectRatio(CHANNEL_LOGO_ASPECT),
                        )
                    }
                    CinemaText(
                        text =
                            listOfNotNull(
                                best?.stream?.name?.let(::shownChannelName),
                                best?.let { channelNote(resources, it) } ?: noChannelNote(resources, channels),
                            ).joinToString(" · "),
                        role = TextRole.BodySmall,
                        tone = TextTone.Accent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    MoreChannelsBadge(channels)
                }
            }
            trailing?.invoke(this)
        }
    }
}

/**
 * A result on a team page (the team results plan): the game card's logos, the title with the score
 * and the competition and day. No click: a result opens nothing.
 */
@Composable
fun MobileResultCard(
    game: SportsGame,
    modifier: Modifier = Modifier,
    team: SportsTeam? = null,
    cardColors: CardColors = sportsCardColors(),
) {
    val resources = LocalResources.current
    CinemaCard(modifier = modifier.fillMaxWidth(), colors = cardColors) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            GameLogos(gameLogoTeams(game, team))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CinemaText(text = resultTitle(resources, game), role = TextRole.Heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CinemaText(
                    text = resultDetails(resources, game),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A game card's logos: one team large; two smaller, one above the other, home on top as in the title. */
@Composable
private fun GameLogos(teams: List<SportsTeam>) {
    val size = if (teams.size > 1) MobileDimensions.iconLarge else MobileDimensions.iconXLarge
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        teams.forEach { logoTeam ->
            CinemaThumbnail(
                url = logoTeam.logoUrl,
                fallbackLetter = logoTeam.name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** A channel logo's frame: wide, drawn whole. */
internal const val CHANNEL_LOGO_ASPECT = 16f / 9f

/**
 * A game's reminder bell (the sports teams plan → Reminders): sets or clears its reminder, the
 * icon and label saying which ([GameBells]); its label in a long-press tooltip. Nothing once the
 * game has started ([canRemind]).
 */
@Composable
fun MobileGameBell(
    game: SportsGame,
    bells: GameBells,
    nowSec: Long,
) {
    if (canRemind(game, nowSec)) {
        val on = bells.reminded.has(game)
        IconAction(
            onClick = { bells.toggle(game) },
            icon = if (on) CinemaIcons.BellOn else CinemaIcons.Bell,
            label = stringResource(if (on) R.string.sports_reminder_set else R.string.sports_remind_me),
            on = on,
        )
    }
}

/** A team's bell for every one of its games, in its page's top bar once the profile follows it. */
@Composable
fun MobileTeamBell(
    team: SportsTeam,
    bells: GameBells,
) {
    val on = bells.reminded.hasTeam(team)
    IconAction(
        onClick = { bells.toggleTeam(team) },
        icon = if (on) CinemaIcons.BellOn else CinemaIcons.Bell,
        label = stringResource(if (on) R.string.sports_remind_team_set else R.string.sports_remind_team),
        on = on,
    )
}

/**
 * A game on Home's Upcoming Games row (the sports teams plan → Home row): both teams' logos, each
 * team's name on its own line (home first), the day and time, and its best channel (logo, name on
 * one line). A tap opens the game's sheet.
 */
@Composable
fun MobileUpcomingGameCard(
    game: UpcomingGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val start = game.game.startEpochSec
    val day = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault()).toLocalDate()
    val best = game.bestChannel
    CinemaCard(onClick = onClick, modifier = modifier.width(MobileDimensions.homeCardWidth)) {
        Column(modifier = Modifier.padding(CinemaSpacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOfNotNull(game.game.home, game.game.away).forEach { team ->
                    CinemaThumbnail(
                        url = team.logoUrl,
                        fallbackLetter = team.name.firstOrNull(Char::isLetterOrDigit),
                        contentType = ThumbnailContentType.LIVE_TV,
                        contentDescription = null,
                        modifier = Modifier.size(MobileDimensions.iconXLarge),
                    )
                }
            }
            listOfNotNull(game.game.home, game.game.away).forEach { team ->
                // The title role of every other Home card.
                CinemaText(text = team.name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CinemaText(
                text = DateText.dayAndTime(DateText.shortDayLabel(resources, day), TimeFormat.formatTime(start)),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                best?.stream?.let { stream ->
                    CinemaThumbnail(
                        url = stream.iconUrl,
                        fallbackLetter = channelLetter(stream.name),
                        contentType = ThumbnailContentType.LIVE_TV,
                        contentDescription = null,
                        modifier = Modifier.height(MobileDimensions.iconDefault).aspectRatio(CHANNEL_LOGO_ASPECT),
                    )
                }
                CinemaText(
                    // A line even with no channel, so the cards in the row line up.
                    text = best?.stream?.name?.let(::shownChannelName) ?: stringResource(R.string.sports_not_found),
                    role = TextRole.BodySmall,
                    tone = TextTone.Accent,
                    // One line: two lines of a narrow card broke "AU (STAN 45)" into "AU" and "(ST…".
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                MoreChannelsBadge(game.channels)
            }
        }
    }
}
