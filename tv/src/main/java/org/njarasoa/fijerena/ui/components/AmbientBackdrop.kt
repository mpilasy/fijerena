package org.njarasoa.fijerena.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalPosterBackdrop
import org.njarasoa.fijerena.core.ui.theme.cinemaGround

/**
 * Full-bleed decorative background: a heavily blurred, saturated wash of the given image (channel
 * logo / poster art) over a theme-accent gradient. Cross-dissolves when [imageUrl] changes.
 * Visual only (no focus or clicks), so it can go behind any screen's root content.
 *
 * - API 31+ with a non-blank [imageUrl]: blurred, saturated image wash over the gradient.
 * - API < 31 with a non-blank [imageUrl]: there is no `RenderEffect` blur (Shield TVs on Android
 *   11), so the gradient is tinted with the image's dominant colour ([Palette], decoded at 64 px to
 *   stay cheap on old hardware).
 * - No image: the gradient wash only.
 * - [blur] false: the API < 31 tint on every device. For screens with live video in front: the GPU
 *   of a weak device (Chromecast HD) cannot redraw the blur under every video frame, and the video
 *   stutters.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    blurRadius: Float = 140f,
    imageAlpha: Float = CinemaAlpha.imageOverlay,
    blur: Boolean = true,
) {
    // Off (the profile's poster background setting): the theme's background alone, nothing decoded.
    val shownUrl = imageUrl.takeIf { LocalPosterBackdrop.current }
    val palette = CinemaTheme.colors
    val supportsBlur = blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val extractedTint by
        produceState<Color?>(initialValue = null, shownUrl, supportsBlur) {
            value = null
            if (supportsBlur || shownUrl.isNullOrBlank()) return@produceState
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(shownUrl)
                                .allowHardware(false)
                                .size(64)
                                .build()
                        val result = SingletonImageLoader.get(context).execute(request)
                        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
                        bitmap?.let { bmp ->
                            val swatch =
                                Palette.from(bmp).generate().let {
                                    it.dominantSwatch ?: it.vibrantSwatch ?: it.mutedSwatch
                                }
                            swatch?.let { Color(it.rgb) }
                        }
                    }.getOrNull()
                }
        }

    val crossfadeTarget = shownUrl.takeIf { !it.isNullOrBlank() && supportsBlur }

    // Remembered, so the RenderEffect is built again only when blurRadius changes, not on every redraw.
    // supportsBlur never changes for a call site, so the gate around remember() is stable. It must stay
    // gated: RenderEffect is API 31+ and would crash the older devices (Shield TVs on Android 11).
    val ambientRenderEffect =
        if (supportsBlur) {
            remember(blurRadius) {
                val saturationMatrix = ColorMatrix().apply { setSaturation(1.35f) }
                val blurEffect =
                    android.graphics.RenderEffect
                        .createBlurEffect(
                            blurRadius,
                            blurRadius,
                            android.graphics.Shader.TileMode.CLAMP,
                        )
                android.graphics.RenderEffect
                    .createColorFilterEffect(
                        ColorMatrixColorFilter(saturationMatrix),
                        blurEffect,
                    ).asComposeRenderEffect()
            }
        } else {
            null
        }

    Box(modifier = modifier.fillMaxSize().cinemaGround(palette, extractedTint)) {
        Crossfade(
            targetState = crossfadeTarget,
            animationSpec = tween(600),
            label = "ambient_backdrop",
        ) { url ->
            if (url != null) {
                @Suppress("NewApi")
                AsyncImage(
                    model = remember(context, url) { ImageRequest.Builder(context).data(url).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = imageAlpha,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                renderEffect = ambientRenderEffect
                            },
                )
            }
        }
        // The wash takes the image's own brightness: a light logo turned the whole screen pale and the white
        // and grey text over it vanished. The theme's background over it caps the brightness, so text stays
        // readable whatever the image.
        if (crossfadeTarget != null) {
            Box(modifier = Modifier.fillMaxSize().background(palette.background.copy(alpha = BACKDROP_SCRIM_ALPHA)))
        }
    }
}

/** How much of the theme's background covers the image wash: enough that white text always reads. */
private const val BACKDROP_SCRIM_ALPHA = 0.75f
