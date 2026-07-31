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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder

/**
 * Full-bleed decorative background: a heavily blurred, saturation-boosted wash of the given
 * image (poster/thumbnail art) over a theme-accent gradient fallback. Cross-dissolves when
 * [imageUrl] changes instead of popping.
 *
 * Purely visual — no touch/click handling — so it is safe to place behind any screen's root
 * content without touching that screen's state logic.
 *
 * - API 31+ with a non-blank [imageUrl]: blurred, saturated image wash over the gradient.
 * - API < 31, or no image: gradient wash only.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    blurRadius: Float = 140f,
    imageAlpha: Float = CinemaAlpha.imageOverlay,
) {
    val palette = CinemaThemeHolder.current
    val fallbackBrush =
        remember(palette.background, palette.accentDark) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        palette.accentDark.copy(alpha = CinemaAlpha.tint),
                        palette.background,
                        palette.background,
                    ),
            )
        }

    val crossfadeTarget =
        imageUrl.takeIf { !it.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    Box(modifier = modifier.fillMaxSize().background(fallbackBrush)) {
        Crossfade(
            targetState = crossfadeTarget,
            animationSpec = tween(600),
            label = "ambient_backdrop",
        ) { url ->
            if (url != null) {
                val context = LocalContext.current
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
                                val saturationMatrix = ColorMatrix().apply { setSaturation(1.35f) }
                                val blurEffect =
                                    android.graphics.RenderEffect
                                        .createBlurEffect(
                                            blurRadius,
                                            blurRadius,
                                            android.graphics.Shader.TileMode.CLAMP,
                                        )
                                renderEffect =
                                    android.graphics.RenderEffect
                                        .createColorFilterEffect(
                                            ColorMatrixColorFilter(saturationMatrix),
                                            blurEffect,
                                        ).asComposeRenderEffect()
                            },
                )
            }
        }
    }
}
