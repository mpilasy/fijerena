package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.wanted.ReleaseInfo
import org.njarasoa.fijerena.core.network.wanted.ReleaseStage
import org.njarasoa.fijerena.core.network.wanted.ReleaseStatus
import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserMatchedStream
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.asContentTypeLabel
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleBadges
import org.njarasoa.fijerena.core.ui.history.LocalShowSourceBadges
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.search.SearchFacetLabels
import org.njarasoa.fijerena.core.ui.search.rememberSearchFacetLabels
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SearchFacetChoice
import org.njarasoa.fijerena.core.ui.viewmodels.SearchFacets
import org.njarasoa.fijerena.core.ui.viewmodels.SearchSession
import org.njarasoa.fijerena.core.ui.viewmodels.SearchSourceCount
import org.njarasoa.fijerena.core.ui.viewmodels.SearchTab
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.activeSearchTab
import org.njarasoa.fijerena.core.ui.viewmodels.buildGroupedSearchResults
import org.njarasoa.fijerena.core.ui.viewmodels.effectiveFacetChoice
import org.njarasoa.fijerena.core.ui.viewmodels.effectiveSourceFilter
import org.njarasoa.fijerena.core.ui.viewmodels.fromSource
import org.njarasoa.fijerena.core.ui.viewmodels.lastRunQuery
import org.njarasoa.fijerena.core.ui.viewmodels.matches
import org.njarasoa.fijerena.core.ui.viewmodels.queryToRunOnTabSwitch
import org.njarasoa.fijerena.core.ui.viewmodels.searchFacets
import org.njarasoa.fijerena.core.ui.viewmodels.searchSourceCounts
import org.njarasoa.fijerena.core.ui.viewmodels.searchTabs
import org.njarasoa.fijerena.core.ui.viewmodels.toggled
import org.njarasoa.fijerena.core.ui.wanted.label
import org.njarasoa.fijerena.feature.epgbrowser.MobileGuideSearchPane
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaSearchField
import org.njarasoa.fijerena.ui.components.CinemaTabRow
import org.njarasoa.fijerena.ui.components.ItemActionsDialog
import org.njarasoa.fijerena.ui.components.ItemMenuAction
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.chips.CinemaAssistChip
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
import org.njarasoa.fijerena.ui.components.mobileClockTopBarInsets
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import java.time.LocalDate

@Composable
private fun localizedContentTypeLabel(contentType: String): String =
    when (contentType) {
        "ALL" -> stringResource(R.string.content_type_all_label)
        ContentType.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        ContentType.MOVIES -> stringResource(R.string.provider_movies_label)
        ContentType.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
        else -> contentType.asContentTypeLabel()
    }

/** The field's hint names what the search covers, as on the TV. */
@Composable
private fun searchPlaceholder(contentType: String): String =
    when (contentType) {
        ContentType.LIVE_TV -> stringResource(R.string.tv_search_placeholder_channels)
        ContentType.MOVIES -> stringResource(R.string.tv_search_placeholder_films)
        ContentType.TV_SHOWS -> stringResource(R.string.tv_search_placeholder_shows)
        else -> stringResource(R.string.tv_search_placeholder_all)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSearchScreen(
    contentType: String,
    onStreamSelected: (itemId: String, itemName: String, categoryId: String, contentType: String, providerId: Long) -> Unit,
    onCategorySelected: (categoryId: String, contentType: String, providerId: Long) -> Unit = { _, _, _ -> },
    // Told the query just before a result opens, so a result opened in another tab can bring the
    // search along (MobileNavHost.openInSectionTab) and Back still returns to these results.
    onResultOpening: (query: String, source: Long?) -> Unit = { _, _ -> },
    // Null on the Search tab's root: no Back arrow.
    onBack: (() -> Unit)?,
    // A "Not in your sources" result: its TMDB page (wanted titles plan → Search).
    onTmdbTitleSelected: (TmdbTitle) -> Unit = {},
    initialQuery: String? = null,
    initialTypeFilter: String? = null,
    // Opened on the Guide tab, its [initialQuery] run there: a Guide result's search, brought along
    // to the Live TV tab (MobileNavHost.openInSectionTab).
    initialGuideTab: Boolean = false,
    // The source chosen in the search brought along (MobileNavHost.openInSectionTab).
    initialSource: Long? = null,
    // A Guide tab result: its channel, as Search the guide opens it.
    onGuideResult: (EpgBrowserMatchedStream) -> Unit = {},
    // A person found by the People type: their actor page (the people search plan).
    onPersonSelected: (tmdbId: String, name: String) -> Unit = { _, _ -> },
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
    val hasGuide by viewModel.hasGuide.collectAsStateWithLifecycle()
    val notInSources by viewModel.notInSources.collectAsStateWithLifecycle()
    val wishlistKeys by viewModel.wishlistKeys.collectAsStateWithLifecycle()
    // Long-press on a "Not in your sources" row: Add to / Remove from Wishlist (wishlist UX plan, Phase 2).
    var tmdbMenu by remember { mutableStateOf<TmdbTitle?>(null) }
    tmdbMenu?.let { title ->
        val on = title.key in wishlistKeys
        ItemActionsDialog(
            title = title.title,
            isFavorite = false,
            onToggleFavorite = null,
            onDismiss = { tmdbMenu = null },
            actions =
                listOf(
                    ItemMenuAction(
                        if (on) CinemaIcons.Delete else CinemaIcons.BookmarkBorder,
                        stringResource(if (on) R.string.wanted_remove else R.string.wanted_add),
                    ) { viewModel.toggleWishlist(title) },
                ),
        )
    }
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    // The app's one search (SearchSession), shared by both tabs and every Search screen: Back to any
    // of them shows the latest. Arriving with a query, filter, source or tab brought along sets it,
    // once per screen.
    SearchSession.forProfile(appSettings.activeProfileId)
    var arrived by rememberSaveable { mutableStateOf(false) }
    if (!arrived) {
        arrived = true
        initialQuery?.takeIf { it.isNotBlank() }?.let { SearchSession.query = it }
        initialTypeFilter?.let { SearchSession.typeFilter = it }
        initialSource?.let { SearchSession.source = it }
        if (initialGuideTab) SearchSession.tab = SearchTab.GUIDE
    }
    var searchQuery by SearchSession::query
    var selectedTab by SearchSession::tab
    // People only with a TMDB key (PS-D1), never for a child.
    val hasTmdbKey = remember { TmdbApiService.getInstance(context.applicationContext).hasApiKey() }
    val child = remember { appSettings.isChildProfile(appSettings.activeProfileId) }
    val tabs = searchTabs(contentType, hasGuide, hasTmdbKey, child = child)
    val showTabs = tabs.isNotEmpty()
    val activeTab = activeSearchTab(selectedTab, tabs)
    val guideActive = activeTab == SearchTab.GUIDE
    val currentOtherTab by rememberUpdatedState(activeTab != SearchTab.CATALOGUE)
    val snackbarHostState = remember { SnackbarHostState() }
    // Scope filter selection lives here, not inside SearchResults — performSearch() emits a
    // transitional UiState.Loading on every debounced re-search (not just the first), which
    // unmounts SearchResults entirely. A plain rememberSaveable inside it doesn't survive that
    // disposal (same gotcha as tab content resetting without a SaveableStateHolder), so the
    // filter would silently reset to "All" every time the user kept typing.
    // A section's Search arrives with its own section selected; All is one tap away.
    var selectedTypeFilter by SearchSession::typeFilter
    // The source chip chosen in a multi-source profile (null: every source).
    var selectedSource by SearchSession::source
    // The decades and genres chosen in the filter (decade and genre filter plan).
    var selectedFacets by SearchSession::facets

    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }

    // The menu's result's source: its Favourite and Mark show watched act there.
    var favoriteMenuSource by remember { mutableLongStateOf(0L) }

    favoriteMenuTarget?.let { target ->
        val source = favoriteMenuSource
        MobileSearchFavoriteDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is FavoriteMenuTarget.Category -> {
                        viewModel.toggleFavoriteCategory(
                            source,
                            target.categoryId,
                            target.categoryName,
                            target.contentType,
                            target.isFavorite,
                        )
                    }

                    is FavoriteMenuTarget.Stream -> {
                        viewModel.toggleFavorite(
                            source,
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
            // Null when the result isn't a film or an episode, or its source keeps watched state on its server.
            onToggleWatched =
                (target as? FavoriteMenuTarget.Stream)?.isWatched?.let { isWatched ->
                    { viewModel.toggleWatched(source, target.itemId, target.contentType, isWatched) }
                },
            // Null when the result isn't a show or its source keeps watched state on its server.
            onToggleShowWatched =
                (target as? FavoriteMenuTarget.Stream)?.isShowWatched?.let { isShowWatched ->
                    {
                        viewModel.setShowWatched(source, target.itemId, !isShowWatched) { message ->
                            android.widget.Toast
                                .makeText(context, message, android.widget.Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                },
        )
    }
    val longPressScope = rememberCoroutineScope()

    // As-you-type search: debounced so every keystroke doesn't fire its own query. The manual
    // triggers below (search icon tap, keyboard search action) still work unchanged — performSearch()
    // already cancels any in-flight search job before starting a new one, so an explicit tap
    // landing right after a debounced fire is a harmless redundant call, not a race.
    // The catalogue only: the Guide tab doesn't run it unseen (D3).
    // Coming back to a screen whose results are already for this term runs nothing again.
    LaunchedEffect(searchQuery) {
        if (activeTab != SearchTab.CATALOGUE) return@LaunchedEffect
        if ((viewModel.uiState.value as? SearchViewModel.UiState.Success)?.query == searchQuery) return@LaunchedEffect
        if (searchQuery.length >= 2) {
            delay(CinemaAnimation.searchDebounceMs)
            // Switched to another type while waiting: not run unseen.
            if (currentOtherTab) return@LaunchedEffect
            viewModel.performSearch(searchQuery, asYouType = true)
        } else if (searchQuery.isEmpty()) {
            viewModel.clearSearch()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = mobileClockTopBarInsets(),
                title = {
                    Column {
                        CinemaText(
                            stringResource(R.string.common_search),
                            role = TextRole.Title,
                            tone = TextTone.Inherit,
                        )
                        CinemaText(
                            text = localizedContentTypeLabel(contentType),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
                        }
                    }
                },
            )
        },
        // Above the floating bottom bar, not behind it.
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = LocalDockInset.current)) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (showTabs) {
                MobileSearchTabs(
                    tabs = tabs,
                    selected = activeTab,
                    onSelect = { tab ->
                        selectedTab = tab
                        // The Guide tab reruns on entry (MobileSearchGuideTab): its view model may not exist yet.
                        if (tab == SearchTab.CATALOGUE) {
                            val lastRun = (viewModel.uiState.value as? SearchViewModel.UiState.Success)?.query
                            queryToRunOnTabSwitch(searchQuery, lastRun)?.let { viewModel.performSearch(it) }
                        }
                    },
                )
            }
            if (guideActive) {
                MobileSearchGuideTab(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onGuideResult = {
                        // The guide's results aren't filtered by source (plan D1).
                        onResultOpening(searchQuery, null)
                        onGuideResult(it)
                    },
                    snackbarHostState = snackbarHostState,
                )
                return@Column
            }
            if (activeTab == SearchTab.PEOPLE) {
                MobilePeopleSearchPane(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    wishlistKeys = wishlistKeys,
                    onPersonSelected = onPersonSelected,
                    onTitleSelected = { item, type ->
                        onResultOpening(searchQuery, null)
                        viewModel.keepTypedSearch()
                        onStreamSelected(item.id, item.name, item.categoryId, type, item.providerId)
                    },
                    onTmdbTitleSelected = { title ->
                        onResultOpening(searchQuery, null)
                        viewModel.keepTypedSearch()
                        onTmdbTitleSelected(title)
                    },
                    onTmdbTitleLongPress = { tmdbMenu = it },
                )
                return@Column
            }

            // The results' sources (the search source filter plan): Expert mode
            // only, as the source badges — without it results don't name their source.
            val successState = uiState as? SearchViewModel.UiState.Success
            val showSources = LocalShowSourceBadges.current
            val sources =
                remember(successState?.categoryResults, successState?.filteredResults, showSources) {
                    if (showSources && successState != null) {
                        searchSourceCounts(successState.categoryResults, successState.filteredResults)
                    } else {
                        emptyList()
                    }
                }
            val source = effectiveSourceFilter(selectedSource, sources)
            // TMDB's films and shows no source has: not when one source is chosen ("not in your
            // sources" would be wrong for what another source has).
            val tmdbSection = notInSources?.takeIf { source == null && it.query == successState?.query }
            val sourceResults =
                remember(successState?.filteredResults, source) { successState?.filteredResults.orEmpty().fromSource(source) }
            val tmdbTitles = tmdbSection?.titles.orEmpty()
            val facetChoice = effectiveFacetChoice(selectedFacets, sourceResults, tmdbTitles)
            val facets = remember(sourceResults, tmdbTitles, facetChoice) { searchFacets(sourceResults, tmdbTitles, facetChoice) }
            val facetLabels = rememberSearchFacetLabels()

            // Search bar — searches as you type; the keyboard's search action runs it at once.
            val keyboardController = LocalSoftwareKeyboardController.current
            CinemaSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                placeholder = searchPlaceholder(contentType),
                leadingIcon = {
                    Icon(
                        imageVector = CinemaIcons.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (uiState is SearchViewModel.UiState.Loading) {
                        CinemaSpinner(modifier = Modifier.size(MobileDimensions.iconDefault))
                    } else if (searchQuery.isNotEmpty() || uiState is SearchViewModel.UiState.Success) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sources.isNotEmpty() || !facets.isEmpty) {
                                SearchFilterAction(
                                    sources = sources,
                                    selectedSource = source,
                                    onSelectSource = { selectedSource = it },
                                    facets = facets,
                                    choice = facetChoice,
                                    onChoiceChange = { selectedFacets = it },
                                    labels = facetLabels,
                                )
                            }
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    viewModel.clearSearch()
                                },
                            ) {
                                Icon(
                                    imageVector = CinemaIcons.Close,
                                    contentDescription = stringResource(R.string.provider_clear_button),
                                    tint = CinemaTheme.colors.textPrimary,
                                )
                            }
                        }
                    }
                },
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
            )
            facetLabels.summary(sources.firstOrNull { it.providerId == source }?.name, facets, facetChoice)?.let { summary ->
                CinemaText(
                    text = stringResource(R.string.search_source_only_format, summary),
                    modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.xs),
                    role = TextRole.Caption,
                    tone = TextTone.Accent,
                )
            }

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
                        RetryWhenOnline { viewModel.performSearch(searchQuery) }
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
                            notInSources = tmdbTitles.filter { it.matches(facetChoice) },
                            notInSourcesError = tmdbSection?.devError,
                            wishlistKeys = wishlistKeys,
                            onTmdbTitleLongPress = { tmdbMenu = it },
                            onTmdbTitleClick = { title ->
                                onResultOpening(searchQuery, source)
                                viewModel.keepTypedSearch()
                                onTmdbTitleSelected(title)
                            },
                            // Categories have no year or genre: a decade or genre choice leaves them out.
                            categoryResults = if (facetChoice.isActive) emptyList() else state.categoryResults.fromSource(source),
                            sourceErrors = state.sourceErrors.takeIf { appSettings.isDevMode }.orEmpty(),
                            results = sourceResults.filter { it.matches(facetChoice) },
                            excludedCountByType = state.excludedCountByType,
                            query = state.query,
                            queryContentType = contentType,
                            isSearching = state.isSearching,
                            searchProgress = state.searchProgress ?: "",
                            devStats = devStats,
                            searchHistory = searchHistory,
                            selectedTypeFilter = selectedTypeFilter,
                            onTypeFilterChange = { selectedTypeFilter = it },
                            onHistoryItemClick = { term ->
                                searchQuery = term
                                viewModel.performSearch(term)
                                keyboardController?.hide()
                            },
                            onHistoryItemRemove = { viewModel.removeSearchHistoryEntry(it) },
                            onClearHistory = { viewModel.clearSearchHistory() },
                            onResultClick = { result ->
                                onResultOpening(searchQuery, source)
                                viewModel.keepTypedSearch()
                                onStreamSelected(
                                    result.itemId,
                                    result.streamName,
                                    result.categoryId,
                                    result.contentType,
                                    result.providerId,
                                )
                            },
                            onResultLongPress = { result ->
                                // Favourite, Mark watched (a film or an episode) or Mark show watched (a
                                // show), on the result's own source: the menu waits for one watch-state read.
                                longPressScope.launch {
                                    val target = viewModel.menuTarget(result)
                                    favoriteMenuSource = result.providerId
                                    favoriteMenuTarget = target
                                }
                            },
                            onCategoryClick = { catResult ->
                                onResultOpening(searchQuery, source)
                                viewModel.keepTypedSearch()
                                onCategorySelected(catResult.categoryId, catResult.contentType, catResult.providerId)
                            },
                            onCategoryLongPress = { catResult ->
                                favoriteMenuSource = catResult.providerId
                                favoriteMenuTarget =
                                    FavoriteMenuTarget.Category(
                                        categoryId = catResult.categoryId,
                                        categoryName = catResult.categoryName,
                                        contentType = catResult.contentType,
                                        isFavorite =
                                            viewModel.isFavoriteCategory(
                                                catResult.providerId,
                                                catResult.categoryId,
                                                catResult.contentType,
                                            ),
                                    )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** "Catalogue | People | Guide" ([tabs]), in the look of the category list's mode tabs (phone UI audit #1). */
@Composable
internal fun MobileSearchTabs(
    tabs: List<SearchTab>,
    selected: SearchTab,
    onSelect: (SearchTab) -> Unit,
) {
    CinemaTabRow(
        selectedIndex = tabs.indexOf(selected).coerceAtLeast(0),
        labels =
            tabs.map { tab ->
                stringResource(
                    when (tab) {
                        SearchTab.CATALOGUE -> R.string.search_tab_catalogue
                        SearchTab.PEOPLE -> R.string.search_tab_people
                        SearchTab.GUIDE -> R.string.search_tab_guide
                    },
                )
            },
        onSelect = { onSelect(tabs[it]) },
        transparent = true,
    )
}

/**
 * The filter icon in the field: a menu with a section each for the results' sources (one choice),
 * decades and genres (several), with their counts; **Clear filters** while one is active. The menu
 * stays open as choices are made; the icon takes the accent colour while one is active.
 */
@Composable
private fun SearchFilterAction(
    sources: List<SearchSourceCount>,
    selectedSource: Long?,
    onSelectSource: (Long?) -> Unit,
    facets: SearchFacets,
    choice: SearchFacetChoice,
    onChoiceChange: (SearchFacetChoice) -> Unit,
    labels: SearchFacetLabels,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = selectedSource != null || choice.isActive
    Box {
        IconAction(
            onClick = { expanded = true },
            icon = CinemaIcons.Tune,
            label = stringResource(R.string.search_filter_label),
            tint = if (active) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            @Composable
            fun Heading(text: String) {
                CinemaText(
                    text,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    role = TextRole.Caption,
                    tone = TextTone.Secondary,
                )
            }

            @Composable
            fun Choice(
                label: String,
                on: Boolean,
                check: Boolean,
                onClick: () -> Unit,
            ) {
                DropdownMenuItem(
                    text = { CinemaText(label, role = TextRole.Label, tone = TextTone.Inherit) },
                    onClick = onClick,
                    leadingIcon = {
                        Icon(
                            imageVector =
                                when {
                                    check && on -> CinemaIcons.CheckBox
                                    check -> CinemaIcons.CheckBoxOutlineBlank
                                    on -> CinemaIcons.RadioButtonChecked
                                    else -> CinemaIcons.RadioButtonUnchecked
                                },
                            contentDescription = null,
                        )
                    },
                )
            }
            if (active) {
                DropdownMenuItem(
                    text = { CinemaText(stringResource(R.string.search_filter_clear), role = TextRole.Label, tone = TextTone.Inherit) },
                    onClick = {
                        onSelectSource(null)
                        onChoiceChange(SearchFacetChoice())
                        expanded = false
                    },
                    leadingIcon = { Icon(imageVector = CinemaIcons.Close, contentDescription = null) },
                )
            }
            if (sources.isNotEmpty()) {
                Heading(stringResource(R.string.search_filter_source))
                Choice(
                    "${stringResource(R.string.search_all_sources_label)} (${sources.sumOf { it.count }})",
                    selectedSource == null,
                    check = false,
                ) {
                    onSelectSource(null)
                }
                for (source in sources) {
                    Choice("${source.name} (${source.count})", selectedSource == source.providerId, check = false) {
                        onSelectSource(source.providerId)
                    }
                }
            }
            if (facets.decades.isNotEmpty()) {
                Heading(stringResource(R.string.search_filter_decade))
                for (decade in facets.decades) {
                    val on = decade.value in choice.decades
                    Choice("${labels.decade(decade.value)} (${decade.count})", on, check = true) {
                        onChoiceChange(choice.copy(decades = if (on) choice.decades - decade.value else choice.decades + decade.value))
                    }
                }
            }
            if (facets.genres.isNotEmpty()) {
                Heading(stringResource(R.string.search_filter_genre))
                for (genre in facets.genres) {
                    val on = genre.value in choice.genres
                    Choice("${genre.label} (${genre.count})", on, check = true) {
                        onChoiceChange(choice.copy(genres = if (on) choice.genres - genre.value else choice.genres + genre.value))
                    }
                }
            }
            if (facets.languages.isNotEmpty()) {
                Heading(stringResource(R.string.search_filter_language))
                for (language in facets.languages) {
                    val on = language.value in choice.languages
                    Choice("${labels.language(language.value)} (${language.count})", on, check = true) {
                        onChoiceChange(
                            choice.copy(languages = if (on) choice.languages - language.value else choice.languages + language.value),
                        )
                    }
                }
            }
            if (facets.regions.isNotEmpty()) {
                Heading(stringResource(R.string.search_filter_region))
                for (region in facets.regions) {
                    val on = region.value in choice.regions
                    Choice("${labels.region(region.value)} (${region.count})", on, check = true) {
                        onChoiceChange(choice.copy(regions = if (on) choice.regions - region.value else choice.regions + region.value))
                    }
                }
            }
        }
    }
}

/**
 * The Guide tab: Search the guide's pane over the whole guide, without its top-bar actions (O3).
 * Its view model is made the first time the tab shows, scoped to Search's back-stack entry.
 */
@Composable
private fun MobileSearchGuideTab(
    query: String,
    onQueryChange: (String) -> Unit,
    onGuideResult: (EpgBrowserMatchedStream) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val guideViewModel: EpgBrowserViewModel =
        viewModel(factory = remember { EpgBrowserViewModelFactory(context.applicationContext, null, 0L) })
    LaunchedEffect(guideViewModel) {
        guideViewModel.userMessage.collect { snackbarHostState.showSnackbar(it.asString(context)) }
    }
    // Shown with text it last ran another query for (or none): run it here (D3).
    LaunchedEffect(guideViewModel) {
        queryToRunOnTabSwitch(query, guideViewModel.uiState.value.lastRunQuery())?.let { guideViewModel.performSearch(it) }
    }
    MobileGuideSearchPane(
        viewModel = guideViewModel,
        query = query,
        onQueryChange = onQueryChange,
        onNavigateToPlayer = onGuideResult,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun LoadingView(message: String? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MitadyLoading(
            role = TextRole.Title,
            tone = TextTone.Accent,
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
            CinemaText(
                text = stringResource(R.string.common_error),
                role = TextRole.Display,
                tone = TextTone.Danger,
            )
            CinemaText(
                text = message,
                role = TextRole.Body,
                tone = TextTone.Inherit,
            )
        }
    }
}

@Composable
private fun SearchResults(
    notInSources: List<TmdbTitle>,
    notInSourcesError: String?,
    onTmdbTitleClick: (TmdbTitle) -> Unit,
    wishlistKeys: Set<WantedTitles.Key>,
    onTmdbTitleLongPress: (TmdbTitle) -> Unit,
    categoryResults: List<SearchViewModel.CategorySearchResult>,
    // Developer mode: a raw line per source left out because its search failed.
    sourceErrors: List<String>,
    results: List<SearchViewModel.SearchResult>,
    excludedCountByType: Map<String, Int>,
    query: String,
    queryContentType: String,
    isSearching: Boolean,
    searchProgress: String?,
    devStats: String?,
    searchHistory: List<String>,
    selectedTypeFilter: String?,
    onTypeFilterChange: (String?) -> Unit,
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
            containerColor = CinemaTheme.colors.accentStrong.copy(alpha = CinemaAlpha.tint),
        )
    val streamCardColors = CardDefaults.cardColors(containerColor = CinemaTheme.colors.surface)

    fun toggleGroup(contentType: String) {
        expandedGroups = expandedGroups.toggled(contentType)
    }

    val hasResults = categoryResults.isNotEmpty() || results.isNotEmpty() || notInSources.isNotEmpty() || isSearching
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
                CinemaText(
                    text = stringResource(R.string.search_categories_streams_hint),
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                )
            }
        }
    } else if (!isSearching && categoryResults.isEmpty() && results.isEmpty() && notInSources.isEmpty() && notInSourcesError == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CinemaText(
                text =
                    if (queryContentType == "ALL") {
                        stringResource(R.string.search_no_results_query_format, query)
                    } else {
                        stringResource(R.string.search_no_results_type_format, localizedContentTypeLabel(queryContentType), query)
                    },
                role = TextRole.Body,
                tone = TextTone.Secondary,
            )
            if (sourceErrors.isNotEmpty()) {
                CinemaText(
                    text = sourceErrors.joinToString("\n"),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    role = TextRole.BodySmall,
                    tone = TextTone.Danger,
                )
            }
        }
    } else {
        // Filter chips and the results list must be vertical siblings — this whole branch is
        // composed directly inside the caller's Box (SearchScreen.kt), which stacks children by
        // paint order, not position. Without this Column, the LazyColumn below (declared after
        // the Row) painted on top of it and ate every touch meant for the chips.
        Column(modifier = Modifier.fillMaxSize()) {
            // Pre-compute grouped results outside LazyColumn to avoid O(N×types) filter per recomposition
            val groupedByType =
                remember(categoryResults, results) {
                    buildGroupedSearchResults(categoryResults, results)
                }

            // A section with no results has no chip: show everything (All selected) rather than an
            // empty list for the section a tab's Search arrived with.
            val effectiveFilter =
                selectedTypeFilter?.takeIf { filter ->
                    if (filter == SearchSession.NOT_IN_SOURCES) {
                        notInSources.isNotEmpty()
                    } else {
                        groupedByType.any { (type, cats, streams) -> type == filter && (cats.isNotEmpty() || streams.isNotEmpty()) }
                    }
                }

            // Scope filter chips — only meaningful for Global Search, where results already span
            // multiple content types worth triaging. A type-scoped search (queryContentType != "ALL")
            // only ever has one type of result to begin with. Selection is hoisted to the caller (see
            // MobileSearchScreen) so it survives the transitional Loading state performSearch() emits
            // on every re-search, not just the first.
            if (queryContentType == "ALL" && groupedByType.size + (if (notInSources.isEmpty()) 0 else 1) > 1) {
                val totalCount = categoryResults.size + results.size + notInSources.size
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    CinemaFilterChip(
                        selected = effectiveFilter == null,
                        onClick = { onTypeFilterChange(null) },
                        label = {
                            CinemaText(
                                "${stringResource(R.string.content_type_all_label)} ($totalCount)",
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        },
                    )
                    for ((type, cats, streams) in groupedByType) {
                        val count = cats.size + streams.size
                        if (count > 0) {
                            CinemaFilterChip(
                                selected = effectiveFilter == type,
                                onClick = { onTypeFilterChange(if (effectiveFilter == type) null else type) },
                                label = {
                                    CinemaText(
                                        "${localizedContentTypeLabel(type)} ($count)",
                                        role = TextRole.Label,
                                        tone = TextTone.Inherit,
                                    )
                                },
                            )
                        }
                    }
                    // TMDB's titles no source has: the last chip, only while there are some.
                    if (notInSources.isNotEmpty()) {
                        CinemaFilterChip(
                            selected = effectiveFilter == SearchSession.NOT_IN_SOURCES,
                            onClick = {
                                onTypeFilterChange(
                                    if (effectiveFilter ==
                                        SearchSession.NOT_IN_SOURCES
                                    ) {
                                        null
                                    } else {
                                        SearchSession.NOT_IN_SOURCES
                                    },
                                )
                            },
                            label = {
                                CinemaText(
                                    "${stringResource(R.string.wanted_not_in_sources)} (${notInSources.size})",
                                    role = TextRole.Label,
                                    tone = TextTone.Inherit,
                                )
                            },
                        )
                    }
                }
            }
            val visibleGroups =
                effectiveFilter?.let { filter -> groupedByType.filter { it.first == filter } } ?: groupedByType

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(bottom = Spacing.md + LocalDockInset.current),
            ) {
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
                                role = TextRole.Body,
                                tone = TextTone.Accent,
                            )
                            CinemaText(
                                text = " ($searchProgress)",
                                role = TextRole.Body,
                                tone = TextTone.Accent,
                            )
                        }
                    }
                } else if (!isSearching && searchProgress != null) {
                    item(key = "search_complete", contentType = "status") {
                        CinemaText(
                            text = searchProgress,
                            modifier = Modifier.padding(vertical = Spacing.xs),
                            role = TextRole.Body,
                            tone = TextTone.Accent,
                        )
                    }
                }

                if (devStats != null) {
                    item(key = "dev_stats", contentType = "status") {
                        CinemaText(
                            text = devStats,
                            role = TextRole.Caption,
                            tone = TextTone.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }

                if (queryContentType == "ALL") {
                    visibleGroups.forEach { (type, typeCats, typeStreams) ->

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
                                items(
                                    typeCats,
                                    key = { "cat_${it.listKey}" },
                                    contentType = { "category" },
                                ) { catResult ->
                                    CategoryResultCard(
                                        result = catResult,
                                        cardColors = categoryCardColors,
                                        onClick = { onCategoryClick(catResult) },
                                        onLongClick = { onCategoryLongPress(catResult) },
                                    )
                                }
                                items(
                                    typeStreams,
                                    key = { "stream_${it.listKey}" },
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
                        items(
                            categoryResults,
                            key = { "cat_${it.listKey}" },
                            contentType = { "category" },
                        ) { catResult ->
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
                            key = { "search_${it.listKey}" },
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

                // TMDB's section, under the catalogue's; the type chips filter it too (none under Live TV).
                val shownTmdb =
                    notInSources.filter {
                        effectiveFilter == null || effectiveFilter == SearchSession.NOT_IN_SOURCES || it.contentType == effectiveFilter
                    }
                if (shownTmdb.isNotEmpty() || (notInSourcesError != null && effectiveFilter != ContentType.LIVE_TV)) {
                    item(key = "tmdb_header", contentType = "header") {
                        Column {
                            SearchSectionHeader(
                                title = stringResource(R.string.wanted_not_in_sources),
                                count = shownTmdb.size,
                                hiddenCount = 0,
                            )
                            notInSourcesError?.let { CinemaText(text = it, role = TextRole.BodySmall, tone = TextTone.Danger) }
                        }
                    }
                    items(shownTmdb, key = { "tmdb_${it.contentType}_${it.tmdbId}" }, contentType = { "tmdb" }) { title ->
                        TmdbTitleCard(
                            title = title,
                            onWishlist = title.key in wishlistKeys,
                            cardColors = streamCardColors,
                            onClick = { onTmdbTitleClick(title) },
                            onLongClick = { onTmdbTitleLongPress(title) },
                        )
                    }
                }

                if (sourceErrors.isNotEmpty()) {
                    item(key = "source_errors", contentType = "status") {
                        CinemaText(
                            text = sourceErrors.joinToString("\n"),
                            role = TextRole.BodySmall,
                            tone = TextTone.Danger,
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
            CinemaText(
                text = stringResource(R.string.epg_browser_recent_searches),
                role = TextRole.Label,
                tone = TextTone.Secondary,
            )
            IconButton(onClick = onClearAll) {
                Icon(
                    imageVector = CinemaIcons.Delete,
                    contentDescription = stringResource(R.string.epg_browser_clear_all_description),
                    tint = CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.textLow),
                    modifier = Modifier.size(MobileDimensions.iconSmall),
                )
            }
        }
        // Wraps rather than scrolling sideways: the whole history should be visible at a glance.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            history.forEach { term ->
                CinemaAssistChip(
                    onClick = { onItemClick(term) },
                    label = {
                        CinemaText(
                            term,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            role = TextRole.Label,
                            keepCase = true,
                            tone = TextTone.Inherit,
                        )
                    },
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
    val label = pluralStringResource(R.plurals.search_results_count, count, count)
    return if (hiddenCount > 0) {
        label + " · " + stringResource(R.string.search_results_hidden_format, hiddenCount)
    } else {
        label
    }
}

@Composable
internal fun SearchSectionHeader(
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
        CinemaText(
            text = title,
            role = TextRole.Heading,
            tone = TextTone.Primary,
        )
        Spacer(Modifier.weight(1f))
        CinemaText(
            text = searchCountLabel(count, hiddenCount),
            role = TextRole.Body,
            tone = TextTone.Secondary,
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
        color = CinemaTheme.colors.surfaceRaised.copy(alpha = CinemaAlpha.tint),
        shape = CinemaTheme.shapes.chip,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaText(
                text = title,
                role = TextRole.Heading,
                tone = TextTone.Primary,
                emphasis = true,
            )
            Spacer(Modifier.weight(1f))
            CinemaText(
                text = searchCountLabel(count, hiddenCount),
                modifier = Modifier.padding(end = Spacing.xs),
                role = TextRole.Body,
                tone = TextTone.Secondary,
            )
            Icon(
                imageVector = if (isExpanded) CinemaIcons.KeyboardArrowUp else CinemaIcons.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                tint = CinemaTheme.colors.accent,
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
                tint = CinemaTheme.colors.accent,
            )
            CinemaText(
                text = result.categoryName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                role = TextRole.Heading,
                tone = TextTone.Primary,
                modifier = Modifier.weight(1f),
            )
            // At the row's end, as on a title's row.
            result.sourceName?.let { SourceBadge(it, providerId = result.providerId) }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SearchResultCard(
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
            // Same title as the browse lists: the provider's "EN - " / "4K-NF - " tags as badges, on the second line.
            val parsedTitle =
                remember(result.streamName, result.categoryName) {
                    parseDisplayTitle(result.streamName).withoutCodeOf(result.categoryName)
                }
            val shownTitle = parsedTitle.titleWithoutYear.ifBlank { result.streamName }
            // 16:9 at the card's full inner height; a channel's logo drawn whole.
            val isChannel = result.contentType == ContentType.LIVE_TV
            CinemaThumbnail(
                url = result.thumbnailUrl,
                fallbackLetter = shownTitle.firstOrNull(Char::isLetterOrDigit),
                contentType = if (isChannel) ThumbnailContentType.LIVE_TV else ThumbnailContentType.DEFAULT,
                overlayGradient = !isChannel,
                modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CinemaText(
                        text = shownTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        role = TextRole.Heading,
                        tone = TextTone.Primary,
                    )
                    // Its source (when the search covers several) ends the line, in the same place on every row.
                    result.sourceName?.let { SourceBadge(it, providerId = result.providerId) }
                }
                // Its category, after the title's badges.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TitleBadges(parsedTitle)
                    CinemaText(
                        // The year the title gave, before the category, so a long category can't push it off.
                        text = listOfNotNull(parsedTitle.year?.toString(), result.categoryName).joinToString(" · "),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One "Not in your sources" result: TMDB's poster, the title, its year and kind, and the "Not available" mark. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TmdbTitleCard(
    title: TmdbTitle,
    // On the Wishlist: a filled bookmark in place of the "Not available" cloud.
    onWishlist: Boolean,
    cardColors: CardColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    CinemaCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(MobileDimensions.streamCardHeight)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = stringResource(R.string.row_more_actions),
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
                url = TmdbApiService.posterUrl(title.posterPath),
                fallbackLetter = title.title.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.DEFAULT,
                overlayGradient = true,
                modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CinemaText(
                    text = title.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                )
                CinemaText(
                    text =
                        listOfNotNull(
                            // "Coming 14 Mar 2027" for a title not out yet, else its year (wishlist UX plan, Phase 3).
                            ReleaseStatus
                                .of(title.contentType, ReleaseInfo(title.releaseDate), LocalDate.now())
                                .takeIf { it.stage == ReleaseStage.COMING || it.stage == ReleaseStage.STARTS }
                                ?.label(LocalResources.current)
                                ?: title.year?.toString(),
                            stringResource(
                                if (title.contentType ==
                                    ContentType.MOVIES
                                ) {
                                    R.string.wanted_kind_movie
                                } else {
                                    R.string.wanted_kind_show
                                },
                            ),
                        ).joinToString(" · "),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (onWishlist) CinemaIcons.Bookmark else CinemaIcons.CloudOff,
                contentDescription = stringResource(if (onWishlist) R.string.wanted_title else R.string.wanted_not_available),
                tint = if (onWishlist) CinemaTheme.colors.accent else CinemaTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun MobileSearchFavoriteDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    // A film's or an episode's "Mark watched / unwatched", worded from the target's isWatched.
    // Null hides it.
    onToggleWatched: (() -> Unit)? = null,
    // A show's "Mark show watched / unwatched" (the season show watched plan
    // Phase 3), worded from the target's isShowWatched. Null hides it.
    onToggleShowWatched: (() -> Unit)? = null,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()
    val isWatched = (target as? FavoriteMenuTarget.Stream)?.isWatched == true
    val isShowWatched = (target as? FavoriteMenuTarget.Stream)?.isShowWatched == true
    val showWatchedLabel = stringResource(if (isShowWatched) R.string.watched_unmark_show else R.string.watched_mark_show)

    ItemActionsDialog(
        title = itemName,
        isFavorite = isFavorite,
        onToggleFavorite = onConfirm,
        onDismiss = onDismiss,
        actions =
            listOfNotNull(
                onToggleWatched?.let { toggle ->
                    ItemMenuAction(
                        icon = if (isWatched) CinemaIcons.VisibilityOff else CinemaIcons.Visibility,
                        label = stringResource(if (isWatched) R.string.watched_unmark else R.string.watched_mark),
                        onClick = toggle,
                    )
                },
                onToggleShowWatched?.let { toggle ->
                    ItemMenuAction(
                        icon = if (isShowWatched) CinemaIcons.VisibilityOff else CinemaIcons.Visibility,
                        label = showWatchedLabel,
                        onClick = toggle,
                    )
                },
            ),
    )
}
