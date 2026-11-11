package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService

/**
 * Holds guide indexing back while something plays (the pausable guide indexing plan):
 * [await] returns at once when nothing has played for [quietMs], else waits, checking every
 * [pollMs], until that is true. The quiet time covers the moments playback stops on its own: a
 * channel change, a reconnect, the preview going full screen. One instance per run, so the last
 * time it saw playback carries from one batch to the next.
 */
class PlaybackPause internal constructor(
    private val isPlaying: () -> Boolean,
    private val quietMs: Long = QUIET_MS,
    private val pollMs: Long = POLL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private var lastPlayingAtMs: Long? = null

    /**
     * Waits until nothing has played for [quietMs]. [onWaiting] is told true when the wait starts
     * and false when it ends (not called when there was no wait). Returns how long it waited.
     */
    suspend fun await(onWaiting: (Boolean) -> Unit = {}): Long {
        val start = clock()
        var waiting = false
        while (true) {
            val now = clock()
            if (isPlaying()) lastPlayingAtMs = now
            val last = lastPlayingAtMs
            if (last == null || now - last >= quietMs) break
            if (!waiting) {
                waiting = true
                onWaiting(true)
            }
            sleep(pollMs)
        }
        if (waiting) onWaiting(false)
        return clock() - start
    }

    companion object {
        const val QUIET_MS = 60_000L
        const val POLL_MS = 5_000L
    }
}

/** Whether the app plays video right now: playing or buffering. */
@OptIn(UnstableApi::class)
fun isVideoPlaying(): Boolean {
    val state = StreamingPlaybackService.getInstance()?.playbackState?.value ?: return false
    return state is PlaybackState.Playing || state is PlaybackState.Buffering
}

/**
 * Whether anything plays on the device: this app's video, or sound from any app (YouTube, a game
 * with sound), which Android tells every app without a permission. Guide indexing waits for both
 * (the pausable guide indexing plan, IX4).
 */
fun isMediaPlaying(context: Context): Boolean =
    isVideoPlaying() || context.getSystemService(AudioManager::class.java)?.isMusicActive == true
