package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.sports.GameReminderAlert
import org.njarasoa.fijerena.core.network.sports.GameReminderAlerts
import org.njarasoa.fijerena.core.network.sports.GameReminderNotifications
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton

/**
 * The TV game reminder banner's keys (the sports teams plan → Reminders; the Nerd role plan → R):
 * Left and Right choose Watch, Dismiss or Manage reminders, in that order (Watch first, Dismiss
 * without a channel), and OK acts on the choice. See [TvBannerKeys]. The banner goes by itself
 * after [CinemaAnimation.reminderBannerDismissMs].
 */
@Stable
class ReminderBannerState {
    /** The banner's buttons, in their order on screen. */
    internal enum class Choice { WATCH, DISMISS, MANAGE }

    internal var onWatch: (GameReminderAlert) -> Unit = {}

    internal var onManage: (GameReminderAlert) -> Unit = {}

    internal val keys =
        TvBannerKeys<GameReminderAlert, Choice>(
            first = Choice.WATCH,
            fallback = Choice.DISMISS,
            // Watch only with a channel.
            choices = { alert -> Choice.entries.filter { it != Choice.WATCH || alert.channel != null } },
            onBack = { GameReminderAlerts.dismissBanner(it) },
            onOk = { alert, choice ->
                GameReminderAlerts.dismissBanner(alert)
                when (choice) {
                    Choice.WATCH -> onWatch(alert)
                    Choice.MANAGE -> onManage(alert)
                    Choice.DISMISS -> Unit
                }
            },
        )

    internal fun close(alert: GameReminderAlert) {
        keys.reset()
        GameReminderAlerts.dismissBanner(alert)
    }
}

@Composable
fun rememberReminderBannerState(): ReminderBannerState = remember { ReminderBannerState() }

/**
 * On the screen's content (every screen, the rail and their overlays, not the banner): while a
 * reminder shows, its keys go to the banner, before the focused control sees them.
 */
fun Modifier.reminderBannerKeys(state: ReminderBannerState): Modifier = onPreviewKeyEvent(state.keys::onKey)

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
        state.keys.shown = alert?.takeIf { windowFocused }
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
    val chosen = state.keys.chosen(current)

    TvBannerPanel(
        tag = REMINDER_BANNER_TAG,
        title = GameReminderNotifications.title(context, current),
        detail = GameReminderNotifications.detail(context, current),
        modifier = modifier,
    ) {
        if (hasChannel) {
            CinemaPrimaryButton(
                onClick = {
                    state.close(current)
                    onWatch(current)
                },
                text = stringResource(R.string.epg_browser_watch_now_btn),
                modifier = Modifier.bannerChoice(chosen == ReminderBannerState.Choice.WATCH),
                highlighted = chosen == ReminderBannerState.Choice.WATCH,
            )
        }
        CinemaSecondaryButton(
            onClick = { state.close(current) },
            text = stringResource(R.string.game_reminder_dismiss),
            modifier = Modifier.bannerChoice(chosen == ReminderBannerState.Choice.DISMISS),
            highlighted = chosen == ReminderBannerState.Choice.DISMISS,
        )
        CinemaSecondaryButton(
            onClick = {
                state.close(current)
                onManage(current)
            },
            text = stringResource(R.string.reminders_manage),
            modifier = Modifier.bannerChoice(chosen == ReminderBannerState.Choice.MANAGE),
            highlighted = chosen == ReminderBannerState.Choice.MANAGE,
        )
    }
}

/** The banner's test tag: its bar, without the screen around it. */
internal const val REMINDER_BANNER_TAG = "game_reminder_banner"
