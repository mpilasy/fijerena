package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.SkeletonList
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
internal fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.common_loading),
            style = MaterialTheme.typography.titleMedium,
            color = CinemaTextSecondary,
        )
        SkeletonList(
            rowCount = 6,
            rowHeight = TvDimensions.cardHeight,
            thumbnailWidth = TvDimensions.posterWidth * 0.5f,
            thumbnailHeight = TvDimensions.posterHeight * 0.5f,
            verticalSpacing = Spacing.sm,
        )
    }
}

/** The section's name: the browse header's title (TV UI audit X6). */
@Composable
internal fun sectionTitle(contentType: String): String =
    when (contentType) {
        ContentType.MOVIES -> stringResource(R.string.provider_movies_label)
        ContentType.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
        else -> stringResource(R.string.provider_live_tv_label)
    }

/** The section's icon, for its empty states. */
@Composable
internal fun sectionIcon(contentType: String): ImageVector =
    when (contentType) {
        ContentType.MOVIES -> CinemaIcons.Movie
        ContentType.TV_SHOWS -> CinemaIcons.Tv
        else -> CinemaIcons.LiveTv
    }

/** "12 channels", "12 films" or "12 shows" — the section's own word, not "streams" (TV UI audit X8). */
@Composable
internal fun itemCountText(
    contentType: String,
    count: Int,
): String =
    when (contentType) {
        ContentType.MOVIES -> pluralStringResource(R.plurals.browse_film_count, count, count)
        ContentType.TV_SHOWS -> pluralStringResource(R.plurals.browse_show_count, count, count)
        else -> pluralStringResource(R.plurals.browse_channel_count, count, count)
    }

/** An empty category, in the section's words (TV UI audit X8). */
@Composable
internal fun noItemsText(contentType: String): String =
    when (contentType) {
        ContentType.MOVIES -> stringResource(R.string.browse_no_films)
        ContentType.TV_SHOWS -> stringResource(R.string.browse_no_shows)
        else -> stringResource(R.string.category_no_channels)
    }

/** No category picked yet, in the section's words. */
@Composable
internal fun selectCategoryText(contentType: String): String =
    when (contentType) {
        ContentType.MOVIES -> stringResource(R.string.browse_select_to_view_films)
        ContentType.TV_SHOWS -> stringResource(R.string.browse_select_to_view_shows)
        else -> stringResource(R.string.category_select_to_view_channels)
    }
