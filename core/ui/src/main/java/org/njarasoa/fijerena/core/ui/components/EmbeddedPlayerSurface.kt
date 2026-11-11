package org.njarasoa.fijerena.core.ui.components

import android.view.LayoutInflater
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.R
import java.lang.ref.WeakReference

/**
 * Bare ExoPlayer surface bound to the single app-wide playback engine ([StreamingPlaybackService]),
 * sized by [modifier]. No focus grabbing, no key handling, no overlays: used for the preview panes
 * (TV Live TV split, mobile docked mini-player) and for full screen. It shows whatever the shared
 * engine plays; the caller decides what that is.
 */
@OptIn(UnstableApi::class)
@Composable
fun EmbeddedPlayerSurface(
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    // Use a TextureView instead of the default SurfaceView. Required when this surface sits next to
    // a scrolling/recomposing sibling (the Live TV preview pane beside the channel list) — a
    // SurfaceView there stalls the main thread and ANRs. Full-screen playback keeps SurfaceView.
    useTextureView: Boolean = false,
    // Pixels at the bottom the subtitles keep clear of (0: none, Media3's own placement). The
    // players lift them above their controls while those are up. The subtitle view's padding, not
    // Media3's bottom padding fraction: that one only moves cues without a line position, and
    // many subtitle files give every cue one.
    subtitleBottomInsetPx: Int = 0,
    // Full-screen players: keep the last frame on screen while the same stream waits or reconnects,
    // instead of black. A new stream still starts from black (newStreamStarting below). The preview
    // panes keep Media3's default: black whenever the player has no tracks.
    keepLastFrame: Boolean = false,
    // Gets the video view, so the frame on screen can be copied.
    sceneFrame: SceneFrame? = null,
) {
    // The playback service is created asynchronously; bump a tick once it's ready (and if it is
    // recycled) so the AndroidView update block re-runs and (re)binds the live player.
    var bindTick by remember { mutableIntStateOf(0) }
    // True until the stream on screen draws its first frame; black until then.
    var newStreamStarting by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val service = StreamingPlaybackService.awaitInstanceOrNull() ?: return@LaunchedEffect
        bindTick++
        if (keepLastFrame) service.newStreamStarting.collect { newStreamStarting = it }
    }

    AndroidView(
        factory = { ctx ->
            val view =
                if (useTextureView) {
                    // surface_type is fixed at inflation, so a TextureView-backed PlayerView must
                    // come from XML.
                    LayoutInflater.from(ctx).inflate(R.layout.view_embedded_texture_player, null) as PlayerView
                } else {
                    PlayerView(ctx)
                }
            view.apply {
                useController = false
                keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                // PlayerView is transparent by default, so the themed background (a dark navy, say) would
                // show through the letterbox/pillarbox bars RESIZE_MODE_FIT leaves. Bars are black in every theme.
                setBackgroundColor(android.graphics.Color.BLACK)
                // Media3 closes its shutter (black) whenever the player has no tracks: on every
                // reconnect of the same stream too. Kept open here; the update block closes it for a
                // new stream.
                setKeepContentOnPlayerReset(keepLastFrame)
                this.resizeMode = resizeMode
                // Subtitle text sized on the whole view, as PlayerView sets it (the device's caption
                // scale included) but ignoring padding, so lifting them above the controls
                // (subtitleBottomInsetPx) doesn't shrink them.
                val captioning = ctx.getSystemService(CaptioningManager::class.java)
                val captionScale = if (captioning?.isEnabled == true) captioning.fontScale else 1f
                subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionScale, true)
                // Block all native focus so the surface never steals D-pad focus from the list.
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                sceneFrame?.videoView = WeakReference(videoSurfaceView)
            }
        },
        modifier = modifier,
        update = { view ->
            // Read bindTick so this block re-runs when the service becomes ready.
            @Suppress("UNUSED_EXPRESSION")
            bindTick
            view.resizeMode = resizeMode
            view.subtitleView?.let { subtitles ->
                if (subtitles.paddingBottom != subtitleBottomInsetPx) subtitles.setPadding(0, 0, 0, subtitleBottomInsetPx)
            }
            val player = StreamingPlaybackService.getInstance()?.getPlayer()
            if (view.player != player) {
                view.player = player
            }
            // The service's own value too: the state above lags it, and closing the shutter after
            // the first frame (which opens it) would leave the stream black.
            val starting = newStreamStarting && StreamingPlaybackService.getInstance()?.newStreamStarting?.value == true
            if (keepLastFrame && starting) {
                // A new stream: the surface still holds the previous one's last frame. The shutter
                // (Media3's black cover) hides it; PlayerView opens it again on the new stream's
                // first frame.
                view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = android.view.View.VISIBLE
            }
            if (sceneFrame != null && sceneFrame.videoView?.get() !== view.videoSurfaceView) {
                sceneFrame.videoView = WeakReference(view.videoSurfaceView)
            }
        },
        // The shared ExoPlayer outlives this composable and keeps the last view it was bound to as its
        // video output. Without detaching here, every leave-and-return leaks the old PlayerView and its
        // Surface (a SurfaceFlinger resource, not only Java heap) for the life of the process.
        onRelease = { view ->
            view.player = null
            sceneFrame?.videoView = null
        },
    )
}
