package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.rememberNowEpochSecondsState
import org.njarasoa.fijerena.core.ui.guide.GuideCell
import org.njarasoa.fijerena.core.ui.guide.GuideLayout
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CinemaTextTertiary
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** How far in from the canvas's left edge "now" lands: about a third, so what's next shows. */
private const val NOW_REVEAL_FRACTION = 1f / 3f

/** A pending "scroll to now": on first open without animation, from the Now chip with one. */
enum class NowScroll { JUMP, ANIMATE }

/**
 * The phone's TV Guide grid (UX overhaul plan Part III, GD3): a fixed channel column and a time
 * canvas placed by [GuideLayout] (1 h = 160 dp). The ruler and every row are windows onto the same
 * canvas, scrolled by the one [scrollState] — each row is a `Layout` of cells at `xFor(start)` with
 * their true width, never a `LazyRow` per row — so a horizontal drag anywhere moves them all and a
 * programme's left edge always sits under its tick (G-M1, G-M2). Rows compose only the cells in
 * `composeRange` (the viewport ± one viewport). A vertical line marks now, past cells are dimmed, the
 * on-air cell has an accent bar and accent title, and a channel without listings is its channel cell
 * beside an empty, dimmed track.
 *
 * [nowScroll], while not null, asks the grid to put now about a third in from the left edge once the
 * canvas is measured and the day shown is today; [onNowScrolled] clears it.
 */
@Composable
fun MobileGuideGrid(
    channelRows: List<EpgChannelRow>,
    selectedDate: LocalDate,
    scrollState: ScrollState,
    listState: LazyListState,
    nowScroll: NowScroll?,
    onNowScrolled: () -> Unit,
    onProgramClick: (EpgProgram, MediaItem) -> Unit,
    onChannelClick: (MediaItem) -> Unit,
    onRowsVisible: (first: Int, last: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val zone = remember { ZoneId.systemDefault() }
    val layout =
        remember(selectedDate, density) {
            GuideLayout(
                pxPerMinute = with(density) { GuideLayout.PHONE_DP_PER_MINUTE.dp.toPx() },
                windowStartSec = selectedDate.atStartOfDay(zone).toEpochSecond(),
                windowEndSec = selectedDate.plusDays(1).atStartOfDay(zone).toEpochSecond(),
                minLabelWidthPx = with(density) { CinemaSpacing.xxl.toPx() },
            )
        }
    var viewportPx by remember { mutableIntStateOf(0) }
    // Quantised, so rows recompose every half viewport of scrolling, not every frame.
    val composeRange by remember(layout) {
        derivedStateOf { layout.composeRange(scrollState.value.toFloat(), viewportPx.toFloat()) }
    }
    // Held as State: cells read it through a derived phase, the now line in the draw phase, so the
    // 60 s tick never recomposes the whole grid.
    val nowEpochSeconds = rememberNowEpochSecondsState()
    val channelColumnWidth = MobileDimensions.posterWidth
    val canvasLeftPx = with(density) { channelColumnWidth.toPx() }
    val nowLineWidthPx = with(density) { MobileDimensions.strokeWidth.toPx() }

    LaunchedEffect(nowScroll, layout, viewportPx) {
        val request = nowScroll ?: return@LaunchedEffect
        val now = nowEpochSeconds.value
        // Wait for the canvas to be measured, and for today's grid when "Now" reloaded today.
        if (viewportPx == 0 || now < layout.windowStartSec || now >= layout.windowEndSec) return@LaunchedEffect
        val maxScroll = (layout.totalWidthPx - viewportPx).coerceAtLeast(0f)
        val target = (layout.xFor(now) - viewportPx * NOW_REVEAL_FRACTION).coerceIn(0f, maxScroll).roundToInt()
        if (request == NowScroll.ANIMATE) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
        onNowScrolled()
    }

    // Paging (GD4): the rows on screen go to the ViewModel, which loads their page and the next one
    // when they come near it; rows of pages not loaded yet are their channel cell and an empty track.
    val currentOnRowsVisible by rememberUpdatedState(onRowsVisible)
    LaunchedEffect(listState) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null else visible.first().index to visible.last().index
        }.filterNotNull()
            .distinctUntilChanged()
            .collect { (first, last) -> currentOnRowsVisible(first, last) }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // The now line, across the ruler and every row. Draw-phase reads only.
                    val now = nowEpochSeconds.value
                    if (now < layout.windowStartSec || now >= layout.windowEndSec) return@drawWithContent
                    val x = canvasLeftPx + layout.xFor(now) - scrollState.value
                    if (x < canvasLeftPx || x > size.width) return@drawWithContent
                    drawLine(
                        color = CinemaAccent,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = nowLineWidthPx,
                    )
                },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(channelColumnWidth))
            TimeRuler(
                layout = layout,
                composeRange = composeRange,
                scrollState = scrollState,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(CinemaSpacing.xl)
                        .onSizeChanged { viewportPx = it.width },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = channelRows.size,
                key = { channelRows[it].channel.id },
                contentType = { "guide_row" },
            ) { index ->
                val row = channelRows[index]
                GuideRow(
                    row = row,
                    layout = layout,
                    composeRange = composeRange,
                    scrollState = scrollState,
                    nowEpochSeconds = nowEpochSeconds,
                    channelColumnWidth = channelColumnWidth,
                    onChannelClick = { onChannelClick(row.channel) },
                    onProgramClick = { program -> onProgramClick(program, row.channel) },
                )
            }
        }
    }
}

@Composable
private fun TimeRuler(
    layout: GuideLayout,
    composeRange: LongRange,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val ticks = remember(layout, composeRange) { layout.tickMarks(composeRange.first, composeRange.last) }
    val tickWidthPx = (GuideLayout.DEFAULT_TICK_STEP_MIN * layout.pxPerMinute).roundToInt().coerceAtLeast(1)
    val totalWidthPx = layout.totalWidthPx.roundToInt()

    Box(modifier = modifier.clipToBounds().horizontalScroll(scrollState)) {
        Layout(
            content = {
                ticks.forEach { tick ->
                    key(tick.epochSec) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight()) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(MobileDimensions.dividerThin)
                                        .fillMaxHeight()
                                        .background(CinemaTextTertiary),
                            )
                            Text(
                                text = TimeFormat.formatTime(tick.epochSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = CinemaTextSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(start = CinemaSpacing.xxs),
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val height = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: 0
            val placeables =
                measurables.map { it.measure(Constraints(maxWidth = tickWidthPx, minHeight = height, maxHeight = height)) }
            layout(totalWidthPx, height) {
                placeables.forEachIndexed { i, placeable -> placeable.place(ticks[i].x.roundToInt(), 0) }
            }
        }
    }
}

@Composable
private fun GuideRow(
    row: EpgChannelRow,
    layout: GuideLayout,
    composeRange: LongRange,
    scrollState: ScrollState,
    nowEpochSeconds: State<Long>,
    channelColumnWidth: Dp,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit,
) {
    val cells = remember(row.programs, layout, composeRange) { layout.cellsIn(row.programs, composeRange.first, composeRange.last) }
    val totalWidthPx = layout.totalWidthPx.roundToInt()

    Row(modifier = Modifier.fillMaxWidth().height(MobileDimensions.epgProgramHeight)) {
        ChannelCell(
            channel = row.channel,
            onClick = onChannelClick,
            modifier = Modifier.width(channelColumnWidth).fillMaxHeight(),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .horizontalScroll(scrollState),
        ) {
            Layout(
                content = {
                    // The track: what a row without listings shows, and the gaps between programmes.
                    Box(
                        modifier =
                            Modifier
                                .padding(vertical = CinemaSpacing.xxs)
                                .alpha(CinemaAlpha.tint)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    cells.forEach { cell ->
                        key(cell.program.id) {
                            ProgramCell(
                                cell = cell,
                                scrollState = scrollState,
                                nowEpochSeconds = nowEpochSeconds,
                                onClick = { onProgramClick(cell.program) },
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val height = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: 0
                val track = measurables.first().measure(Constraints.fixed(totalWidthPx, height))
                val placeables =
                    measurables.drop(1).mapIndexed { i, measurable ->
                        measurable.measure(Constraints.fixed(cells[i].width.roundToInt().coerceAtLeast(1), height))
                    }
                layout(totalWidthPx, height) {
                    track.place(0, 0)
                    placeables.forEachIndexed { i, placeable -> placeable.place(cells[i].x.roundToInt(), 0) }
                }
            }
        }
    }
}

/** The channel's logo with its name beneath, or the name alone when it has no logo. Tap = tune. */
@Composable
private fun ChannelCell(
    channel: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasLogo = !channel.thumbnailUrl.isNullOrBlank()
    Column(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(horizontal = CinemaSpacing.xxs, vertical = CinemaSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (hasLogo) {
            CinemaThumbnail(
                url = channel.thumbnailUrl,
                fallbackLetter = channel.name.firstOrNull(),
                contentType = ThumbnailContentType.LIVE_TV,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (hasLogo) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class CellPhase { PAST, ON_AIR, UPCOMING }

@Composable
private fun ProgramCell(
    cell: GuideCell,
    scrollState: ScrollState,
    nowEpochSeconds: State<Long>,
    onClick: () -> Unit,
) {
    // The shared tick, read through derivedStateOf so a cell recomposes only when its phase flips.
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
    val radius = CinemaCornerRadius.small
    val shape = remember(radius) { RoundedCornerShape(radius) }
    val onAir = phase == CellPhase.ON_AIR

    Row(
        modifier =
            Modifier
                .padding(horizontal = MobileDimensions.dividerThin, vertical = CinemaSpacing.xxs)
                .alpha(if (phase == CellPhase.PAST) CinemaAlpha.textDisabled else 1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
    ) {
        if (onAir) {
            Box(
                modifier =
                    Modifier
                        .width(CinemaSpacing.xxs)
                        .fillMaxHeight()
                        .background(CinemaAccent),
            )
        }
        if (cell.labelFits) {
            // The label follows the left edge while the programme's start is scrolled off screen:
            // a long programme on air since before the visible window (opened at "now") showed a
            // blank bar, its title off to the left (phone UI audit, TV guide on bears). Read in
            // graphicsLayer, so scrolling moves it at draw time without recomposing the row.
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val hidden = scrollState.value - cell.x
                            translationX = hidden.coerceIn(0f, (cell.width - size.width * LABEL_MIN_SHARE).coerceAtLeast(0f))
                        }.padding(horizontal = CinemaSpacing.xxs, vertical = CinemaSpacing.xxs),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = cell.program.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (onAir) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (onAir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeFormat.formatTimeRange(cell.program.startTime, cell.program.endTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** How much of a cell's width its label keeps visible when it slides to stay on screen. */
private const val LABEL_MIN_SHARE = 0.3f
