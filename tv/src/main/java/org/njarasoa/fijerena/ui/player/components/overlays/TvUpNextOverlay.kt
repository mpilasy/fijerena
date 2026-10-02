@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.upNextLabel
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * "Up next" card shown over the playing episode near its end (autoplay next episode): names
 * [episode] and the playback time left. The player owns its focus ([playNowFocus], moved here as
 * the card appears) and its keys — see PlayerScreen; [onFocusChanged] reports whether focus is
 * inside the card. Sits mid-right, clear of the controls' bottom row.
 */
@Composable
fun TvUpNextOverlay(
    episode: EpisodeItem,
    secondsLeft: Int,
    playNowFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        contentAlignment = Alignment.CenterEnd,
    ) {
        GlassPanel(
            modifier =
                Modifier
                    .width(TvDimensions.dialogWidth)
                    .onFocusChanged { onFocusChanged(it.hasFocus) },
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = stringResource(R.string.player_up_next_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = CinemaAccent,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = upNextLabel(episode),
                    style = MaterialTheme.typography.titleLarge,
                    color = CinemaTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.player_up_next_countdown_format, secondsLeft),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CinemaPrimaryButton(
                        onClick = onPlayNow,
                        text = stringResource(R.string.player_up_next_play_now),
                        modifier = Modifier.focusRequester(playNowFocus),
                    )
                    CinemaSecondaryButton(
                        onClick = onCancel,
                        text = stringResource(R.string.common_cancel),
                    )
                }
            }
        }
    }
}
