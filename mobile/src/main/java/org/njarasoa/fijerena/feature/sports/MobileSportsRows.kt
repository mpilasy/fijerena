package org.njarasoa.fijerena.feature.sports

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
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import org.njarasoa.fijerena.core.ui.sports.channelNote
import org.njarasoa.fijerena.core.ui.sports.gameTitle
import org.njarasoa.fijerena.core.ui.sports.isHomeGame
import org.njarasoa.fijerena.core.ui.sports.opponentIn
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

// The sports cards on the phone (the sports teams plan → Screens), shared by Search's Teams type,
// the team and league pages, and the Sports tab and Home row to come.

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
                Icon(
                    imageVector = CinemaIcons.Star,
                    contentDescription = stringResource(R.string.sports_followed),
                    tint = CinemaTheme.colors.accent,
                    modifier = Modifier.size(MobileDimensions.iconDefault),
                )
            }
        }
    }
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
 * A game: the side that matters ([team]'s opponent on a team page, else both teams), home or
 * away, the competition and time, then its best channel ([channels]' first: logo, name, "In the
 * guide" or "On Canal+"; "Looking for channels…" while [channels] is null). [trailing]: a slot at
 * the end for an action of its own (a reminder bell, the sports teams plan → Reminders); none yet.
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
    val opponent = team?.let { opponentIn(game, it) }
    val title = if (team != null) opponent?.name ?: game.home.name else gameTitle(resources, game)
    val logo = if (team != null) opponent?.logoUrl else game.home.logoUrl
    val details =
        listOfNotNull(
            TimeFormat.formatTime(game.startEpochSec),
            team?.takeIf { opponent != null }?.let {
                stringResource(if (isHomeGame(game, it)) R.string.sports_home_game else R.string.sports_away_game)
            },
            game.competition.name.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    val best = channels?.firstOrNull()
    CinemaCard(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = cardColors) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            CinemaThumbnail(
                url = logo,
                fallbackLetter = title.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.size(MobileDimensions.iconXLarge),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CinemaText(text = title, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CinemaText(
                    text = details,
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                    best?.stream?.let { stream ->
                        CinemaThumbnail(
                            url = stream.iconUrl,
                            fallbackLetter = stream.name.firstOrNull(Char::isLetterOrDigit),
                            contentType = ThumbnailContentType.LIVE_TV,
                            contentDescription = null,
                            modifier = Modifier.height(MobileDimensions.iconDefault).aspectRatio(CHANNEL_LOGO_ASPECT),
                        )
                    }
                    CinemaText(
                        text =
                            listOfNotNull(
                                best?.stream?.name,
                                best?.let { channelNote(resources, it) } ?: stringResource(R.string.sports_matching),
                            ).joinToString(" · "),
                        role = TextRole.BodySmall,
                        tone = TextTone.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

/** A channel logo's frame: wide, drawn whole. */
internal const val CHANNEL_LOGO_ASPECT = 16f / 9f
