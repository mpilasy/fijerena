package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.view.LayoutInflater
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService

/**
 * Bare ExoPlayer surface bound to the single app-wide playback engine
 * ([StreamingPlaybackService]), sized entirely by [modifier]. No focus grabbing, no key handling,
 * no overlays — safe to embed as a small preview pane (TV Live TV split / mobile docked
 * mini-player) or as the full-screen surface.
 *
 * Extracted from the TV `PlayerScreen` so the full-screen player and the embedded preview share
 * one surface implementation. The surface always shows whatever the shared engine is currently
 * playing; the caller decides what that is.
 */
@Composable
fun EmbeddedPlayerSurface(
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    // Use a TextureView instead of the default SurfaceView. Required when this surface sits next to
    // a scrolling/recomposing sibling (the Live TV preview pane beside the channel list) — a
    // SurfaceView there stalls the main thread and ANRs. Full-screen playback keeps SurfaceView.
    useTextureView: Boolean = false,
) {
    // The playback service is created asynchronously; bump a tick once it's ready (and if it is
    // recycled) so the AndroidView update block re-runs and (re)binds the live player.
    var bindTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        StreamingPlaybackService.awaitInstance()
        bindTick++
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
                this.resizeMode = resizeMode
                // Block all native focus so the surface never steals D-pad focus from the list.
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        modifier = modifier,
        update = { view ->
            // Read bindTick so this block re-runs when the service becomes ready.
            @Suppress("UNUSED_EXPRESSION")
            bindTick
            view.resizeMode = resizeMode
            val player = StreamingPlaybackService.getInstance()?.getPlayer()
            if (view.player != player) {
                view.player = player
            }
        },
    )
}
