package org.njarasoa.fijerena.feature.epg

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.GuideSource
import org.njarasoa.fijerena.core.player.domain.CatchupAvailability
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.rememberNowEpochSecondsState
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.guideListingsEnded
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date tabs: today and the six days after it (the guide loads any day; a week is what fits a thumb). */
private const val DAY_TAB_COUNT = 7

/** Past date tabs go back as far as the longest channel archive, a week at most (catch-up). */
private const val MAX_PAST_DAY_TABS = 7

/** A programme tapped in the grid, shown in the details sheet. */
private data class GuideSelection(
    val program: EpgProgram,
    val channel: MediaItem,
)

/**
 * The phone's TV Guide (UX overhaul plan Part III, GD3): "TV Guide · <category>" with the GD1
 * status line beneath it, date tabs (Today / Tomorrow / weekdays) and a "Now" chip, then the
 * [MobileGuideGrid] time grid. Tapping a programme opens its details sheet ("Watch channel" docks the
 * channel; "Watch from start" replays it from the channel's archive when it holds it, catch-up, and
 * past days get date tabs back to the longest archive); tapping a channel tunes it. Search opens "Search the guide" (the EPG Browser) on this
 * guide's channels — the grid has no search of its own (G-9, GD5). [focusChannelId] scrolls the
 * grid to that channel's row on first open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgGuideScreen(
    categoryId: String,
    categoryName: String,
    onProgramSelected: (program: EpgProgram, channel: MediaItem) -> Unit,
    onWatchFromStart: (program: EpgProgram, channel: MediaItem) -> Unit,
    onChannelSelected: (channel: MediaItem) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    focusChannelId: String? = null,
    // The source whose channels the guide shows (the route's).
    providerId: Long = 0L,
    viewModel: EpgViewModel =
        viewModel(
            factory =
                EpgViewModelFactory(
                    context = LocalContext.current.applicationContext,
                    categoryId = categoryId,
                    providerId = providerId,
                ),
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    // Hoisted here so a day change (the grid leaves composition while it loads) keeps the time of
    // day and the row the user was on; both are saveable, so returning from the dock keeps them too.
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()
    // First open puts now a third of the way in (G-8); saveable so a return doesn't jump again.
    var nowScroll by rememberSaveable { mutableStateOf<NowScroll?>(NowScroll.JUMP) }
    var requestedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selection by remember { mutableStateOf<GuideSelection?>(null) }
    val today = remember { LocalDate.now() }
    // First open on a given channel (GD5): its row is scrolled into view once, when the grid is ready.
    var entryRowPending by rememberSaveable { mutableStateOf(focusChannelId != null) }

    val state = uiState
    // Looked up with the channels, so known on a day without listings too (a stale guide).
    val catchupDays by viewModel.archiveDays.collectAsStateWithLifecycle()
    val pastDayTabs = remember(catchupDays) { (catchupDays.values.maxOrNull() ?: 0).coerceAtMost(MAX_PAST_DAY_TABS) }
    val shownDate =
        when (state) {
            is EpgViewModel.UiState.Ready -> state.selectedDate
            is EpgViewModel.UiState.NoListings -> state.selectedDate
            else -> requestedDate
        }
    val onSelectDate = { date: LocalDate ->
        requestedDate = date
        if (date != shownDate) viewModel.loadEpgData(date)
    }
    val onNow = {
        val now = LocalDate.now()
        nowScroll = if (shownDate == now && state is EpgViewModel.UiState.Ready) NowScroll.ANIMATE else NowScroll.JUMP
        if (shownDate != now) {
            requestedDate = now
            viewModel.jumpToNow()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        CinemaText(
                            text = stringResource(R.string.epg_guide_header_title_format, categoryName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            role = TextRole.Title,
                            tone = TextTone.Inherit,
                        )
                        if (state is EpgViewModel.UiState.Ready) {
                            CinemaText(
                                text = statusLine(state),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back))
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(CinemaIcons.Search, stringResource(R.string.common_search))
                    }
                    IconButton(
                        onClick = { viewModel.forceRefresh() },
                        enabled = !isRefreshing,
                    ) {
                        if (isRefreshing) {
                            CinemaSpinner(modifier = Modifier.size(MobileDimensions.progressIndicatorSmall))
                        } else {
                            Icon(CinemaIcons.Refresh, stringResource(R.string.provider_refresh_button))
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (state is EpgViewModel.UiState.Ready && appSettings.isDevMode) {
                CinemaText(
                    text = state.devStats,
                    role = TextRole.Caption,
                    tone = TextTone.Tertiary,
                    modifier = Modifier.padding(horizontal = CinemaSpacing.md),
                )
            }
            // The tabs stay while a day loads and when it has no listings: another day may.
            if (state is EpgViewModel.UiState.Loading ||
                state is EpgViewModel.UiState.Ready ||
                state is EpgViewModel.UiState.NoListings
            ) {
                DateTabs(
                    selectedDate = shownDate,
                    today = today,
                    pastDays = pastDayTabs,
                    onSelectDate = onSelectDate,
                    onNow = onNow,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (state) {
                    is EpgViewModel.UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CinemaSpinner()
                                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                                CinemaText(
                                    text = stringResource(R.string.epg_loading_guide),
                                    role = TextRole.Body,
                                    tone = TextTone.Secondary,
                                )
                            }
                        }
                    }

                    is EpgViewModel.UiState.Ready -> {
                        if (entryRowPending) {
                            LaunchedEffect(Unit) {
                                val entryKey = focusChannelId?.let { sourceKey(providerId, it) }
                                val row = state.channelRows.indexOfFirst { it.channel.sourceKey == entryKey }
                                if (row >= 0) listState.scrollToItem(row)
                                entryRowPending = false
                            }
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            ListingsEndNote(state)
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.forceRefresh() },
                            ) {
                                MobileGuideGrid(
                                    channelRows = state.channelRows,
                                    selectedDate = state.selectedDate,
                                    scrollState = scrollState,
                                    listState = listState,
                                    nowScroll = nowScroll,
                                    onNowScrolled = { nowScroll = null },
                                    onProgramClick = { program, channel -> selection = GuideSelection(program, channel) },
                                    onChannelClick = { channel ->
                                        onChannelSelected(channel)
                                    },
                                    onRowsVisible = viewModel::onRowsVisible,
                                    isRowLoaded = state::isRowLoaded,
                                    catchupDays = state.catchupDays,
                                    sourceNames = state.sourceNames,
                                )
                            }
                        }
                    }

                    is EpgViewModel.UiState.NoListings -> {
                        CentredMessage(
                            title = stringResource(R.string.epg_guide_no_listings_title),
                            message = noListingsMessage(state),
                            actionLabel = stringResource(R.string.common_refresh),
                            onAction = { viewModel.forceRefresh() },
                        )
                    }

                    is EpgViewModel.UiState.NoGuide -> {
                        CentredMessage(
                            title = stringResource(R.string.epg_guide_no_guide_title),
                            message = stringResource(R.string.epg_guide_no_guide_message),
                            actionLabel = stringResource(R.string.common_retry),
                            onAction = { viewModel.loadEpgData() },
                        )
                    }

                    // NoChannels shares the error layout: a message and Retry.
                    is EpgViewModel.UiState.Error, EpgViewModel.UiState.NoChannels -> {
                        RetryWhenOnline { viewModel.loadEpgData() }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(CinemaSpacing.xl),
                            ) {
                                CinemaText(
                                    text = stringResource(R.string.epg_error_loading),
                                    role = TextRole.Display,
                                    tone = TextTone.Danger,
                                )
                                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                                CinemaText(
                                    text =
                                        (state as? EpgViewModel.UiState.Error)?.message
                                            ?: stringResource(R.string.epg_error_no_channels_in_category),
                                    role = TextRole.Body,
                                    tone = TextTone.Secondary,
                                )
                                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                                CinemaButton(
                                    onClick = { viewModel.loadEpgData() },
                                ) {
                                    CinemaText(
                                        stringResource(R.string.common_retry),
                                        role = TextRole.Label,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selection?.let { picked ->
        val replayable =
            remember(picked) {
                CatchupAvailability.isPlayable(
                    catchupDays[picked.channel.sourceKey] ?: 0,
                    picked.program,
                    System.currentTimeMillis() / 1000,
                )
            }
        ProgramDetailsSheet(
            selection = picked,
            today = today,
            onWatchChannel = {
                selection = null
                onProgramSelected(picked.program, picked.channel)
            },
            onWatchFromStart =
                if (replayable) {
                    {
                        selection = null
                        onWatchFromStart(picked.program, picked.channel)
                    }
                } else {
                    null
                },
            onDismiss = { selection = null },
        )
    }
}

/**
 * "N of M channels have listings · source · updated …" (GD1), shown under the title. While pages are
 * still to load (GD4) it counts only the loaded rows and says so: "N of K loaded channels have
 * listings · M in all".
 */
@Composable
private fun statusLine(state: EpgViewModel.UiState.Ready): String =
    if (state.loadedCount < state.totalCount) {
        stringResource(
            R.string.epg_guide_status_partial_format,
            state.listedCount,
            state.loadedCount,
            state.totalCount,
            sourceLabel(state.source),
            updatedLabel(state.updatedAtMs),
        )
    } else {
        stringResource(
            R.string.epg_guide_status_format,
            state.listedCount,
            state.totalCount,
            sourceLabel(state.source),
            updatedLabel(state.updatedAtMs),
        )
    }

/**
 * "Listings end at …" above the grid when now is on the day shown and past its last listing (GD4),
 * instead of rows that go silently empty (data a day old ends in the early morning).
 */
@Composable
private fun ListingsEndNote(state: EpgViewModel.UiState.Ready) {
    val lastEnd = state.lastListingEndSec ?: return
    val nowEpochSeconds by rememberNowEpochSecondsState()
    val zone = remember { ZoneId.systemDefault() }
    val dayStart = remember(state.selectedDate) { state.selectedDate.atStartOfDay(zone).toEpochSecond() }
    val dayEnd =
        remember(state.selectedDate) {
            state.selectedDate
                .plusDays(1)
                .atStartOfDay(zone)
                .toEpochSecond()
        }
    if (!guideListingsEnded(lastEnd, nowEpochSeconds, dayStart, dayEnd)) return
    CinemaText(
        text = stringResource(R.string.epg_guide_listings_end_format, TimeFormat.formatTime(lastEnd)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xxs),
        role = TextRole.BodySmall,
        tone = TextTone.Accent,
    )
}

/**
 * Today / Tomorrow / weekday tabs, scrollable, with the "Now" chip pinned at the end (G-M3, G-8).
 * [pastDays] tabs before today (Yesterday, then weekdays) for the channels' archives (catch-up); the
 * row opens scrolled to today.
 */
@Composable
private fun DateTabs(
    selectedDate: LocalDate,
    today: LocalDate,
    pastDays: Int,
    onSelectDate: (LocalDate) -> Unit,
    onNow: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { dayTabFormatter(locale) }
    val days = remember(today, pastDays) { (-pastDays until DAY_TAB_COUNT).map { today.plusDays(it.toLong()) } }
    val tabsState = rememberLazyListState(initialFirstVisibleItemIndex = pastDays)
    // Past tabs that arrive after the row first showed (the archive days load with the channels)
    // must not push today out of view.
    LaunchedEffect(pastDays) { if (selectedDate == today) tabsState.scrollToItem(pastDays) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CinemaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = tabsState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = CinemaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        ) {
            items(days, key = { it.toEpochDay() }, contentType = { "day_tab" }) { day ->
                CinemaFilterChip(
                    selected = day == selectedDate,
                    onClick = { onSelectDate(day) },
                    label = {
                        CinemaText(
                            dayLabel(day, today, formatter),
                            role = TextRole.Label,
                            tone = TextTone.Inherit,
                        )
                    },
                )
            }
        }
        CinemaFilterChip(
            selected = false,
            onClick = onNow,
            label = {
                CinemaText(
                    stringResource(R.string.epg_jump_to_now),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            },
            modifier = Modifier.padding(end = CinemaSpacing.sm),
        )
    }
}

/** "Fri 3" in the user's locale (its own order and abbreviations). */
private fun dayTabFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEd"), locale)

@Composable
private fun dayLabel(
    day: LocalDate,
    today: LocalDate,
    formatter: DateTimeFormatter,
): String =
    when (day) {
        today -> stringResource(R.string.epg_tab_today)
        today.plusDays(1) -> stringResource(R.string.epg_tab_tomorrow)
        today.minusDays(1) -> stringResource(R.string.epg_tab_yesterday)
        else -> remember(day, formatter) { day.format(formatter) }
    }

/** Tap on a programme: what it is, where and when, and a way to watch the channel (dock). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramDetailsSheet(
    selection: GuideSelection,
    today: LocalDate,
    onWatchChannel: () -> Unit,
    /** Catch-up: the programme from its start, from the channel's archive. Null: not replayable. */
    onWatchFromStart: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val program = selection.program
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { dayTabFormatter(locale) }
    val startDay =
        remember(program.startTime) {
            Instant
                .ofEpochSecond(program.startTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    val description = program.description?.takeIf { it.isNotBlank() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ProvideUiScaledDensity {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CinemaSpacing.md)
                        .padding(bottom = CinemaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
            ) {
                CinemaText(
                    text = program.title,
                    role = TextRole.Title,
                    tone = TextTone.Inherit,
                    emphasis = true,
                )
                CinemaText(
                    text = selection.channel.name,
                    role = TextRole.Body,
                    tone = TextTone.Accent,
                )
                CinemaText(
                    text =
                        dayLabel(startDay, today, formatter) + " · " +
                            TimeFormat.formatTimeRange(program.startTime, program.endTime),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                )
                if (description != null) {
                    CinemaText(
                        text = description,
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        role = TextRole.Body,
                        tone = TextTone.Primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = CinemaSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm, Alignment.End),
                ) {
                    CinemaTextButton(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        },
                    ) {
                        CinemaText(
                            stringResource(R.string.common_close),
                            role = TextRole.Label,
                            tone = TextTone.Inherit,
                        )
                    }
                    if (onWatchFromStart != null) {
                        CinemaTextButton(onClick = onWatchChannel) {
                            CinemaText(
                                stringResource(R.string.epg_details_watch_channel),
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        }
                        CinemaButton(onClick = onWatchFromStart) {
                            CinemaText(
                                stringResource(R.string.epg_details_watch_from_start),
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        }
                    } else {
                        CinemaButton(onClick = onWatchChannel) {
                            CinemaText(
                                stringResource(R.string.epg_details_watch_channel),
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CentredMessage(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(CinemaSpacing.xl),
        ) {
            CinemaText(
                text = title,
                role = TextRole.Display,
                tone = TextTone.Primary,
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaText(
                text = message,
                role = TextRole.Body,
                tone = TextTone.Secondary,
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.md))
            CinemaButton(onClick = onAction) {
                CinemaText(
                    actionLabel,
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        }
    }
}

@Composable
private fun noListingsMessage(state: EpgViewModel.UiState.NoListings): String =
    when (state.reason) {
        EpgViewModel.NoListingsReason.INDEX_EMPTY -> {
            stringResource(R.string.epg_guide_no_listings_index_empty)
        }

        EpgViewModel.NoListingsReason.NONE -> {
            stringResource(R.string.epg_guide_no_listings_none)
        }

        EpgViewModel.NoListingsReason.STALE -> {
            stringResource(
                R.string.epg_guide_no_listings_stale_format,
                sourceLabel(state.source ?: GuideSource.XMLTV),
                updatedLabel(state.updatedAtMs),
            )
        }
    }

@Composable
private fun sourceLabel(source: GuideSource): String =
    when (source) {
        GuideSource.XMLTV -> stringResource(R.string.epg_guide_source_xmltv)
        GuideSource.NATIVE -> stringResource(R.string.epg_guide_source_native)
    }

@Composable
private fun updatedLabel(updatedAtMs: Long?): String =
    if (updatedAtMs == null) {
        stringResource(R.string.epg_guide_updated_unknown)
    } else {
        DateUtils
            .getRelativeTimeSpanString(updatedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            .toString()
    }
