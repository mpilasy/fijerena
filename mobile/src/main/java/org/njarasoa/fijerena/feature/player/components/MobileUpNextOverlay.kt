package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.rememberUpNextCountdown
import org.njarasoa.fijerena.core.ui.components.upNextLabel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * "Up next" card shown when an episode ends with the profile's autoplay on: names [episode],
 * counts down, then calls [onPlayNow]. Back is a BackHandler in the player that calls [onCancel].
 */
@Composable
fun MobileUpNextOverlay(
    episode: EpisodeItem,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    val secondsLeft = rememberUpNextCountdown(episode, onPlayNow)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
        contentAlignment = Alignment.BottomEnd,
    ) {
        GlassPanel(modifier = Modifier.widthIn(max = MobileDimensions.statsOverlayMaxWidth)) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = stringResource(R.string.player_up_next_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = upNextLabel(episode),
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.player_up_next_countdown_format, secondsLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CinemaButton(onClick = onPlayNow) {
                        Text(stringResource(R.string.player_up_next_play_now))
                    }
                    CinemaOutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}
