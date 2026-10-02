package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import org.njarasoa.fijerena.core.ui.components.upNextCode
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * "Up next" card shown over the playing episode near its end (autoplay next episode): one line
 * naming [episode] and the playback time left ([secondsLeft]), with Play now / Cancel beside it.
 * A small translucent panel in the top-right corner below the status bar and display cutout,
 * [belowClock] pushing it under the controls' top bar while they are up. Back is a BackHandler in
 * the player that calls [onCancel]. If the stats overlay is open it shares this corner and is
 * drawn over the card.
 */
@Composable
fun MobileUpNextOverlay(
    episode: EpisodeItem,
    secondsLeft: Int,
    belowClock: Boolean,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(Spacing.md)
                .padding(top = if (belowClock) Spacing.xl else Spacing.none),
        contentAlignment = Alignment.TopEnd,
    ) {
        GlassPanel(
            modifier = Modifier.widthIn(max = MobileDimensions.statsOverlayMaxWidth * 1.5f),
            backgroundAlpha = CinemaAlpha.scrim,
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.player_up_next_compact_format, upNextCode(episode), secondsLeft),
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                CinemaTextButton(onClick = onPlayNow) {
                    Text(
                        text = stringResource(R.string.player_up_next_play_now),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                CinemaTextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                    )
                }
            }
        }
    }
}
