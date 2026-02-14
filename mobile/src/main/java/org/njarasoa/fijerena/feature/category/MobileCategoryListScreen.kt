package org.njarasoa.fijerena.feature.category

import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileCategoryListScreen(
    contentType: String,
    initialCategoryId: String? = null,
    onStreamSelected: (itemId: String, itemName: String, categoryId: String, contentType: String) -> Unit,
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
    val uiState by viewModel.uiState.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val supportsNativeEpg by viewModel.supportsNativeEpg.collectAsState()
    val context = LocalContext.current
    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
    val epgIndexState by epgIndexer.state.collectAsState()
    val appSettings = remember { AppSettings(context.applicationContext) }
    val isDevMode = remember { appSettings.isDevMode }

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
                    if (contentType == "LIVE_TV") {
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
                        if (contentType == "LIVE_TV") {
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
                                        "EPG: ${epg.programmeCount} progs, ${epg.channelCount} channels"
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
                            onCategorySelected = { categoryId ->
                                viewModel.loadStreams(categoryId)
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
                                nowPlaying = nowPlaying,
                                onItemSelected = { itemId, itemName, categoryId ->
                                    onStreamSelected(itemId, itemName, categoryId, contentType)
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

@Composable
private fun CategoryChipRow(
    categories: List<org.njarasoa.fijerena.core.player.domain.MediaCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (String) -> Unit
) {
    val virtualCategoryIds = setOf(
        CategoryViewModel.FAVORITES_CATEGORY_ID,
        CategoryViewModel.LAST_WATCHED_CATEGORY_ID
    )
    val virtualCategories = categories.filter { it.id in virtualCategoryIds }
    val regularCategories = categories.filter { it.id !in virtualCategoryIds }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null && selectedCategoryId !in virtualCategoryIds) {
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
                items(virtualCategories, key = { it.id }) { category ->
                    FilterChip(
                        selected = category.id == selectedCategoryId,
                        onClick = { onCategorySelected(category.id) },
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
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
            items(regularCategories, key = { it.id }) { category ->
                FilterChip(
                    selected = category.id == selectedCategoryId,
                    onClick = { onCategorySelected(category.id) },
                    label = {
                        Text(
                            text = category.name,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
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
    nowPlaying: Map<String, EpgProgram> = emptyMap(),
    onItemSelected: (itemId: String, itemName: String, categoryId: String) -> Unit
) {
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        text = "${items.size} streams",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }
                items(items, key = { it.id }) { item ->
                    StreamCard(
                        item = item,
                        nowPlayingProgram = nowPlaying[item.id],
                        onClick = {
                            onItemSelected(item.id, item.name, item.categoryId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamCard(
    item: org.njarasoa.fijerena.core.player.domain.MediaItem,
    nowPlayingProgram: EpgProgram? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(MobileDimensions.streamCardHeight),
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
                    modifier = Modifier.basicMarquee()
                )
                item.metadata?.rating?.let { rating ->
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
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }
    }
}
