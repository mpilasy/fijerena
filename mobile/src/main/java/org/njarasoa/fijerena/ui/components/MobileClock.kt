package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.LocalTextTone
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import java.util.Date

/**
 * The clock at the top right of every phone screen, at the player controls' clock's place
 * (docs/plans/20261025_tv-clock-and-full-screen-recent-plan.md). The phone copy of TV's TvClock.
 *
 * - The nav host owns one [MobileClockState], provides it as [LocalMobileClock] and draws
 *   [MobileClockOverlay] over the screens.
 * - Full-screen playback, picture-in-picture and the Live TV dock when it covers the screen call
 *   [HideMobileClock]: the player's controls have their own clock.
 * - Each top bar takes [mobileClockTopBarInsets], so its title and actions stay clear of the clock.
 *
 * Without a provider (previews, tests) [LocalMobileClock] is null and all of this does nothing.
 */
@Stable
class MobileClockState {
    private var hideCount by mutableIntStateOf(0)

    /** Off for the whole app: the setting is off, or the profile is a child's. */
    var off by mutableStateOf(false)

    /** The clock's drawn width, for [mobileClockTopBarInsets]. */
    var width by mutableStateOf(Dp.Unspecified)
        internal set

    /** False while off or while any screen holds [HideMobileClock]. */
    val visible: Boolean get() = !off && hideCount == 0

    internal fun hide() {
        hideCount++
    }

    internal fun show() {
        hideCount = (hideCount - 1).coerceAtLeast(0)
    }
}

val LocalMobileClock = staticCompositionLocalOf<MobileClockState?> { null }

/** Hides the clock while this is composed (full-screen playback, picture-in-picture). */
@Composable
fun HideMobileClock() {
    val clock = LocalMobileClock.current ?: return
    DisposableEffect(clock) {
        clock.hide()
        onDispose { clock.show() }
    }
}

/**
 * The window insets for a top bar (Material's TopAppBar default), plus the clock's corner at the
 * end while the clock shows. With no clock, the same as the default.
 */
@Composable
fun mobileClockTopBarInsets(): WindowInsets {
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    val clock = LocalMobileClock.current
    if (clock == null || !clock.visible || !clock.width.isSpecified) return insets
    val clearance = clockClearance(clock.width)
    return if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        insets.add(WindowInsets(right = clearance))
    } else {
        insets.add(WindowInsets(left = clearance))
    }
}

/** The clock over the screens, at the top right; nothing while [MobileClockState.visible] is false. */
@Composable
fun MobileClockOverlay(state: MobileClockState) {
    if (!state.visible) return
    val density = LocalDensity.current
    // The same place as the player controls' clock: inside the safe area, CinemaSpacing.md in.
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .padding(CinemaSpacing.md),
    ) {
        MobileClockText(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .onSizeChanged { with(density) { state.width = it.width.toDp() } },
        )
    }
}

/** The time, in the system's 12/24 h format, ticking each second. Shared with the player's controls. */
@Composable
fun MobileClockText(
    modifier: Modifier = Modifier,
    tone: TextTone = LocalTextTone.current,
) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000L)
        }
    }
    CinemaText(text = TimeFormat.formatClockTime(Date(tick)), role = TextRole.Heading, tone = tone, modifier = modifier)
}

/**
 * How far a top bar's end moves in for the clock: see [mobileClockTopBarInsets]. The clock sits
 * CinemaSpacing.md in from the safe edge; the bar's actions have a small end padding of their own.
 */
internal fun clockClearance(clockWidth: Dp): Dp = clockWidth + CinemaSpacing.md + CinemaSpacing.xxs
