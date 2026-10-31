package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.sports.channelNote
import org.njarasoa.fijerena.core.ui.sports.gameDayLabel
import org.njarasoa.fijerena.core.ui.sports.gameTitle
import org.njarasoa.fijerena.core.ui.sports.noChannelNote
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import java.time.Instant
import java.time.ZoneId

/**
 * A game's sheet over its page (the sports teams plan → Game sheet): the game, its competition, day
 * and time; then only the profile's channels that show it, in the matcher's order (guide
 * programmes first, then event channels, then networks' channels); "Not found on your channels"
 * when there is none. OK on a channel calls [onPlay] (the channel's preview, as a guide result);
 * Back calls [onClose], and the page gives focus back to the game. An in-tree overlay: focus stays
 * in it.
 *
 * [contentPadding]: the safe margins on a page of its own; none inside a screen that has them
 * (Search). [actions]: the header's icon actions (the reminder bell, [TvGameBell]); Up from the first row
 * reaches them. Focus: the first row on opening, and again when the channels come; Back from a channel
 * returns to its row ([returnFocus]).
 */
@Composable
fun GameSheetOverlay(
    game: SportsGame,
    channels: List<GameChannel>?,
    returnFocus: NavReturnFocus,
    onPlay: (GameChannel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val resources = LocalResources.current
    val firstRow = remember { FocusRequester() }
    // The first row on opening, and once the channels replace "Looking for channels…".
    LaunchedEffect(channels != null) {
        if (returnFocus.key?.startsWith(RETURN_SHEET_PREFIX) != true) firstRow.requestFocusWithRetry()
    }
    val day = Instant.ofEpochSecond(game.startEpochSec).atZone(ZoneId.systemDefault()).toLocalDate()
    val subtitle =
        listOfNotNull(
            game.competition.name.takeIf { it.isNotBlank() },
            gameDayLabel(resources, day),
            TimeFormat.formatTime(game.startEpochSec),
        ).joinToString(" · ")
    val first = Modifier.focusRequester(firstRow)

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(CinemaTheme.colors.background)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        onClose()
                        true
                    } else {
                        false
                    }
                }.focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "header") { TvScreenHeader(title = gameTitle(resources, game), subtitle = subtitle, actions = actions) }
        if (channels.isNullOrEmpty()) {
            item(key = "no_channel") {
                TextRow(text = noChannelNote(resources, channels), modifier = first)
            }
        } else {
            item(key = "channels_heading") { TvSportsHeading(stringResource(R.string.sports_channels_heading)) }
            itemsIndexed(channels, key = { _, channel -> channelKey(channel) }) { index, channel ->
                val key = RETURN_SHEET_PREFIX + channelKey(channel)
                ChannelRow(
                    channel = channel,
                    onClick = {
                        returnFocus.leaveFrom(key)
                        onPlay(channel)
                    },
                    modifier = (if (index == 0) first else Modifier).navReturnFocusTarget(returnFocus, key),
                )
            }
        }
    }
}

/** A channel that shows the game: its logo and name, then the programme and when it starts, or how it was found. */
@Composable
private fun ChannelRow(
    channel: GameChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stream = channel.stream
    val resources = LocalResources.current
    val programme = channel.programme
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val note =
        if (programme != null && programme.startSec > nowSec) {
            stringResource(R.string.sports_starts_at, TimeFormat.formatTime(programme.startSec))
        } else {
            channelNote(resources, channel)
        }
    TvCard(
        onClick = onClick,
        look = TvCardLooks.listRow(),
        modifier = modifier.fillMaxWidth().height(TvDimensions.cardHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaThumbnail(
                url = stream.iconUrl,
                fallbackLetter = stream.name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.height(TvDimensions.iconXLarge).aspectRatio(CHANNEL_LOGO_ASPECT),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                CinemaText(text = stream.name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CinemaText(
                    text = listOfNotNull(programme?.title, note).joinToString(" · "),
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A row of text alone, focusable so the sheet always has a focus stop. */
@Composable
private fun TextRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    TvCard(onClick = {}, look = TvCardLooks.listRow(), modifier = modifier.fillMaxWidth()) {
        CinemaText(text = text, tone = TextTone.Secondary, modifier = Modifier.padding(Spacing.md))
    }
}

private fun channelKey(channel: GameChannel): String = "${channel.stream.providerId}:${channel.stream.streamId}"

// Keys of the sheet's rows that open a channel — see rememberNavReturnFocus.
const val RETURN_SHEET_PREFIX = "game-sheet:"
