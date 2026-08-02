package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

private val CinemaTextSecondaryHigh = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)

@Composable
internal fun TwoColumnLayout(
    categoryViewModel: CategoryViewModel,
    categories: ImmutableCategoryList,
    selectedCategoryId: String?,
    streams: ImmutableMediaList?,
    streamsLoading: Boolean,
    categoriesRefreshing: Boolean,
    lastPlayedItemId: String?,
    nowPlaying: ImmutableNowPlaying,
    contentType: String,
    favoriteIds: ImmutableStringSet = ImmutableStringSet(),
    favoriteCategoryIds: ImmutableStringSet = ImmutableStringSet(),
    watchProgress: ImmutableWatchProgress = ImmutableWatchProgress(),
    supportsNativeEpg: Boolean,
    epgIndexState: EpgIndexState,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, providerData: Map<String, String>) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onSearchClick: () -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf(appSettings.providerName) }
    // Read once — avoids SharedPreferences disk I/O on every recomposition
    val isDevMode = remember { appSettings.isDevMode }

    // Load actual provider name from database (AppSettings default is "My Provider")
    LaunchedEffect(Unit) {
        val repo =
            org.njarasoa.fijerena.core.network.provider
                .ProviderRepository(context.applicationContext)
        repo.getActiveProvider()?.let { providerName = it.name }
    }

    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val displaySmall = typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    // Long-press favorite menu state
    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }

    val categoryMap = remember(categories) {
        categories.associateBy { it.id }
    }

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
                            target.contentType,
                        )
                    }
                    is FavoriteMenuTarget.Stream -> {
                        categoryViewModel.toggleFavoriteStream(
                            target.itemId,
                            target.itemName,
                            target.categoryId,
                            target.contentType,
                        )
                    }
                }
            },
            onDismiss = { favoriteMenuTarget = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.lg.scaled(scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CinemaIconButton(
                    onClick = onSearchClick,
                    icon = {
                        Icon(
                            imageVector = CinemaIcons.Search,
                            contentDescription = "Search",
                            tint = CinemaTextPrimary
                        )
                    },
                )
                // EPG button - show for Live TV when native EPG or XMLTV index is available
                val hasEpgData =
                    supportsNativeEpg ||
                        epgIndexState is EpgIndexState.Indexed
                if (contentType == ContentType.LIVE_TV && selectedCategoryId != null && hasEpgData) {
                    val selectedCategoryName = categoryMap[selectedCategoryId]?.name
                    if (selectedCategoryName != null) {
                        CinemaSecondaryButton(
                            onClick = { onEpgClick(selectedCategoryId, selectedCategoryName) },
                            text = "TV Guide",
                        )
                    }
                }
                Column {
                    Text(
                        text = providerName,
                        style = scaledStyles.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = contentType.replace("_", " "),
                        style = scaledStyles.titleMedium,
                        color = CinemaAccent,
                    )
                    Text(
                        text = "${categories.size} categories",
                        style = scaledStyles.labelSmall,
                        color = CinemaTextSecondary,
                    )
                }
            }
            Text(
                text = providerName,
                style = scaledStyles.titleSmall,
                color = CinemaTextSecondaryHigh,
            )
        }

        // EPG error/status banner (Live TV only)
        if (contentType == ContentType.LIVE_TV) {
            val epgErrorMessage =
                when (epgIndexState) {
                    is EpgIndexState.Failed -> "EPG indexing failed"
                    is EpgIndexState.Indexing -> "EPG indexing ${epgIndexState.progressPercent}%..."
                    else -> null
                }
            if (epgErrorMessage != null) {
                Text(
                    text = epgErrorMessage,
                    style = scaledStyles.bodySmall,
                    color =
                        if (epgIndexState is EpgIndexState.Indexing) {
                            CinemaTextSecondary
                        } else {
                            CinemaError
                        },
                    modifier = Modifier.padding(bottom = Spacing.xs.scaled(scale)),
                )
            }
        }

        // Two-column content
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Left column: Categories (30% width)
            CategoryList(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                categoriesRefreshing = categoriesRefreshing,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                favoriteCategoryIds = favoriteCategoryIds,
                onCategorySelected = onCategorySelected,
                onRefreshCategories = onRefreshCategories,
                onCategoryLongPress = { category ->
                    favoriteMenuTarget =
                        FavoriteMenuTarget.Category(
                            categoryId = category.id,
                            categoryName = category.name,
                            contentType = contentType,
                            isFavorite = categoryViewModel.isFavoriteCategory(category.id, contentType),
                        )
                },
                modifier =
                    Modifier
                        .weight(0.3f)
                        .fillMaxHeight(),
            )

            // Right column: Streams (70% width)
            StreamList(
                streams = streams,
                streamsLoading = streamsLoading,
                selectedCategoryId = selectedCategoryId,
                selectedCategoryName = selectedCategoryId?.let { categoryMap[it]?.name },
                lastPlayedItemId = lastPlayedItemId,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                isDevMode = isDevMode,
                favoriteIds = favoriteIds,
                watchProgress = watchProgress,
                onStreamSelected = { streamId, streamName, categoryId, providerData ->
                    // Check if this is a category reference from "Recent Categories" or "Favorite Categories"
                    if (providerData["isCategoryRef"] == "true") {
                        val targetCategoryId = providerData["categoryId"]
                        if (targetCategoryId != null) {
                            onCategorySelected(targetCategoryId)
                        }
                    } else {
                        onStreamSelected(streamId, streamName, categoryId, providerData)
                    }
                },
                onStreamLongPress = { item ->
                    // Category reference items (from "Favorite Categories" / "Recent Categories")
                    // should toggle the category favorite, not create a stream favorite
                    val realCategoryId = item.providerData["categoryId"]
                    if (item.providerData["isCategoryRef"] == "true" && realCategoryId != null) {
                        favoriteMenuTarget =
                            FavoriteMenuTarget.Category(
                                categoryId = realCategoryId,
                                categoryName = item.name,
                                contentType = contentType,
                                isFavorite = categoryViewModel.isFavoriteCategory(realCategoryId, contentType),
                            )
                    } else {
                        favoriteMenuTarget =
                            FavoriteMenuTarget.Stream(
                                itemId = item.id,
                                itemName = item.name,
                                categoryId = item.categoryId,
                                contentType = contentType,
                                isFavorite = categoryViewModel.isFavorite(item.id, contentType),
                            )
                    }
                },
                onRefreshStreams = onRefreshStreams,
                modifier =
                    Modifier
                        .weight(0.7f)
                        .fillMaxHeight(),
            )
        }
    }
}
