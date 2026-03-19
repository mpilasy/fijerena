package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun CacheManagementSection(
    cacheStats: XtreamRepository.CacheStats?,
    onClearAllClick: () -> Unit,
    onClearLiveTvClick: () -> Unit,
    onClearMoviesClick: () -> Unit,
    onClearTvShowsClick: () -> Unit,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    // Memoize scaled TextStyles to avoid allocating new copies per recomposition
    val styles =
        remember(scale, typography) {
            object {
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val headlineSmall = typography.headlineSmall.copy(fontSize = typography.headlineSmall.fontSize.scaled(scale))
                val bodyLarge = typography.bodyLarge.copy(fontSize = typography.bodyLarge.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

    Text(
        text = "Cache Management",
        style = styles.titleMedium,
        color = CinemaAccent,
    )
    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
    Text(
        text = "Clear cached data to free up storage space",
        style = styles.bodySmall,
        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
    )
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    cacheStats?.let { stats ->
        val totalItems = stats.liveTv.streamListsCount + stats.movies.streamListsCount + stats.tvShows.streamListsCount

        // Total cache
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Total Database Items",
                    style = styles.bodyLarge,
                    color = CinemaTextPrimary,
                )
                Text(
                    text = "$totalItems Items",
                    style = styles.headlineSmall,
                    color = CinemaAccent,
                )
            }
            CinemaDangerButton(
                onClick = onClearAllClick,
                text = "Clear All",
            )
        }

        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
        HorizontalDivider(color = CinemaTextSecondary.copy(alpha = CinemaAlpha.focusedTint))
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Live TV Cache
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live TV",
                    style = styles.titleSmall,
                    color = CinemaTextPrimary,
                )
                Text(
                    text = "${stats.liveTv.streamListsCount} channels",
                    style = styles.bodyMedium,
                    color = CinemaAccent,
                )
                Text(
                    text = if (stats.liveTv.categoryCached) "Categories cached" else "No categories",
                    style = styles.bodySmall,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )
            }
            CinemaSecondaryButton(
                onClick = onClearLiveTvClick,
                text = "Clear",
                enabled = stats.liveTv.streamListsCount > 0,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Movies Cache
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Movies",
                    style = styles.titleSmall,
                    color = CinemaTextPrimary,
                )
                Text(
                    text = "${stats.movies.streamListsCount} movies",
                    style = styles.bodyMedium,
                    color = CinemaAccent,
                )
                Text(
                    text = if (stats.movies.categoryCached) "Categories cached" else "No categories",
                    style = styles.bodySmall,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )
            }
            CinemaSecondaryButton(
                onClick = onClearMoviesClick,
                text = "Clear",
                enabled = stats.movies.streamListsCount > 0,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // TV Shows Cache
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TV Shows",
                    style = styles.titleSmall,
                    color = CinemaTextPrimary,
                )
                Text(
                    text = "${stats.tvShows.streamListsCount} shows",
                    style = styles.bodyMedium,
                    color = CinemaAccent,
                )
                Text(
                    text = if (stats.tvShows.categoryCached) "Categories cached" else "No categories",
                    style = styles.bodySmall,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )
            }
            CinemaSecondaryButton(
                onClick = onClearTvShowsClick,
                text = "Clear",
                enabled = stats.tvShows.streamListsCount > 0,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // EPG & Other
        Text(
            text = "EPG Data: ${stats.epgCount} channels",
            style = styles.bodySmall,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
    }
}
