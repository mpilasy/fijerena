@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.search

import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.*

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
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String) -> Unit,
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
                    println("SearchScreen: Rendering with ${successState.filteredResults.size} results, query='${successState.query}', isSearching=${successState.isSearching}")
                    SearchContent(
                        query = successState.query,
                        results = successState.filteredResults,
                        isSearching = successState.isSearching,
                        searchProgress = successState.searchProgress,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onResultClick = { result ->
                            onStreamSelected(result.streamId, result.streamName, result.categoryId)
                        }
                    )
                }
            }
        }
    }
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
                modifier = Modifier.size(48.dp),
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
    results: List<SearchResult>,
    isSearching: Boolean,
    searchProgress: String?,
    onQueryChange: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit
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
            onQueryChange = { newValue ->
                localQuery = newValue
                onQueryChange(newValue)
            },
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
                    text = "Enter search term to find streams across all categories",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary.copy(alpha = 0.87f)
                )
            }
        } else {
            SearchResultsList(
                results = results,
                query = localQuery,
                isSearching = isSearching,
                searchProgress = searchProgress,
                onResultClick = onResultClick
            )
        }
    }
}

@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search") },
        placeholder = { Text("Enter stream name...") },
        singleLine = true,
        modifier = Modifier
            .width(600.dp)
            .focusRequester(focusRequester),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = CinemaTextPrimary,
            unfocusedTextColor = CinemaTextPrimary,
            cursorColor = CinemaAccent,
            focusedBorderColor = CinemaAccent,
            unfocusedBorderColor = CinemaTextSecondary,
            focusedLabelColor = CinemaAccent,
            unfocusedLabelColor = CinemaTextSecondary.copy(alpha = 0.87f),
            focusedPlaceholderColor = CinemaTextSecondary,
            unfocusedPlaceholderColor = CinemaTextSecondary
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { /* Focus stays on field for continued searching */ }
        )
    )

    // Auto-focus on screen open
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    query: String,
    isSearching: Boolean,
    searchProgress: String?,
    onResultClick: (SearchResult) -> Unit
) {
    println("SearchResultsList: Received ${results.size} results, query='$query', isSearching=$isSearching")
    if (results.isNotEmpty()) {
        println("SearchResultsList: First result - ${results.first().streamName}")
    }

    val focusRequesters = remember(results) {
        results.associate { it.streamId to FocusRequester() }
    }

    if (isSearching && results.isEmpty()) {
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
                    modifier = Modifier.size(48.dp),
                    color = CinemaAccent
                )
                Text(
                    text = searchProgress ?: "Searching...",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary
                )
            }
        }
    } else if (results.isEmpty()) {
        // No results found after search completed
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No results found for '$query'. Try different keywords.",
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary.copy(alpha = 0.87f)
            )
        }
    } else {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${results.size} results",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary.copy(alpha = 0.87f)
                )

                if (isSearching && searchProgress != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CinemaAccent,
                            strokeWidth = 2.dp
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
                items(results, key = { "${it.streamId}_${it.categoryId}" }) { result ->
                    SearchResultItem(
                        result = result,
                        onClick = { onResultClick(result) },
                        focusRequester = focusRequesters[result.streamId]
                    )
                }
            }
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
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(80.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = 0.15f),
            focusedContentColor = CinemaTextPrimary
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = CinemaAccentLight)
            )
        ),
        shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(CornerRadius.medium)),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.1f,
            pressedScale = 0.98f
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = CinemaAccent.copy(alpha = 0.4f),
                elevation = 8.dp
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
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
