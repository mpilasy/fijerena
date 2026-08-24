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
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder

/**
 * Full-bleed decorative background: a heavily blurred, saturation-boosted wash of the given
 * image (channel logo / poster art) over a theme-accent gradient fallback. Cross-dissolves
 * when [imageUrl] changes instead of popping.
 *
 * Purely visual — no focus/click handling — so it is safe to place behind any screen's root
 * content without touching that screen's D-pad/state logic.
 *
 * - API 31+ with a non-blank [imageUrl]: blurred, saturated image wash over the gradient.
 * - API < 31 with a non-blank [imageUrl]: `RenderEffect` blur doesn't exist on this API level
 *   (e.g. Shield TVs stuck on Android 11), so instead the gradient is tinted with the image's
 *   dominant color (via [Palette], decoded at 64px so it's cheap on old hardware).
 * - No image (either API level): gradient wash only.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    blurRadius: Float = 140f,
    imageAlpha: Float = CinemaAlpha.imageOverlay,
) {
    val palette = CinemaThemeHolder.current
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val extractedTint by
        produceState<Color?>(initialValue = null, imageUrl, supportsBlur) {
            value = null
            if (supportsBlur || imageUrl.isNullOrBlank()) return@produceState
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val request =
                            ImageRequest.Builder(context)
                                .data(imageUrl)
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

    val fallbackBrush =
        remember(palette.background, palette.accentDark, extractedTint) {
            val tint = extractedTint ?: palette.accentDark
            Brush.verticalGradient(
                colors =
                    listOf(
                        tint.copy(alpha = CinemaAlpha.tint),
                        palette.background,
                        palette.background,
                    ),
            )
        }

    val crossfadeTarget = imageUrl.takeIf { !it.isNullOrBlank() && supportsBlur }

    // Hoisted so the blur/saturation RenderEffect isn't rebuilt on every redraw of the layer
    // (recomposition, Crossfade animation ticks) — only when blurRadius actually changes.
    // supportsBlur never changes for the life of the process (it's Build.VERSION.SDK_INT), so
    // gating the remember() call on it is stable across this composable instance's recompositions.
    // Must stay gated: RenderEffect is API 31+, so calling it below that level would crash exactly
    // the older devices (e.g. Shield TVs on Android 11) this branch exists to avoid.
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

    Box(modifier = modifier.fillMaxSize().background(fallbackBrush)) {
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
    }
}
