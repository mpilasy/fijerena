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
 * screen's content): Left and Right choose Watch or Dismiss (Watch first, Dismiss without a
 * channel), OK acts on the choice, Back closes the banner, and no other key reaches the screen
 * (volume keys still work). The banner goes by itself after [CinemaAnimation.reminderBannerDismissMs].
 */
@Stable
class ReminderBannerState {
    /** The reminder on screen, which has the keys; null while none shows or a Dialog window is on top. */
    internal var shown: GameReminderAlert? = null

    /** Watch is chosen, else Dismiss; without a channel Dismiss is the only choice. */
    internal var watchChosen by mutableStateOf(true)

    internal var onWatch: (GameReminderAlert) -> Unit = {}

    /** The key that went down while the banner showed: its key up acts, never one from before. */
    private var down: Key? = null

    internal fun close(alert: GameReminderAlert) {
        shown = null
        watchChosen = true
        down = null
        GameReminderAlerts.dismissBanner(alert)
    }

    internal fun act(alert: GameReminderAlert) {
        val watch = watchChosen && alert.channel != null
        close(alert)
        if (watch) onWatch(alert)
    }

    /** Takes [event] while a reminder shows; volume keys go on to the system. */
    internal fun onKey(event: KeyEvent): Boolean {
        val alert = shown ?: return false
        if (event.key == Key.VolumeUp || event.key == Key.VolumeDown || event.key == Key.VolumeMute) return false
        if (event.type == KeyEventType.KeyDown) {
            down = event.key
            when (event.key) {
                Key.DirectionLeft -> watchChosen = true
                Key.DirectionRight -> watchChosen = false
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
 * Chelsea starts at 20:00", the best channel, and Watch (only with a channel) and Dismiss. Place it
 * at the top centre, after the content, so it draws over it. While composed and the app is started,
 * it counts as the banner host, so reminders show here instead of as notifications. See
 * [ReminderBannerState] for its keys.
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
    // A Dialog window (a CinemaAlertDialog, the trailer player) has the keys, and the banner under
    // it could not get them: it waits until the dialog closes, inside its time.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    SideEffect {
        state.shown = alert?.takeIf { windowFocused }
        state.onWatch = onWatch
    }
    val current = alert ?: return
    val context = LocalContext.current
    val hasChannel = current.channel != null

    LaunchedEffect(current) {
        delay(CinemaAnimation.reminderBannerDismissMs)
        state.close(current)
    }
    if (!windowFocused) return
    val watchChosen = hasChannel && state.watchChosen

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
                modifier = Modifier.reminderChoice(watchChosen),
                highlighted = watchChosen,
            )
        }
        CinemaSecondaryButton(
            onClick = { state.close(current) },
            text = stringResource(R.string.game_reminder_dismiss),
            modifier = Modifier.reminderChoice(!watchChosen),
            highlighted = !watchChosen,
        )
    }
}

/** A banner button: never focused (the keys choose it), and selected while chosen. */
private fun Modifier.reminderChoice(chosen: Boolean): Modifier = focusProperties { canFocus = false }.semantics { selected = chosen }

/** The banner's test tag: its bar, without the screen around it. */
internal const val REMINDER_BANNER_TAG = "game_reminder_banner"
