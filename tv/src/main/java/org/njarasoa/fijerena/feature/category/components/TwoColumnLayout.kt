package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
internal fun TwoColumnLayout(
    categoryViewModel: CategoryViewModel,
    categories: List<MediaCategory>,
    selectedCategoryId: String?,
    streams: List<MediaItem>?,
    streamsLoading: Boolean,
    categoriesRefreshing: Boolean,
    lastPlayedItemId: String?,
    nowPlaying: Map<String, EpgProgram>,
    contentType: String,
    supportsNativeEpg: Boolean,
    epgIndexState: EpgIndexState,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onSearchClick: () -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }

    val scale = LocalUiScale.current

    // Long-press favorite menu state
    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }

    // Show the context menu dialog when a target is set
    favoriteMenuTarget?.let { target ->
        FavoriteContextMenuDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is FavoriteMenuTarget.Category -> {
                        categoryViewModel.toggleFavoriteCategory(
                            target.categoryId,
                            target.categoryName,
                            target.contentType
                        )
                    }
                    is FavoriteMenuTarget.Stream -> {
                        categoryViewModel.toggleFavoriteStream(
                            target.itemId,
                            target.itemName,
                            target.categoryId,
                            target.contentType
                        )
                    }
                }
            },
            onDismiss = { favoriteMenuTarget = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg.scaled(scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CinemaIconButton(
                    onClick = onSearchClick,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                )
                // EPG button - show for Live TV when native EPG or XMLTV index is available
                val hasEpgData = supportsNativeEpg ||
                    epgIndexState is EpgIndexState.Indexed
                if (contentType == ContentType.LIVE_TV && selectedCategoryId != null && hasEpgData) {
                    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name
                    if (selectedCategoryName != null) {
                        CinemaSecondaryButton(
                            onClick = { onEpgClick(selectedCategoryId, selectedCategoryName) },
                            text = "TV Guide"
                        )
                    }
                }
                Column {
                    Text(
                        text = "IPTV.atr",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = contentType.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Text(
                        text = "${categories.size} categories",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary
                    )
                }
            }
            Text(
                text = providerName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
        }

        // EPG error/status banner (Live TV only)
        if (contentType == ContentType.LIVE_TV) {
            val epgErrorMessage = when (epgIndexState) {
                is EpgIndexState.Failed -> "EPG indexing failed"
                is EpgIndexState.Indexing -> "EPG indexing ${epgIndexState.progressPercent}%..."
                else -> null
            }
            if (epgErrorMessage != null) {
                Text(
                    text = epgErrorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                    ),
                    color = if (epgIndexState is EpgIndexState.Indexing) {
                        CinemaTextSecondary
                    } else {
                        CinemaError
                    },
                    modifier = Modifier.padding(bottom = Spacing.xs.scaled(scale))
                )
            }
        }

        // Two-column content
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left column: Categories (30% width)
            CategoryList(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                categoriesRefreshing = categoriesRefreshing,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                onCategorySelected = onCategorySelected,
                onRefreshCategories = onRefreshCategories,
                onCategoryLongPress = { category ->
                    favoriteMenuTarget = FavoriteMenuTarget.Category(
                        categoryId = category.id,
                        categoryName = category.name,
                        contentType = contentType,
                        isFavorite = categoryViewModel.isFavoriteCategory(category.id, contentType)
                    )
                },
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
            )

            // Right column: Streams (70% width)
            StreamList(
                streams = streams,
                streamsLoading = streamsLoading,
                selectedCategoryId = selectedCategoryId,
                selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name,
                lastPlayedItemId = lastPlayedItemId,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                isDevMode = appSettings.isDevMode,
                onStreamSelected = { streamId, streamName, categoryId ->
                    // Check if this is a category reference from "Recent Categories" or "Favorite Categories"
                    val item = streams?.firstOrNull { it.id == streamId }
                    if (item?.providerData?.get("isCategoryRef") == "true") {
                        val targetCategoryId = item.providerData["categoryId"]
                        if (targetCategoryId != null) {
                            onCategorySelected(targetCategoryId)
                        }
                    } else {
                        onStreamSelected(streamId, streamName, categoryId)
                    }
                },
                onStreamLongPress = { item ->
                    favoriteMenuTarget = FavoriteMenuTarget.Stream(
                        itemId = item.id,
                        itemName = item.name,
                        categoryId = item.categoryId,
                        contentType = contentType,
                        isFavorite = categoryViewModel.isFavorite(item.id, contentType)
                    )
                },
                onRefreshStreams = onRefreshStreams,
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
            )
        }
    }
}
