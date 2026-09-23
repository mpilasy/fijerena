package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * Home-screen "Jump Back In" row — see docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md,
 * Phase 3.
 */
@Composable
fun MobileContinueWatchingShelf(
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
                modifier = Modifier.padding(bottom = CinemaSpacing.sm),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                contentPadding = PaddingValues(horizontal = CinemaSpacing.xxs),
            ) {
                items(items, key = { it.id }) { item ->
                    MobileContinueWatchingCard(item = item, onClick = { onItemSelected(item) })
                }
            }
        }
    }
}

@Composable
private fun MobileContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(MobileDimensions.continueWatchingCardWidth),
        shape = RoundedCornerShape(CinemaCornerRadius.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = MobileDimensions.cardRowElevation),
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
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(MobileDimensions.resumeBarHeight),
                color = CinemaAccent,
                trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
            )
            Column(modifier = Modifier.padding(CinemaSpacing.sm)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val remainingLabel = stringResource(R.string.series_remaining_format, formatDuration((item.remainingMs / 1000).toString()))
                val subtitleLine = item.subtitle?.let { "$it • $remainingLabel" } ?: remainingLabel
                Text(
                    text = subtitleLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
