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
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun CacheManagementSection(
    cacheStats: XtreamRepository.CacheStats?,
    syncState: SyncState = SyncState.Idle,
    isXtream: Boolean = false,
    lastSyncedAtMs: Long = 0L,
    lastSyncDurationMs: Long = 0L,
    lastSyncError: String? = null,
    onSyncClick: () -> Unit = {},
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
        // Sync Data Button (Xtream only)
        if (isXtream) {
            CinemaPrimaryButton(
                onClick = onSyncClick,
                text = if (syncState is SyncState.Syncing) "Syncing..." else "Sync Data Now",
                enabled = syncState !is SyncState.Syncing,
            )

            // Last Sync Stats
            if (lastSyncedAtMs > 0L) {
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                val context = androidx.compose.ui.platform.LocalContext.current
                val time = NumberUtils.formatTimestamp(context, lastSyncedAtMs)
                val duration = NumberUtils.formatDuration(lastSyncDurationMs)
                Text(
                    text = "Last Sync: Finished at $time • Took $duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                )
            }

            if (syncState is SyncState.Error || (syncState is SyncState.Idle && lastSyncError != null)) {
                val errorMsg = (syncState as? SyncState.Error)?.message ?: lastSyncError
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text = errorMsg,
                        style = styles.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (syncState is SyncState.Success) {
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                Text(
                    text = "Sync completed successfully",
                    style = styles.bodySmall,
                    color = CinemaAccent,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
        }

        val totalItems = stats.liveTv.itemsCount + stats.movies.itemsCount + stats.tvShows.itemsCount

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
                    text = "${NumberUtils.formatCount(totalItems)} Items",
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
                    text = "${NumberUtils.formatCount(stats.liveTv.categoryCount)} Categories · ${NumberUtils.formatCount(stats.liveTv.itemsCount)} Channels",
                    style = styles.bodyMedium,
                    color = CinemaAccent,
                )
            }
            CinemaSecondaryButton(
                onClick = onClearLiveTvClick,
                text = "Clear",
                enabled = stats.liveTv.itemsCount > 0,
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
                    text = "${NumberUtils.formatCount(stats.movies.categoryCount)} Categories · ${NumberUtils.formatCount(stats.movies.itemsCount)} Movies",
                    style = styles.bodyMedium,
                    color = CinemaAccent,
                )
            }
            CinemaSecondaryButton(
                onClick = onClearMoviesClick,
                text = "Clear",
                enabled = stats.movies.itemsCount > 0,
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
                    text = "${NumberUtils.formatCount(stats.tvShows.categoryCount)} Categories · ${NumberUtils.formatCount(stats.tvShows.itemsCount)} Series · ${NumberUtils.formatCount(stats.tvShows.episodesCount)} Episodes",
                    style = styles.bodyMedium,
                    color = CinemaAccent,
                )
            }
            CinemaSecondaryButton(
                onClick = onClearTvShowsClick,
                text = "Clear",
                enabled = stats.tvShows.itemsCount > 0,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // EPG & Other
        Text(
            text = "EPG Data: ${NumberUtils.formatCount(stats.epgCount)} channels",
            style = styles.bodySmall,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
    }
}
