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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.njarasoa.fijerena.core.ui.sports.BroadcasterGroup
import org.njarasoa.fijerena.core.ui.sports.GameSheet
import org.njarasoa.fijerena.core.ui.sports.broadcasterCountries
import org.njarasoa.fijerena.core.ui.sports.channelNote
import org.njarasoa.fijerena.core.ui.sports.gameDayLabel
import org.njarasoa.fijerena.core.ui.sports.gameTitle
import org.njarasoa.fijerena.core.ui.sports.hasMoreCountries
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.time.Instant
import java.time.ZoneId

/**
 * A game's sheet over its page (the sports teams plan → Game sheet): the game, its competition, day
 * and time; every channel of the profile that shows it, in the matcher's order; then the
 * broadcasters the profile has no channel for, one row per broadcaster with its countries
 * ("SuperSport · 48 countries"; a tap shows them all). "TV not announced yet" when nothing is
 * known. A tap on a channel calls [onPlay] (the channel's mini-player, as a guide result); the
 * sheet stays, for Back from the channel. Dismissing it calls [onDismiss].
 *
 * [actions]: icon actions beside the title (the reminder bell, [MobileGameBell]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileGameSheet(
    game: SportsGame,
    channels: List<GameChannel>?,
    onPlay: (GameChannel) -> Unit,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val resources = LocalResources.current
    val sheet = remember(channels) { channels?.let(GameSheet::of) }
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
            when {
                sheet == null -> {
                    item(key = "matching") { SheetText(stringResource(R.string.sports_matching)) }
                }

                sheet.notAnnounced -> {
                    item(key = "not_announced") { SheetText(stringResource(R.string.sports_not_announced)) }
                }

                else -> {
                    if (sheet.channels.isNotEmpty()) {
                        item(key = "channels_heading") { SheetHeading(stringResource(R.string.sports_channels_heading)) }
                        items(sheet.channels, key = { "${it.stream?.providerId}:${it.stream?.streamId}" }) { channel ->
                            ChannelLine(channel = channel, onClick = { onPlay(channel) })
                        }
                    }
                    if (sheet.broadcasters.isNotEmpty()) {
                        item(key = "broadcasters_heading") { SheetHeading(stringResource(R.string.sports_broadcasters_heading)) }
                        items(sheet.broadcasters, key = { "broadcaster:${it.name}" }) { group -> BroadcasterLine(group) }
                    }
                }
            }
        }
    }
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
    val stream = channel.stream ?: return
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
            fallbackLetter = stream.name.firstOrNull(Char::isLetterOrDigit),
            contentType = ThumbnailContentType.LIVE_TV,
            contentDescription = null,
            modifier = Modifier.height(MobileDimensions.iconXLarge).aspectRatio(CHANNEL_LOGO_ASPECT),
        )
        Column(modifier = Modifier.weight(1f)) {
            CinemaText(text = stream.name, role = TextRole.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CinemaText(
                text = listOfNotNull(programme?.title, note).joinToString(" · "),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A broadcaster with no channel of the profile: its name and countries; a tap shows every country of a long list. */
@Composable
private fun BroadcasterLine(group: BroadcasterGroup) {
    val resources = LocalResources.current
    var expanded by rememberSaveable(group.name) { mutableStateOf(false) }
    val more = hasMoreCountries(group)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (more) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
    ) {
        CinemaText(text = group.name, role = TextRole.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val countries = broadcasterCountries(resources, group, expanded)
        if (countries.isNotEmpty()) {
            CinemaText(text = countries, role = TextRole.BodySmall, tone = TextTone.Secondary)
        }
    }
}
