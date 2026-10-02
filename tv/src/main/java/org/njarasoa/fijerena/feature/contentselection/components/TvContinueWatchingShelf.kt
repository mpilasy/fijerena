@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * Home-screen "Jump Back In" row — see docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md,
 * Phase 3.
 */
@Composable
fun TvContinueWatchingShelf(
    items: List<ContinueWatchingItem>,
    onItemSelected: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isNotEmpty()) {
        Column(modifier = modifier) {
            Text(
                text = stringResource(R.string.series_continue_watching_badge),
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextPrimary,
                modifier = Modifier.padding(bottom = Spacing.sm, start = Spacing.xxs),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(horizontal = Spacing.xxs),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    TvContinueWatchingCard(
                        item = item,
                        onClick = { onItemSelected(item) },
                        // Right on the last card stays put: without this the focus search leaves
                        // the row and lands on the top bar (Settings).
                        modifier =
                            Modifier.focusProperties {
                                if (index == items.lastIndex) right = FocusRequester.Cancel
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(TvDimensions.continueWatchingCardWidth),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaSurface,
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleSubtle,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
        border =
            CardDefaults.border(
                border = Border(BorderStroke(TvFocusTokens.borderThin, CinemaGlassBorder)),
                focusedBorder =
                    Border(
                        border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaTextPrimary),
                        shape = RoundedCornerShape(CinemaCornerRadius.medium),
                    ),
            ),
        glow = CardDefaults.glow(glow = TvFocusTokens.restingGlow, focusedGlow = TvFocusTokens.focusedGlow),
    ) {
        Column {
            CinemaThumbnail(
                url = item.thumbnailUrl,
                fallbackLetter = item.name.firstOrNull(),
                contentType = if (item.contentType == ContentType.TV_SHOWS) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
            )
            if (item.upNext) {
                // Nothing watched of it yet: no bar, but its height, so cards in the row line up.
                Spacer(modifier = Modifier.height(TvDimensions.resumeBarHeight))
            } else {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(TvDimensions.resumeBarHeight),
                    color = CinemaAccent,
                    trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
                )
            }
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitleLine =
                    if (item.upNext) {
                        val upNext = stringResource(R.string.continue_watching_up_next)
                        item.subtitle?.let { "$upNext • $it" } ?: upNext
                    } else {
                        val remainingLabel = stringResource(R.string.series_remaining_format, formatDuration((item.remainingMs / 1000).toString()))
                        item.subtitle?.let { "$it • $remainingLabel" } ?: remainingLabel
                    }
                Text(
                    text = subtitleLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
