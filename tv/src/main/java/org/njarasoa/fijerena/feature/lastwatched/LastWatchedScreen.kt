@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.lastwatched

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.WatchedItem
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.components.cards.CinemaStandardCard
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton

@Composable
fun LastWatchedScreen(
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, contentType: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scale = LocalUiScale.current
    var historyItems by remember { mutableStateOf<List<WatchedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val providerRepo = ProviderRepository(context.applicationContext)
            val activeProvider = providerRepo.getActiveProvider()
            if (activeProvider != null) {
                val repository = MediaRepository(context.applicationContext, activeProvider.id)
                historyItems = repository.getWatchHistory()
            }
        }
        isLoading = false
    }

    val gridState = rememberTvLazyGridState()

    // Auto-focus the first item (most recent)
    LaunchedEffect(historyItems) {
        if (historyItems.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical
            )
    ) {
        // Header
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
        ) {
            CinemaIconButton(
                onClick = onBack,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            )
            Column {
                Text(
                    text = "Last Watched",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (historyItems.isNotEmpty()) {
                    Text(
                        text = "${historyItems.size} items",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = MaterialTheme.typography.labelMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading history...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                    ),
                    color = CinemaTextSecondary
                )
            }
        } else if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No watch history yet",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize.scaled(scale)
                    ),
                    color = CinemaTextSecondary
                )
            }
        } else {
            TvLazyVerticalGrid(
                state = gridState,
                columns = TvGridCells.Adaptive(minSize = TvDimensions.posterWidth.scaled(scale)),
                contentPadding = PaddingValues(bottom = Spacing.lg.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                modifier = Modifier.fillMaxSize()
            ) {
                items(historyItems) { item ->
                    WatchedItemCard(
                        item = item,
                        onClick = {
                            onStreamSelected(item.itemId, item.itemName, item.categoryId, item.contentType)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchedItemCard(
    item: WatchedItem,
    onClick: () -> Unit
) {
    val scale = LocalUiScale.current
    CinemaStandardCard(
        onClick = onClick,
        modifier = Modifier.size(
            width = TvDimensions.posterWidth.scaled(scale),
            height = (TvDimensions.posterHeight * 1.2f).scaled(scale)
        )
    ) {
        Column {
            // We don't have thumbnails in WatchedItem, ideally we'd fetch them or use a placeholder
            // For now using a placeholder with the initial letter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                CinemaThumbnail(
                    url = null, // No URL in WatchedItem directly, would need to fetch item details
                    fallbackLetter = item.itemName.firstOrNull(),
                    contentType = when (item.contentType) {
                        "MOVIES" -> ThumbnailContentType.MOVIE
                        "TV_SHOWS" -> ThumbnailContentType.TV_SHOW
                        else -> ThumbnailContentType.DEFAULT
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Progress bar overlay
                if (item.duration > 0 && !item.isCompleted) {
                    val progress = (item.playbackPosition.toFloat() / item.duration.toFloat()).coerceIn(0f, 1f)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TvDimensions.borderFocused.scaled(scale))
                            .align(Alignment.BottomCenter),
                        color = CinemaAccent,
                        trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(Spacing.sm.scaled(scale))
                    .fillMaxWidth()
            ) {
                Text(
                    text = item.itemName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                    ),
                    color = CinemaTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = item.contentType.replace("_", " "), // e.g. "LIVE TV"
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                    ),
                    color = CinemaTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
