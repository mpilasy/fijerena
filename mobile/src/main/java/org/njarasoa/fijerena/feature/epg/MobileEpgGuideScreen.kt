package org.njarasoa.fijerena.feature.epg

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.rememberNowEpochSecondsState
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.guideListingsEnded
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date tabs: today and the six days after it (the guide loads any day; a week is what fits a thumb). */
private const val DAY_TAB_COUNT = 7

/** A programme tapped in the grid, shown in the details sheet. */
private data class GuideSelection(
    val program: EpgProgram,
    val channel: MediaItem,
)

/**
 * The phone's TV Guide (UX overhaul plan Part III, GD3): "TV Guide · <category>" with the GD1
 * status line beneath it, date tabs (Today / Tomorrow / weekdays) and a "Now" chip, then the
 * [MobileGuideGrid] time grid. Tapping a programme opens its details sheet ("Watch channel" docks the
 * channel); tapping a channel tunes it. Back in search mode closes the search, not the guide (G-9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgGuideScreen(
    categoryId: String,
    categoryName: String,
    onProgramSelected: (program: EpgProgram, channel: MediaItem) -> Unit,
    onChannelSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: EpgViewModel =
        viewModel(
            factory =
                EpgViewModelFactory(
                    context = LocalContext.current.applicationContext,
                    categoryId = categoryId,
                ),
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
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

    val closeSearch = {
        isSearchActive = false
        viewModel.clearSearch()
    }
    BackHandler(enabled = isSearchActive) { closeSearch() }

    val state = uiState
    val shownDate =
        when (state) {
            is EpgViewModel.UiState.Ready -> state.selectedDate
            is EpgViewModel.UiState.NoListings -> state.selectedDate
            else -> requestedDate
        }
    val onSelectDate = { date: LocalDate ->
        if (isSearchActive) closeSearch()
        requestedDate = date
        if (date != shownDate) viewModel.loadEpgData(date)
    }
    val onNow = {
        if (isSearchActive) closeSearch()
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
                        Text(
                            text = stringResource(R.string.epg_guide_header_title_format, categoryName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state is EpgViewModel.UiState.Ready) {
                            Text(
                                text = statusLine(state),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (isSearchActive) closeSearch() else onBack() }) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) closeSearch() else isSearchActive = true
                        },
                    ) {
                        Icon(
                            if (isSearchActive) CinemaIcons.Close else CinemaIcons.Search,
                            if (isSearchActive) stringResource(R.string.epg_search_close) else stringResource(R.string.common_search),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.forceRefresh() },
                        enabled = !isRefreshing,
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(MobileDimensions.progressIndicatorSmall),
                                strokeWidth = MobileDimensions.strokeWidth,
                            )
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
                Text(
                    text = state.devStats,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
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
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                                Text(
                                    text = stringResource(R.string.epg_loading_guide),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    is EpgViewModel.UiState.Ready -> {
                        if (isSearchActive) {
                            MobileEpgSearchContent(
                                searchQuery = searchQuery,
                                searchResults = searchResults,
                                onSearchQueryChanged = { viewModel.searchPrograms(it) },
                                onProgramSelected = onProgramSelected,
                            )
                        } else {
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
                                            onChannelSelected(channel.id, channel.name, channel.categoryId)
                                        },
                                        onRowsVisible = viewModel::onRowsVisible,
                                    )
                                }
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

                    is EpgViewModel.UiState.Error -> {
                        RetryWhenOnline { viewModel.loadEpgData() }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(CinemaSpacing.xl),
                            ) {
                                Text(
                                    text = stringResource(R.string.epg_error_loading),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                                CinemaButton(
                                    onClick = { viewModel.loadEpgData() },
                                ) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selection?.let { picked ->
        ProgramDetailsSheet(
            selection = picked,
            today = today,
            onWatchChannel = {
                selection = null
                onProgramSelected(picked.program, picked.channel)
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
    Text(
        text = stringResource(R.string.epg_guide_listings_end_format, TimeFormat.formatTime(lastEnd)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xxs),
    )
}

/** Today / Tomorrow / weekday tabs, scrollable, with the "Now" chip pinned at the end (G-M3, G-8). */
@Composable
private fun DateTabs(
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onNow: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { dayTabFormatter(locale) }
    val days = remember(today) { (0 until DAY_TAB_COUNT).map { today.plusDays(it.toLong()) } }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CinemaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = CinemaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        ) {
            items(days, key = { it.toEpochDay() }, contentType = { "day_tab" }) { day ->
                CinemaFilterChip(
                    selected = day == selectedDate,
                    onClick = { onSelectDate(day) },
                    label = { Text(dayLabel(day, today, formatter)) },
                )
            }
        }
        CinemaFilterChip(
            selected = false,
            onClick = onNow,
            label = { Text(stringResource(R.string.epg_jump_to_now)) },
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
        else -> remember(day, formatter) { day.format(formatter) }
    }

/** Tap on a programme: what it is, where and when, and a way to watch the channel (dock). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramDetailsSheet(
    selection: GuideSelection,
    today: LocalDate,
    onWatchChannel: () -> Unit,
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
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selection.channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text =
                        dayLabel(startDay, today, formatter) + " · " +
                            TimeFormat.formatTimeRange(program.startTime, program.endTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
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
                        Text(stringResource(R.string.common_close))
                    }
                    CinemaButton(onClick = onWatchChannel) {
                        Text(stringResource(R.string.epg_details_watch_channel))
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
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.md))
            CinemaButton(onClick = onAction) {
                Text(actionLabel)
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

@Composable
private fun MobileEpgSearchContent(
    searchQuery: String,
    searchResults: List<EpgViewModel.EpgSearchResult>,
    onSearchQueryChanged: (String) -> Unit,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = CinemaSpacing.sm),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            label = { Text(stringResource(R.string.epg_search_placeholder)) },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = CinemaSpacing.sm),
        )

        if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.epg_search_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Built once for the list rather than once per row — CardDefaults.cardColors is
            // @Composable, so it can't be remembered inside the item body.
            val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = CinemaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
            ) {
                items(searchResults, key = {
                    "search_${it.channel.id}_${it.program.id}_${it.program.startTime}"
                }, contentType = { "epg_search_result" }) { result ->
                    MobileSearchResultCard(
                        result = result,
                        cardColors = cardColors,
                        onClick = { onProgramSelected(result.program, result.channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileSearchResultCard(
    result: EpgViewModel.EpgSearchResult,
    cardColors: CardColors,
    onClick: () -> Unit,
) {
    CinemaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CinemaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.program.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.channel.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text =
                        TimeFormat.formatTimeRange(
                            result.program.startTime,
                            result.program.endTime,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                result.program.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (result.isCurrent) {
                Text(
                    text = stringResource(R.string.epg_now_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
