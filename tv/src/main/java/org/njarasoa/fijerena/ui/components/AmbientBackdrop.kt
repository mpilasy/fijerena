package org.njarasoa.fijerena.ui.components

import android.os.Build
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
 * Full-bleed decorative background: a heavily blurred, low-alpha wash of the given image
 * (channel logo / poster art) over a theme-accent gradient fallback.
 *
 * Purely visual — no focus/click handling — so it is safe to place behind any screen's root
 * content without touching that screen's D-pad/state logic.
 *
 * - API 31+ with a non-blank [imageUrl]: blurred image wash over the gradient.
 * - API < 31, or no image: gradient wash only.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    blurRadius: Float = 80f,
    imageAlpha: Float = CinemaAlpha.imageOverlayLight,
) {
    val palette = CinemaThemeHolder.current
    val fallbackBrush =
        remember(palette.background, palette.accentDark) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        palette.accentDark.copy(alpha = CinemaAlpha.ghost),
                        palette.background,
                        palette.background,
                    ),
            )
        }

    Box(modifier = modifier.fillMaxSize().background(fallbackBrush)) {
        if (!imageUrl.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            @Suppress("NewApi")
            AsyncImage(
                model = remember(context, imageUrl) { ImageRequest.Builder(context).data(imageUrl).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = imageAlpha,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            renderEffect =
                                android.graphics.RenderEffect
                                    .createBlurEffect(
                                        blurRadius,
                                        blurRadius,
                                        android.graphics.Shader.TileMode.CLAMP,
                                    ).asComposeRenderEffect()
                        },
            )
        }
    }
}
