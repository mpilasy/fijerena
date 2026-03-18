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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.CategorySearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
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
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, contentType: String) -> Unit,
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    // Favorite long-press state
    var favoriteMenuTarget by remember { mutableStateOf<SearchFavoriteTarget?>(null) }

    favoriteMenuTarget?.let { target ->
        SearchFavoriteDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is SearchFavoriteTarget.Category -> {
                        viewModel.toggleFavoriteCategory(
                            target.categoryId,
                            target.categoryName,
                            target.contentType,
                            target.isFavorite
                        )
                    }
                    is SearchFavoriteTarget.Stream -> {
                        viewModel.toggleFavorite(
                            target.itemId,
                            target.itemName,
                            target.categoryId,
                            target.contentType,
                            target.isFavorite
                        )
                    }
                }
            },
            onDismiss = { favoriteMenuTarget = null }
        )
    }

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
            HeaderRow(contentType = contentType)

            Spacer(modifier = Modifier.height(Spacing.lg))

            when (uiState) {
                is SearchViewModel.UiState.Loading -> LoadingView()
                is SearchViewModel.UiState.Error -> ErrorView((uiState as SearchViewModel.UiState.Error).message)
                is SearchViewModel.UiState.Success -> {
                    val successState = uiState as SearchViewModel.UiState.Success
                    val failedSuffix = if (successState.failedCalls > 0) " (${successState.failedCalls} failed)" else ""
                    val errorSuffix = if (successState.firstError != null) "\n${successState.firstError}" else ""
                    val devStats = if (appSettings.isDevMode && successState.searchDataSize != null) {
                        "${successState.searchDataSize} fetched | ${successState.totalDuration} total | network: ${successState.networkWallDuration} wall / ${successState.networkAccumDuration} accum | ${successState.networkCalls} calls$failedSuffix$errorSuffix"
                    } else null
                    SearchContent(
                        query = successState.query,
                        categoryResults = successState.categoryResults,
                        results = successState.filteredResults,
                        isSearching = successState.isSearching,
                        searchProgress = successState.searchProgress ?: "",
                        devStats = devStats,
                        contentType = contentType,
                        onSearchSubmit = { viewModel.performSearch(it) },
                        onResultClick = { result ->
                            onStreamSelected(result.itemId, result.streamName, result.categoryId, result.contentType)
                        },
                        onResultLongPress = { result ->
                            favoriteMenuTarget = SearchFavoriteTarget.Stream(
                                itemId = result.itemId,
                                itemName = result.streamName,
                                categoryId = result.categoryId,
                                contentType = result.contentType,
                                isFavorite = viewModel.isFavorite(result.itemId, result.contentType)
                            )
                        },
                        onCategoryClick = { catResult ->
                            onCategorySelected(catResult.categoryId, catResult.contentType)
                        },
                        onCategoryLongPress = { catResult ->
                            favoriteMenuTarget = SearchFavoriteTarget.Category(
                                categoryId = catResult.categoryId,
                                categoryName = catResult.categoryName,
                                contentType = catResult.contentType,
                                isFavorite = viewModel.isFavoriteCategory(catResult.categoryId, catResult.contentType)
                            )
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
    contentType: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    devStats: String?,
    contentType: String,
    onSearchSubmit: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onResultLongPress: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit,
    onCategoryLongPress: (CategorySearchResult) -> Unit
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

        // Results or empty state — show results whenever they exist, even if text field is cleared
        val hasResults = categoryResults.isNotEmpty() || results.isNotEmpty() || isSearching
        if (!hasResults && query.isEmpty()) {
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
                query = query,
                queryContentType = contentType,
                isSearching = isSearching,
                searchProgress = searchProgress,
                devStats = devStats,
                onResultClick = onResultClick,
                onResultLongPress = onResultLongPress,
                onCategoryClick = onCategoryClick,
                onCategoryLongPress = onCategoryLongPress
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
            CinemaIconButton(
                onClick = onSearchSubmit,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = androidx.compose.ui.graphics.Color.Unspecified
                    )
                }
            )
        }
    }

    // Auto-focus on screen open
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: IllegalStateException) {}
    }
}

@Composable
private fun SearchResultsList(
    categoryResults: List<CategorySearchResult>,
    results: List<SearchResult>,
    query: String,
    queryContentType: String,
    isSearching: Boolean,
    searchProgress: String?,
    devStats: String?,
    onResultClick: (SearchResult) -> Unit,
    onResultLongPress: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit,
    onCategoryLongPress: (CategorySearchResult) -> Unit
) {
    // Stable map — only add missing keys, never discard existing FocusRequesters
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    var expandedGroups by rememberSaveable { mutableStateOf(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }

    fun toggleGroup(contentType: String) {
        expandedGroups = if (expandedGroups.contains(contentType)) {
            expandedGroups - contentType
        } else {
            expandedGroups + contentType
        }
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

                if (searchProgress != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(TvDimensions.iconSmall),
                                color = CinemaAccent,
                                strokeWidth = TvDimensions.borderFocused
                            )
                        }
                        Text(
                            text = searchProgress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaAccent
                        )
                    }
                }
            }

            if (devStats != null) {
                Text(
                    text = devStats,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * CinemaAlpha.textMedium
                    ),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xs),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }

            // Pre-compute grouped results outside TvLazyColumn to avoid O(N×types) per recomposition
            val groupedByType = remember(categoryResults, results) {
                val catsByType = categoryResults.groupBy { it.contentType }
                val streamsByType = results.groupBy { it.contentType }
                val allTypes = (catsByType.keys + streamsByType.keys).distinct()
                val sortedTypes = listOf("LIVE_TV", "MOVIES", "TV_SHOWS") + (allTypes - setOf("LIVE_TV", "MOVIES", "TV_SHOWS"))
                sortedTypes.map { type ->
                    Triple(type, catsByType[type].orEmpty(), streamsByType[type].orEmpty())
                }
            }

            TvLazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize()
            ) {
                if (queryContentType == "ALL") {
                    groupedByType.forEach { (type, typeCats, typeStreams) ->

                        if (typeCats.isNotEmpty() || typeStreams.isNotEmpty()) {
                            val isExpanded = expandedGroups.contains(type)
                            item(key = "header_$type", contentType = "header") {
                                CollapsibleHeader(
                                    title = getContentTypeLabel(type),
                                    isExpanded = isExpanded,
                                    onToggle = { toggleGroup(type) }
                                )
                            }

                            if (isExpanded) {
                                // Show categories for this type
                                items(typeCats, key = { "cat_${it.categoryId}_${it.contentType}" }, contentType = { "category" }) { catResult ->
                                    CategoryResultItem(
                                        result = catResult,
                                        onClick = { onCategoryClick(catResult) },
                                        onLongPress = { onCategoryLongPress(catResult) }
                                    )
                                }
                                // Show streams for this type
                                items(typeStreams, key = { "stream_${it.itemId}_${it.categoryId}_${it.contentType}" }, contentType = { "stream" }) { result ->
                                    SearchResultItem(
                                        result = result,
                                        onClick = { onResultClick(result) },
                                        onLongPress = { onResultLongPress(result) },
                                        focusRequester = focusRequesters.getOrPut(result.itemId) { FocusRequester() }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Specific content type search - no need for collapsible groups
                    if (categoryResults.isNotEmpty()) {
                        item(key = "category_header", contentType = "header") {
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
                        items(categoryResults, key = { "cat_${it.categoryId}_${it.contentType}" }, contentType = { "category" }) { catResult ->
                            CategoryResultItem(
                                result = catResult,
                                onClick = { onCategoryClick(catResult) },
                                onLongPress = { onCategoryLongPress(catResult) }
                            )
                        }
                    }

                    if (results.isNotEmpty()) {
                        item(key = "stream_header", contentType = "header") {
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
                        items(results, key = { "${it.itemId}_${it.categoryId}" }, contentType = { "stream" }) { result ->
                            SearchResultItem(
                                result = result,
                                onClick = { onResultClick(result) },
                                onLongPress = { onResultLongPress(result) },
                                focusRequester = focusRequesters.getOrPut(result.itemId) { FocusRequester() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        colors = CardDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedTint)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    TvDimensions.borderFocused,
                    CinemaAccent
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaAccent,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = CinemaAccent,
                modifier = Modifier.size(TvDimensions.iconSmall)
            )
        }
    }
}

private fun getContentTypeLabel(contentType: String): String {
    return when (contentType) {
        "LIVE_TV" -> "Live TV"
        "MOVIES" -> "Movies"
        "TV_SHOWS" -> "TV Shows"
        else -> contentType.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

@Composable
private fun CategoryResultItem(
    result: CategorySearchResult,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .fillMaxWidth()
            .height(TvDimensions.cardHeight)
            .tvLongPress(onLongPress),
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
    onLongPress: () -> Unit = {},
    focusRequester: FocusRequester?
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .fillMaxWidth()
            .height(TvDimensions.cardHeight)
            .tvLongPress(onLongPress)
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

private sealed class SearchFavoriteTarget {
    data class Category(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val isFavorite: Boolean
    ) : SearchFavoriteTarget()

    data class Stream(
        val itemId: String,
        val itemName: String,
        val categoryId: String,
        val contentType: String,
        val isFavorite: Boolean
    ) : SearchFavoriteTarget()
}

@Composable
private fun SearchFavoriteDialog(
    target: SearchFavoriteTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (itemName, isFavorite) = when (target) {
        is SearchFavoriteTarget.Category -> target.categoryName to target.isFavorite
        is SearchFavoriteTarget.Stream -> target.itemName to target.isFavorite
    }
    val actionText = if (isFavorite) "Remove from Favorites" else "Add to Favorites"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.tv.material3.Text(
                text = itemName,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                maxLines = 2
            )
        },
        text = null,
        confirmButton = {
            CinemaPrimaryButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                text = actionText
            )
        },
        dismissButton = {
            CinemaSecondaryButton(
                onClick = onDismiss,
                text = "Cancel"
            )
        },
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
        shape = RoundedCornerShape(CornerRadius.large)
    )
}

private fun Modifier.tvLongPress(onLongPress: () -> Unit): Modifier = composed {
    var longPressDetected by remember { mutableStateOf(false) }
    this.onPreviewKeyEvent { event ->
        val keyCode = event.key.nativeKeyCode
        val isDpadCenter = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == android.view.KeyEvent.KEYCODE_ENTER
        if (isDpadCenter &&
            event.type == KeyEventType.KeyDown &&
            event.nativeKeyEvent.repeatCount > 0 &&
            event.nativeKeyEvent.isLongPress &&
            !longPressDetected
        ) {
            longPressDetected = true
            true
        } else if (isDpadCenter && event.type == KeyEventType.KeyDown && longPressDetected) {
            true
        } else if (isDpadCenter && event.type == KeyEventType.KeyUp && longPressDetected) {
            longPressDetected = false
            onLongPress()
            true
        } else {
            false
        }
    }
}
