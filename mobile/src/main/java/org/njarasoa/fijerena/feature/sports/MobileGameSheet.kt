package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleBadges
import org.njarasoa.fijerena.core.ui.sports.GameBells
import org.njarasoa.fijerena.core.ui.sports.addGameToCalendar
import org.njarasoa.fijerena.core.ui.sports.asksBeforeWatching
import org.njarasoa.fijerena.core.ui.sports.canRemind
import org.njarasoa.fijerena.core.ui.sports.channelLetter
import org.njarasoa.fijerena.core.ui.sports.channelNote
import org.njarasoa.fijerena.core.ui.sports.gameDayLabel
import org.njarasoa.fijerena.core.ui.sports.gameTitle
import org.njarasoa.fijerena.core.ui.sports.noChannelNote
import org.njarasoa.fijerena.core.ui.sports.shownChannelName
import org.njarasoa.fijerena.core.ui.sports.watchGameQuestion
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.utils.canOpenCalendar
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.time.Instant
import java.time.ZoneId

/**
 * A game's sheet over its page (the sports teams plan → Game sheet): the game, its competition, day
 * and time; then only the profile's channels that show it, in the matcher's order; "Not found on
 * your channels" when there is none. A tap on a channel calls [onPlay] (the channel's mini-player,
 * as a guide result); the sheet stays, for Back from the channel. Before the game starts, the tap
 * asks first: Watch now, Remind Me ([bells]) or Add to calendar, as a guide result that is not on
 * yet. Dismissing it calls [onDismiss].
 *
 * [actions]: icon actions beside the title (the reminder bell, [MobileGameBell]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileGameSheet(
    game: SportsGame,
    channels: List<GameChannel>?,
    onPlay: (GameChannel) -> Unit,
    bells: GameBells,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val resources = LocalResources.current
    var asking by remember { mutableStateOf<GameChannel?>(null) }
    val day = Instant.ofEpochSecond(game.startEpochSec).atZone(ZoneId.systemDefault()).toLocalDate()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CinemaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                CinemaText(text = gameTitle(resources, game), role = TextRole.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CinemaText(
                    text =
                        listOfNotNull(
                            game.competition.name.takeIf { it.isNotBlank() },
                            gameDayLabel(resources, day),
                            TimeFormat.formatTime(game.startEpochSec),
                        ).joinToString(" · "),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                )
            }
            actions()
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = CinemaSpacing.sm, bottom = CinemaSpacing.md)) {
            if (channels.isNullOrEmpty()) {
                item(key = "no_channel") { SheetText(noChannelNote(resources, channels)) }
            } else {
                item(key = "channels_heading") { SheetHeading(stringResource(R.string.sports_channels_heading)) }
                items(channels, key = { "${it.stream.providerId}:${it.stream.streamId}" }) { channel ->
                    ChannelLine(channel = channel, onClick = {
                        if (asksBeforeWatching(game, System.currentTimeMillis() / 1000)) asking = channel else onPlay(channel)
                    })
                }
            }
        }
    }
    asking?.let { channel ->
        WatchOrRemindDialog(
            game = game,
            channel = channel,
            bells = bells,
            onWatch = {
                asking = null
                onPlay(channel)
            },
            onDismiss = { asking = null },
        )
    }
}

/** Watch a game's channel before the game starts, set the game's reminder (when it has none), or add it to the calendar (when the device has a calendar app). */
@Composable
private fun WatchOrRemindDialog(
    game: SportsGame,
    channel: GameChannel,
    bells: GameBells,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val canSetReminder = canRemind(game, System.currentTimeMillis() / 1000) && !bells.reminded.has(game)
    val context = LocalContext.current
    val canCalendar = remember { canOpenCalendar(context) }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(stringResource(R.string.epg_browser_watch_confirm_title), role = TextRole.Title, tone = TextTone.Inherit)
        },
        text = { CinemaText(watchGameQuestion(resources, game, channel), role = TextRole.Body, tone = TextTone.Inherit) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                CinemaDialogActionButton(onClick = onWatch, kind = ButtonKind.Other) {
                    CinemaText(stringResource(R.string.epg_browser_watch_now_btn), role = TextRole.Label, tone = TextTone.Inherit)
                }
                if (canSetReminder) {
                    CinemaDialogActionButton(onClick = {
                        bells.toggle(game)
                        onDismiss()
                    }, kind = ButtonKind.Other) {
                        CinemaText(stringResource(R.string.sports_remind_me), role = TextRole.Label, tone = TextTone.Inherit)
                    }
                }
                if (canCalendar) {
                    CinemaDialogActionButton(onClick = {
                        addGameToCalendar(context, game, channel)
                        onDismiss()
                    }, kind = ButtonKind.Other) {
                        CinemaText(stringResource(R.string.epg_browser_add_calendar_btn), role = TextRole.Label, tone = TextTone.Inherit)
                    }
                }
            }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}

@Composable
private fun SheetHeading(text: String) {
    CinemaText(
        text = text,
        role = TextRole.Label,
        tone = TextTone.Secondary,
        modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xs),
    )
}

@Composable
private fun SheetText(text: String) {
    CinemaText(
        text = text,
        role = TextRole.Body,
        tone = TextTone.Secondary,
        modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
    )
}

/** A channel that shows the game: its logo and name, then the programme and when it starts, or how it was found. */
@Composable
private fun ChannelLine(
    channel: GameChannel,
    onClick: () -> Unit,
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CinemaThumbnail(
            url = stream.iconUrl,
            fallbackLetter = channelLetter(stream.name),
            contentType = ThumbnailContentType.LIVE_TV,
            contentDescription = null,
            modifier = Modifier.height(MobileDimensions.iconXLarge).aspectRatio(CHANNEL_LOGO_ASPECT),
        )
        Column(modifier = Modifier.weight(1f)) {
            // As in the Live TV list: the name without its tags, the tags as badges before the programme.
            val parsed = remember(stream.name) { parseDisplayTitle(stream.name) }
            CinemaText(text = shownChannelName(stream.name), role = TextRole.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                TitleBadges(parsed, maxLines = 1)
                CinemaText(
                    text = listOfNotNull(programme?.title, note).joinToString(" · "),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}
