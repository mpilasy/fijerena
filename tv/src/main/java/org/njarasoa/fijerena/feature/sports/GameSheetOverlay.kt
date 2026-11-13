package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.PanelCorner
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleBadges
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.history.rememberSourceName
import org.njarasoa.fijerena.core.ui.sports.GameBells
import org.njarasoa.fijerena.core.ui.sports.WatchQuestionBody
import org.njarasoa.fijerena.core.ui.sports.addGameToCalendar
import org.njarasoa.fijerena.core.ui.sports.asksBeforeWatching
import org.njarasoa.fijerena.core.ui.sports.canRemind
import org.njarasoa.fijerena.core.ui.sports.channelLetter
import org.njarasoa.fijerena.core.ui.sports.channelNote
import org.njarasoa.fijerena.core.ui.sports.clockOr
import org.njarasoa.fijerena.core.ui.sports.gameRowTitle
import org.njarasoa.fijerena.core.ui.sports.noChannelNote
import org.njarasoa.fijerena.core.ui.sports.shownChannelName
import org.njarasoa.fijerena.core.ui.sports.watchQuestionTitle
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.core.ui.utils.canOpenCalendar
import org.njarasoa.fijerena.feature.category.components.RowActionsHint
import org.njarasoa.fijerena.feature.contentselection.components.openActionsOnMenuKey
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.TvActionMenu
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaButtonArea
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * A game's sheet over its page (the sports teams plan, Game sheet): the game, its competition, day
 * and time; then only the profile's channels that show it, in the matcher's order (guide programmes
 * first, then event channels, then networks' channels); "Not found on your channels" when there is
 * none. OK on a channel calls [onPlay] (the channel's preview, as a guide result); before the game
 * starts it asks first: Watch now, Remind Me ([bells]) or Add to calendar, as a guide result that
 * is not on yet. Long-press OK or the Menu key on a channel opens its action menu (Watch now,
 * Remind me, Add to calendar), as on every other channel row; a "⋮" on the focused row is the hint.
 * Back calls [onClose], and the page gives focus back to the game. An in-tree overlay: focus stays
 * in it.
 *
 * [contentPadding]: the safe margins on a page of its own; none inside a screen that has them
 * (Search). [actions]: the header's icon actions (the reminder bell, [TvGameBell]); Up from the
 * first row reaches them. Focus: the first row on opening, and again when the channels come; Back
 * from a channel returns to its row ([returnFocus]).
 */
@Composable
fun GameSheetOverlay(
    game: SportsGame,
    channels: List<GameChannel>?,
    returnFocus: NavReturnFocus,
    onPlay: (GameChannel) -> Unit,
    bells: GameBells,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val resources = LocalResources.current
    val firstRow = remember { FocusRequester() }
    var asking by remember { mutableStateOf<GameChannel?>(null) }
    var menuFor by remember { mutableStateOf<GameChannel?>(null) }
    // The first row on opening, and once the channels replace "Looking for channels…".
    LaunchedEffect(channels != null) {
        if (returnFocus.key?.startsWith(RETURN_SHEET_PREFIX) != true) firstRow.requestFocusWithRetry()
    }
    val day = DateText.day(game.startEpochSec)
    val subtitle =
        listOfNotNull(
            game.competition.name.takeIf { it.isNotBlank() },
            clockOr(game, DateText.dayAndTime(DateText.shortDayLabel(resources, day), TimeFormat.formatTime(game.startEpochSec))),
        ).joinToString(" · ")
    val first = Modifier.focusRequester(firstRow)

    // The ground hides the page. The header stays at the top while the channels scroll under it,
    // and the inner space keeps a focused row (which grows) clear of the rail.
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .panel(PanelFill.Ground)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        onClose()
                        true
                    } else {
                        false
                    }
                }.focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .padding(contentPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = Spacing.md)) {
            TvScreenHeader(
                title = gameRowTitle(resources, game),
                subtitle = subtitle,
                actions = actions,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
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
                                if (asksBeforeWatching(game, System.currentTimeMillis() / 1000)) asking = channel else onPlay(channel)
                            },
                            onOpenActions = {
                                returnFocus.leaveFrom(key)
                                menuFor = channel
                            },
                            modifier = (if (index == 0) first else Modifier).navReturnFocusTarget(returnFocus, key),
                        )
                    }
                }
            }
        }
    }
    menuFor?.let { channel ->
        ChannelActionsMenu(
            game = game,
            channel = channel,
            bells = bells,
            onWatch = { onPlay(channel) },
            onDismiss = { menuFor = null },
        )
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
    val canSetReminder = canRemind(game, System.currentTimeMillis() / 1000) && !bells.reminded.has(game)
    val context = LocalContext.current
    val canCalendar = remember { canOpenCalendar(context) }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = watchQuestionTitle(game.startEpochSec),
        text = { WatchQuestionBody(channel.stream.name, channel.stream.providerId) },
        main = DialogAction(stringResource(R.string.epg_browser_watch_now_btn), onClick = onWatch),
        others =
            listOfNotNull(
                if (canSetReminder) {
                    DialogAction(stringResource(R.string.sports_remind_me)) {
                        bells.toggle(game)
                        onDismiss()
                    }
                } else {
                    null
                },
                if (canCalendar) {
                    DialogAction(stringResource(R.string.epg_browser_add_calendar_btn)) {
                        addGameToCalendar(context, game, channel)
                        onDismiss()
                    }
                } else {
                    null
                },
            ),
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}

/**
 * A channel row's action menu (long-press OK or Menu): Watch now, Remind me (before the game, when
 * it has no reminder) and Add to calendar (on a device with a calendar app), as the question before
 * a game asks.
 */
@Composable
private fun ChannelActionsMenu(
    game: SportsGame,
    channel: GameChannel,
    bells: GameBells,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canSetReminder = canRemind(game, System.currentTimeMillis() / 1000) && !bells.reminded.has(game)
    val context = LocalContext.current
    val canCalendar = remember { canOpenCalendar(context) }
    TvActionMenu(
        title = shownChannelName(channel.stream.name),
        actions =
            listOfNotNull(
                MenuAction(CinemaIcons.PlayArrow, stringResource(R.string.epg_browser_watch_now_btn), onClick = onWatch),
                if (canSetReminder) {
                    MenuAction(CinemaIcons.Bell, stringResource(R.string.sports_remind_me)) { bells.toggle(game) }
                } else {
                    null
                },
                if (canCalendar) {
                    MenuAction(CinemaIcons.DateRange, stringResource(R.string.epg_browser_add_calendar_btn)) {
                        addGameToCalendar(context, game, channel)
                    }
                } else {
                    null
                },
            ),
        onDismiss = onDismiss,
    )
}

/** A channel that shows the game: its logo and name, then the programme and when it starts, or how it was found. */
@Composable
private fun ChannelRow(
    channel: GameChannel,
    onClick: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
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
        onLongClick = onOpenActions,
        look = TvCardLooks.listRow(),
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDimensions.cardHeight)
                .onFocusChanged { isFocused = it.isFocused }
                .openActionsOnMenuKey(onOpenActions),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaThumbnail(
                url = stream.iconUrl,
                fallbackLetter = channelLetter(stream.name),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.height(TvDimensions.iconXLarge).aspectRatio(CHANNEL_LOGO_ASPECT),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                // As in the Live TV list: the name without its tags, the tags as badges before the programme.
                val parsed = remember(stream.name) { parseDisplayTitle(stream.name) }
                // The source ends the name's line, in the same place on every row, as on every list row.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    CinemaText(
                        text = shownChannelName(stream.name),
                        role = TextRole.Heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    rememberSourceName(stream.providerId)?.let { SourceBadge(it, providerId = stream.providerId) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    TitleBadges(parsed, maxLines = 1)
                    CinemaText(
                        text = listOfNotNull(programme?.title, note).joinToString(" · "),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            // The row-actions hint, as on the other channel rows: a glyph, not a focus stop.
            if (isFocused) RowActionsHint()
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
