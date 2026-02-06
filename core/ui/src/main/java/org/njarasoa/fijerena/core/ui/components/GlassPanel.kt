package org.njarasoa.fijerena.core.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder

/**
 * Frosted glass surface composable.
 *
 * - API 31+: Blurred backdrop layer underneath sharp content
 * - API < 31: Solid semi-transparent background (75% opacity)
 * - Always applies shape + subtle gradient border
 *
 * The blur is applied ONLY to the background layer, not to content.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    blurRadius: Float = 20f,
    content: @Composable () -> Unit
) {
    val palette = CinemaThemeHolder.current
    val shape = RoundedCornerShape(CinemaCornerRadius.large)

    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = 1.dp,
                color = palette.glassBorder,
                shape = shape
            )
    ) {
        // Background layer: blurred on API 31+, solid on older
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        @Suppress("NewApi")
                        Modifier.graphicsLayer {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                blurRadius, blurRadius,
                                android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    } else {
                        Modifier
                    }
                )
                .background(palette.glassBackground)
        )
        // Content layer: always sharp, rendered on top
        content()
    }
}
