package org.njarasoa.fijerena.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder

/**
 * Content type hint for choosing fallback gradient colors.
 */
enum class ThumbnailContentType {
    LIVE_TV,
    MOVIE,
    TV_SHOW,
    DEFAULT,
}

/**
 * Image loading composable with gradient fallback and shimmer placeholder.
 *
 * - Non-null URL: Coil AsyncImage with crossfade
 * - Null/blank/error: Large letter over content-type gradient background
 * - Shimmer placeholder while loading
 */
@Composable
fun CinemaThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackLetter: Char? = null,
    contentType: ThumbnailContentType = ThumbnailContentType.DEFAULT,
    overlayGradient: Boolean = false,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(CinemaCornerRadius.medium)
    val context = LocalContext.current

    // Track measured size so Coil decodes at display resolution, not full source resolution
    Box(
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            // Key on url so shimmer/fallback state resets when the image URL changes
            var showShimmer by remember(url) { mutableStateOf(true) }
            var showFallback by remember(url) { mutableStateOf(false) }

            val model =
                remember(context, url) {
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .crossfade(CinemaAnimation.imageLoadCrossfadeMs)
                        .build()
                }

            if (!showFallback) {
                AsyncImage(
                    model = model,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { showShimmer = false },
                    onError = {
                        showShimmer = false
                        showFallback = true
                    },
                )
            }

            if (showShimmer && !showFallback) {
                ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
            }

            if (showFallback) {
                TypographyFallback(
                    letter = fallbackLetter,
                    contentType = contentType,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            TypographyFallback(
                letter = fallbackLetter,
                contentType = contentType,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (overlayGradient) {
            GradientOverlay(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Typography fallback: large letter centered over a content-type gradient.
 */
@Composable
fun TypographyFallback(
    letter: Char?,
    contentType: ThumbnailContentType,
    modifier: Modifier = Modifier,
) {
    val palette = CinemaThemeHolder.current
    val gradient =
        remember(contentType, palette) {
            when (contentType) {
                ThumbnailContentType.LIVE_TV ->
                    Brush.verticalGradient(
                        colors = listOf(palette.orange, palette.orangeDark),
                    )
                ThumbnailContentType.MOVIE ->
                    Brush.verticalGradient(
                        colors = listOf(palette.accent, palette.accentDark),
                    )
                ThumbnailContentType.TV_SHOW ->
                    Brush.verticalGradient(
                        colors = listOf(palette.accentLight, palette.accent),
                    )
                ThumbnailContentType.DEFAULT ->
                    Brush.verticalGradient(
                        colors = listOf(palette.surfaceVariant, palette.surface),
                    )
            }
        }

    Box(
        modifier = modifier.background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        if (letter != null) {
            val textStyle =
                remember(palette) {
                    androidx.compose.ui.text.TextStyle(
                        color = palette.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            androidx.compose.foundation.text.BasicText(
                text = letter.uppercase(),
                style = textStyle,
            )
        }
    }
}

/**
 * Shimmer placeholder effect while images load.
 *
 * Uses a static full-width gradient shifted via graphicsLayer.translationX,
 * so no Brush allocation occurs per animation frame.
 */
@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val palette = CinemaThemeHolder.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = CinemaAnimation.shimmerDurationMs,
                        easing = LinearEasing,
                    ),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmer_translate",
    )

    // Memoize gradient brush — only palette changes between themes, not every animation frame
    val shimmerBrush =
        remember(palette) {
            Brush.linearGradient(
                colors =
                    listOf(
                        palette.surface,
                        palette.surfaceLight.copy(alpha = CinemaAlpha.imageOverlayLight),
                        palette.surface,
                    ),
                start = Offset(0f, 0f),
                end = Offset(300f, 0f),
            )
        }

    Box(
        modifier =
            modifier
                .background(shimmerBrush)
                .graphicsLayer { translationX = translateX },
    )
}
