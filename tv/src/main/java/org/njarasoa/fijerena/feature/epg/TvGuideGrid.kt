@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.epg

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.GuideSource
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.rememberNowEpochSecondsState
import org.njarasoa.fijerena.core.ui.guide.GuideCell
import org.njarasoa.fijerena.core.ui.guide.GuideLayout
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextTertiary
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.guideListingsEnded
import org.njarasoa.fijerena.feature.category.components.FavoriteContextMenuDialog
import org.njarasoa.fijerena.feature.category.components.RowActionsHint
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.rememberPaneFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.input.tvPane
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/*
 * The TV Guide grid — UX overhaul plan Part III, GD2.
 *
 * Layout: a fixed channel column on the left and a time canvas on the right. The canvas is one
 * `ScrollState` shared by the header ruler and every channel row; each row is a `Layout` that
 * places its cells at `GuideLayout.xFor(start)` with their true width, so a programme's left edge
 * sits under the tick of its start time (G-T1, G-T2). Rows compose only the cells inside
 * `GuideLayout.composeRange` (the viewport plus one on each side). A vertical line marks now
 * across the ruler and the rows; past cells are dimmed; the on-air cell carries the P5 "current"
 * style (accent bar + accent title on the resting container) (G-T3).
 *
 * Focus (G-T4, G-8): every move inside the grid is decided here, not by Compose's geometric
 * search — `GuideFocus` knows which cell has focus and `onPreviewKeyEvent` on the grid body
 * answers the D-pad. Left/Right step by programme within the row; Up/Down keep the *time*: the
 * target row's cell under the focused cell's visible start (an anchor that sticks across rows);
 * Channel Up/Down page rows; Left from the first programme lands on the channel cell, Left from
 * a channel cell stays; Up leaves the grid only from the first row, into the header's labelled
 * buttons; "Now" scrolls to now and focuses the on-air cell. Back leaves the guide. Opened from
 * the player (GD5), entry focus goes to the playing channel's row instead of the first one on air.
 *
 * OK on a programme opens its details panel, whose Watch channel opens the channel's preview (what
 * OK on a programme did before), as OK on a channel does. Long-press OK or the Menu key on either
 * kind of cell opens the channel's row actions (P3). Both are Dialog windows: Back closes them and
 * focus goes back to the cell (GD6).
 *
 * Search (GD5, G-9): the header's Search opens the EPG Browser ("Search the guide") filtered to
 * this guide's channels; Back from it lands on the Search button. There is no in-grid search.
 *
 * Paging (GD4): every channel is a row, but listings arrive a page of rows at a time; the rows on
 * screen are reported to the ViewModel, which loads their page (and the next one when they come
 * near it). A row whose page has not arrived is a channel cell with an empty track — moves treat it
 * like a row without listings, and when its page lands focus goes on to the cell at the kept time.
 */

private val EPG_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private const val HEADER_PREV = "prev"
private const val HEADER_NOW = "now"
private const val HEADER_NEXT = "next"
private const val HEADER_SEARCH = "search"
private const val HEADER_REFRESH = "refresh"
private val HEADER_KEYS = listOf(HEADER_PREV, HEADER_NOW, HEADER_NEXT, HEADER_SEARCH, HEADER_REFRESH)

// Keys for cells, shared by GuideFocus and NavReturnFocus (the cell that opened a preview gets
// focus back). A programme key is prefix + channel id + separator + programme id, so its channel
// cell can tell it is the fallback.
private const val KEY_CHANNEL_PREFIX = "channel:"
private const val KEY_PROGRAM_PREFIX = "program:"
private const val KEY_SEPARATOR = "\n"

private fun channelKey(channelId: String) = KEY_CHANNEL_PREFIX + channelId

private fun programKey(
    channelId: String,
    programId: String,
) = KEY_PROGRAM_PREFIX + channelId + KEY_SEPARATOR + programId

/** Frames to wait for the canvas to be measured before placing "now" (first open). */
private const val VIEWPORT_WAIT_FRAMES = 30

/**
 * Horizontal scrolling of the canvas is decided by the grid's moves alone: a focused cell does not
 * pull the canvas around (on Android TV the default spec pivots every focused child, which would
 * shift the time axis on each Up/Down). The vertical list keeps the default.
 */
@OptIn(ExperimentalFoundationApi::class)
private val CanvasBringIntoView =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float = 0f
    }

/** Where the viewport's left edge lands relative to the time being revealed (a third in). */
private const val REVEAL_FRACTION = 0.35f

@Composable
fun TvGuideGrid(
    categoryName: String,
    state: EpgViewModel.UiState,
    showDevStats: Boolean,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
    onChannelSelected: (String, String, String) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToNow: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onRowsVisible: (first: Int, last: Int) -> Unit,
    isFavoriteChannel: suspend (channelId: String) -> Boolean,
    onToggleFavorite: (MediaItem) -> Unit,
    /** Null where Remove from Recent is not offered (not the Recent guide, or the source keeps the history). */
    onRemoveFromRecent: ((MediaItem) -> Unit)?,
    focusChannelId: String? = null,
) {
    val scale = LocalUiScale.current
    val scope = rememberCoroutineScope()
    // Back from a channel's preview lands on the programme or channel cell that opened it, and
    // Back from the EPG Browser on the Search button — not on the guide's first-open target.
    val returnFocus = rememberNavReturnFocus()
    val headerPane = rememberPaneFocus()
    val focus = remember { GuideFocus().apply { entryChannelId = focusChannelId } }
    // Shared by the ruler and every row: the one time axis (G-T1). Saveable, so Back from a
    // preview keeps the hours the user was looking at.
    val scrollState = rememberScrollState()
    val verticalListState = rememberLazyListState()
    // First-open focus runs once per screen; day changes keep focus on the header button pressed.
    var entryFocusDone by rememberSaveable { mutableStateOf(false) }
    // "Now" on another day reloads today; this makes the reload's Ready scroll and focus like "Now".
    var jumpToNowPending by remember { mutableStateOf(false) }
    val noListingsRefreshRequester = remember { FocusRequester() }
    val today = remember { LocalDate.now() }

    val ready = state as? EpgViewModel.UiState.Ready
    val selectedDate =
        when (state) {
            is EpgViewModel.UiState.Ready -> state.selectedDate
            is EpgViewModel.UiState.NoListings -> state.selectedDate
            else -> null
        }
    var lastDate by remember { mutableStateOf(selectedDate ?: today) }
    if (selectedDate != null) lastDate = selectedDate

    headerPane.bind(selectedKey = null, firstKey = HEADER_PREV, listState = null, indexOf = { HEADER_KEYS.indexOf(it) })
    focus.onExitUp = { scope.launch { headerPane.focusEntry() } }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical,
                ).onPreviewKeyEvent { event ->
                    // Back is taken here, before any focused button can swallow it (NAVIGATION_GUIDE
                    // → "TV Back on Detail Screens"): it leaves the guide. Both edges are consumed;
                    // the action runs on the release.
                    val isBack = event.key == Key.Back
                    if (isBack && event.type == KeyEventType.KeyUp) onBack()
                    isBack
                },
    ) {
        GuideHeader(
            categoryName = categoryName,
            state = state,
            selectedDate = lastDate,
            showDevStats = showDevStats,
            focus = focus,
            headerPane = headerPane,
            returnFocus = returnFocus,
            isRefreshing = isRefreshing,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onJumpToNow = {
                if (ready != null && ready.selectedDate == today) {
                    scope.launch { focus.focusNow(animate = true) }
                } else {
                    jumpToNowPending = true
                    onJumpToNow()
                }
            },
            onRefresh = onRefresh,
            onSearch = {
                returnFocus.leaveFrom(HEADER_SEARCH, verticalListState)
                onSearch()
            },
            // Down from the header lands on what is under it: the grid's last cell, or the
            // "No listings" Refresh; while loading it stays.
            onDownIntoGrid = {
                scope.launch {
                    when {
                        state is EpgViewModel.UiState.Ready -> focus.focusRemembered()
                        state is EpgViewModel.UiState.NoListings -> noListingsRefreshRequester.requestFocusWithRetry()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

        when {
            state is EpgViewModel.UiState.Ready -> {
                GuideBody(
                    state = state,
                    focus = focus,
                    scrollState = scrollState,
                    verticalListState = verticalListState,
                    returnFocus = returnFocus,
                    onProgramSelected = onProgramSelected,
                    onChannelSelected = onChannelSelected,
                    onRowsVisible = onRowsVisible,
                    isFavoriteChannel = isFavoriteChannel,
                    onToggleFavorite = onToggleFavorite,
                    // The rows reload without the channel, so its cell goes: focus lands again,
                    // as on first open, on the row that took its place.
                    onRemoveFromRecent =
                        onRemoveFromRecent?.let { remove ->
                            { channel ->
                                entryFocusDone = false
                                remove(channel)
                            }
                        },
                )
                LaunchedEffect(state) {
                    // Back from a preview or the browser: NavReturnFocus hands focus to the cell
                    // or the Search button that opened it.
                    if (returnFocus.key != null) return@LaunchedEffect
                    if (!entryFocusDone) {
                        entryFocusDone = true
                        focus.focusNow(animate = false)
                    } else if (jumpToNowPending && state.selectedDate == today) {
                        jumpToNowPending = false
                        focus.focusNow(animate = true)
                    }
                }
            }

            state is EpgViewModel.UiState.NoListings -> {
                // GuideBody's hand-back is not composed here: Back from the browser lands on Search.
                NavReturnFocusEffect(returnFocus)
                NoListingsBody(
                    state = state,
                    refreshRequester = noListingsRefreshRequester,
                    // This button goes away with the reload, so whatever comes back takes focus again.
                    onRefresh = {
                        entryFocusDone = false
                        onRefresh()
                    },
                    takeFocus = !entryFocusDone,
                    onFocusTaken = { entryFocusDone = true },
                )
            }

            else -> {
                LoadingBody()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header: title, status line, labelled buttons
// ---------------------------------------------------------------------------------------------

@Composable
private fun GuideHeader(
    categoryName: String,
    state: EpgViewModel.UiState,
    selectedDate: LocalDate,
    showDevStats: Boolean,
    focus: GuideFocus,
    headerPane: org.njarasoa.fijerena.ui.components.input.PaneFocusState,
    returnFocus: NavReturnFocus,
    isRefreshing: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToNow: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onDownIntoGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val scope = rememberCoroutineScope()
    val typography = MaterialTheme.typography
    val titleStyle = remember(scale, typography) { typography.titleLarge.copy(fontSize = typography.titleLarge.fontSize.scaled(scale)) }
    val lineStyle = remember(scale, typography) { typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale)) }
    val buttonColors = guideButtonColors()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.epg_guide_title_format, categoryName),
                style = titleStyle,
                color = CinemaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = Spacing.md.scaled(scale)),
            )
            // Left/Right walk the buttons in order and stop at the ends; Down enters the grid on
            // the cell it last had; Up stays (tvPane, exitUp = false). Entering from the grid lands
            // on the last button used, else "Previous day".
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                modifier =
                    Modifier
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val current = HEADER_KEYS.indexOf(headerPane.rememberedKey)
                            when (event.key) {
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    val step = if (event.key == Key.DirectionLeft) -1 else 1
                                    HEADER_KEYS.getOrNull(current + step)?.let { key -> scope.launch { headerPane.focusKey(key) } }
                                    true
                                }

                                Key.DirectionDown -> {
                                    onDownIntoGrid()
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }.tvPane(headerPane, exitUp = false),
            ) {
                CinemaButton(onClick = onPreviousDay, colors = buttonColors, modifier = Modifier.paneItem(headerPane, HEADER_PREV)) {
                    Icon(imageVector = CinemaIcons.KeyboardArrowLeft, contentDescription = null)
                    Text(stringResource(R.string.epg_prev_day))
                }
                CinemaButton(onClick = onJumpToNow, colors = buttonColors, modifier = Modifier.paneItem(headerPane, HEADER_NOW)) {
                    Text(stringResource(R.string.epg_jump_to_now))
                }
                CinemaButton(onClick = onNextDay, colors = buttonColors, modifier = Modifier.paneItem(headerPane, HEADER_NEXT)) {
                    Text(stringResource(R.string.epg_next_day))
                    Icon(imageVector = CinemaIcons.KeyboardArrowRight, contentDescription = null)
                }
                // Opens "Search the guide" (the EPG Browser) on this guide's channels (GD5).
                CinemaButton(
                    onClick = onSearch,
                    colors = buttonColors,
                    modifier = Modifier.paneItem(headerPane, HEADER_SEARCH).navReturnFocusTarget(returnFocus, HEADER_SEARCH),
                ) {
                    Icon(imageVector = CinemaIcons.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                    Text(stringResource(R.string.common_search))
                }
                // Never disabled: a disabled button drops the focus it holds. A press while
                // refreshing does nothing.
                CinemaButton(
                    onClick = { if (!isRefreshing) onRefresh() },
                    colors = buttonColors,
                    modifier = Modifier.paneItem(headerPane, HEADER_REFRESH),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            strokeWidth = TvDimensions.borderDefault,
                            color = CinemaTextPrimary,
                        )
                    } else {
                        Icon(imageVector = CinemaIcons.Refresh, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                    Text(stringResource(R.string.common_refresh))
                }
            }
        }

        // Date and status: "N of M channels have listings · source · updated …" (GD1), and the dev
        // stats dimmed beneath it in dev mode only — never in the title (G-10).
        val dateLabel = selectedDate.format(EPG_DATE_FORMATTER)
        val statusLine =
            when (state) {
                is EpgViewModel.UiState.Ready -> {
                    statusLine(state)
                }

                is EpgViewModel.UiState.Loading -> {
                    stringResource(R.string.epg_loading_guide)
                }

                else -> {
                    null
                }
            }
        Text(
            text = if (statusLine == null) dateLabel else "$dateLabel · $statusLine",
            style = lineStyle,
            color = CinemaTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showDevStats && state is EpgViewModel.UiState.Ready) {
            Text(
                text = state.devStats,
                style = lineStyle,
                color = CinemaTextTertiary,
                maxLines = 1,
            )
        }
        if (state is EpgViewModel.UiState.Ready) {
            GuideFocusLine(focus = focus, state = state, style = lineStyle)
        }
    }
}

/**
 * "N of M channels have listings · source · updated …" (GD1). While pages are still to load
 * (GD4) it counts only the loaded rows and says so: "N of K loaded channels have listings · M in all".
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
 * The header's last line (GD4): the focused programme's title and time — a short cell drops its
 * label, so this is where it can always be read — and, when now is past the day's last listing,
 * "Listings end at …" instead of rows that go silently empty. Its own composable, so focus moves
 * recompose this line only.
 */
@Composable
private fun GuideFocusLine(
    focus: GuideFocus,
    state: EpgViewModel.UiState.Ready,
    style: TextStyle,
) {
    val scale = LocalUiScale.current
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
    // The last cell focused, while it belongs to the day shown (after a day change focus is on the header).
    val program = focus.shownProgram?.takeIf { it.endTime > dayStart && it.startTime < dayEnd }
    val endedAt = state.lastListingEndSec?.takeIf { guideListingsEnded(it, nowEpochSeconds, dayStart, dayEnd) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                if (program == null) {
                    ""
                } else {
                    program.title + " · " + TimeFormat.formatTimeRange(program.startTime, program.endTime)
                },
            style = style,
            color = CinemaTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (endedAt != null) {
            Text(
                text = stringResource(R.string.epg_guide_listings_end_format, TimeFormat.formatTime(endedAt)),
                style = style,
                color = CinemaAccentLight,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.md.scaled(scale)),
            )
        }
    }
}

/**
 * Resting container, lifted container on focus, accent text on focus — the secondary-button look.
 * The glyphs take `LocalContentColor`, so they follow the text and never vanish into the container
 * the way the white-on-white icon buttons did (G-T4). So do the labels, being tv-material `Text`: the
 * material3 one ignores the button's content colour, which left them dim, reading as disabled (GD6).
 */
@Composable
private fun guideButtonColors(): ButtonColors =
    ButtonDefaults.colors(
        containerColor = TvFocusTokens.restingContainer,
        contentColor = CinemaTextPrimary,
        focusedContainerColor = TvFocusTokens.focusedContainer,
        focusedContentColor = CinemaAccentLight,
        pressedContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.textMedium),
        disabledContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.scrim),
        disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint),
    )

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

// ---------------------------------------------------------------------------------------------
// Loading / NoListings bodies, inside the same chrome
// ---------------------------------------------------------------------------------------------

@Composable
private fun LoadingBody() {
    val scale = LocalUiScale.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(TvDimensions.progressIndicator.scaled(scale)),
            color = CinemaAccent,
        )
    }
}

/** No programme on the day for any channel: the reason, and Refresh; the header keeps day navigation. */
@Composable
private fun NoListingsBody(
    state: EpgViewModel.UiState.NoListings,
    refreshRequester: FocusRequester,
    onRefresh: () -> Unit,
    takeFocus: Boolean,
    onFocusTaken: () -> Unit,
) {
    val scale = LocalUiScale.current
    LaunchedEffect(Unit) {
        if (takeFocus) {
            onFocusTaken()
            refreshRequester.requestFocusWithRetry()
        }
    }
    val message =
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
            modifier = Modifier.padding(Spacing.xl.scaled(scale)),
        ) {
            Text(
                text = stringResource(R.string.epg_guide_no_listings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = CinemaTextPrimary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary,
            )
            CinemaPrimaryButton(
                onClick = onRefresh,
                text = stringResource(R.string.common_refresh),
                modifier = Modifier.focusRequester(refreshRequester),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The grid: ruler + rows on one time axis, and its focus controller
// ---------------------------------------------------------------------------------------------

/**
 * Which cell has focus, which cells are composed, and the moves between them. Rows and cells are
 * addressed by [programKey]/[channelKey]; the composable that owns the grid state installs the
 * suspending movers ([bindMovers]) every composition, since they need the layout and scroll state
 * of the current grid, while the controller itself outlives search mode and day reloads.
 */
@Stable
private class GuideFocus {
    private val requesters = HashMap<String, FocusRequester>()

    /**
     * Where Up from the first row goes: the header. Set by the grid's owner. Not left to Compose's
     * geometric search — the header buttons sit at the far right, outside the channel column's beam,
     * so from a channel cell (or any cell left of them) Up found nothing and the header was unreachable.
     */
    var onExitUp: () -> Unit = {}

    /**
     * Row index and programme (null for a channel cell) of the focused cell; -1 / null when none.
     * A move sets them to its target before focus lands ([moveTo]), so a held key steps on from
     * where the last press was going rather than from a cell still waiting for its scroll.
     */
    var focusedRow: Int = -1
        private set
    var focusedProgram: EpgProgram? = null
        private set

    /** The cell focus was last on; where Down from the header lands. */
    var rememberedKey: String? = null
        private set

    /** The programme of the focused cell (null on a channel cell), for the header line; observable. */
    var shownProgram by mutableStateOf<EpgProgram?>(null)
        private set

    /**
     * The time Up/Down keep: set on the first vertical move from a cell's visible start, kept while
     * the cells landed on contain it, dropped by a horizontal move (G-T4).
     */
    var anchorSec: Long? = null

    /** The channel whose row the first "now" focus lands on (the playing one, GD5); null: the first on air. */
    var entryChannelId: String? = null

    private var movers: GuideMovers? = null

    fun bindMovers(movers: GuideMovers?) {
        this.movers = movers
    }

    fun register(
        key: String,
        requester: FocusRequester,
    ) {
        requesters[key] = requester
    }

    /** Whether the cell with [key] is composed now (its requester registered). */
    fun isComposed(key: String): Boolean = key in requesters

    fun unregister(
        key: String,
        requester: FocusRequester,
    ) {
        if (requesters[key] === requester) requesters.remove(key)
    }

    fun onFocused(
        key: String,
        rowIndex: Int,
        program: EpgProgram?,
    ) {
        focusedRow = rowIndex
        focusedProgram = program
        rememberedKey = key
        shownProgram = program
    }

    /** A move is on its way to [program] in [rowIndex] (null: the channel cell). */
    fun moveTo(
        rowIndex: Int,
        program: EpgProgram?,
    ) {
        focusedRow = rowIndex
        focusedProgram = program
    }

    /** Waits for the cell with [key] to compose (a few frames at most), then focuses it. */
    suspend fun focusKey(key: String): Boolean {
        var requester = requesters[key]
        var frames = 0
        while (requester == null && frames < FOCUS_WAIT_FRAMES) {
            withFrameNanos { }
            frames++
            requester = requesters[key]
        }
        return requester?.requestFocusWithRetry() ?: false
    }

    suspend fun focusRemembered(): Boolean = awaitMovers()?.focusRemembered() ?: false

    suspend fun focusNow(animate: Boolean): Boolean = awaitMovers()?.focusNow(animate) ?: false

    /** The grid's movers, waiting a few frames for a grid that is about to compose. */
    private suspend fun awaitMovers(): GuideMovers? {
        var frames = 0
        while (movers == null && frames < FOCUS_WAIT_FRAMES) {
            withFrameNanos { }
            frames++
        }
        return movers
    }

    /** The D-pad inside the grid. Returns whether the key was taken. */
    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val movers = movers ?: return false
        if (focusedRow < 0) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                movers.stepInRow(-1)
                true
            }

            Key.DirectionRight -> {
                movers.stepInRow(+1)
                true
            }

            Key.DirectionUp -> {
                // From the first row, Up leaves to the header.
                if (focusedRow == 0) onExitUp() else movers.moveRows(-1)
                true
            }

            Key.DirectionDown -> {
                movers.moveRows(+1)
                true
            }

            Key.ChannelUp -> {
                movers.moveRows(-movers.pageSize())
                true
            }

            Key.ChannelDown -> {
                movers.moveRows(+movers.pageSize())
                true
            }

            else -> {
                false
            }
        }
    }

    companion object {
        private const val FOCUS_WAIT_FRAMES = 30
    }
}

/** The moves, bound to the current grid (rows, layout, scroll states) by [GuideBody]. */
private interface GuideMovers {
    fun stepInRow(step: Int)

    fun moveRows(delta: Int)

    fun pageSize(): Int

    suspend fun focusRemembered(): Boolean

    suspend fun focusNow(animate: Boolean): Boolean

    /**
     * Focus sits on a channel cell with a time kept (a row whose page had not loaded): now that the
     * rows changed, land on the cell at that time if the row has one.
     */
    suspend fun focusAnchorInLoadedRow()
}

@Composable
private fun GuideBody(
    state: EpgViewModel.UiState.Ready,
    focus: GuideFocus,
    scrollState: ScrollState,
    verticalListState: LazyListState,
    returnFocus: NavReturnFocus,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit,
    onChannelSelected: (String, String, String) -> Unit,
    onRowsVisible: (first: Int, last: Int) -> Unit,
    isFavoriteChannel: suspend (channelId: String) -> Boolean,
    onToggleFavorite: (MediaItem) -> Unit,
    onRemoveFromRecent: ((MediaItem) -> Unit)?,
) {
    val scale = LocalUiScale.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val channelRows = state.channelRows
    val zone = remember { ZoneId.systemDefault() }
    val layout =
        remember(state.selectedDate, density, scale) {
            val dayStart = state.selectedDate.atStartOfDay(zone).toEpochSecond()
            val dayEnd =
                state.selectedDate
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toEpochSecond()
            GuideLayout(
                pxPerMinute =
                    with(density) {
                        GuideLayout.DP_PER_MINUTE.dp
                            .scaled(scale)
                            .toPx()
                    },
                windowStartSec = dayStart,
                windowEndSec = dayEnd,
                minLabelWidthPx = with(density) { Spacing.xxl.scaled(scale).toPx() },
            )
        }
    val rowIndexById =
        remember(channelRows) {
            HashMap<String, Int>(channelRows.size * 2).also { m ->
                channelRows.forEachIndexed { i, r ->
                    m[r.channel.id] =
                        i
                }
            }
        }
    var viewportPx by remember { mutableIntStateOf(0) }
    // Quantised, so rows recompose every half viewport of scrolling, not every frame.
    val composeRange by remember(layout) { derivedStateOf { layout.composeRange(scrollState.value.toFloat(), viewportPx.toFloat()) } }
    // Held as State, not read here: reading it in this scope would make the 60s tick recompose the
    // whole grid. Cells read it through a derived phase; the now line reads it in the draw phase.
    val nowEpochSeconds = rememberNowEpochSecondsState()
    val cardStyle = guideCardStyle()
    val channelColumnWidth = TvDimensions.epgChannelColumnWidth.scaled(scale)
    val columnGap = Spacing.sm.scaled(scale)
    val canvasLeftPx = with(density) { (channelColumnWidth + columnGap).toPx() }

    val movers =
        remember(layout, channelRows, scrollState, verticalListState, focus, scope) {
            object : GuideMovers {
                private fun rowOf(key: String): Int {
                    val id =
                        when {
                            key.startsWith(KEY_PROGRAM_PREFIX) -> key.removePrefix(KEY_PROGRAM_PREFIX).substringBefore(KEY_SEPARATOR)
                            key.startsWith(KEY_CHANNEL_PREFIX) -> key.removePrefix(KEY_CHANNEL_PREFIX)
                            else -> return -1
                        }
                    return rowIndexById[id] ?: -1
                }

                private fun programOf(key: String): EpgProgram? {
                    if (!key.startsWith(KEY_PROGRAM_PREFIX)) return null
                    val row = rowOf(key)
                    if (row < 0) return null
                    val programId = key.substringAfter(KEY_SEPARATOR)
                    return channelRows[row].programs.firstOrNull { it.id == programId }
                }

                private fun inWindow(program: EpgProgram) =
                    program.endTime > layout.windowStartSec && program.startTime < layout.windowEndSec

                private fun viewport() = viewportPx.toFloat()

                private fun clampScroll(x: Float) = x.coerceIn(0f, (layout.totalWidthPx - viewport()).coerceAtLeast(0f))

                /** Puts [sec] a third of the viewport in from the left edge. */
                private suspend fun scrollToReveal(
                    sec: Long,
                    animate: Boolean,
                ) {
                    val target = clampScroll(layout.xFor(sec) - viewport() * REVEAL_FRACTION)
                    if (animate) scrollState.animateScrollTo(target.roundToInt()) else scrollState.scrollTo(target.roundToInt())
                }

                /**
                 * Composes [row] without jumping the list when it is already on screen; a row just
                 * past the last visible one comes in at the bottom, not the top.
                 */
                private suspend fun ensureRowComposed(row: Int) {
                    val info = verticalListState.layoutInfo
                    val visible = info.visibleItemsInfo
                    val first = visible.firstOrNull()?.index ?: return verticalListState.scrollToItem(row)
                    val last = visible.last().index
                    when {
                        row < first -> verticalListState.scrollToItem(row)
                        row > last -> verticalListState.scrollToItem((row - (last - first)).coerceAtLeast(0))
                    }
                }

                /**
                 * Focuses [program] in [row]. With [revealStart] (Left/Right) the canvas scrolls so
                 * the cell's start is on screen; otherwise (Up/Down, Now) only when no part of the
                 * cell is — the canvas holds still while the time is kept.
                 */
                private suspend fun focusCell(
                    row: Int,
                    program: EpgProgram,
                    revealStart: Boolean,
                ): Boolean {
                    focus.moveTo(row, program)
                    val startSec = program.startTime.coerceAtLeast(layout.windowStartSec)
                    val endSec = program.endTime.coerceAtMost(layout.windowEndSec)
                    val left = layout.xFor(startSec)
                    val right = layout.xFor(endSec)
                    val scroll = scrollState.value.toFloat()
                    val visibleEnd = scroll + viewport() - layout.minLabelWidthPx
                    val hidden =
                        if (revealStart) {
                            left < scroll || left > visibleEnd
                        } else {
                            right <= scroll || left >= visibleEnd
                        }
                    ensureRowComposed(row)
                    val key = programKey(channelRows[row].channel.id, program.id)
                    // A composed target takes focus before the scroll, not after: scrolling first
                    // can carry the cell that still has focus out of the composed range (a long
                    // programme reached by Left from the end of the day), focus drops, the grid
                    // puts it back on the remembered cell and that cell's scroll cancels this one.
                    if (focus.isComposed(key)) {
                        val focused = focus.focusKey(key)
                        if (hidden) scrollToReveal(startSec, animate = true)
                        return focused
                    }
                    if (hidden) scrollToReveal(startSec, animate = true)
                    return focus.focusKey(key)
                }

                private suspend fun focusChannel(row: Int): Boolean {
                    focus.moveTo(row, null)
                    ensureRowComposed(row)
                    return focus.focusKey(channelKey(channelRows[row].channel.id))
                }

                override fun stepInRow(step: Int) {
                    val row = focus.focusedRow
                    val programs = channelRows.getOrNull(row)?.programs ?: return
                    val current = focus.focusedProgram
                    focus.anchorSec = null
                    if (current == null) {
                        // Right from the channel cell: the on-air cell when now is on screen, else
                        // the cell at the canvas's left edge — not the day's first programme (G-T4).
                        // Left on a channel cell stays.
                        if (step > 0) {
                            val visible = layout.visibleRange(scrollState.value.toFloat(), viewport())
                            val now = nowEpochSeconds.value
                            val at = if (now in visible) now else visible.first
                            GuideLayout.programAt(programs, at)?.takeIf(::inWindow)?.let { target ->
                                scope.launch { focusCell(row, target, revealStart = false) }
                            }
                        }
                        return
                    }
                    val index = programs.indexOfFirst { it.id == current.id }
                    val target = programs.getOrNull(index + step)?.takeIf(::inWindow)
                    scope.launch {
                        when {
                            target != null -> focusCell(row, target, revealStart = true)
                            step < 0 -> focusChannel(row)
                        }
                    }
                }

                override fun moveRows(delta: Int) {
                    val row = focus.focusedRow
                    val target = (row + delta).coerceIn(0, channelRows.lastIndex)
                    if (target == row) return
                    val current = focus.focusedProgram
                    // Keep the time: the anchor while the focused cell still contains it, else this
                    // cell's visible start (a long programme scrolled half off-screen anchors where
                    // the eye is, not hours to the left). On a channel cell the anchor survives only
                    // when a row without listings put focus there; Left to the channel clears it.
                    val anchor =
                        if (current == null) {
                            focus.anchorSec
                        } else {
                            val visibleStart = layout.visibleRange(scrollState.value.toFloat(), viewport()).first
                            focus.anchorSec?.takeIf { it >= current.startTime && it < current.endTime }
                                ?: maxOf(current.startTime, visibleStart)
                        }
                    focus.anchorSec = anchor
                    val targetProgram = anchor?.let { GuideLayout.programAt(channelRows[target].programs, it) }?.takeIf(::inWindow)
                    scope.launch {
                        if (targetProgram != null) focusCell(target, targetProgram, revealStart = false) else focusChannel(target)
                    }
                }

                override fun pageSize(): Int =
                    verticalListState.layoutInfo.visibleItemsInfo.size
                        .coerceAtLeast(1)

                override suspend fun focusRemembered(): Boolean {
                    val key = focus.rememberedKey
                    val row = key?.let(::rowOf) ?: -1
                    val program = key?.let(::programOf)
                    return when {
                        row < 0 -> focusNow(animate = false)
                        program != null -> focusCell(row, program, revealStart = false)
                        else -> focusChannel(row)
                    }
                }

                override suspend fun focusNow(animate: Boolean): Boolean {
                    // Placing "now" needs the canvas width; it is measured on the first layout pass.
                    var frames = 0
                    while (viewportPx == 0 && frames < VIEWPORT_WAIT_FRAMES) {
                        withFrameNanos { }
                        frames++
                    }
                    val now = nowEpochSeconds.value
                    val inDay = now >= layout.windowStartSec && now < layout.windowEndSec
                    val at = if (inDay) now else layout.windowStartSec
                    // The focused row when there is one, else the entry channel's (the one playing,
                    // GD5), else the first channel with a programme on at that time, else the first
                    // channel with listings (G-8: never a channel without listings, never
                    // yesterday's programme). An entry row whose page has not loaded lands on its
                    // channel cell and moves on to the on-air cell when the page arrives.
                    val row =
                        focus.focusedRow.takeIf { it in channelRows.indices }
                            ?: focus.entryChannelId?.let { rowIndexById[it] }
                            ?: channelRows
                                .indexOfFirst { r -> r.programs.any { at >= it.startTime && at < it.endTime } }
                                .takeIf { it >= 0 }
                            ?: channelRows.indexOfFirst { it.programs.isNotEmpty() }.coerceAtLeast(0)
                    scrollToReveal(at, animate)
                    focus.anchorSec = at
                    val program = GuideLayout.programAt(channelRows[row].programs, at)?.takeIf(::inWindow)
                    return if (program != null) focusCell(row, program, revealStart = false) else focusChannel(row)
                }

                override suspend fun focusAnchorInLoadedRow() {
                    if (focus.focusedProgram != null) return
                    val anchor = focus.anchorSec ?: return
                    val row = focus.focusedRow
                    val programs = channelRows.getOrNull(row)?.programs ?: return
                    val target = GuideLayout.programAt(programs, anchor)?.takeIf(::inWindow) ?: return
                    focusCell(row, target, revealStart = false)
                }
            }
        }
    DisposableEffect(focus, movers) {
        focus.bindMovers(movers)
        onDispose { focus.bindMovers(null) }
    }

    // Paging (GD4): the rows on screen — where scrolling and every focus move end up, since a move
    // composes its row first — go to the ViewModel, which loads their page and the next one when
    // they come near it. Rows of pages not loaded yet are channel cells over an empty track, so
    // Up/Down and Channel Up/Down walk them like rows without listings and keep the time.
    val currentOnRowsVisible by rememberUpdatedState(onRowsVisible)
    LaunchedEffect(verticalListState) {
        snapshotFlow {
            val visible = verticalListState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null else visible.first().index to visible.last().index
        }.filterNotNull()
            .distinctUntilChanged()
            .collect { (first, last) -> currentOnRowsVisible(first, last) }
    }
    // A page arrived while focus waited on a channel cell of its rows: move to the cell at the kept time.
    var gridHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(channelRows) {
        if (gridHasFocus) movers.focusAnchorInLoadedRow()
    }

    // A programme that is gone on return (the day reloaded) falls back to its channel cell.
    val returnChannelRequester = remember { FocusRequester() }
    val returnRowId =
        returnFocus.key
            ?.takeIf {
                it.startsWith(KEY_PROGRAM_PREFIX)
            }?.removePrefix(KEY_PROGRAM_PREFIX)
            ?.substringBefore(KEY_SEPARATOR)
    NavReturnFocusEffect(returnFocus, listState = verticalListState, fallback = returnChannelRequester)

    // OK on a programme: its details panel (GD6).
    var details by remember { mutableStateOf<Pair<EpgProgram, MediaItem>?>(null) }
    details?.let { (program, channel) ->
        ProgramDetailsDialog(
            program = program,
            channel = channel,
            onWatchChannel = {
                details = null
                returnFocus.leaveFrom(programKey(channel.id, program.id), verticalListState)
                onProgramSelected(program, channel)
            },
            onDismiss = { details = null },
        )
    }
    // Long-press OK or Menu on a cell: its channel's row actions, as in the channel lists (P3).
    // The channel and whether it is a favourite, read when the menu opens.
    var actions by remember { mutableStateOf<Pair<MediaItem, Boolean>?>(null) }
    actions?.let { (channel, isFavorite) ->
        FavoriteContextMenuDialog(
            target =
                FavoriteMenuTarget.Stream(
                    itemId = channel.id,
                    itemName = channel.name,
                    categoryId = channel.categoryId,
                    contentType = ContentType.LIVE_TV,
                    isFavorite = isFavorite,
                    isInRecent = onRemoveFromRecent != null,
                ),
            onConfirm = { onToggleFavorite(channel) },
            onDismiss = { actions = null },
            onRemoveFromRecent = onRemoveFromRecent?.let { remove -> { remove(channel) } },
        )
    }
    val openActions: (MediaItem) -> Unit = { channel -> scope.launch { actions = channel to isFavoriteChannel(channel.id) } }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // The now line, across the ruler and every row (G-T3). Draw-phase reads only.
                    val now = nowEpochSeconds.value
                    if (now < layout.windowStartSec || now >= layout.windowEndSec) return@drawWithContent
                    val x = canvasLeftPx + layout.xFor(now) - scrollState.value
                    if (x < canvasLeftPx || x > size.width) return@drawWithContent
                    drawLine(
                        color = CinemaAccent,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = TvDimensions.borderFocused.toPx(),
                    )
                },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(channelColumnWidth + columnGap))
            TimeRuler(
                layout = layout,
                composeRange = composeRange,
                scrollState = scrollState,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(Spacing.xl.scaled(scale))
                        .onSizeChanged { viewportPx = it.width },
            )
        }

        // The grid proper: a D-pad move into it lands on the remembered cell (Down from the header
        // goes through GuideFocus directly; this covers any other entry); Down past the last row
        // stays; the rest of the D-pad is decided by GuideFocus before any cell sees it.
        LazyColumn(
            state = verticalListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .onFocusChanged { gridHasFocus = it.hasFocus }
                    .onPreviewKeyEvent(focus::onKey)
                    .focusProperties {
                        onEnter = {
                            if (requestedFocusDirection.isDpadMove) {
                                cancelFocusChange()
                                scope.launch { focus.focusRemembered() }
                            }
                        }
                        onExit = {
                            if (requestedFocusDirection == FocusDirection.Down) cancelFocusChange()
                        }
                    }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs.scaled(scale)),
        ) {
            items(
                count = channelRows.size,
                key = { channelRows[it].channel.id },
                contentType = { "channel_row" },
            ) { index ->
                val row = channelRows[index]
                GuideRow(
                    row = row,
                    rowIndex = index,
                    layout = layout,
                    composeRange = composeRange,
                    scrollState = scrollState,
                    nowEpochSeconds = nowEpochSeconds,
                    cardStyle = cardStyle,
                    focus = focus,
                    returnFocus = returnFocus,
                    returnChannelRequester = returnChannelRequester.takeIf { returnRowId == row.channel.id },
                    channelColumnWidth = channelColumnWidth,
                    columnGap = columnGap,
                    onChannelClick = {
                        returnFocus.leaveFrom(channelKey(row.channel.id), verticalListState)
                        onChannelSelected(row.channel.id, row.channel.name, row.channel.categoryId)
                    },
                    onProgramClick = { program -> details = program to row.channel },
                    onRowActions = { openActions(row.channel) },
                )
            }
        }
    }
}

private val FocusDirection.isDpadMove: Boolean
    get() = this == FocusDirection.Left || this == FocusDirection.Right || this == FocusDirection.Up || this == FocusDirection.Down

/** A window onto the time canvas, scrolled by the one [scrollState] the ruler and every row share. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasScroll(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides CanvasBringIntoView) {
        Box(modifier = modifier.clipToBounds().horizontalScroll(scrollState)) { content() }
    }
}

@Composable
private fun TimeRuler(
    layout: GuideLayout,
    composeRange: LongRange,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val labelStyle = remember(scale, typography) { typography.labelMedium.copy(fontSize = typography.labelMedium.fontSize.scaled(scale)) }
    val ticks = remember(layout, composeRange) { layout.tickMarks(composeRange.first, composeRange.last) }
    val tickWidthPx = (GuideLayout.DEFAULT_TICK_STEP_MIN * layout.pxPerMinute).roundToInt().coerceAtLeast(1)
    val totalWidthPx = layout.totalWidthPx.roundToInt()

    CanvasScroll(scrollState = scrollState, modifier = modifier) {
        Layout(
            content = {
                ticks.forEach { tick ->
                    androidx.compose.runtime.key(tick.epochSec) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight()) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(TvDimensions.borderDefault)
                                        .fillMaxHeight()
                                        .background(CinemaTextTertiary),
                            )
                            Text(
                                text = TimeFormat.formatTime(tick.epochSec),
                                style = labelStyle,
                                color = CinemaTextSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(start = Spacing.xxs.scaled(scale)),
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val height = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: 0
            val placeables = measurables.map { it.measure(Constraints(maxWidth = tickWidthPx, minHeight = height, maxHeight = height)) }
            layout(totalWidthPx, height) {
                placeables.forEachIndexed { i, placeable -> placeable.place(ticks[i].x.roundToInt(), 0) }
            }
        }
    }
}

@Composable
private fun GuideRow(
    row: EpgChannelRow,
    rowIndex: Int,
    layout: GuideLayout,
    composeRange: LongRange,
    scrollState: ScrollState,
    nowEpochSeconds: State<Long>,
    cardStyle: GuideCardStyle,
    focus: GuideFocus,
    returnFocus: NavReturnFocus,
    returnChannelRequester: FocusRequester?,
    channelColumnWidth: androidx.compose.ui.unit.Dp,
    columnGap: androidx.compose.ui.unit.Dp,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit,
    onRowActions: () -> Unit,
) {
    val scale = LocalUiScale.current
    val rowHeight = TvDimensions.epgRowHeight.scaled(scale)
    val channelId = row.channel.id
    val cells = remember(row.programs, layout, composeRange) { layout.cellsIn(row.programs, composeRange.first, composeRange.last) }
    val totalWidthPx = layout.totalWidthPx.roundToInt()

    Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
        val chKey = channelKey(channelId)
        ChannelCell(
            channel = row.channel,
            cardStyle = cardStyle,
            onClick = onChannelClick,
            onLongClick = onRowActions,
            modifier =
                Modifier
                    .width(channelColumnWidth)
                    .guideCell(focus, chKey, rowIndex, program = null)
                    .navReturnFocusTarget(returnFocus, chKey)
                    .then(if (returnChannelRequester != null) Modifier.focusRequester(returnChannelRequester) else Modifier),
        )
        Spacer(modifier = Modifier.width(columnGap))
        CanvasScroll(scrollState = scrollState, modifier = Modifier.weight(1f).fillMaxHeight()) {
            Layout(
                content = {
                    cells.forEach { cell ->
                        androidx.compose.runtime.key(cell.program.id) {
                            val key = programKey(channelId, cell.program.id)
                            ProgramCell(
                                cell = cell,
                                nowEpochSeconds = nowEpochSeconds,
                                cardStyle = cardStyle,
                                onClick = { onProgramClick(cell.program) },
                                onLongClick = onRowActions,
                                modifier =
                                    Modifier
                                        .guideCell(focus, key, rowIndex, cell.program)
                                        .navReturnFocusTarget(returnFocus, key),
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val height = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: 0
                val placeables =
                    measurables.mapIndexed { i, measurable ->
                        measurable.measure(Constraints.fixed(cells[i].width.roundToInt().coerceAtLeast(1), height))
                    }
                layout(totalWidthPx, height) {
                    placeables.forEachIndexed { i, placeable -> placeable.place(cells[i].x.roundToInt(), 0) }
                }
            }
        }
    }
}

/** Registers this cell with [focus] under [key] and reports it as the focused cell while it has focus. */
@Composable
private fun Modifier.guideCell(
    focus: GuideFocus,
    key: String,
    rowIndex: Int,
    program: EpgProgram?,
): Modifier {
    val requester = remember(key) { FocusRequester() }
    DisposableEffect(focus, key, requester) {
        focus.register(key, requester)
        onDispose { focus.unregister(key, requester) }
    }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) focus.onFocused(key, rowIndex, program) }
}

/** The Menu key does what long-press OK does: the row actions (P3). */
private fun Modifier.onMenuKey(onMenu: () -> Unit): Modifier =
    onKeyEvent { event ->
        val isMenu = event.type == KeyEventType.KeyDown && event.key == Key.Menu
        if (isMenu) onMenu()
        isMenu
    }

// ---------------------------------------------------------------------------------------------
// Programme details (GD6)
// ---------------------------------------------------------------------------------------------

/** Lines of description shown; the panel never scrolls, so a long one is cut. */
private const val DETAILS_DESCRIPTION_MAX_LINES = 8

/**
 * What a programme is, on which channel, when, and its description, with Watch channel (opens the
 * channel's preview) and Close. Opens on Watch channel; Back or Close returns focus to the cell.
 */
@Composable
private fun ProgramDetailsDialog(
    program: EpgProgram,
    channel: MediaItem,
    onWatchChannel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val watchRequester = remember { FocusRequester() }
    val today = remember { LocalDate.now() }
    val day = remember(program.startTime) { Instant.ofEpochSecond(program.startTime).atZone(ZoneId.systemDefault()).toLocalDate() }
    val dayLabel =
        when (day) {
            today -> stringResource(R.string.epg_tab_today)
            today.plusDays(1) -> stringResource(R.string.epg_tab_tomorrow)
            else -> day.format(EPG_DATE_FORMATTER)
        }
    val description = program.description?.takeIf { it.isNotBlank() }
    val typography = MaterialTheme.typography
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = program.title,
                style = typography.headlineSmall,
                color = CinemaTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = channel.name, style = typography.bodyLarge, color = CinemaAccentLight, maxLines = 1)
                Text(
                    text = dayLabel + " · " + TimeFormat.formatTimeRange(program.startTime, program.endTime),
                    style = typography.bodyLarge,
                    color = CinemaTextSecondary,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = typography.bodyLarge,
                        color = CinemaTextPrimary,
                        maxLines = DETAILS_DESCRIPTION_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        initialFocus = watchRequester,
        confirmButton = {
            CinemaDialogActionButton(
                onClick = onWatchChannel,
                modifier = Modifier.focusRequester(watchRequester),
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaAccent,
                        contentColor = CinemaTextPrimary,
                    ),
            ) { Text(stringResource(R.string.epg_details_watch_channel), color = CinemaTextPrimary) }
        },
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary,
                    ),
            ) { Text(stringResource(R.string.common_close), color = CinemaTextPrimary) }
        },
        containerColor = CinemaSurface,
    )
}

// ---------------------------------------------------------------------------------------------
// Cells
// ---------------------------------------------------------------------------------------------

/**
 * Card styling for the guide, built once per grid composition instead of once per cell: the
 * `CardDefaults` calls are `@Composable`, so hoisting them is what stops the 50×N allocations.
 */
@Immutable
private data class GuideCardStyle(
    val colors: CardColors,
    val cardScale: CardScale,
    val glow: CardGlow,
    val border: CardBorder,
    val shape: CardShape,
    val titleStyle: TextStyle,
    val timeStyle: TextStyle,
    val channelStyle: TextStyle,
)

@Composable
private fun guideCardStyle(): GuideCardStyle {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val shape = RoundedCornerShape(CinemaCornerRadius.medium)
    return GuideCardStyle(
        colors =
            CardDefaults.colors(
                containerColor = TvFocusTokens.restingContainer,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = TvFocusTokens.focusedContainer,
                focusedContentColor = CinemaTextPrimary,
            ),
        cardScale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
        border =
            CardDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccentLight),
                        shape = shape,
                    ),
            ),
        shape = CardDefaults.shape(shape = shape),
        titleStyle = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale)),
        timeStyle = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale)),
        channelStyle = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale)),
    )
}

@Composable
private fun ChannelCell(
    channel: MediaItem,
    cardStyle: GuideCardStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier =
            modifier
                .fillMaxHeight()
                .padding(vertical = Spacing.xxs.scaled(scale))
                .onFocusChanged { isFocused = it.isFocused }
                .onMenuKey(onLongClick),
        colors = cardStyle.colors,
        scale = cardStyle.cardScale,
        glow = cardStyle.glow,
        border = cardStyle.border,
        shape = cardStyle.shape,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Spacing.sm.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = channel.name,
                style = cardStyle.channelStyle,
                color = CinemaTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The row-actions hint, as on the channel lists' rows; programme cells are too narrow for it.
            if (isFocused) RowActionsHint()
        }
    }
}

private enum class CellPhase { PAST, ON_AIR, UPCOMING }

@Composable
private fun ProgramCell(
    cell: GuideCell,
    nowEpochSeconds: State<Long>,
    cardStyle: GuideCardStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    // Shared tick instead of per-cell System.currentTimeMillis(), read through derivedStateOf so a
    // cell recomposes only when its own phase flips — not on every 60s tick.
    val phase by
        remember(cell.startSec, cell.endSec, nowEpochSeconds) {
            derivedStateOf {
                val now = nowEpochSeconds.value
                when {
                    now >= cell.endSec -> CellPhase.PAST
                    now >= cell.startSec -> CellPhase.ON_AIR
                    else -> CellPhase.UPCOMING
                }
            }
        }

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier =
            modifier
                .padding(horizontal = TvDimensions.borderDefault, vertical = Spacing.xxs.scaled(scale))
                .onMenuKey(onLongClick),
        colors = cardStyle.colors,
        scale = cardStyle.cardScale,
        glow = cardStyle.glow,
        border = cardStyle.border,
        shape = cardStyle.shape,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (phase == CellPhase.ON_AIR) {
                Box(
                    modifier =
                        Modifier
                            .width(Spacing.xxs.scaled(scale))
                            .fillMaxHeight()
                            .background(CinemaAccent),
                )
            }
            if (cell.labelFits) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.sm.scaled(scale), vertical = Spacing.xs.scaled(scale))
                            .alpha(if (phase == CellPhase.PAST) CinemaAlpha.textDisabled else 1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = cell.program.title,
                        style = cardStyle.titleStyle,
                        fontWeight = if (phase == CellPhase.ON_AIR) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (phase == CellPhase.ON_AIR) CinemaAccentLight else CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = TimeFormat.formatTimeRange(cell.program.startTime, cell.program.endTime),
                        style = cardStyle.timeStyle,
                        color = CinemaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}
