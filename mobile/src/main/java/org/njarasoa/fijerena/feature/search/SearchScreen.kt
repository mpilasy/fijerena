package org.njarasoa.fijerena.feature.search

import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSearchScreen(
    contentType: String,
    onStreamSelected: (itemId: String, itemName: String, categoryId: String, contentType: String) -> Unit,
    onCategorySelected: (categoryId: String, contentType: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(
            context = LocalContext.current.applicationContext,
            contentType = contentType
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar — search triggers on magnifying glass tap or keyboard search action
            val keyboardController = LocalSoftwareKeyboardController.current
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                placeholder = { Text("Search streams...") },
                leadingIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.updateSearchQuery(searchQuery)
                            keyboardController?.hide()
                        }
                    }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.updateSearchQuery(searchQuery)
                            keyboardController?.hide()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textDisabled),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.tint),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
            )

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md)
            ) {
                when (val state = uiState) {
                    is SearchViewModel.UiState.Loading -> {
                        LoadingView()
                    }
                    is SearchViewModel.UiState.Error -> {
                        ErrorView(message = state.message)
                    }
                    is SearchViewModel.UiState.Success -> {
                        val devStats = if (appSettings.isDevMode && state.searchDataSize != null) {
                            " | ${state.searchDataSize} in ${state.searchDuration}"
                        } else ""
                        SearchResults(
                            categoryResults = state.categoryResults,
                            results = state.filteredResults,
                            query = searchQuery,
                            isSearching = state.isSearching,
                            searchProgress = (state.searchProgress ?: "") + devStats,
                            onResultClick = { result ->
                                onStreamSelected(
                                    result.itemId,
                                    result.streamName,
                                    result.categoryId,
                                    result.contentType
                                )
                            },
                            onCategoryClick = { catResult ->
                                onCategorySelected(catResult.categoryId, catResult.contentType)
                            }
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
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading categories...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.xl)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SearchResults(
    categoryResults: List<SearchViewModel.CategorySearchResult>,
    results: List<SearchViewModel.SearchResult>,
    query: String,
    isSearching: Boolean,
    searchProgress: String?,
    onResultClick: (SearchViewModel.SearchResult) -> Unit,
    onCategoryClick: (SearchViewModel.CategorySearchResult) -> Unit
) {
    if (query.isBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Search categories and streams across all categories",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
            )
        }
    } else if (!isSearching && categoryResults.isEmpty() && results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No results found for '$query'\nTry different keywords",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            contentPadding = PaddingValues(bottom = Spacing.md)
        ) {
            // Progress / complete message
            if (isSearching && searchProgress != null) {
                item(key = "search_progress") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MobileDimensions.progressIndicatorSmall),
                            strokeWidth = MobileDimensions.strokeWidth
                        )
                        Text(
                            text = searchProgress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (!isSearching && searchProgress != null) {
                item(key = "search_complete") {
                    Text(
                        text = searchProgress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    )
                }
            }

            // Result count
            if (categoryResults.isNotEmpty() || results.isNotEmpty()) {
                item(key = "result_count") {
                    Text(
                        text = "${categoryResults.size + results.size} results",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                }
            }

            if (categoryResults.isNotEmpty()) {
                item(key = "category_header") {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = Spacing.xxs)
                    )
                }
                items(categoryResults, key = { "cat_${it.categoryId}" }) { catResult ->
                    CategoryResultCard(
                        result = catResult,
                        onClick = { onCategoryClick(catResult) }
                    )
                }
            }
            if (results.isNotEmpty()) {
                item(key = "stream_header") {
                    Text(
                        text = "Streams",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = Spacing.xxs)
                    )
                }
                items(results, key = { it.itemId }) { result ->
                    SearchResultCard(
                        result = result,
                        onClick = { onResultClick(result) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryResultCard(
    result: SearchViewModel.CategorySearchResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = CinemaAlpha.tint)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = result.categoryName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchViewModel.SearchResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(MobileDimensions.streamCardHeight),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(CinemaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
        ) {
            CinemaThumbnail(
                url = result.thumbnailUrl,
                fallbackLetter = result.streamName.firstOrNull(),
                contentType = ThumbnailContentType.DEFAULT,
                modifier = Modifier.size(
                    width = MobileDimensions.posterWidth,
                    height = MobileDimensions.posterHeight
                )
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = result.streamName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Category: ${result.categoryName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
            }
        }
    }
}
