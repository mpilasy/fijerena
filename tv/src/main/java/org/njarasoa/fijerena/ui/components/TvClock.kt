package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.theme.Spacing
import java.util.Date

/**
 * The clock at the top right of every TV screen, at the player controls' clock's place
 * (docs/plans/20261025_tv-clock-and-full-screen-recent-plan.md).
 *
 * - The nav host owns one [TvClockState], provides it as [LocalTvClock] and draws [TvClockOverlay]
 *   over the screens.
 * - Full-screen playback calls [HideTvClock]: the player's controls have their own clock.
 * - What a screen puts at the top right takes [clearOfTvClock] (moves left) or [belowTvClock]
 *   (moves down), so the clock never covers it.
 *
 * Without a provider (previews, tests) [LocalTvClock] is null and all of this does nothing.
 */
@Stable
class TvClockState {
    private var hideCount by mutableIntStateOf(0)

    /** Off for the whole app, e.g. for a child profile. */
    var off by mutableStateOf(false)

    /** The clock's drawn width and height, for [clearOfTvClock] and [belowTvClock]. */
    var width by mutableStateOf(Dp.Unspecified)
        internal set
    var height by mutableStateOf(Dp.Unspecified)
        internal set

    /** False while off or while any screen holds [HideTvClock]. */
    val visible: Boolean get() = !off && hideCount == 0

    internal fun hide() {
        hideCount++
    }

    internal fun show() {
        hideCount = (hideCount - 1).coerceAtLeast(0)
    }
}

val LocalTvClock = staticCompositionLocalOf<TvClockState?> { null }

/** Hides the clock while this is composed (full-screen playback). */
@Composable
fun HideTvClock() {
    val clock = LocalTvClock.current ?: return
    DisposableEffect(clock) {
        clock.hide()
        onDispose { clock.show() }
    }
}

/**
 * Keeps the clock's corner free: an end padding of the clock's width while it shows. For the row
 * at the top right of a screen inside the safe margins (a header's buttons).
 */
fun Modifier.clearOfTvClock(): Modifier =
    composed {
        val clock = LocalTvClock.current
        if (clock == null || !clock.visible || !clock.width.isSpecified) {
            this
        } else {
            this.padding(end = clockClearance(clock.width))
        }
    }

/**
 * Keeps the clock's corner free: a top padding of the clock's height while it shows. For a column
 * at the top right of a screen inside the safe margins, too narrow to move left (the Live TV
 * preview's channel list).
 */
fun Modifier.belowTvClock(): Modifier =
    composed {
        val clock = LocalTvClock.current
        if (clock == null || !clock.visible || !clock.height.isSpecified) {
            this
        } else {
            // The clock's top is Spacing.xl from the top edge, the safe margin's top too.
            this.padding(top = clock.height + Spacing.xl - Spacing.tvSafeMarginVertical + Spacing.sm)
        }
    }

/** The clock over the screens, at the top right; nothing while [TvClockState.visible] is false. */
@Composable
fun TvClockOverlay(state: TvClockState) {
    if (!state.visible) return
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().padding(horizontal = Spacing.xxl, vertical = Spacing.xl)) {
        // No panel behind it: a soft halo keeps it readable on bright art.
        TvClockText(
            halo = true,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .onSizeChanged {
                        with(density) {
                            state.width = it.width.toDp()
                            state.height = it.height.toDp()
                        }
                    },
        )
    }
}

/**
 * The time, in the system's 12/24 h format, ticking each second. Shared with the player's controls.
 * [halo]: drawn as [TvBackdropText], for a clock with no panel behind it.
 */
@Composable
fun TvClockText(
    modifier: Modifier = Modifier,
    halo: Boolean = false,
) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val time = TimeFormat.formatClockTime(Date(tick))
    if (halo) {
        // One halo is too faint on white art: the same text drawn again darkens it.
        Box(modifier) { repeat(CLOCK_HALO_LAYERS) { TvBackdropText(text = time, role = TextRole.Heading) } }
    } else {
        CinemaText(text = time, role = TextRole.Heading, modifier = modifier)
    }
}

/**
 * How far a screen's top-right item moves left for the clock: see [clearOfTvClock]. The clock sits
 * Spacing.xxl from the right edge, the safe margin a little further in.
 */
internal fun clockClearance(clockWidth: Dp): Dp = clockWidth + Spacing.xxl - Spacing.tvSafeMarginHorizontal + Spacing.lg

/** How many halos the screens' clock draws under its digits. */
private const val CLOCK_HALO_LAYERS = 3
