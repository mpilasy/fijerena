package org.njarasoa.fijerena.feature.category

import androidx.compose.foundation.ExperimentalFoundationApi
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileCategoryListScreen(
    contentType: String,
    initialCategoryId: String? = null,
    onStreamSelected: (itemId: String, itemName: String, categoryId: String, contentType: String, providerData: Map<String, String>) -> Unit,
    onSearchClick: () -> Unit = {},
    onEpgClick: (categoryId: String, categoryName: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    viewModel: CategoryViewModel = viewModel(
        factory = CategoryViewModelFactory(
            context = LocalContext.current.applicationContext,
            contentType = contentType,
            initialCategoryId = initialCategoryId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlayingMap by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val nowPlaying = remember(nowPlayingMap) { ImmutableNowPlaying(nowPlayingMap) }
    val supportsNativeEpg by viewModel.supportsNativeEpg.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
    val epgIndexState by epgIndexer.state.collectAsStateWithLifecycle()
    val appSettings = remember { AppSettings(context.applicationContext) }
    val isDevMode = remember { appSettings.isDevMode }

    // Refresh last played item when returning from player
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshLastPlayedItem()
        }
    }

    // Long-press favorite menu state
    var favoriteMenuTarget by remember { mutableStateOf<MobileFavoriteMenuTarget?>(null) }

    // Show the context menu dialog when a target is set
    favoriteMenuTarget?.let { target ->
        MobileFavoriteContextMenuDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is MobileFavoriteMenuTarget.Category -> {
                        viewModel.toggleFavoriteCategory(
                            target.categoryId,
                            target.categoryName,
                            target.contentType
                        )
                    }
                    is MobileFavoriteMenuTarget.Stream -> {
                        viewModel.toggleFavoriteStream(
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contentType.replace("_", " ")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // EPG button - show for Live TV when native EPG or XMLTV file is available
                    if (contentType == ContentType.LIVE_TV) {
                        val state = uiState
                        if (state is CategoryViewModel.UiState.Success) {
                            val selectedCatId = state.selectedCategoryId
                            val selectedCatName = state.categories.find { it.id == selectedCatId }?.name
                            val hasEpgData = supportsNativeEpg ||
                                epgIndexState is EpgIndexState.Indexed
                            if (selectedCatId != null && selectedCatName != null && hasEpgData) {
                                IconButton(onClick = { onEpgClick(selectedCatId, selectedCatName) }) {
                                    Icon(Icons.Default.DateRange, "TV Guide")
                                }
                            }
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, "Search")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is CategoryViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is CategoryViewModel.UiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // EPG error/status banner (Live TV only)
                        if (contentType == ContentType.LIVE_TV) {
                            val epgMessage = when (epgIndexState) {
                                is EpgIndexState.Failed -> "EPG indexing failed"
                                is EpgIndexState.Indexing ->
                                    "EPG indexing ${(epgIndexState as EpgIndexState.Indexing).progressPercent}%..."
                                else -> null
                            }
                            if (epgMessage != null) {
                                Text(
                                    text = epgMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (epgIndexState is EpgIndexState.Indexing) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier.padding(
                                        horizontal = CinemaSpacing.md,
                                        vertical = CinemaSpacing.xs
                                    )
                                )
                            }
                            // Dev mode EPG info
                            if (isDevMode) {
                                val epgInfo = when (val epg = epgIndexState) {
                                    is EpgIndexState.Indexed -> {
                                        "EPG: ${NumberUtils.formatCount(epg.programmeCount)} progs, ${NumberUtils.formatCount(epg.channelCount)} channels"
                                    }
                                    is EpgIndexState.Indexing -> "EPG: Indexing..."
                                    else -> null
                                }
                                if (epgInfo != null) {
                                    Text(
                                        text = epgInfo,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = CinemaSpacing.md,
                                            vertical = CinemaSpacing.xs
                                        )
                                    )
                                }
                            }
                        }

                        // Horizontal category chips
                        CategoryChipRow(
                            categories = state.categories,
                            selectedCategoryId = state.selectedCategoryId,
                            contentType = contentType,
                            categoryViewModel = viewModel,
                            onCategorySelected = { categoryId ->
                                viewModel.loadStreams(categoryId)
                            },
                            onCategoryLongPress = { category ->
                                favoriteMenuTarget = MobileFavoriteMenuTarget.Category(
                                    categoryId = category.id,
                                    categoryName = category.name,
                                    contentType = contentType,
                                    isFavorite = viewModel.isFavoriteCategory(category.id, contentType)
                                )
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            thickness = MobileDimensions.dividerThin
                        )

                        // Streams list with pull-to-refresh
                        PullToRefreshBox(
                            isRefreshing = state.streamsLoading,
                            onRefresh = {
                                state.selectedCategoryId?.let { viewModel.refreshStreams(it) }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            StreamsList(
                                items = state.streams,
                                streamsLoading = state.streamsLoading,
                                selectedCategoryId = state.selectedCategoryId,
                                lastPlayedItemId = state.lastPlayedItemId,
                                nowPlaying = nowPlaying,
                                onItemSelected = { itemId, itemName, categoryId ->
                                    // Check if this is a category reference from "Recent Categories" or "Favorite Categories"
                                    val item = state.streams?.firstOrNull { it.id == itemId }
                                    val providerData = item?.providerData ?: emptyMap()
                                    if (providerData["isCategoryRef"] == "true") {
                                        val targetCategoryId = providerData["categoryId"]
                                        if (targetCategoryId != null) {
                                            viewModel.loadStreams(targetCategoryId)
                                        }
                                    } else {
                                        onStreamSelected(itemId, itemName, categoryId, contentType, providerData)
                                    }
                                },
                                onItemLongPress = { item ->
                                    // Category reference items (from "Favorite Categories" / "Recent Categories")
                                    // should toggle the category favorite, not create a stream favorite
                                    val realCategoryId = item.providerData["categoryId"]
                                    if (item.providerData["isCategoryRef"] == "true" && realCategoryId != null) {
                                        favoriteMenuTarget = MobileFavoriteMenuTarget.Category(
                                            categoryId = realCategoryId,
                                            categoryName = item.name,
                                            contentType = contentType,
                                            isFavorite = viewModel.isFavoriteCategory(realCategoryId, contentType)
                                        )
                                    } else {
                                        favoriteMenuTarget = MobileFavoriteMenuTarget.Stream(
                                            itemId = item.id,
                                            itemName = item.name,
                                            categoryId = item.categoryId,
                                            contentType = contentType,
                                            isFavorite = viewModel.isFavorite(item.id, contentType)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                is CategoryViewModel.UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(onClick = { viewModel.retry() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Extracted as a top-level constant to avoid allocating a new Set on every recomposition
private val VIRTUAL_CATEGORY_IDS = setOf(
    CategoryViewModel.FAVORITES_CATEGORY_ID,
    CategoryViewModel.FAVORITE_CATEGORIES_ID,
    CategoryViewModel.LAST_WATCHED_CATEGORY_ID,
    CategoryViewModel.CONTINUE_WATCHING_CATEGORY_ID,
    CategoryViewModel.RECENTLY_VIEWED_CATEGORIES_ID
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChipRow(
    categories: List<org.njarasoa.fijerena.core.player.domain.MediaCategory>,
    selectedCategoryId: String?,
    contentType: String,
    categoryViewModel: CategoryViewModel,
    onCategorySelected: (String) -> Unit,
    onCategoryLongPress: (org.njarasoa.fijerena.core.player.domain.MediaCategory) -> Unit = {}
) {
    // Single-pass partition instead of two separate filter() calls
    val (virtualCategories, regularCategories) = remember(categories) {
        categories.partition { it.id in VIRTUAL_CATEGORY_IDS }
    }

    val listState = rememberLazyListState()
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
    )

    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null && selectedCategoryId !in VIRTUAL_CATEGORY_IDS) {
            val index = regularCategories.indexOfFirst { it.id == selectedCategoryId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Column {
        // Virtual categories row (Favorites, Last Watched)
        if (virtualCategories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CinemaSpacing.sm, bottom = CinemaSpacing.xs),
                contentPadding = PaddingValues(horizontal = CinemaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
            ) {
                items(virtualCategories, key = { it.id }, contentType = { "category" }) { category ->
                    FilterChip(
                        selected = category.id == selectedCategoryId,
                        onClick = { onCategorySelected(category.id) },
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                modifier = Modifier.bounceMarquee()
                            )
                        },
                        colors = chipColors
                    )
                }
            }
        }

        // Regular categories row
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CinemaSpacing.sm),
            contentPadding = PaddingValues(horizontal = CinemaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
        ) {
            items(regularCategories, key = { it.id }, contentType = { "category" }) { category ->
                val isFavCat = categoryViewModel.isFavoriteCategory(category.id, contentType)
                FilterChip(
                    selected = category.id == selectedCategoryId,
                    onClick = { onCategorySelected(category.id) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isFavCat) {
                                Text(
                                    text = "\u2605",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = category.name,
                                maxLines = 1,
                                modifier = Modifier.bounceMarquee()
                            )
                        }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { onCategorySelected(category.id) },
                        onLongClick = { onCategoryLongPress(category) }
                    ),
                    colors = chipColors
                )
            }
        }
    }
}

@Composable
private fun StreamsList(
    items: List<org.njarasoa.fijerena.core.player.domain.MediaItem>?,
    streamsLoading: Boolean,
    selectedCategoryId: String?,
    lastPlayedItemId: String? = null,
    nowPlaying: ImmutableNowPlaying = ImmutableNowPlaying(),
    onItemSelected: (itemId: String, itemName: String, categoryId: String) -> Unit,
    onItemLongPress: (org.njarasoa.fijerena.core.player.domain.MediaItem) -> Unit = {}
) {
    val listState = rememberLazyListState()

    LaunchedEffect(items, lastPlayedItemId) {
        if (!items.isNullOrEmpty() && lastPlayedItemId != null) {
            val index = items.indexOfFirst { it.id == lastPlayedItemId }
            if (index > 0) {
                // +1 to account for the header item
                listState.animateScrollToItem(index + 1)
            }
        }
    }
    when {
        selectedCategoryId == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        streamsLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading streams...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items.isNullOrEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No streams in this category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(contentType = "header") {
                    Text(
                        text = "${items.size} streams",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }
                items(items, key = { it.id }, contentType = { "stream" }) { item ->
                    StreamCard(
                        item = item,
                        nowPlayingProgram = nowPlaying[item.id],
                        onClick = {
                            onItemSelected(item.id, item.name, item.categoryId)
                        },
                        onLongClick = { onItemLongPress(item) }
                    )
                }
            }
        }
    }
}

/**
 * Data class representing a pending favorite action from a long-press on mobile.
 */
private sealed class MobileFavoriteMenuTarget {
    data class Category(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val isFavorite: Boolean
    ) : MobileFavoriteMenuTarget()

    data class Stream(
        val itemId: String,
        val itemName: String,
        val categoryId: String,
        val contentType: String,
        val isFavorite: Boolean
    ) : MobileFavoriteMenuTarget()
}

/**
 * Themed context menu dialog for favoriting categories/streams on mobile.
 */
@Composable
private fun MobileFavoriteContextMenuDialog(
    target: MobileFavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (itemName, isFavorite) = when (target) {
        is MobileFavoriteMenuTarget.Category -> target.categoryName to target.isFavorite
        is MobileFavoriteMenuTarget.Stream -> target.itemName to target.isFavorite
    }

    val actionText = if (isFavorite) "Remove from Favorites" else "Add to Favorites"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = itemName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(actionText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CinemaCornerRadius.large)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamCard(
    item: org.njarasoa.fijerena.core.player.domain.MediaItem,
    nowPlayingProgram: EpgProgram? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(MobileDimensions.streamCardHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CinemaCornerRadius.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(CinemaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
        ) {
            // Poster thumbnail
            CinemaThumbnail(
                url = item.thumbnailUrl,
                fallbackLetter = item.name.firstOrNull(),
                contentType = ThumbnailContentType.DEFAULT,
                modifier = Modifier.size(
                    width = MobileDimensions.posterWidth,
                    height = MobileDimensions.posterHeight
                )
            )
            // Stream name + rating
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.bounceMarquee()
                )
                item.metadata.rating?.let { rating ->
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.textMedium),
                        maxLines = 1
                    )
                }
                // "What's On Now" for Live TV
                nowPlayingProgram?.let { program ->
                    Text(
                        text = "Now: ${program.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        modifier = Modifier.bounceMarquee()
                    )
                }
            }
        }
    }
}
