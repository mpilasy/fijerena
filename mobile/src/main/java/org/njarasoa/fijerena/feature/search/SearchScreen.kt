package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSearchScreen(
    contentType: String,
    onStreamSelected: (itemId: String, itemName: String, categoryId: String, contentType: String) -> Unit,
    onCategorySelected: (categoryId: String, contentType: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    viewModel: SearchViewModel =
        viewModel(
            factory =
                SearchViewModelFactory(
                    context = LocalContext.current.applicationContext,
                    contentType = contentType,
                ),
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }

    // Favorite long-press state
    var favoriteMenuTarget by remember { mutableStateOf<MobileSearchFavoriteTarget?>(null) }

    favoriteMenuTarget?.let { target ->
        MobileSearchFavoriteDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is MobileSearchFavoriteTarget.Category -> {
                        viewModel.toggleFavoriteCategory(
                            target.categoryId,
                            target.categoryName,
                            target.contentType,
                            target.isFavorite,
                        )
                    }
                    is MobileSearchFavoriteTarget.Stream -> {
                        viewModel.toggleFavorite(
                            target.itemId,
                            target.itemName,
                            target.categoryId,
                            target.contentType,
                            target.isFavorite,
                        )
                    }
                }
            },
            onDismiss = { favoriteMenuTarget = null },
        )
    }

    // Restore search query from ViewModel on screen re-entry (e.g. back from stream playback)
    LaunchedEffect(Unit) {
        val vmQuery = (viewModel.uiState.value as? SearchViewModel.UiState.Success)?.query ?: ""
        if (vmQuery.isNotEmpty() && searchQuery.isEmpty()) {
            searchQuery = vmQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Search bar — search triggers on magnifying glass tap or keyboard search action
            val keyboardController = LocalSoftwareKeyboardController.current
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                placeholder = { Text("Search streams...") },
                leadingIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.performSearch(searchQuery)
                            keyboardController?.hide()
                        }
                    }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.performSearch(searchQuery)
                                keyboardController?.hide()
                            }
                        },
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textDisabled),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.tint),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    ),
            )

            // Content
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md),
            ) {
                when (val state = uiState) {
                    is SearchViewModel.UiState.Loading -> {
                        LoadingView()
                    }
                    is SearchViewModel.UiState.Error -> {
                        ErrorView(message = state.message)
                    }
                    is SearchViewModel.UiState.Success -> {
                        val failedSuffix = if (state.failedCalls > 0) " (${state.failedCalls} failed)" else ""
                        val errorSuffix = if (state.firstError != null) "\n${state.firstError}" else ""
                        val devStats =
                            if (appSettings.isDevMode && state.searchDataSize != null) {
                                "${state.searchDataSize} fetched | ${state.totalDuration} total | network: ${state.networkWallDuration} wall / ${state.networkAccumDuration} accum | ${state.networkCalls} calls$failedSuffix$errorSuffix"
                            } else {
                                null
                            }
                        SearchResults(
                            categoryResults = state.categoryResults,
                            results = state.filteredResults,
                            query = state.query,
                            queryContentType = contentType,
                            isSearching = state.isSearching,
                            searchProgress = state.searchProgress ?: "",
                            devStats = devStats,
                            searchHistory = searchHistory,
                            onHistoryItemClick = { term ->
                                searchQuery = term
                                viewModel.performSearch(term)
                                keyboardController?.hide()
                            },
                            onHistoryItemRemove = { viewModel.removeSearchHistoryEntry(it) },
                            onClearHistory = { viewModel.clearSearchHistory() },
                            onResultClick = { result ->
                                onStreamSelected(
                                    result.itemId,
                                    result.streamName,
                                    result.categoryId,
                                    result.contentType,
                                )
                            },
                            onResultLongPress = { result ->
                                favoriteMenuTarget =
                                    MobileSearchFavoriteTarget.Stream(
                                        itemId = result.itemId,
                                        itemName = result.streamName,
                                        categoryId = result.categoryId,
                                        contentType = result.contentType,
                                        isFavorite = viewModel.isFavorite(result.itemId, result.contentType),
                                    )
                            },
                            onCategoryClick = { catResult ->
                                onCategorySelected(catResult.categoryId, catResult.contentType)
                            },
                            onCategoryLongPress = { catResult ->
                                favoriteMenuTarget =
                                    MobileSearchFavoriteTarget.Category(
                                        categoryId = catResult.categoryId,
                                        categoryName = catResult.categoryName,
                                        contentType = catResult.contentType,
                                        isFavorite = viewModel.isFavoriteCategory(catResult.categoryId, catResult.contentType),
                                    )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading categories...",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.xl),
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SearchResults(
    categoryResults: List<SearchViewModel.CategorySearchResult>,
    results: List<SearchViewModel.SearchResult>,
    query: String,
    queryContentType: String,
    isSearching: Boolean,
    searchProgress: String?,
    devStats: String?,
    searchHistory: List<String>,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onResultClick: (SearchViewModel.SearchResult) -> Unit,
    onResultLongPress: (SearchViewModel.SearchResult) -> Unit,
    onCategoryClick: (SearchViewModel.CategorySearchResult) -> Unit,
    onCategoryLongPress: (SearchViewModel.CategorySearchResult) -> Unit,
) {
    var expandedGroups by rememberSaveable { mutableStateOf(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }

    fun toggleGroup(contentType: String) {
        expandedGroups =
            if (expandedGroups.contains(contentType)) {
                expandedGroups - contentType
            } else {
                expandedGroups + contentType
            }
    }

    val hasResults = categoryResults.isNotEmpty() || results.isNotEmpty() || isSearching
    if (!hasResults && query.isBlank()) {
        if (searchHistory.isNotEmpty()) {
            MobileSearchHistorySection(
                history = searchHistory,
                onItemClick = onHistoryItemClick,
                onItemRemove = onHistoryItemRemove,
                onClearAll = onClearHistory,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Search categories and streams across all categories",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
            }
        }
    } else if (!isSearching && categoryResults.isEmpty() && results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No results found for '$query'\nTry different keywords",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )
        }
    } else {
        // Pre-compute grouped results outside LazyColumn to avoid O(N×types) filter per recomposition
        val groupedByType =
            remember(categoryResults, results) {
                val catsByType = categoryResults.groupBy { it.contentType }
                val streamsByType = results.groupBy { it.contentType }
                val allTypes = (catsByType.keys + streamsByType.keys).distinct()
                val sortedTypes = listOf("LIVE_TV", "MOVIES", "TV_SHOWS") + (allTypes - setOf("LIVE_TV", "MOVIES", "TV_SHOWS"))
                sortedTypes.map { type ->
                    Triple(type, catsByType[type].orEmpty(), streamsByType[type].orEmpty())
                }
            }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            contentPadding = PaddingValues(bottom = Spacing.md),
        ) {
            // Progress / complete message
            if (isSearching && searchProgress != null) {
                item(key = "search_progress", contentType = "status") {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MobileDimensions.progressIndicatorSmall),
                            strokeWidth = MobileDimensions.strokeWidth,
                        )
                        Text(
                            text = searchProgress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else if (!isSearching && searchProgress != null) {
                item(key = "search_complete", contentType = "status") {
                    Text(
                        text = searchProgress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = Spacing.xs),
                    )
                }
            }

            // Dev stats on separate line
            if (devStats != null) {
                item(key = "dev_stats", contentType = "status") {
                    Text(
                        text = devStats,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * CinemaAlpha.textMedium,
                            ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }

            // Result count
            if (categoryResults.isNotEmpty() || results.isNotEmpty()) {
                item(key = "result_count", contentType = "status") {
                    Text(
                        text = "${categoryResults.size + results.size} results",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                }
            }

            if (queryContentType == "ALL") {
                groupedByType.forEach { (type, typeCats, typeStreams) ->

                    if (typeCats.isNotEmpty() || typeStreams.isNotEmpty()) {
                        val isExpanded = expandedGroups.contains(type)
                        item(key = "header_$type", contentType = "header") {
                            MobileCollapsibleHeader(
                                title = getContentTypeLabel(type),
                                isExpanded = isExpanded,
                                onToggle = { toggleGroup(type) },
                            )
                        }

                        if (isExpanded) {
                            items(typeCats, key = { "cat_${it.categoryId}_${it.contentType}" }, contentType = { "category" }) { catResult ->
                                CategoryResultCard(
                                    result = catResult,
                                    onClick = { onCategoryClick(catResult) },
                                    onLongClick = { onCategoryLongPress(catResult) },
                                )
                            }
                            items(
                                typeStreams,
                                key = { "stream_${it.itemId}_${it.categoryId}_${it.contentType}" },
                                contentType = { "stream" },
                            ) { result ->
                                SearchResultCard(
                                    result = result,
                                    onClick = { onResultClick(result) },
                                    onLongClick = { onResultLongPress(result) },
                                )
                            }
                        }
                    }
                }
            } else {
                if (categoryResults.isNotEmpty()) {
                    item(key = "category_header", contentType = "header") {
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = Spacing.xxs),
                        )
                    }
                    items(categoryResults, key = { "cat_${it.categoryId}_${it.contentType}" }, contentType = { "category" }) { catResult ->
                        CategoryResultCard(
                            result = catResult,
                            onClick = { onCategoryClick(catResult) },
                            onLongClick = { onCategoryLongPress(catResult) },
                        )
                    }
                }
                if (results.isNotEmpty()) {
                    item(key = "stream_header", contentType = "header") {
                        Text(
                            text = "Streams",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = Spacing.xxs),
                        )
                    }
                    items(
                        results,
                        key = { "search_${it.contentType}_${it.categoryId}_${it.itemId}" },
                        contentType = { "stream" },
                    ) { result ->
                        SearchResultCard(
                            result = result,
                            onClick = { onResultClick(result) },
                            onLongClick = { onResultLongPress(result) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileSearchHistorySection(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onItemRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent Searches",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )
            IconButton(onClick = onClearAll) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear all",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    modifier = Modifier.size(MobileDimensions.iconSmall),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            history.forEach { term ->
                AssistChip(
                    onClick = { onItemClick(term) },
                    label = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onItemRemove(term) },
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MobileCollapsibleHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xxs),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CinemaAlpha.tint),
        shape = RoundedCornerShape(CinemaCornerRadius.small),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MobileDimensions.iconSmall),
            )
        }
    }
}

private fun getContentTypeLabel(contentType: String): String =
    when (contentType) {
        "LIVE_TV" -> "Live TV"
        "MOVIES" -> "Movies"
        "TV_SHOWS" -> "TV Shows"
        else -> contentType.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CategoryResultCard(
    result: SearchViewModel.CategorySearchResult,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = CinemaAlpha.tint),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = result.categoryName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SearchResultCard(
    result: SearchViewModel.SearchResult,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(MobileDimensions.streamCardHeight)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(CinemaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            CinemaThumbnail(
                url = result.thumbnailUrl,
                fallbackLetter = result.streamName.firstOrNull(),
                contentType = ThumbnailContentType.DEFAULT,
                modifier =
                    Modifier.size(
                        width = MobileDimensions.posterWidth,
                        height = MobileDimensions.posterHeight,
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = result.streamName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Category: ${result.categoryName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
            }
        }
    }
}

private sealed class MobileSearchFavoriteTarget {
    data class Category(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val isFavorite: Boolean,
    ) : MobileSearchFavoriteTarget()

    data class Stream(
        val itemId: String,
        val itemName: String,
        val categoryId: String,
        val contentType: String,
        val isFavorite: Boolean,
    ) : MobileSearchFavoriteTarget()
}

@Composable
private fun MobileSearchFavoriteDialog(
    target: MobileSearchFavoriteTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (itemName, isFavorite) =
        when (target) {
            is MobileSearchFavoriteTarget.Category -> target.categoryName to target.isFavorite
            is MobileSearchFavoriteTarget.Stream -> target.itemName to target.isFavorite
        }
    val actionText = if (isFavorite) "Remove from Favorites" else "Add to Favorites"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = itemName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(actionText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(CinemaCornerRadius.large),
    )
}
