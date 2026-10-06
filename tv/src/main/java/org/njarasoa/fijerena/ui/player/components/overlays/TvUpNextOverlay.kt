@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.upNextCode
import org.njarasoa.fijerena.core.ui.components.upNextPlot
import org.njarasoa.fijerena.core.ui.components.upNextTitle
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * "Up next" card shown over the playing episode near its end (autoplay next episode): one line
 * naming [episode] and the playback time left, with Play now / Cancel beside it. A small
 * translucent panel in the top-right corner inside the TV-safe margins, [belowClock] pushing it
 * under the controls' clock while they are up. While focus is inside the card a second panel of
 * the same width under it gives the episode's title and synopsis, when known. The player owns its
 * focus ([playNowFocus], moved here as the card appears) and its keys — see PlayerScreen;
 * [onFocusChanged] reports whether focus is inside the card.
 */
@Composable
fun TvUpNextOverlay(
    episode: EpisodeItem,
    secondsLeft: Int,
    belowClock: Boolean,
    playNowFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    start = Spacing.tvSafeMarginHorizontal,
                    end = Spacing.tvSafeMarginHorizontal,
                    top = Spacing.tvSafeMarginVertical + if (belowClock) Spacing.xl else Spacing.none,
                    bottom = Spacing.tvSafeMarginVertical,
                ),
        contentAlignment = Alignment.TopEnd,
    ) {
        var focused by remember { mutableStateOf(false) }
        var stripWidthPx by remember { mutableIntStateOf(0) }
        val title = upNextTitle(episode)
        val plot = upNextPlot(episode)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            GlassPanel(
                modifier =
                    Modifier
                        .widthIn(max = TvDimensions.dialogWidth)
                        .onSizeChanged { stripWidthPx = it.width }
                        .onFocusChanged {
                            focused = it.hasFocus
                            onFocusChanged(it.hasFocus)
                        },
                backgroundAlpha = CinemaAlpha.scrim,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        text = stringResource(R.string.player_up_next_compact_format, upNextCode(episode), secondsLeft),
                        style = MaterialTheme.typography.labelLarge,
                        color = CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
            AnimatedVisibility(
                visible = focused && (title != null || plot != null),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                GlassPanel(
                    modifier = Modifier.width(with(LocalDensity.current) { stripWidthPx.toDp() }),
                    backgroundAlpha = CinemaAlpha.scrim,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        title?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleSmall,
                                color = CinemaTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        plot?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
