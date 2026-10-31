package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.sports.GameReminderAlert
import org.njarasoa.fijerena.core.network.sports.GameReminderAlerts
import org.njarasoa.fijerena.core.network.sports.GameReminderNotifications
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The TV game reminder banner's focus (the sports teams plan → Reminders). The banner never takes
 * focus by itself, as [TvUndoBar]: a reminder rings over whatever is on screen, full-screen
 * playback included, and must not move focus from it. Menu, or holding OK, moves focus onto the
 * banner's buttons ([reminderBannerKeys]); Back or Dismiss gives it back to the control that had
 * it ([reminderBannerContent], a `focusRestorer` on the screen's content). Untouched, the banner
 * goes after [CinemaAnimation.reminderBannerDismissMs].
 */
@Stable
class ReminderBannerState {
    internal val contentFocus = FocusRequester()
    internal val watchFocus = FocusRequester()
    internal val dismissFocus = FocusRequester()

    /** A reminder shows. */
    internal var showing by mutableStateOf(false)

    /** The buttons may take focus: set by Menu or a held OK, cleared when the banner closes. */
    internal var armed by mutableStateOf(false)

    /** Focus is on one of the banner's buttons. */
    internal var holdsFocus by mutableStateOf(false)

    /** Each Menu or held OK asks the banner to take focus once more. */
    internal var focusRequests by mutableIntStateOf(0)

    /** OK is held from the press that armed the banner: the rest of that press is swallowed. */
    internal var swallowingOk = false

    internal fun arm() {
        armed = true
        focusRequests++
    }

    /** Gives focus back to the screen's content (the control that had it), then closes [alert]. */
    internal fun close(alert: GameReminderAlert?) {
        if (holdsFocus) contentFocus.requestFocus(FocusDirection.Enter)
        armed = false
        alert?.let(GameReminderAlerts::dismissBanner)
    }
}

@Composable
fun rememberReminderBannerState(): ReminderBannerState = remember { ReminderBannerState() }

/**
 * On the screen's content (every screen, the rail and their overlays, not the banner): focus
 * entering it from the banner goes back to the control that had it.
 */
fun Modifier.reminderBannerContent(state: ReminderBannerState): Modifier = focusRequester(state.contentFocus).focusRestorer().focusGroup()

/**
 * On the screen's content, before [reminderBannerContent]: while a reminder shows and focus is on
 * the content, Menu or a held OK moves focus onto the banner. Previewed before the focused control
 * sees the key, as [undoOnRemoteKeys]; a short OK still does the focused control's job.
 */
fun Modifier.reminderBannerKeys(state: ReminderBannerState): Modifier =
    onPreviewKeyEvent { event ->
        val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (isOk && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) state.swallowingOk = false
        when {
            isOk && state.swallowingOk -> {
                if (event.type == KeyEventType.KeyUp) state.swallowingOk = false
                true
            }

            !state.showing || state.holdsFocus -> {
                false
            }

            event.key == Key.Menu -> {
                if (event.type == KeyEventType.KeyDown) state.arm()
                true
            }

            isOk && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                state.swallowingOk = true
                state.arm()
                true
            }

            else -> {
                false
            }
        }
    }

/**
 * The reminder that rang while the TV app is on screen ([GameReminderAlerts.banner]): "Arsenal v
 * Chelsea starts at 20:00", the best channel, and Watch (only with a channel) and Dismiss. Place it
 * at the top centre, after the content, so it draws over it. While composed and the app is started,
 * it counts as the banner host, so reminders show here instead of as notifications. See
 * [ReminderBannerState] for its focus.
 */
@Composable
fun TvGameReminderBanner(
    state: ReminderBannerState,
    onWatch: (GameReminderAlert) -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleStartEffect(Unit) {
        GameReminderAlerts.bannerHostStarted()
        onStopOrDispose { GameReminderAlerts.bannerHostStopped() }
    }
    val alert by GameReminderAlerts.banner.collectAsStateWithLifecycle()
    SideEffect { state.showing = alert != null }
    val current = alert ?: return
    val context = LocalContext.current
    val hasChannel = current.channel != null

    LaunchedEffect(current, state.holdsFocus) {
        if (!state.holdsFocus) {
            delay(CinemaAnimation.reminderBannerDismissMs)
            state.close(current)
        }
    }
    LaunchedEffect(state.focusRequests) {
        if (state.focusRequests > 0 && state.armed) {
            (if (hasChannel) state.watchFocus else state.dismissFocus).requestFocusWithRetry(fallback = state.dismissFocus)
        }
    }

    val shape = cornerShape(CornerRadius.medium)
    Row(
        modifier =
            modifier
                .padding(top = Spacing.tvSafeMarginVertical)
                .widthIn(max = TvDimensions.dialogWidthLarge)
                .background(CinemaTheme.colors.surface, shape)
                .border(TvDimensions.borderDefault, CinemaTheme.colors.glassBorder, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .onFocusChanged { state.holdsFocus = it.hasFocus }
                // On the banner: Back closes it, and the D-pad stays on its buttons.
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.Back -> {
                            if (event.type == KeyEventType.KeyUp) state.close(current)
                            true
                        }

                        Key.DirectionLeft -> {
                            if (event.type == KeyEventType.KeyDown && hasChannel) state.watchFocus.requestFocus()
                            true
                        }

                        Key.DirectionRight -> {
                            if (event.type == KeyEventType.KeyDown) state.dismissFocus.requestFocus()
                            true
                        }

                        Key.DirectionUp, Key.DirectionDown -> {
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CinemaText(
                text = GameReminderNotifications.title(context, current),
                role = TextRole.Heading,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            CinemaText(
                text = GameReminderNotifications.detail(context, current),
                tone = TextTone.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!state.holdsFocus) {
                CinemaText(text = stringResource(R.string.game_reminder_banner_hint), role = TextRole.Caption, tone = TextTone.Tertiary)
            }
        }
        if (hasChannel) {
            CinemaPrimaryButton(
                onClick = {
                    state.close(current)
                    onWatch(current)
                },
                text = stringResource(R.string.game_reminder_watch),
                modifier = Modifier.focusRequester(state.watchFocus).focusProperties { canFocus = state.armed },
            )
        }
        CinemaSecondaryButton(
            onClick = { state.close(current) },
            text = stringResource(R.string.game_reminder_dismiss),
            modifier = Modifier.focusRequester(state.dismissFocus).focusProperties { canFocus = state.armed },
        )
    }
}
