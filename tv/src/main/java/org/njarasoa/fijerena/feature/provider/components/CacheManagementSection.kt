package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun CacheManagementSection(
    cacheStats: XtreamRepository.CacheStats?,
    onClearAllClick: () -> Unit,
    onClearLiveTvClick: () -> Unit,
    onClearMoviesClick: () -> Unit,
    onClearTvShowsClick: () -> Unit
) {
    val scale = LocalUiScale.current

    Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

    Text(
        text = "Cache Management",
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
        ),
        color = CinemaAccent
    )
    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
    Text(
        text = "Clear cached data to free up storage space",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
    )
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    cacheStats?.let { stats ->
        // Total cache size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total Cache Size",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)),
                    color = CinemaTextPrimary
                )
                Text(
                    text = formatBytes(stats.totalSize),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize.scaled(scale)),
                    color = CinemaAccent
                )
            }
            CinemaDangerButton(
                onClick = onClearAllClick,
                text = "Clear All"
            )
        }

        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
        HorizontalDivider(color = CinemaTextSecondary.copy(alpha = CinemaAlpha.focusedTint))
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Live TV Cache
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live TV",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                    color = CinemaTextPrimary
                )
                Text(
                    text = formatBytes(stats.liveTv.size),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                    color = CinemaAccent
                )
                Text(
                    text = "${if (stats.liveTv.categoryCached) "1 category" else "No categories"}, ${stats.liveTv.streamListsCount} stream lists",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )
            }
            CinemaSecondaryButton(
                onClick = onClearLiveTvClick,
                text = "Clear",
                enabled = stats.liveTv.size > 0
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Movies Cache
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Movies",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                    color = CinemaTextPrimary
                )
                Text(
                    text = formatBytes(stats.movies.size),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                    color = CinemaAccent
                )
                Text(
                    text = "${if (stats.movies.categoryCached) "1 category" else "No categories"}, ${stats.movies.streamListsCount} stream lists",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )
            }
            CinemaSecondaryButton(
                onClick = onClearMoviesClick,
                text = "Clear",
                enabled = stats.movies.size > 0
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // TV Shows Cache
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TV Shows",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                    color = CinemaTextPrimary
                )
                Text(
                    text = formatBytes(stats.tvShows.size),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                    color = CinemaAccent
                )
                Text(
                    text = "${if (stats.tvShows.categoryCached) "1 category" else "No categories"}, ${stats.tvShows.streamListsCount} stream lists",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )
            }
            CinemaSecondaryButton(
                onClick = onClearTvShowsClick,
                text = "Clear",
                enabled = stats.tvShows.size > 0
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // EPG & Other
        Text(
            text = "EPG Data: ${stats.epgCount} channels",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
        )
        Text(
            text = "Other: ${formatBytes(stats.otherSize)}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
