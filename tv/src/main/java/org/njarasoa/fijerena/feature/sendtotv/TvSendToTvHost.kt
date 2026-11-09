package org.njarasoa.fijerena.feature.sendtotv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.sync.PlayRequests
import org.njarasoa.fijerena.core.network.sync.SyncPayloads.PlayAnswer
import org.njarasoa.fijerena.core.ui.sync.PlayRequestResolver
import org.njarasoa.fijerena.core.ui.sync.PlayResolution
import org.njarasoa.fijerena.core.ui.sync.SendToTv
import org.njarasoa.fijerena.ui.components.SendBanner
import org.njarasoa.fijerena.ui.components.SendBannerState
import org.njarasoa.fijerena.ui.components.TvSendBanner

/** How long the banner stays before it answers dismissed by itself. */
const val SEND_BANNER_MS = 60_000L

/**
 * A request's route through the TV (the send to TV plan, ST-D4 to ST-D6): it answers received at
 * once, then busy on a screen that cannot show it, a refusal from the checks, or shows the banner.
 * A newer request replaces the banner's (the old one answers dismissed); a closed one removes it.
 */
internal class SendRequestHandler(
    private val resolver: PlayRequestResolver,
    private val answer: (String, String) -> Unit,
    private val done: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var banner by mutableStateOf<SendBanner?>(null)
        private set

    suspend fun update(
        held: PlayRequests.Incoming?,
        isBusy: Boolean,
        activeProfileId: String,
    ) {
        val current = banner
        if (current != null && held?.request?.requestId != current.id) {
            if (held != null) finish(current.id, PlayAnswer.DISMISSED)
            banner = null
        }
        if (held == null || banner?.id == held.request.requestId) return
        val id = held.request.requestId
        answer(id, PlayAnswer.RECEIVED)
        when {
            PlayRequests.isExpired(held, now()) -> {
                finish(id, PlayAnswer.DISMISSED)
            }

            isBusy -> {
                finish(id, PlayAnswer.BUSY)
            }

            else -> {
                when (val result = resolver.resolve(held.request)) {
                    is PlayResolution.Refused -> {
                        finish(id, result.result)
                    }

                    is PlayResolution.Playable -> {
                        val forProfile = result.profileName.takeIf { result.profileId != activeProfileId }
                        banner = SendBanner(held.request, result, forProfile, held.receivedAt)
                    }
                }
            }
        }
    }

    fun dismiss(shown: SendBanner) {
        if (banner?.id != shown.id) return
        banner = null
        finish(shown.id, PlayAnswer.DISMISSED)
    }

    suspend fun play(
        shown: SendBanner,
        onPlay: suspend (PlayResolution.Playable) -> Unit,
    ) {
        if (banner?.id != shown.id) return
        banner = null
        done(shown.id)
        onPlay(shown.playable)
        answer(shown.id, PlayAnswer.PLAYING)
    }

    private fun finish(
        id: String,
        result: String,
    ) {
        done(id)
        answer(id, result)
    }
}

/**
 * The send banner over every screen: takes each request of [PlayRequests], answers the phone, and
 * shows the banner. [isBusy] reads the current screen; [reminderShowing]: the reminder banner has
 * the keys, the send waits. [onPlay] opens the item: the banner never plays anything by itself.
 */
@Composable
fun TvSendToTvHost(
    state: SendBannerState,
    isBusy: () -> Boolean,
    activeProfileId: () -> String,
    reminderShowing: Boolean,
    onPlay: suspend (PlayResolution.Playable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val currentIsBusy by rememberUpdatedState(isBusy)
    val currentProfile by rememberUpdatedState(activeProfileId)
    val currentOnPlay by rememberUpdatedState(onPlay)
    val handler =
        remember {
            SendRequestHandler(
                resolver = PlayRequestResolver.forDevice(context),
                answer = { id, result -> SendToTv.answer(context, id, result) },
                done = PlayRequests::done,
            )
        }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        PlayRequests.incoming.collect { handler.update(it, currentIsBusy(), currentProfile()) }
    }

    val banner = handler.banner
    // The banner's own time, from when it can show; it closes at the receive time's limit too.
    LaunchedEffect(banner?.id, reminderShowing) {
        val current = banner ?: return@LaunchedEffect
        if (reminderShowing) return@LaunchedEffect
        val left = PlayRequests.MAX_AGE_MS - (System.currentTimeMillis() - current.receivedAt)
        delay(minOf(SEND_BANNER_MS, left).coerceAtLeast(0))
        state.keys.reset()
        handler.dismiss(current)
    }

    TvSendBanner(
        state = state,
        banner = banner,
        blocked = reminderShowing,
        onPlay = { shown -> scope.launch { handler.play(shown) { currentOnPlay(it) } } },
        onDismiss = handler::dismiss,
        modifier = modifier,
    )
}
