package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BufferingDetails
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.WaitIndicator
import org.njarasoa.fijerena.core.ui.components.WaitKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.WaitStyle
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Live TV zap feedback (LT5, the ux overhaul plan → L-7): from the moment a channel is chosen until
 * it plays, the wait ring with the channel's logo in it ([channelName] when it has none), centred
 * over a dimmed picture. Drawn over whatever the surface shows — the engine's `stop()` + new media
 * source closes PlayerView's shutter, so after the first moment of a zap that is black, not the
 * last frame. Not focusable; the remote keeps zapping. The ring is full size in the preview pane too,
 * so a wide logo stays readable. [compact] is the preview pane; full screen shows Expert mode's
 * [BufferingDetails] under the ring, as the loading indicator does.
 * [unavailable]: the screen gave up on the channel (its load failed, or no picture in 10 s) — no
 * ring, and a pill says "<name> is not available right now" rather than tuning for ever; OK then
 * goes to the next channel (UX audit fixes plan, UX-D2).
 */
@Composable
fun TvTuningOverlay(
    channelName: String,
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    compact: Boolean = false,
    unavailable: Boolean = false,
) {
    val background = CinemaTheme.colors.background
    val scrim = remember(background) { background.copy(alpha = CinemaAlpha.scrim) }
    val pill = remember(background) { background.copy(alpha = CinemaAlpha.glass) }
    val pillShape = CinemaTheme.shapes.card
    val context = LocalContext.current
    val showDetails = remember { !compact && AppSettings(context.applicationContext).isDevMode }
    // The clean name: "CP24 ◉", not "CA - CP24 HD ◉".
    val name = parseDisplayTitle(channelName).title.ifBlank { channelName }
    Box(
        modifier = modifier.fillMaxSize().background(scrim),
        contentAlignment = Alignment.Center,
    ) {
        if (unavailable) {
            CinemaText(
                text = stringResource(R.string.live_channel_unavailable, name),
                modifier =
                    Modifier
                        .padding(Spacing.md)
                        .background(pill, pillShape)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                role = if (compact) TextRole.Heading else TextRole.Title,
                tone = TextTone.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            WaitIndicator(
                kind = WaitKind.Live,
                logoUrl = logoUrl,
                name = name,
                contentDescription = stringResource(R.string.live_tuning_format, name),
            )
            // Below the ring, so the details never push it off the centre.
            if (showDetails) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxHeight(HALF)
                            .padding(top = WaitStyle.indicatorSize / 2 + Spacing.sm),
                    contentAlignment = Alignment.TopCenter,
                ) { BufferingDetails() }
            }
        }
    }
}

private const val HALF = 0.5f
