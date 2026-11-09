package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sync.PlayResolution
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton

/** What the send banner shows: [request], with [forProfile] set when Play changes this TV's profile. */
data class SendBanner(
    val request: SyncPayloads.PlayRequest,
    val playable: PlayResolution.Playable,
    val forProfile: String?,
    val receivedAt: Long,
) {
    val id: String get() = request.requestId
}

/** The keys of the send banner: Play first, then Dismiss; Back dismisses. See [TvBannerKeys]. */
@Stable
class SendBannerState {
    internal enum class Choice { PLAY, DISMISS }

    internal var onPlay: (SendBanner) -> Unit = {}

    internal var onDismiss: (SendBanner) -> Unit = {}

    internal val keys =
        TvBannerKeys<SendBanner, Choice>(
            first = Choice.PLAY,
            fallback = Choice.DISMISS,
            choices = { Choice.entries },
            onBack = { onDismiss(it) },
            onOk = { banner, choice -> if (choice == Choice.PLAY) onPlay(banner) else onDismiss(banner) },
        )
}

@Composable
fun rememberSendBannerState(): SendBannerState = remember { SendBannerState() }

/** On the screen's content: while a send banner shows, its keys go to it. See [TvBannerKeys]. */
fun Modifier.sendBannerKeys(state: SendBannerState): Modifier = onPreviewKeyEvent(state.keys::onKey)

/**
 * A phone sends something to this TV (the send to TV plan): "<device> sends Malcolm X · From
 * 42:10", and Play, Dismiss. It never takes focus, and stays out while a Dialog window has the
 * keys or [blocked] (a reminder banner shows). Place it after the content, at the top centre.
 */
@Composable
fun TvSendBanner(
    state: SendBannerState,
    banner: SendBanner?,
    onPlay: (SendBanner) -> Unit,
    onDismiss: (SendBanner) -> Unit,
    modifier: Modifier = Modifier,
    blocked: Boolean = false,
) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val showing = banner?.takeIf { windowFocused && !blocked }
    SideEffect {
        state.keys.shown = showing
        state.onPlay = onPlay
        state.onDismiss = onDismiss
    }
    val current = showing ?: return
    val chosen = state.keys.chosen(current)
    val request = current.request
    val from = request.fromDeviceName.ifBlank { stringResource(R.string.send_to_tv_a_device) }

    // Live has no position to show.
    val position =
        request.positionMs
            ?.takeIf { it > 0 && request.contentType != ContentType.LIVE_TV }
            ?.let { stringResource(R.string.send_to_tv_from_position, formatTime(it)) }
    val forProfile = current.forProfile?.let { stringResource(R.string.send_to_tv_for_profile, it) }

    TvBannerPanel(
        tag = SEND_BANNER_TAG,
        title = stringResource(R.string.send_to_tv_banner_title, from, request.title),
        detail = listOfNotNull(request.subtitle, position, forProfile).joinToString(" · "),
        modifier = modifier,
    ) {
        CinemaPrimaryButton(
            onClick = { onPlay(current) },
            text = stringResource(R.string.player_play),
            modifier = Modifier.bannerChoice(chosen == SendBannerState.Choice.PLAY),
            highlighted = chosen == SendBannerState.Choice.PLAY,
        )
        CinemaSecondaryButton(
            onClick = { onDismiss(current) },
            text = stringResource(R.string.game_reminder_dismiss),
            modifier = Modifier.bannerChoice(chosen == SendBannerState.Choice.DISMISS),
            highlighted = chosen == SendBannerState.Choice.DISMISS,
        )
    }
}

/** The banner's test tag: its bar, without the screen around it. */
internal const val SEND_BANNER_TAG = "send_banner"
