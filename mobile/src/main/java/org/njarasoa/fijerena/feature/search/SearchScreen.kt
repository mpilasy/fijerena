package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.asContentTypeLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.viewmodels.buildGroupedSearchResults
import org.njarasoa.fijerena.core.ui.viewmodels.toggled
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.chips.CinemaAssistChip
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@Composable
private fun localizedContentTypeLabel(contentType: String): String =
    when (contentType) {
        "ALL" -> stringResource(R.string.content_type_all_label)
        ContentType.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        ContentType.MOVIES -> stringResource(R.string.provider_movies_label)
        ContentType.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
        else -> contentType.asContentTypeLabel()
    }

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
    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }

    favoriteMenuTarget?.let { target ->
        MobileSearchFavoriteDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is FavoriteMenuTarget.Category -> {
                        viewModel.toggleFavoriteCategory(
                            target.categoryId,
                            target.categoryName,
                            target.contentType,
                            target.isFavorite,
                        )
                    }
                    is FavoriteMenuTarget.Stream -> {
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
                title = {
                    Text(
                        stringResource(R.string.search_title_format, localizedContentTypeLabel(contentType)),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
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
                placeholder = { Text(stringResource(R.string.search_streams_placeholder), color = CinemaTextPrimary.copy(alpha = 0.6f)) },
                shape = androidx.compose.foundation.shape.CircleShape,
                leadingIcon = {
                    CinemaIconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.performSearch(searchQuery)
                                keyboardController?.hide()
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = CinemaIcons.Search,
                                contentDescription = stringResource(R.string.common_search),
                                tint = CinemaTextPrimary
                            )
                        }
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty() || uiState is SearchViewModel.UiState.Success) {
                        CinemaIconButton(
                            onClick = {
                                searchQuery = ""
                                viewModel.clearSearch()
                            },
                            icon = {
                                Icon(
                                    imageVector = CinemaIcons.Close,
                                    contentDescription = stringResource(R.string.provider_clear_button),
                                    tint = CinemaTextPrimary
                                )
                            }
                        )
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
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        disabledTextColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textDisabled),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = CinemaSurfaceVariant,
                        unfocusedContainerColor = CinemaSurfaceLight,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = CinemaTextPrimary.copy(alpha = 0.2f),
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
                        LoadingView(message = state.message)
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
                            excludedCountByType = state.excludedCountByType,
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
                                    FavoriteMenuTarget.Stream(
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
                                    FavoriteMenuTarget.Category(
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
private fun LoadingView(message: String? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MitadyLoading(
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            overrideText = message,
        )
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
                text = stringResource(R.string.common_error),
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
    excludedCountByType: Map<String, Int>,
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

    // Built once for the whole result list rather than once per row. CardDefaults.cardColors is
    // @Composable, so it can't be wrapped in remember — hoisting the call out of the item bodies
    // is what stops a CardColors being allocated per visible result per recomposition.
    val categoryCardColors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = CinemaAlpha.tint),
        )
    val streamCardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

    fun toggleGroup(contentType: String) {
        expandedGroups = expandedGroups.toggled(contentType)
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
                    text = stringResource(R.string.search_categories_streams_hint),
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
                text =
                    if (queryContentType == "ALL") {
                        stringResource(R.string.search_no_results_query_format, query)
                    } else {
                        stringResource(R.string.search_no_results_type_format, localizedContentTypeLabel(queryContentType), query)
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )
        }
    } else {
        // Pre-compute grouped results outside LazyColumn to avoid O(N×types) filter per recomposition
        val groupedByType =
            remember(categoryResults, results) {
                buildGroupedSearchResults(categoryResults, results)
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
                        MitadyLoading(
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = " ($searchProgress)",
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

            if (queryContentType == "ALL") {
                groupedByType.forEach { (type, typeCats, typeStreams) ->

                    if (typeCats.isNotEmpty() || typeStreams.isNotEmpty()) {
                        val isExpanded = expandedGroups.contains(type)
                        item(key = "header_$type", contentType = "header") {
                            MobileCollapsibleHeader(
                                title = localizedContentTypeLabel(type),
                                count = typeCats.size + typeStreams.size,
                                hiddenCount = excludedCountByType[type] ?: 0,
                                isExpanded = isExpanded,
                                onToggle = { toggleGroup(type) },
                            )
                        }

                        if (isExpanded) {
                            items(typeCats, key = { "cat_${it.categoryId}_${it.contentType}" }, contentType = { "category" }) { catResult ->
                                CategoryResultCard(
                                    result = catResult,
                                    cardColors = categoryCardColors,
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
                                    cardColors = streamCardColors,
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
                        SearchSectionHeader(
                            title = stringResource(R.string.search_tab_categories),
                            count = categoryResults.size,
                            hiddenCount = 0,
                        )
                    }
                    items(categoryResults, key = { "cat_${it.categoryId}_${it.contentType}" }, contentType = { "category" }) { catResult ->
                        CategoryResultCard(
                            result = catResult,
                            cardColors = categoryCardColors,
                            onClick = { onCategoryClick(catResult) },
                            onLongClick = { onCategoryLongPress(catResult) },
                        )
                    }
                }
                if (results.isNotEmpty()) {
                    item(key = "stream_header", contentType = "header") {
                        SearchSectionHeader(
                            title = stringResource(R.string.search_tab_streams),
                            count = results.size,
                            hiddenCount = excludedCountByType[queryContentType] ?: 0,
                        )
                    }
                    items(
                        results,
                        key = { "search_${it.contentType}_${it.categoryId}_${it.itemId}" },
                        contentType = { "stream" },
                    ) { result ->
                        SearchResultCard(
                            result = result,
                            cardColors = streamCardColors,
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
                text = stringResource(R.string.epg_browser_recent_searches),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )
            IconButton(onClick = onClearAll) {
                Icon(
                    imageVector = CinemaIcons.Delete,
                    contentDescription = stringResource(R.string.epg_browser_clear_all_description),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    modifier = Modifier.size(MobileDimensions.iconSmall),
                )
            }
        }
        // Horizontally-scrollable single row, not FlowRow — see MatchTypeChipRow note in
        // tv/ProviderDialogs.kt for why FlowRow is avoided right now. Unlike that fixed-4-item
        // case, this list is unbounded, so a manual 2-per-row wrap isn't a safe substitute here;
        // a scrolling row is the standard pattern for an open-ended chip list like this.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            history.forEach { term ->
                CinemaAssistChip(
                    onClick = { onItemClick(term) },
                    label = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = CinemaIcons.Search,
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
                                imageVector = CinemaIcons.Close,
                                contentDescription = stringResource(R.string.epg_browser_remove_description),
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        }
    }
}

/** "12 results" or "12 results · 3 hidden" when the provider's category filters hid matches. */
@Composable
private fun searchCountLabel(
    count: Int,
    hiddenCount: Int,
): String {
    val label = stringResource(R.string.search_results_count_format, count)
    return if (hiddenCount > 0) {
        label + " · " + stringResource(R.string.search_results_hidden_format, hiddenCount)
    } else {
        label
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    hiddenCount: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = searchCountLabel(count, hiddenCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
    }
}

@Composable
private fun MobileCollapsibleHeader(
    title: String,
    count: Int,
    hiddenCount: Int,
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
            Spacer(Modifier.weight(1f))
            Text(
                text = searchCountLabel(count, hiddenCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                modifier = Modifier.padding(end = Spacing.xs),
            )
            Icon(
                imageVector = if (isExpanded) CinemaIcons.KeyboardArrowUp else CinemaIcons.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MobileDimensions.iconSmall),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CategoryResultCard(
    result: SearchViewModel.CategorySearchResult,
    cardColors: CardColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    CinemaCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors = cardColors,
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
                CinemaIcons.Folder,
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
    cardColors: CardColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    CinemaCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(MobileDimensions.streamCardHeight)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors = cardColors,
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
                    text = stringResource(R.string.search_result_category_format, result.categoryName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
            }
        }
    }
}

@Composable
private fun MobileSearchFavoriteDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()
    val actionText = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add)

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = itemName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(actionText)
            }
        },
        dismissButton = {
            CinemaDialogTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
