package org.njarasoa.fijerena.feature.sports

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BadgeKind
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.SectionHeader
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.rememberNowEpochSeconds
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
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.MobileLoadingState
import org.njarasoa.fijerena.ui.components.MobileScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * The phone's Reminders page (the Reminders page plan → Design P): the profile's reminders on this
 * device. In the top bar, Reminder Time (the device's lead, a picker) and Clear All (it asks first).
 * Under it the note "Reminders ring on this device only", then Upcoming (games and guide programmes,
 * the soonest first) and Every Game Of (a team's "every game"). A row's Open shows a game's sheet
 * ([MobileGameSheet]) or a programme's channel ([onPlay], asking first while it has not started);
 * Remove takes the reminder off at once. Stop ends a team's "every game".
 */
@Composable
fun MobileRemindersScreen(
    onBack: () -> Unit,
    onPlay: (Screen.CategoryList) -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory(LocalContext.current)),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leadMinutes by viewModel.leadMinutes.collectAsStateWithLifecycle()
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profile by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
    val nowSec = rememberNowEpochSeconds()
    val bells = rememberGameBells()
    var showLeadPicker by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var askProgramme by remember { mutableStateOf<ReminderItem.Programme?>(null) }
    // The open game's key is saved, so Back from one of its channels finds its sheet open again.
    var openGameKey by rememberSaveable { mutableStateOf<String?>(null) }
    val loaded = state as? RemindersState.Loaded

    val gameGone = stringResource(R.string.reminders_game_gone)
    // The open game, read from its team's schedule. A game no longer there says so and closes.
    val sheetGame by produceState<SportsGame?>(null, openGameKey, loaded != null) {
        value = null
        val key = openGameKey ?: return@produceState
        val current = state as? RemindersState.Loaded ?: return@produceState
        val item = current.upcoming.filterIsInstance<ReminderItem.Game>().firstOrNull { it.key == key }
        val game = item?.let { gameOrNull(viewModel, it) }
        if (game == null) {
            if (item != null) AppNotices.show(gameGone)
            openGameKey = null
        } else {
            value = game
        }
    }
    // Its channels, as a team page finds them; null while they are looked for.
    val sheetChannels by produceState<List<GameChannel>?>(null, sheetGame) {
        value = null
        val game = sheetGame ?: return@produceState
        value = channelsOf(context.applicationContext, game)
    }

    Scaffold(
        topBar = {
            MobileScreenHeader(title = stringResource(R.string.reminders_title), onBack = onBack) {
                IconAction(
                    onClick = { showLeadPicker = true },
                    icon = CinemaIcons.Schedule,
                    label =
                        listOf(
                            stringResource(R.string.reminders_lead_title),
                            reminderLeadLabel(context.resources, leadMinutes),
                        ).joinToString(" · "),
                )
                if (loaded != null && !loaded.isEmpty) {
                    IconAction(
                        onClick = { confirmClear = true },
                        icon = CinemaIcons.DeleteForever,
                        label = stringResource(R.string.reminders_clear_all),
                        tone = TextTone.Danger,
                    )
                }
            }
        },
    ) { padding ->
        RemindersContent(
            state = state,
            leadMinutes = leadMinutes,
            nowSec = nowSec,
            onOpen = { item ->
                when (item) {
                    is ReminderItem.Game -> {
                        openGameKey = item.key
                    }

                    is ReminderItem.Programme -> {
                        // Asks first while it has not started, as Search the Guide's Watch does.
                        if (item.startEpochSec >
                            System.currentTimeMillis() / 1000
                        ) {
                            askProgramme = item
                        } else {
                            onPlay(item.channel.liveRoute())
                        }
                    }
                }
            },
            onRemove = viewModel::remove,
            onStop = viewModel::stop,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    sheetGame?.let { game ->
        MobileGameSheet(
            game = game,
            channels = sheetChannels,
            onPlay = { channel -> onPlay(channel.liveRoute()) },
            bells = bells,
            onDismiss = { openGameKey = null },
            actions = { MobileGameBell(game, bells, nowSec) },
        )
    }

    askProgramme?.let { item ->
        CinemaAlertDialog(
            onDismissRequest = { askProgramme = null },
            title = watchQuestionTitle(item.startEpochSec),
            text = { WatchQuestionBody(item.channel.name, item.channel.providerId) },
            main =
                DialogAction(stringResource(R.string.epg_browser_watch_now_btn)) {
                    // A second tap before the dialog goes does not open the channel two times.
                    if (askProgramme != null) {
                        askProgramme = null
                        onPlay(item.channel.liveRoute())
                    }
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { askProgramme = null },
        )
    }

    if (showLeadPicker) {
        SettingsPickerDialog(
            title = stringResource(R.string.reminders_lead_title),
            options = viewModel.leadChoices.map { reminderLeadLabel(context.resources, it) to it },
            selected = leadMinutes,
            onSelect = { minutes ->
                viewModel.setLeadMinutes(minutes)
                showLeadPicker = false
            },
            onDismiss = { showLeadPicker = false },
        )
    }

    if (confirmClear) {
        CinemaAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = stringResource(R.string.reminders_clear_all_confirm_title),
            text = {
                CinemaText(
                    stringResource(R.string.reminders_clear_all_confirm_message, profile?.name.orEmpty()),
                    role = TextRole.Body,
                    tone = TextTone.Inherit,
                )
            },
            danger =
                DialogAction(stringResource(R.string.reminders_clear_all)) {
                    confirmClear = false
                    viewModel.clearAll()
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { confirmClear = false },
        )
    }
}

/**
 * The page under its top bar: a spinner while it loads, the empty state when there is no reminder,
 * else the note, Upcoming and Every Game Of. [leadMinutes] and [nowSec] give each row's "Rings at".
 */
@Composable
internal fun RemindersContent(
    state: RemindersState,
    leadMinutes: Int,
    nowSec: Long,
    onOpen: (ReminderItem) -> Unit,
    onRemove: (ReminderItem) -> Unit,
    onStop: (TeamRuleItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state !is RemindersState.Loaded -> {
            MobileLoadingState(modifier = modifier)
        }

        state.isEmpty -> {
            MobileEmptyState(message = stringResource(R.string.reminders_empty), icon = CinemaIcons.Bell, modifier = modifier)
        }

        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding =
                    PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.md + LocalDockInset.current),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item(key = "device_only") {
                    CinemaText(
                        text = stringResource(R.string.reminders_device_only),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                    )
                }
                if (state.upcoming.isNotEmpty()) {
                    item(key = "upcoming_heading") {
                        SectionHeader(title = stringResource(R.string.reminders_upcoming), modifier = Modifier.padding(top = Spacing.sm))
                    }
                    items(state.upcoming, key = { it.key }) { item ->
                        ReminderCard(
                            item = item,
                            ringsAtEpochSec = RemindersPage.ringsAtEpochSec(item, leadMinutes, nowSec),
                            onOpen = { onOpen(item) },
                            onRemove = { onRemove(item) },
                        )
                    }
                }
                if (state.teams.isNotEmpty()) {
                    item(key = "teams_heading") {
                        SectionHeader(
                            title = stringResource(R.string.reminders_every_game_of),
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    items(state.teams, key = { "team:${it.source.id}:${it.teamId}" }) { rule ->
                        TeamRuleCard(rule = rule, onStop = { onStop(rule) })
                    }
                }
            }
        }
    }
}

/** A game or programme reminder: its title, its competition or channel, when it starts and when it rings, then Open and Remove. */
@Composable
private fun ReminderCard(
    item: ReminderItem,
    ringsAtEpochSec: Long,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    CinemaCard(modifier = Modifier.fillMaxWidth(), colors = sportsCardColors()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CinemaText(text = reminderTitle(context, item), role = TextRole.Heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // The channel's source ends its line, for a Nerd, as on every row that names a channel.
                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    CinemaText(
                        text = reminderDetail(item),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item is ReminderItem.Programme && LocalNerd.current) {
                        rememberSourceName(item.channel.providerId)?.let {
                            SourceBadge(it, providerId = item.channel.providerId, kind = BadgeKind.OnCard)
                        }
                    }
                }
                CinemaText(
                    text = reminderWhen(context, item.startEpochSec, ringsAtEpochSec),
                    role = TextRole.BodySmall,
                    tone = TextTone.Accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconAction(onClick = onOpen, icon = CinemaIcons.PlayArrow, label = stringResource(R.string.reminders_open))
            IconAction(
                onClick = onRemove,
                icon = CinemaIcons.Delete,
                label = stringResource(R.string.reminders_remove),
                tone = TextTone.Danger,
            )
        }
    }
}

/** A team's "every game": its crest and name, its next game and how many coming games it covers, then Stop. */
@Composable
private fun TeamRuleCard(
    rule: TeamRuleItem,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    CinemaCard(modifier = Modifier.fillMaxWidth(), colors = sportsCardColors()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            CinemaThumbnail(
                url = rule.team?.logoUrl,
                fallbackLetter = rule.teamName.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = null,
                modifier = Modifier.size(MobileDimensions.iconLarge),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CinemaText(text = rule.teamName, role = TextRole.Heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CinemaText(
                    text =
                        listOfNotNull(
                            rule.nextStartEpochSec?.let { stringResource(R.string.reminders_next_game, dayAndTime(context, it)) },
                            pluralStringResource(R.plurals.reminders_team_games, rule.gameCount, rule.gameCount),
                        ).joinToString(" · "),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconAction(
                onClick = onStop,
                icon = CinemaIcons.BellOff,
                label = stringResource(R.string.reminders_stop_team),
                tone = TextTone.Danger,
            )
        }
    }
}

/** The row's key in the list, and the open game's saved key. */
internal val ReminderItem.key: String
    get() =
        when (this) {
            is ReminderItem.Game -> "game:${source.id}:$gameId"
            is ReminderItem.Programme -> "programme:$id"
        }

/** A reminder's title: both teams ("Arsenal v Chelsea"), the one team or event, or the programme. */
internal fun reminderTitle(
    context: Context,
    item: ReminderItem,
): String =
    when (item) {
        is ReminderItem.Game -> item.awayName?.let { context.getString(R.string.sports_game_format, item.homeName, it) } ?: item.homeName
        is ReminderItem.Programme -> item.title
    }

/** A reminder's second line: the game's competition, or the programme's channel without its tags. */
internal fun reminderDetail(item: ReminderItem): String =
    when (item) {
        is ReminderItem.Game -> item.competitionName
        is ReminderItem.Programme -> shownChannelName(item.channel.name)
    }

/** "Today · 15:00 · Rings at 14:50", in the device's 12- or 24-hour clock. */
internal fun reminderWhen(
    context: Context,
    startEpochSec: Long,
    ringsAtEpochSec: Long,
): String =
    listOf(
        dayAndTime(context, startEpochSec),
        context.getString(R.string.reminders_rings_at, DateText.time(context, ringsAtEpochSec)),
    ).joinToString(" · ")

/** "Today · 15:00", "Tomorrow · 15:00", else "Tue 3 Nov · 15:00". */
private fun dayAndTime(
    context: Context,
    epochSec: Long,
): String = DateText.dayAndTime(DateText.shortDayLabel(context.resources, DateText.day(epochSec)), DateText.time(context, epochSec))

/** The live channel route of a programme's [ReminderChannel]: its mini-player, as a guide result opens it. */
private fun ReminderChannel.liveRoute(): Screen.CategoryList =
    Screen.CategoryList(
        contentType = ContentType.LIVE_TV,
        initialCategoryId = categoryId,
        initialStreamId = streamId.toString(),
        providerId = providerId,
    )

/** [item]'s game; null when it is gone or its team's schedule cannot be read. */
private suspend fun gameOrNull(
    viewModel: RemindersViewModel,
    item: ReminderItem.Game,
): SportsGame? =
    try {
        withContext(Dispatchers.IO) { viewModel.gameOf(item) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("MobileReminders", "Game not read: ${e.javaClass.simpleName}")
        null
    }

/** [game]'s channels, as a team page finds them; none when the match fails. */
private suspend fun channelsOf(
    context: Context,
    game: SportsGame,
): List<GameChannel> =
    try {
        withContext(Dispatchers.IO) { AppSportsPageData(context).matcher().match(game) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("MobileReminders", "Match failed: ${e.javaClass.simpleName}")
        emptyList()
    }
