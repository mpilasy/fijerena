package org.njarasoa.fijerena.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha

/**
 * How long this screen stays up even once the app is ready.
 *
 * The splash in front of it cannot animate — on the Shields (API 30) core-splashscreen takes its
 * compat path, which only sets the icon on an ImageView, and the art is a photograph so an
 * AnimatedVectorDrawable isn't an option on newer devices either. This screen is the only part of
 * startup that moves, and since startup disk I/O moved off the main thread it renders fast enough
 * to be gone in a frame or two without a floor.
 *
 * Note this is a floor on *display* time, not on motion: a device with developer options'
 * animator duration scale at 0 (as darcy is set) collapses every animation below to its end
 * state, so the marble sits still there no matter how long this waits.
 */
const val APP_LOADING_MIN_MS = 900L

/**
 * Startup screen shown while the app works out where to send you — provider lookup, credential
 * migration, session restore. Before this, both nav hosts rendered nothing at all during that
 * window, so a cold start was a blank window of indeterminate length.
 *
 * The launcher art (blue marble in anaglyph glasses) breathes while an accent arc sweeps around
 * it. Painted on solid black rather than the theme background on purpose: the launcher foreground
 * has a black square baked in behind the marble, and any other backdrop shows its edge.
 */
@Composable
fun AppLoadingScreen(
    modifier: Modifier = Modifier,
    logoSize: Dp = 180.dp,
) {
    val transition = rememberInfiniteTransition(label = "app-loading")

    // Slow breathing pulse — the marble is the only thing on screen, so this stays subtle.
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse",
    )

    // Arc orbiting the marble — the part that actually reads as "still working".
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
            ),
        label = "sweep",
    )

    // Fade in rather than appearing hard, so a fast init reads as a transition and not a flash.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "fade",
    )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val ringSize = logoSize * 1.28f
            val ringStroke = logoSize * 0.017f

            Canvas(modifier = Modifier.size(ringSize).graphicsLayer { this.alpha = alpha }) {
                val stroke = ringStroke.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)

                // Faint full ring, so the sweeping head has a track to travel along.
                drawArc(
                    color = CinemaAccent.copy(alpha = CinemaAlpha.divider),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = CinemaAccent,
                    startAngle = sweep,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(logoSize)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                            this.alpha = alpha
                        },
            )
        }
    }
}
