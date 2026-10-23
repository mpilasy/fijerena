@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BufferingDetails
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Live TV zap feedback (LT5, the ux overhaul plan → L-7): "Tuning · <channel>"
 * with a small spinner, centred over a dimmed picture, from the moment a channel is chosen until
 * it plays. Drawn over whatever the surface shows — the engine's `stop()` + new media source
 * closes PlayerView's shutter, so after the first moment of a zap that is black, not the last
 * frame. Not focusable; the remote keeps zapping. [compact] is the preview pane's size; full
 * size shows Expert mode's [BufferingDetails] under the pill, as the loading spinner does.
 * [stalled]: the preview gave up on the channel (it didn't start in time) — no spinner, and the
 * pill says so and that OK tries again, rather than tuning forever.
 */
@Composable
fun TvTuningOverlay(
    channelName: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    stalled: Boolean = false,
) {
    val background = CinemaTheme.colors.background
    val scrim = remember(background) { background.copy(alpha = CinemaAlpha.scrim) }
    val pill = remember(background) { background.copy(alpha = CinemaAlpha.glass) }
    val pillShape = CinemaTheme.shapes.card
    val context = LocalContext.current
    val showDetails = remember { !compact && AppSettings(context.applicationContext).isDevMode }
    Box(
        modifier = modifier.fillMaxSize().background(scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier =
                    Modifier
                        .padding(Spacing.md)
                        .background(pill, pillShape)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (!stalled) CinemaSpinner(modifier = Modifier.size(if (compact) TvDimensions.iconSmall else TvDimensions.iconMedium))
                CinemaText(
                    // The clean name: "CP24 ◉", not "CA - CP24 HD ◉".
                    text =
                        stringResource(
                            if (stalled) R.string.live_preview_not_started_format else R.string.live_tuning_format,
                            parseDisplayTitle(channelName).title.ifBlank { channelName },
                        ),
                    role = if (compact) TextRole.Heading else TextRole.Title,
                    tone = TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showDetails) BufferingDetails()
        }
    }
}
