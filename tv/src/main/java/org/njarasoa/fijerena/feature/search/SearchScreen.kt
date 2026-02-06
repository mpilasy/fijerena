@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.CategorySearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * Search screen for searching streams across all categories.
 *
 * Features:
 * - Search input field at top
 * - Real-time filtered results below
 * - D-pad navigation between search field and results
 * - TV-optimized with 5% overscan padding
 */
@Composable
fun SearchScreen(
    contentType: String,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onCategorySelected: (categoryId: String, contentType: String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SearchViewModel = viewModel(
        factory = remember(contentType) {
            SearchViewModelFactory(
                context = context.applicationContext,
                contentType = contentType
            )
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                )
        ) {
            // Header with back button
            HeaderRow(onBack = onBack, contentType = contentType)

            Spacer(modifier = Modifier.height(Spacing.lg))

            when (uiState) {
                is SearchViewModel.UiState.Loading -> LoadingView()
                is SearchViewModel.UiState.Error -> ErrorView((uiState as SearchViewModel.UiState.Error).message)
                is SearchViewModel.UiState.Success -> {
                    val successState = uiState as SearchViewModel.UiState.Success
                    SearchContent(
                        query = successState.query,
                        categoryResults = successState.categoryResults,
                        results = successState.filteredResults,
                        isSearching = successState.isSearching,
                        searchProgress = successState.searchProgress,
                        onSearchSubmit = { viewModel.updateSearchQuery(it) },
                        onResultClick = { result ->
                            onStreamSelected(result.itemId, result.streamName, result.categoryId)
                        },
                        onCategoryClick = { catResult ->
                            onCategorySelected(catResult.categoryId, catResult.contentType)
                        }
                    )
                }
            }
        }
    }
    } // CompositionLocalProvider
}

@Composable
private fun HeaderRow(
    onBack: () -> Unit,
    contentType: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CinemaSecondaryButton(
                onClick = onBack,
                text = "← Back"
            )
            Column {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = contentType.replace("_", " "),
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaAccent
                )
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
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.iconXLarge),
                color = CinemaAccent
            )
            Text(
                text = "Loading categories...",
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextSecondary
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
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary
            )
        }
    }
}

@Composable
private fun SearchContent(
    query: String,
    categoryResults: List<CategorySearchResult>,
    results: List<SearchResult>,
    isSearching: Boolean,
    searchProgress: String?,
    onSearchSubmit: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    // Local state for text field - manages user input independently
    var localQuery by remember { mutableStateOf(query) }

    // Sync with incoming query only if local is empty (prevents erasing user input)
    LaunchedEffect(query) {
        if (localQuery.isEmpty() && query.isNotEmpty()) {
            localQuery = query
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        SearchTextField(
            query = localQuery,
            onQueryChange = { localQuery = it },
            onSearchSubmit = { onSearchSubmit(localQuery) },
            focusRequester = searchFocusRequester
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Results or empty state
        if (localQuery.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Search categories and streams across all categories",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )
            }
        } else {
            SearchResultsList(
                categoryResults = categoryResults,
                results = results,
                query = localQuery,
                isSearching = isSearching,
                searchProgress = searchProgress,
                onResultClick = onResultClick,
                onCategoryClick = onCategoryClick
            )
        }
    }
}

@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    focusRequester: FocusRequester
) {
    GlassPanel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search") },
                placeholder = { Text("Enter stream name...") },
                singleLine = true,
                modifier = Modifier
                    .width(TvDimensions.formFieldWidth)
                    .focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CinemaTextPrimary,
                    unfocusedTextColor = CinemaTextPrimary,
                    cursorColor = CinemaAccent,
                    focusedBorderColor = CinemaAccent,
                    unfocusedBorderColor = CinemaTextSecondary,
                    focusedLabelColor = CinemaAccent,
                    unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    focusedPlaceholderColor = CinemaTextSecondary,
                    unfocusedPlaceholderColor = CinemaTextSecondary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearchSubmit() }
                )
            )
            CinemaPrimaryButton(
                onClick = onSearchSubmit,
                text = "Search"
            )
        }
    }

    // Auto-focus on screen open
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun SearchResultsList(
    categoryResults: List<CategorySearchResult>,
    results: List<SearchResult>,
    query: String,
    isSearching: Boolean,
    searchProgress: String?,
    onResultClick: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit
) {
    println("SearchResultsList: Received ${results.size} results, query='$query', isSearching=$isSearching")
    if (results.isNotEmpty()) {
        println("SearchResultsList: First result - ${results.first().streamName}")
    }

    val focusRequesters = remember(results) {
        results.associate { it.itemId to FocusRequester() }
    }

    if (isSearching && categoryResults.isEmpty() && results.isEmpty()) {
        // Show loading state while searching
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(TvDimensions.iconXLarge),
                    color = CinemaAccent
                )
                Text(
                    text = searchProgress ?: "Searching...",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary
                )
            }
        }
    } else if (categoryResults.isEmpty() && results.isEmpty()) {
        // No results found after search completed
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No results found for '$query'. Try different keywords.",
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
        }
    } else {
        Column {
            val totalResults = categoryResults.size + results.size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalResults results",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )

                if (isSearching && searchProgress != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(TvDimensions.iconSmall),
                            color = CinemaAccent,
                            strokeWidth = TvDimensions.borderFocused
                        )
                        Text(
                            text = searchProgress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaAccent
                        )
                    }
                }
            }

            TvLazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize()
            ) {
                if (categoryResults.isNotEmpty()) {
                    item(key = "category_header") {
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.titleMedium,
                            color = CinemaAccent,
                            modifier = Modifier.padding(
                                horizontal = Spacing.md,
                                vertical = Spacing.xs
                            )
                        )
                    }
                    items(categoryResults, key = { "cat_${it.categoryId}" }) { catResult ->
                        CategoryResultItem(
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
                            color = CinemaAccent,
                            modifier = Modifier.padding(
                                horizontal = Spacing.md,
                                vertical = Spacing.xs
                            )
                        )
                    }
                    items(results, key = { "${it.itemId}_${it.categoryId}" }) { result ->
                        SearchResultItem(
                            result = result,
                            onClick = { onResultClick(result) },
                            focusRequester = focusRequesters[result.itemId]
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryResultItem(
    result: CategorySearchResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .fillMaxWidth()
            .height(TvDimensions.cardHeight),
        colors = CardDefaults.colors(
            containerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.glassBorder),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(CornerRadius.medium)),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                elevation = TvFocusTokens.focusShadowElevation
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent gradient strip on left edge
            Box(
                modifier = Modifier
                    .width(TvDimensions.borderFocused)
                    .height(TvDimensions.cardHeight)
                    .padding(vertical = Spacing.sm)
                    .then(
                        Modifier.fillMaxHeight()
                    )
            )
            Text(
                text = result.categoryName,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    focusRequester: FocusRequester?
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .fillMaxWidth()
            .height(TvDimensions.cardHeight)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.glassBorder),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(CornerRadius.medium)),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                elevation = TvFocusTokens.focusShadowElevation
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            CinemaThumbnail(
                url = result.thumbnailUrl,
                fallbackLetter = result.streamName.firstOrNull(),
                contentType = ThumbnailContentType.DEFAULT,
                modifier = Modifier.size(
                    width = TvDimensions.posterWidth,
                    height = TvDimensions.posterHeight
                )
            )
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = result.streamName,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Category: ${result.categoryName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary
                )
            }
        }
    }
}
