package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The TV game reminder banner's keys (the sports teams plan → Reminders; the Nerd role plan → R).
 * The banner never takes focus: the control under it keeps focus, so it is still focused when the
 * banner closes. While the banner shows, the remote's keys go to it ([reminderBannerKeys], on the
 * screen's content): Left and Right choose Watch, Dismiss or Manage reminders, in that order (Watch
 * first, Dismiss without a channel), OK acts on the choice, Back closes the banner, and no other
 * key reaches the screen (volume keys still work). The banner goes by itself after
 * [CinemaAnimation.reminderBannerDismissMs].
 */
@Stable
class ReminderBannerState {
    /** The banner's buttons, in their order on screen. */
    internal enum class Choice { WATCH, DISMISS, MANAGE }

    /** The reminder on screen, which has the keys; null while none shows or a Dialog window is on top. */
    internal var shown: GameReminderAlert? = null

    /** The chosen button; Watch at first, which a reminder without a channel reads as Dismiss ([chosen]). */
    private var choice by mutableStateOf(Choice.WATCH)

    internal var onWatch: (GameReminderAlert) -> Unit = {}

    internal var onManage: (GameReminderAlert) -> Unit = {}

    /** The key that went down while the banner showed: its key up acts, never one from before. */
    private var down: Key? = null

    /** [alert]'s buttons: Watch only with a channel. */
    private fun choices(alert: GameReminderAlert): List<Choice> = Choice.entries.filter { it != Choice.WATCH || alert.channel != null }

    internal fun chosen(alert: GameReminderAlert): Choice = choice.takeIf { it in choices(alert) } ?: Choice.DISMISS

    internal fun close(alert: GameReminderAlert) {
        shown = null
        choice = Choice.WATCH
        down = null
        GameReminderAlerts.dismissBanner(alert)
    }

    internal fun act(alert: GameReminderAlert) {
        val acted = chosen(alert)
        close(alert)
        when (acted) {
            Choice.WATCH -> onWatch(alert)
            Choice.MANAGE -> onManage(alert)
            Choice.DISMISS -> Unit
        }
    }

    /** Moves the choice [step] buttons along, stopping at the first and the last. */
    private fun move(
        alert: GameReminderAlert,
        step: Int,
    ) {
        val all = choices(alert)
        choice = all[(all.indexOf(chosen(alert)) + step).coerceIn(0, all.lastIndex)]
    }

    /** Takes [event] while a reminder shows; volume keys go on to the system. */
    internal fun onKey(event: KeyEvent): Boolean {
        val alert = shown ?: return false
        if (event.key == Key.VolumeUp || event.key == Key.VolumeDown || event.key == Key.VolumeMute) return false
        if (event.type == KeyEventType.KeyDown) {
            down = event.key
            when (event.key) {
                Key.DirectionLeft -> move(alert, -1)
                Key.DirectionRight -> move(alert, 1)
            }
        } else if (event.type == KeyEventType.KeyUp && event.key == down) {
            down = null
            when (event.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> act(alert)
                Key.Back -> close(alert)
            }
        }
        return true
    }
}

@Composable
fun rememberReminderBannerState(): ReminderBannerState = remember { ReminderBannerState() }

/**
 * On the screen's content (every screen, the rail and their overlays, not the banner): while a
 * reminder shows, its keys go to the banner, before the focused control sees them.
 */
fun Modifier.reminderBannerKeys(state: ReminderBannerState): Modifier = onPreviewKeyEvent(state::onKey)

/**
 * The reminder that rang while the TV app is on screen ([GameReminderAlerts.banner]): "Arsenal v
 * Chelsea starts at 20:00", the best channel, and Watch (only with a channel), Dismiss and Manage
 * reminders ([onManage], the Reminders page; the banner closes). Place it
 * at the top centre, after the content, so it draws over it. While composed and the app is started,
 * it counts as the banner host, so reminders show here instead of as notifications. See
 * [ReminderBannerState] for its keys.
 */
@Composable
fun TvGameReminderBanner(
    state: ReminderBannerState,
    onWatch: (GameReminderAlert) -> Unit,
    modifier: Modifier = Modifier,
    onManage: (GameReminderAlert) -> Unit = {},
) {
    LifecycleStartEffect(Unit) {
        GameReminderAlerts.bannerHostStarted()
        onStopOrDispose { GameReminderAlerts.bannerHostStopped() }
    }
    val alert by GameReminderAlerts.banner.collectAsStateWithLifecycle()
    // A Dialog window (a CinemaAlertDialog, the trailer player) has the keys, and the banner under
    // it could not get them: it waits until the dialog closes, inside its time.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    SideEffect {
        state.shown = alert?.takeIf { windowFocused }
        state.onWatch = onWatch
        state.onManage = onManage
    }
    val current = alert ?: return
    val context = LocalContext.current
    val hasChannel = current.channel != null

    LaunchedEffect(current) {
        delay(CinemaAnimation.reminderBannerDismissMs)
        state.close(current)
    }
    if (!windowFocused) return
    val chosen = state.chosen(current)

    val shape = cornerShape(CornerRadius.medium)
    Row(
        modifier =
            modifier
                // The TV Surface's Box passes its minimum size on to its content: without this, the
                // bar would fill the whole screen. The bar keeps its own size, at the top centre.
                .wrapContentSize(Alignment.TopCenter)
                .testTag(REMINDER_BANNER_TAG)
                .padding(top = Spacing.tvSafeMarginVertical)
                .widthIn(max = TvDimensions.dialogWidthLarge)
                .background(CinemaTheme.colors.surface, shape)
                .border(TvDimensions.borderDefault, CinemaTheme.colors.glassBorder, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
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
        }
        if (hasChannel) {
            CinemaPrimaryButton(
                onClick = {
                    state.close(current)
                    onWatch(current)
                },
                text = stringResource(R.string.epg_browser_watch_now_btn),
                modifier = Modifier.reminderChoice(chosen == ReminderBannerState.Choice.WATCH),
                highlighted = chosen == ReminderBannerState.Choice.WATCH,
            )
        }
        CinemaSecondaryButton(
            onClick = { state.close(current) },
            text = stringResource(R.string.game_reminder_dismiss),
            modifier = Modifier.reminderChoice(chosen == ReminderBannerState.Choice.DISMISS),
            highlighted = chosen == ReminderBannerState.Choice.DISMISS,
        )
        CinemaSecondaryButton(
            onClick = {
                state.close(current)
                onManage(current)
            },
            text = stringResource(R.string.reminders_manage),
            modifier = Modifier.reminderChoice(chosen == ReminderBannerState.Choice.MANAGE),
            highlighted = chosen == ReminderBannerState.Choice.MANAGE,
        )
    }
}

/** A banner button: never focused (the keys choose it), and selected while chosen. */
private fun Modifier.reminderChoice(chosen: Boolean): Modifier = focusProperties { canFocus = false }.semantics { selected = chosen }

/** The banner's test tag: its bar, without the screen around it. */
internal const val REMINDER_BANNER_TAG = "game_reminder_banner"
