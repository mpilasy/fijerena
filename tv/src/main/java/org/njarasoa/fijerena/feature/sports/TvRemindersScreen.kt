package org.njarasoa.fijerena.feature.sports

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.SectionHeader
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.history.rememberSourceName
import org.njarasoa.fijerena.core.ui.sports.AppSportsPageData
import org.njarasoa.fijerena.core.ui.sports.ReminderItem
import org.njarasoa.fijerena.core.ui.sports.RemindersPage
import org.njarasoa.fijerena.core.ui.sports.RemindersState
import org.njarasoa.fijerena.core.ui.sports.RemindersViewModel
import org.njarasoa.fijerena.core.ui.sports.TeamRuleItem
import org.njarasoa.fijerena.core.ui.sports.WatchQuestionBody
import org.njarasoa.fijerena.core.ui.sports.liveRoute
import org.njarasoa.fijerena.core.ui.sports.rememberGameBells
import org.njarasoa.fijerena.core.ui.sports.reminderLeadLabel
import org.njarasoa.fijerena.core.ui.sports.shownChannelName
import org.njarasoa.fijerena.core.ui.sports.watchQuestionTitle
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvPickerDialog
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.LocalTvNavRail
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The Reminders page on TV (the Reminders page plan → Design T): the profile's reminders on this
 * device. Each write goes through [RemindersViewModel], so the alarms follow. A game's Open finds
 * the game again ([RemindersViewModel.gameOf]) and its channels, then opens its sheet; a channel
 * there, or a programme's Open, calls [onPlay].
 */
@Composable
fun TvRemindersScreen(
    onPlay: (Screen.CategoryList) -> Unit,
    onBack: () -> Unit,
    viewModel: RemindersViewModel = viewModel(key = "reminders", factory = RemindersViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leadMinutes by viewModel.leadMinutes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pageData = remember(context) { AppSportsPageData(context.applicationContext) }
    TvRemindersContent(
        state = state,
        leadMinutes = leadMinutes,
        leadChoices = viewModel.leadChoices,
        actions =
            RemindersActions(
                setLeadMinutes = viewModel::setLeadMinutes,
                remove = viewModel::remove,
                stop = viewModel::stop,
                clearAll = viewModel::clearAll,
                findGame = { item -> orNullOnFailure { viewModel.gameOf(item) } },
                findChannels = { game ->
                    orNullOnFailure { withTimeoutOrNull(CHANNELS_TIMEOUT_MS) { pageData.matcher().match(game) } }.orEmpty()
                },
                play = onPlay,
                back = onBack,
            ),
    )
}

/** What the page's controls do. The tests give their own. */
internal class RemindersActions(
    val setLeadMinutes: (Int) -> Unit,
    val remove: (ReminderItem) -> Unit,
    val stop: (TeamRuleItem) -> Unit,
    val clearAll: () -> Unit,
    /** The game of a game reminder; null when it is gone from its schedule or cannot be read. */
    val findGame: suspend (ReminderItem.Game) -> SportsGame?,
    /** The profile's channels that show the game, best first; none when none is found. */
    val findChannels: suspend (SportsGame) -> List<GameChannel>,
    val play: (Screen.CategoryList) -> Unit,
    val back: () -> Unit,
)

/**
 * The page: the header (the title, "Reminders ring on this device only", Reminder Time and Clear
 * All), then "Upcoming" (games and programmes, the soonest first) and "Every Game Of" (the team
 * rules), or the shared empty state.
 *
 * Focus (RP-D10): the first row on entry (Upcoming, else Every Game Of), Reminder Time when there is
 * none (never Clear All). Right walks a row's icon actions; Up from the first row goes to Reminder
 * Time, Down from the header's actions to the first row. When the focused row goes (Remove, Stop,
 * or a reminder that rang), focus goes to the next row, else the previous one, else Reminder Time.
 * Back from a channel or a game sheet returns to the control that opened it; Back on the page
 * calls [RemindersActions.back].
 */
@Composable
internal fun TvRemindersContent(
    state: RemindersState,
    leadMinutes: Int,
    leadChoices: List<Int>,
    actions: RemindersActions,
) {
    val resources = LocalResources.current
    val bells = rememberGameBells()
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val listState = rememberLazyListState()
    val returnFocus = rememberNavReturnFocus()
    val leadFocus = remember { FocusRequester() }
    val loaded = state as? RemindersState.Loaded
    val rowKeys = remember(loaded) { loaded?.let { it.upcoming.map(::rowKey) + it.teams.map(::rowKey) }.orEmpty() }
    val firstRowKey = rowKeys.firstOrNull()
    val profileName = LocalTvNavRail.current?.profileName.orEmpty()

    var pickingLead by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<Pair<ReminderItem.Programme, String>?>(null) }
    // The game whose sheet is open (its row's key) and the control that opened it: saved, so Back
    // from a channel finds the sheet open again.
    var openGameKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetGame by remember { mutableStateOf<SportsGame?>(null) }
    var sheetChannels by remember { mutableStateOf<List<GameChannel>?>(null) }
    var closedSheet by remember { mutableStateOf<String?>(null) }
    // The row (or HEADER) that had focus last: when that row goes, focus goes to its neighbour.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var shownKeys by remember { mutableStateOf(rowKeys) }
    var entered by rememberSaveable { mutableStateOf(false) }
    val currentLoaded by rememberUpdatedState(loaded)

    fun requesterOf(key: String?): FocusRequester = key?.let { returnFocus.requesterOf(it) } ?: leadFocus

    // Before a row goes (Remove, Stop), focus moves on to its next row, else the previous one, else
    // Reminder Time, so the focused control never goes under focus.
    fun leaveRow(key: String) {
        val index = rowKeys.indexOf(key)
        val target = rowKeys.getOrNull(index + 1) ?: rowKeys.getOrNull(index - 1)
        focusedKey = target ?: HEADER
        requesterOf(target).requestFocus()
    }

    fun play(
        item: ReminderItem.Programme,
        from: String,
    ) {
        returnFocus.leaveFrom(from, listState)
        actions.play(item.channel.route())
    }

    val open: (ReminderItem, String) -> Unit = { item, from ->
        when (item) {
            is ReminderItem.Game -> {
                openedFrom = from
                openGameKey = rowKey(item)
            }

            is ReminderItem.Programme -> {
                // Not on yet: ask first, as Search the Guide's Watch does.
                if (item.startEpochSec > System.currentTimeMillis() / 1000) asking = item to from else play(item, from)
            }
        }
    }

    // The first row once the reminders are read; Reminder Time when there is none.
    LaunchedEffect(loaded != null) {
        if (loaded == null || entered || returnFocus.isReturn) return@LaunchedEffect
        entered = true
        requesterOf(firstRowKey).requestFocusWithRetry(fallback = leadFocus)
    }
    NavReturnFocusEffect(
        returnFocus,
        listState = listState,
        fallback = leadFocus,
        // The rows come with the reminders; a sheet's channel with the sheet's channels.
        prepare = { key ->
            snapshotFlowFirst { currentLoaded != null }
            if (key.startsWith(RETURN_SHEET_PREFIX)) withTimeoutOrNull(CHANNELS_TIMEOUT_MS) { snapshotFlowFirst { sheetChannels != null } }
        },
    )
    // A row that had focus is gone: its next row, else the previous one, else Reminder Time. Not
    // while a sheet is open over the page (its bell can clear the reminder): the sheet keeps focus.
    // Read in composition: once the row's node goes, Compose moves focus on by itself (to the header).
    val goneFocused = remember(rowKeys) { focusedKey?.takeIf { it != HEADER && it !in rowKeys } }
    LaunchedEffect(rowKeys) {
        val before = shownKeys
        shownKeys = rowKeys
        val gone = goneFocused
        if (gone == null || gone !in before || openGameKey != null) return@LaunchedEffect
        val index = before.indexOf(gone)
        val target = before.drop(index + 1).firstOrNull { it in rowKeys } ?: before.take(index).lastOrNull { it in rowKeys }
        focusedKey = target ?: HEADER
        requesterOf(target).requestFocusWithRetry(fallback = leadFocus)
    }
    // Clear All goes with the last reminder: focus moves on to Reminder Time.
    LaunchedEffect(loaded?.isEmpty) {
        if (loaded?.isEmpty == true && entered && openGameKey == null) leadFocus.requestFocusWithRetry()
    }
    // Opening a game: the game read again, then its channels; a game gone from its schedule says so.
    LaunchedEffect(openGameKey, loaded != null) {
        val key = openGameKey
        if (key == null || loaded == null) return@LaunchedEffect
        // The reminder went while the page was away (it rang): no sheet to open again.
        val item = loaded.upcoming.filterIsInstance<ReminderItem.Game>().firstOrNull { rowKey(it) == key }
        val game = item?.let { actions.findGame(it) }
        if (item == null) {
            openGameKey = null
        } else if (game == null) {
            AppNotices.show(resources.getString(R.string.reminders_game_gone))
            openGameKey = null
        } else {
            sheetGame = game
            sheetChannels = actions.findChannels(game)
        }
    }
    LaunchedEffect(closedSheet) {
        closedSheet?.let { requesterOf(it).requestFocusWithRetry(fallback = requesterOf(firstRowKey)) }
        closedSheet = null
    }

    Box(modifier = Modifier.fillMaxSize().panel(PanelFill.Ground)) {
        LazyColumn(
            state = listState,
            // Back on the root, before the focused control: see NAVIGATION_GUIDE → TV Back on Detail Screens.
            modifier =
                Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        actions.back()
                        true
                    } else {
                        false
                    }
                },
            contentPadding = PaddingValues(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Down from the header's actions: the first row, not what lies under the actions' end.
            val downToRows = Modifier.focusProperties { if (firstRowKey != null) down = requesterOf(firstRowKey) }
            item(key = "header") {
                TvScreenHeader(
                    title = stringResource(R.string.reminders_title),
                    subtitle = stringResource(R.string.reminders_device_only),
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedKey = HEADER },
                ) {
                    TvIconAction(
                        onClick = { pickingLead = true },
                        icon = CinemaIcons.Schedule,
                        label = reminderLeadLabel(resources, leadMinutes),
                        modifier = Modifier.focusRequester(leadFocus).leftToRail().then(downToRows),
                    )
                    if (loaded?.isEmpty == false) {
                        TvIconAction(
                            onClick = { confirmingClear = true },
                            icon = CinemaIcons.DeleteForever,
                            label = stringResource(R.string.reminders_clear_all),
                            danger = true,
                            modifier = downToRows.focusProperties { right = FocusRequester.Cancel },
                        )
                    }
                }
            }
            when {
                loaded == null -> {
                    item(key = "loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                            CinemaSpinner()
                        }
                    }
                }

                loaded.isEmpty -> {
                    item(key = "empty") {
                        TvEmptyState(message = stringResource(R.string.reminders_empty), icon = CinemaIcons.Bell, takeFocus = false)
                    }
                }

                else -> {
                    // Up from the first row: Reminder Time, never Clear All above the row's end.
                    fun rowFocus(key: String) =
                        Modifier
                            .navReturnFocusTarget(returnFocus, key)
                            .focusProperties { if (key.substringBefore(ACTION_SEPARATOR) == firstRowKey) up = leadFocus }

                    if (loaded.upcoming.isNotEmpty()) {
                        item(key = "upcoming_heading") { SectionHeader(title = stringResource(R.string.reminders_upcoming)) }
                        itemsIndexed(loaded.upcoming, key = { _, item -> rowKey(item) }) { _, item ->
                            val key = rowKey(item)
                            ReminderRow(
                                item = item,
                                leadMinutes = leadMinutes,
                                nowSec = nowSec,
                                onOpen = { from -> open(item, from) },
                                onRemove = {
                                    leaveRow(key)
                                    actions.remove(item)
                                },
                                rowFocus = ::rowFocus,
                                rowKey = key,
                                modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedKey = key },
                            )
                        }
                    }
                    if (loaded.teams.isNotEmpty()) {
                        item(key = "teams_heading") {
                            SectionHeader(
                                title = stringResource(R.string.reminders_every_game_of),
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }
                        itemsIndexed(loaded.teams, key = { _, rule -> rowKey(rule) }) { _, rule ->
                            val key = rowKey(rule)
                            TeamRuleRow(
                                rule = rule,
                                onStop = {
                                    leaveRow(key)
                                    actions.stop(rule)
                                },
                                rowFocus = ::rowFocus,
                                rowKey = key,
                                modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedKey = key },
                            )
                        }
                    }
                }
            }
        }
        val game = sheetGame
        if (openGameKey != null && game != null) {
            GameSheetOverlay(
                game = game,
                channels = sheetChannels,
                returnFocus = returnFocus,
                onPlay = { channel -> actions.play(channel.liveRoute()) },
                bells = bells,
                onClose = {
                    closedSheet = openedFrom
                    openGameKey = null
                    sheetGame = null
                    sheetChannels = null
                },
                actions = { TvGameBell(game, bells, nowSec) },
            )
        }
    }

    if (pickingLead) {
        TvPickerDialog(
            title = stringResource(R.string.reminders_lead_title),
            options = leadChoices.map { reminderLeadLabel(resources, it) to it },
            selected = leadMinutes,
            onSelect = actions.setLeadMinutes,
            onDismiss = { pickingLead = false },
        )
    }
    if (confirmingClear) {
        CinemaAlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = stringResource(R.string.reminders_clear_all_confirm_title),
            text = {
                CinemaText(
                    text = stringResource(R.string.reminders_clear_all_confirm_message, profileName),
                    tone = TextTone.Inherit,
                )
            },
            danger =
                DialogAction(stringResource(R.string.reminders_clear_all)) {
                    confirmingClear = false
                    actions.clearAll()
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { confirmingClear = false },
        )
    }
    asking?.let { (item, from) ->
        CinemaAlertDialog(
            onDismissRequest = { asking = null },
            title = watchQuestionTitle(item.startEpochSec),
            text = { WatchQuestionBody(item.channel.name, item.channel.providerId) },
            main =
                DialogAction(stringResource(R.string.epg_browser_watch_now_btn)) {
                    // A second click before the dialog leaves does not open the channel twice.
                    if (asking != null) {
                        asking = null
                        play(item, from)
                    }
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { asking = null },
        )
    }
}

/**
 * A game's or a programme's reminder: the title (both teams, or the programme), then the competition
 * or the channel, the day and time and when it rings; the row (OK opens it), then Open and Remove in
 * fixed slots to its right. [rowFocus] gives each control its Back target and focus moves by key.
 */
@Composable
private fun ReminderRow(
    item: ReminderItem,
    leadMinutes: Int,
    nowSec: Long,
    onOpen: (from: String) -> Unit,
    onRemove: () -> Unit,
    rowFocus: (String) -> Modifier,
    rowKey: String,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val ringsAt = RemindersPage.ringsAtEpochSec(item, leadMinutes, nowSec)
    val details =
        listOf(
            when (item) {
                is ReminderItem.Game -> item.competitionName
                is ReminderItem.Programme -> shownChannelName(item.channel.name)
            },
            dayAndTime(item.startEpochSec),
            stringResource(R.string.reminders_rings_at, TimeFormat.formatTime(ringsAt)),
        ).filter { it.isNotBlank() }.joinToString(" · ")
    RowWithActions(
        onClick = { onOpen(rowKey) },
        rowModifier = rowFocus(rowKey),
        modifier = modifier,
        content = {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    CinemaText(
                        text =
                            when (item) {
                                is ReminderItem.Game -> gameName(resources, item)
                                is ReminderItem.Programme -> item.title
                            },
                        role = TextRole.Heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // A programme's channel source ends the title line, for a Nerd, as on every list row.
                    if (item is ReminderItem.Programme && LocalNerd.current) {
                        rememberSourceName(item.channel.providerId)?.let { SourceBadge(it, providerId = item.channel.providerId) }
                    }
                }
                CinemaText(text = details, role = TextRole.Body, tone = TextTone.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    ) {
        TvIconAction(
            onClick = { onOpen(rowKey + ACTION_SEPARATOR + OPEN) },
            icon = CinemaIcons.KeyboardArrowRight,
            label = stringResource(R.string.reminders_open),
            modifier = rowFocus(rowKey + ACTION_SEPARATOR + OPEN),
        )
        TvIconAction(
            onClick = onRemove,
            icon = CinemaIcons.Delete,
            label = stringResource(R.string.reminders_remove),
            danger = true,
            modifier = rowFocus(rowKey + ACTION_SEPARATOR + REMOVE).focusProperties { right = FocusRequester.Cancel },
        )
    }
}

/** A team's "every game": its crest and name, its next game and how many coming games it has; Stop to its right. */
@Composable
private fun TeamRuleRow(
    rule: TeamRuleItem,
    onStop: () -> Unit,
    rowFocus: (String) -> Modifier,
    rowKey: String,
    modifier: Modifier = Modifier,
) {
    val details =
        listOfNotNull(
            rule.leagueName.takeIf { it.isNotBlank() },
            rule.nextStartEpochSec?.let { stringResource(R.string.reminders_next_game, dayAndTime(it)) },
            pluralStringResource(R.plurals.reminders_team_games, rule.gameCount, rule.gameCount),
        ).joinToString(" · ")
    // The row shows the rule; Stop is its one action.
    RowWithActions(
        onClick = {},
        rowModifier = rowFocus(rowKey),
        modifier = modifier,
        content = {
            CinemaThumbnail(
                url = rule.team?.logoUrl,
                fallbackLetter = rule.teamName.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                CinemaText(text = rule.teamName, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CinemaText(text = details, role = TextRole.Body, tone = TextTone.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    ) {
        TvIconAction(
            onClick = onStop,
            icon = CinemaIcons.BellOff,
            label = stringResource(R.string.reminders_stop_team),
            danger = true,
            modifier = rowFocus(rowKey + ACTION_SEPARATOR + STOP).focusProperties { right = FocusRequester.Cancel },
        )
    }
}

/** A list row (one focus stop, Left opens the rail) with its icon actions in slots to its right. */
@Composable
private fun RowWithActions(
    onClick: () -> Unit,
    rowModifier: Modifier,
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvCard(
            onClick = onClick,
            look = TvCardLooks.listRow(),
            modifier = rowModifier.leftToRail().weight(1f).fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
        actions()
    }
}

/** "Arsenal v Chelsea", or the one name of an event with no second team. */
private fun gameName(
    resources: Resources,
    item: ReminderItem.Game,
): String = item.awayName?.let { resources.getString(R.string.sports_game_format, item.homeName, it) } ?: item.homeName

/** "Today · 20:00", in the app language and the device's clock. */
@Composable
private fun dayAndTime(epochSec: Long): String =
    DateText.dayAndTime(DateText.shortDayLabel(LocalResources.current, DateText.day(epochSec)), TimeFormat.formatTime(epochSec))

/** The programme's channel, opened as a guide result opens it: the preview on that channel. */
private fun org.njarasoa.fijerena.core.network.sports.ReminderChannel.route(): Screen.CategoryList =
    Screen.CategoryList(
        contentType = ContentType.LIVE_TV,
        initialCategoryId = categoryId,
        initialStreamId = streamId.toString(),
        providerId = providerId,
    )

private fun rowKey(item: ReminderItem): String =
    when (item) {
        is ReminderItem.Game -> "reminder:game:${item.source.id}/${item.gameId}"
        is ReminderItem.Programme -> "reminder:programme:${item.id}"
    }

private fun rowKey(rule: TeamRuleItem): String = "reminder:team:${rule.source.id}:${rule.teamId}"

/** [block]'s value; null when it fails (no network, a source error), so Open says the game is not found. */
private suspend fun <T> orNullOnFailure(block: suspend () -> T?): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("TvRemindersScreen", "Open failed: ${e.javaClass.simpleName}")
        null
    }

private suspend fun snapshotFlowFirst(condition: () -> Boolean) {
    androidx.compose.runtime
        .snapshotFlow(condition)
        .first { it }
}

// The longest Open waits for a game's channels before the sheet says "Not found on your channels".
private const val CHANNELS_TIMEOUT_MS = 20_000L

// Focus keys: a row's actions are its key, the separator and the action.
private const val HEADER = "reminders:header"
private const val ACTION_SEPARATOR = "#"
private const val OPEN = "open"
private const val REMOVE = "remove"
private const val STOP = "stop"
