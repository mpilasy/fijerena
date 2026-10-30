@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.search

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleBadges
import org.njarasoa.fijerena.core.ui.history.LocalShowSourceBadges
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.search.rememberSearchFacetLabels
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
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
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.CategorySearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.buildGroupedSearchResults
import org.njarasoa.fijerena.core.ui.viewmodels.effectiveFacetChoice
import org.njarasoa.fijerena.core.ui.viewmodels.effectiveSourceFilter
import org.njarasoa.fijerena.core.ui.viewmodels.fromSource
import org.njarasoa.fijerena.core.ui.viewmodels.lastRunQuery
import org.njarasoa.fijerena.core.ui.viewmodels.matches
import org.njarasoa.fijerena.core.ui.viewmodels.queryToRunOnTabSwitch
import org.njarasoa.fijerena.core.ui.viewmodels.searchFacets
import org.njarasoa.fijerena.core.ui.viewmodels.searchSourceCounts
import org.njarasoa.fijerena.core.ui.viewmodels.showsGuideTab
import org.njarasoa.fijerena.core.ui.viewmodels.toggled
import org.njarasoa.fijerena.core.ui.wanted.label
import org.njarasoa.fijerena.feature.category.components.FavoriteContextMenuDialog
import org.njarasoa.fijerena.feature.epgbrowser.TvGuideSearchPane
import org.njarasoa.fijerena.ui.components.DetailsMoreMenu
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.TvUndoBar
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLook
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.cards.TvListRowDefaults
import org.njarasoa.fijerena.ui.components.cards.TvRowTitle
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvSearchField
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.components.rememberUndoBarState
import org.njarasoa.fijerena.ui.components.undoOnRemoteKeys
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
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

/** What the field searches, in the viewer's words (TV UI audit X8): films, shows, channels. */
@Composable
private fun searchPlaceholder(
    contentType: String,
    child: Boolean,
): String =
    when {
        child -> stringResource(R.string.tv_search_placeholder_child)
        contentType == ContentType.LIVE_TV -> stringResource(R.string.tv_search_placeholder_channels)
        contentType == ContentType.MOVIES -> stringResource(R.string.tv_search_placeholder_films)
        contentType == ContentType.TV_SHOWS -> stringResource(R.string.tv_search_placeholder_shows)
        else -> stringResource(R.string.tv_search_placeholder_all)
    }

@Composable
fun SearchScreen(
    contentType: String,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, contentType: String, providerId: Long) -> Unit,
    onCategorySelected: (categoryId: String, contentType: String, providerId: Long) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    initialQuery: String? = null,
    // A Guide tab result: its channel, as on Search the guide.
    onGuideResult: (EpgBrowserMatchedStream) -> Unit = {},
    // A section's Search found nothing: the same words over everything (null on "ALL").
    onSearchEverything: ((query: String) -> Unit)? = null,
    // A "Not in your sources" result: its TMDB page (wanted titles plan → Search).
    onTmdbTitleSelected: (TmdbTitle) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: SearchViewModel =
        viewModel(
            factory =
                remember(contentType) {
                    SearchViewModelFactory(
                        context = context.applicationContext,
                        contentType = contentType,
                    )
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val hasGuide by viewModel.hasGuide.collectAsStateWithLifecycle()
    val notInSources by viewModel.notInSources.collectAsStateWithLifecycle()
    val wishlistKeys by viewModel.wishlistKeys.collectAsStateWithLifecycle()
    // Hold OK / Menu on a "Not in your sources" row: Add to / Remove from Wishlist (wishlist UX plan, Phase 2).
    var tmdbMenu by remember { mutableStateOf<TmdbTitle?>(null) }
    tmdbMenu?.let { title ->
        val on = title.key in wishlistKeys
        DetailsMoreMenu(
            title = title.title,
            refreshLabel = stringResource(if (on) R.string.wanted_remove else R.string.wanted_add),
            icon = if (on) CinemaIcons.Delete else CinemaIcons.BookmarkBorder,
            removes = on,
            onRefresh = { viewModel.toggleWishlist(title) },
            onDismiss = { tmdbMenu = null },
        )
    }

    // Catalogue | Guide (search guide tab plan, D1/O1). Saveable: Back from a guide result's
    // channel comes back to the Guide tab.
    // The app's one search (SearchSession), shared by both tabs and every Search screen: Back to any
    // of them shows the latest.
    val profileId = remember { AppSettings(context.applicationContext).activeProfileId }
    // A child profile finds films and shows only, each opening its page: no channels (its Live TV
    // is its favourites), categories, Guide tab or menus (docs/plans/20261019_kids-home-plan.md, K-D7).
    val child = remember { AppSettings(context.applicationContext).isChildProfile(profileId) }
    val showsTabs = !child && showsGuideTab(contentType, hasGuide)
    SearchSession.forProfile(profileId)
    var selectedTab by SearchSession::tab
    val activeTab = if (showsTabs) selectedTab else SearchTab.CATALOGUE
    // The field's text, shared by both tabs; each tab's own state holds the query it last ran.
    var fieldText by SearchSession::query
    // The source chosen in a multi-source profile (null: every source); kept until Search closes.
    var selectedSource by SearchSession::source
    // The decades and genres chosen in the filter (decade and genre filter plan).
    var selectedFacets by SearchSession::facets
    // The content-type pill (null: All), shared with the phone.
    var selectedTypeFilter by SearchSession::typeFilter
    // Set by a tab switch: focus stays on the tab, so the tab shown takes no first-open focus,
    // keyboard or jump to its results until its field is reached.
    var holdFocus by remember { mutableStateOf(false) }
    val catalogueTabFocus = remember { FocusRequester() }
    val guideTabFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    // Up from the field to the selected tab; the field taking focus ends a tab switch's hold.
    val fieldModifier =
        if (showsTabs) {
            Modifier
                .focusRequester(fieldFocus)
                .focusProperties { up = if (activeTab == SearchTab.GUIDE) guideTabFocus else catalogueTabFocus }
                .onFocusChanged { if (it.hasFocus) holdFocus = false }
        } else {
            Modifier
        }

    // Arrived from a "more like this" card: run its title straight away, once — run again when the
    // screen was rebuilt, it replaced a search typed since. The text field picks the term up from
    // the ViewModel's state on its own.
    var initialQueryApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialQuery) {
        if (!initialQueryApplied && !initialQuery.isNullOrBlank()) {
            initialQueryApplied = true
            fieldText = initialQuery
            viewModel.performSearch(initialQuery)
        } else if (activeTab == SearchTab.CATALOGUE && fieldText.length >= 2 &&
            (viewModel.uiState.value as? SearchViewModel.UiState.Success)?.query != fieldText
        ) {
            // Back to this screen after a search made on another: its results are run again for
            // the latest term instead of showing the replaced one.
            viewModel.performSearch(fieldText)
        }
    }

    val configuration = LocalConfiguration.current
    val appSettings = remember { AppSettings(context.applicationContext) }

    // Back from a result's details (or a category) lands on that result — see SearchResultsList.
    val returnFocus = rememberNavReturnFocus()

    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }

    // The menu's result's source: its Favourite and Mark watched act there.
    var favoriteMenuSource by remember { mutableLongStateOf(0L) }
    val longPressScope = rememberCoroutineScope()

    // A favourite removed from a result's menu: at once, with an Undo (plan → A) — Menu undoes
    // while the bar shows.
    val undoBar = rememberUndoBarState()
    favoriteMenuTarget?.let { target ->
        val source = favoriteMenuSource
        FavoriteContextMenuDialog(
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
                        if (target.isFavorite) {
                            undoBar.show(target.categoryName) {
                                viewModel.toggleFavoriteCategory(source, target.categoryId, target.categoryName, target.contentType, false)
                            }
                        }
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
                        if (target.isFavorite) {
                            undoBar.show(parseDisplayTitle(target.itemName).title.ifBlank { target.itemName }) {
                                viewModel.toggleFavorite(
                                    source,
                                    target.itemId,
                                    target.itemName,
                                    target.categoryId,
                                    target.contentType,
                                    false,
                                )
                            }
                        }
                    }
                }
            },
            onDismiss = { favoriteMenuTarget = null },
            onToggleWatched =
                (target as? FavoriteMenuTarget.Stream)?.isWatched?.let { isWatched ->
                    { viewModel.toggleWatched(source, target.itemId, target.contentType, isWatched) }
                },
            // Null when the result isn't a show or its source keeps watched state on its server.
            onToggleShowWatched =
                (target as? FavoriteMenuTarget.Stream)?.isShowWatched?.let { isShowWatched ->
                    {
                        viewModel.setShowWatched(source, target.itemId, !isShowWatched) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize().undoOnRemoteKeys(undoBar),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            TvScreenHeader(
                title = stringResource(R.string.common_search),
                subtitle = localizedContentTypeLabel(contentType),
            ) {
            }

            if (showsTabs) {
                SearchTabRow(
                    selected = activeTab,
                    onSelect = { tab ->
                        if (tab != selectedTab) {
                            holdFocus = true
                            selectedTab = tab
                        }
                    },
                    catalogueFocus = catalogueTabFocus,
                    guideFocus = guideTabFocus,
                    fieldFocus = fieldFocus,
                )
            }

            if (activeTab == SearchTab.GUIDE) {
                GuideTab(
                    query = fieldText,
                    onQueryChange = { fieldText = it },
                    onGuideResult = onGuideResult,
                    holdFocus = holdFocus,
                    fieldModifier = fieldModifier,
                )
            } else {
                // Shown again with a query it hasn't run: run it here (D3).
                LaunchedEffect(Unit) {
                    queryToRunOnTabSwitch(fieldText, (viewModel.uiState.value as? SearchViewModel.UiState.Success)?.query)
                        ?.let { viewModel.performSearch(it) }
                }
                when (val state = uiState) {
                    is SearchViewModel.UiState.Loading -> {
                        LoadingView(message = state.message)
                    }

                    is SearchViewModel.UiState.Error -> {
                        ErrorView(state.message)
                    }

                    is SearchViewModel.UiState.Success -> {
                        val successState = state
                        val failedSuffix = if (successState.failedCalls > 0) " (${successState.failedCalls} failed)" else ""
                        val errorSuffix = if (successState.firstError != null) "\n${successState.firstError}" else ""
                        val devStats =
                            if (appSettings.isDevMode && successState.searchDataSize != null) {
                                "${successState.searchDataSize} fetched | ${successState.totalDuration} total | network: ${successState.networkWallDuration} wall / ${successState.networkAccumDuration} accum | ${successState.networkCalls} calls$failedSuffix$errorSuffix"
                            } else {
                                null
                            }
                        // Sources left out because their search failed: raw, developer mode only.
                        val sourceErrors =
                            successState.sourceErrors
                                .takeIf { appSettings.isDevMode && it.isNotEmpty() }
                                ?.joinToString("\n")
                        // Expert mode only, as the source badges: without it results don't name their source.
                        val showSources = LocalShowSourceBadges.current
                        val categoryResults = if (child) emptyList() else successState.categoryResults
                        val streamResults =
                            if (child) {
                                successState.filteredResults.filter { it.contentType != ContentType.LIVE_TV }
                            } else {
                                successState.filteredResults
                            }
                        val sources =
                            remember(categoryResults, streamResults, showSources) {
                                if (showSources) {
                                    searchSourceCounts(
                                        categoryResults,
                                        streamResults,
                                    )
                                } else {
                                    emptyList()
                                }
                            }
                        val source = effectiveSourceFilter(selectedSource, sources)
                        // TMDB's films and shows no source has: never for a child, nor when one source is
                        // chosen ("not in your sources" would be wrong for what another source has).
                        val tmdbSection = notInSources?.takeIf { !child && source == null && it.query == successState.query }
                        val sourceResults = remember(streamResults, source) { streamResults.fromSource(source) }
                        val tmdbTitles = tmdbSection?.titles.orEmpty()
                        val facetChoice = effectiveFacetChoice(selectedFacets, sourceResults, tmdbTitles)
                        val facets =
                            remember(sourceResults, tmdbTitles, facetChoice) { searchFacets(sourceResults, tmdbTitles, facetChoice) }
                        SearchContent(
                            query = successState.query,
                            fieldText = fieldText,
                            onFieldTextChange = { fieldText = it },
                            holdFocus = holdFocus,
                            fieldModifier = fieldModifier,
                            // Categories have no year or genre: a decade or genre choice leaves them out.
                            categoryResults = if (facetChoice.isActive) emptyList() else categoryResults.fromSource(source),
                            results = sourceResults.filter { it.matches(facetChoice) },
                            notInSources = tmdbTitles.filter { it.matches(facetChoice) },
                            notInSourcesError = tmdbSection?.devError,
                            onTmdbTitleClick = onTmdbTitleSelected,
                            onTmdbTitleActions = { tmdbMenu = it },
                            wishlistKeys = wishlistKeys,
                            sources = sources,
                            selectedSource = source,
                            onSourceChange = { selectedSource = it },
                            facets = facets,
                            facetChoice = facetChoice,
                            onFacetChoiceChange = { selectedFacets = it },
                            selectedTypeFilter = selectedTypeFilter,
                            onTypeFilterChange = { selectedTypeFilter = it },
                            excludedCountByType = if (child) emptyMap() else successState.excludedCountByType,
                            isSearching = successState.isSearching,
                            searchProgress = successState.searchProgress ?: "",
                            devStats = devStats,
                            sourceErrors = sourceErrors,
                            contentType = contentType,
                            child = child,
                            searchHistory = searchHistory,
                            returnFocus = returnFocus,
                            // A "more like this" arrival is already searching; don't cover it with the keyboard.
                            mayOpenKeyboard = initialQuery.isNullOrBlank(),
                            onSearchSubmit = { viewModel.performSearch(it) },
                            onHistoryItemClick = { term ->
                                viewModel.performSearch(term)
                            },
                            onHistoryItemRemove = { term ->
                                viewModel.removeSearchHistoryEntry(term)
                            },
                            onClearHistory = { viewModel.clearSearchHistory() },
                            onClearSearch = { viewModel.clearSearch() },
                            onResultClick = { result ->
                                onStreamSelected(result.itemId, result.streamName, result.categoryId, result.contentType, result.providerId)
                            },
                            onResultLongPress = { result ->
                                if (child) return@SearchContent
                                // Favourite, Mark watched (a film or an episode) or Mark show watched (a
                                // show), on the result's own source: the menu waits for one watch-state read.
                                longPressScope.launch {
                                    val target = viewModel.menuTarget(result)
                                    favoriteMenuSource = result.providerId
                                    favoriteMenuTarget = target
                                }
                            },
                            onCategoryClick = { catResult ->
                                onCategorySelected(catResult.categoryId, catResult.contentType, catResult.providerId)
                            },
                            onSearchEverything = onSearchEverything,
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
        // Over the screen, not inside its padded column: kept off the overscan edge here.
        TvUndoBar(undoBar, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.tvSafeMarginVertical))
    }
}

/** Catalogue | Guide under the header: OK selects, Left from the first opens the rail, Down goes to the field. */
@Composable
private fun SearchTabRow(
    selected: SearchTab,
    onSelect: (SearchTab) -> Unit,
    catalogueFocus: FocusRequester,
    guideFocus: FocusRequester,
    fieldFocus: FocusRequester,
) {
    val toField = Modifier.focusProperties { down = fieldFocus }
    Row(
        modifier = Modifier.padding(bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TvSelectableButton(
            selected = selected == SearchTab.CATALOGUE,
            onSelect = { onSelect(SearchTab.CATALOGUE) },
            text = stringResource(R.string.search_tab_catalogue),
            modifier = Modifier.focusRequester(catalogueFocus).then(toField).leftToRail(),
        )
        TvSelectableButton(
            selected = selected == SearchTab.GUIDE,
            onSelect = { onSelect(SearchTab.GUIDE) },
            text = stringResource(R.string.search_tab_guide),
            modifier = Modifier.focusRequester(guideFocus).then(toField),
        )
    }
}

/**
 * The type pills, as the phone's chips (All, then each kind of result and "Not in your sources",
 * with their counts; OK on the chosen one goes back to All), then the filter icon
 * (the search decade genre filter plan, Phase 3) with the active choices
 * beside it: OK opens [TvSearchFilterDialog]. [focusRequester] is the chosen pill, else the icon:
 * Down from the field lands there. Up goes to the field (not the Catalogue tab, whose centre is
 * closer), Left from the first item to the rail, Down to the results.
 */
@Composable
private fun SearchFilterRow(
    types: List<Pair<String, Int>>,
    selectedType: String?,
    onSelectType: (String?) -> Unit,
    showsFilter: Boolean,
    summary: String?,
    active: Boolean,
    onOpen: () -> Unit,
    focusRequester: FocusRequester,
    iconFocusRequester: FocusRequester,
    fieldFocus: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
) {
    fun Modifier.entryIf(entry: Boolean) =
        focusProperties { up = fieldFocus }.then(if (entry) Modifier.focusRequester(focusRequester) else Modifier)
    Row(
        modifier =
            Modifier
                .padding(bottom = Spacing.md)
                .onFocusChanged { onFocusChange(it.hasFocus) }
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (types.isNotEmpty()) {
            TvSelectableButton(
                selected = selectedType == null,
                onSelect = { onSelectType(null) },
                text = "${stringResource(R.string.content_type_all_label)} (${types.sumOf { it.second }})",
                modifier = Modifier.entryIf(selectedType == null).leftToRail(),
            )
            for ((type, count) in types) {
                val label =
                    if (type ==
                        SearchSession.NOT_IN_SOURCES
                    ) {
                        stringResource(R.string.wanted_not_in_sources)
                    } else {
                        localizedContentTypeLabel(type)
                    }
                TvSelectableButton(
                    selected = selectedType == type,
                    onSelect = { onSelectType(type) },
                    text = "$label ($count)",
                    modifier = Modifier.entryIf(selectedType == type),
                )
            }
        }
        if (showsFilter) {
            TvIconAction(
                onClick = onOpen,
                icon = CinemaIcons.Tune,
                label = stringResource(R.string.search_filter_label),
                modifier =
                    Modifier
                        .focusRequester(iconFocusRequester)
                        .entryIf(types.isEmpty())
                        .then(if (types.isEmpty()) Modifier.leftToRail() else Modifier),
                iconTint = if (active) CinemaTheme.colors.accent else null,
            )
            summary?.let {
                CinemaText(
                    text = stringResource(R.string.search_source_only_format, it),
                    role = TextRole.Body,
                    tone = TextTone.Accent,
                )
            }
        }
    }
}

/**
 * The Guide tab: Search the guide's pane without its header actions or category chip (O3). Its
 * ViewModel is made the first time the tab shows, so a catalogue-only search never loads the guide.
 */
@Composable
private fun GuideTab(
    query: String,
    onQueryChange: (String) -> Unit,
    onGuideResult: (EpgBrowserMatchedStream) -> Unit,
    holdFocus: Boolean,
    fieldModifier: Modifier,
) {
    val context = LocalContext.current
    val guideViewModel: EpgBrowserViewModel =
        viewModel(factory = remember { EpgBrowserViewModelFactory(context.applicationContext, null, 0L) })
    // Shown with a query it hasn't run: run it here (D3).
    LaunchedEffect(Unit) {
        queryToRunOnTabSwitch(query, guideViewModel.uiState.value.lastRunQuery())?.let { guideViewModel.performSearch(it) }
    }
    TvGuideSearchPane(
        viewModel = guideViewModel,
        query = query,
        onQueryChange = onQueryChange,
        onNavigateToPlayer = onGuideResult,
        // A tab switch keeps focus on the tab, as a header button's return does.
        headerReturnPending = holdFocus,
        fieldModifier = fieldModifier,
    )
}

@Composable
private fun LoadingView(message: String? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CinemaSpinner(modifier = Modifier.size(TvDimensions.iconXLarge))
            CinemaText(
                text =
                    message ?: androidx.compose.ui.res
                        .stringResource(org.njarasoa.fijerena.core.ui.R.string.search_loading_categories),
                role = TextRole.Title,
                tone = TextTone.Secondary,
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
            CinemaText(text = stringResource(R.string.common_error), role = TextRole.Display, tone = TextTone.Danger)
            CinemaText(text = message, tone = TextTone.Secondary)
        }
    }
}

@Composable
private fun SearchContent(
    query: String,
    // The field's text, shared with the Guide tab.
    fieldText: String,
    onFieldTextChange: (String) -> Unit,
    // A tab switch: focus stays on the tab (no first-open focus, keyboard or jump to the results).
    holdFocus: Boolean,
    fieldModifier: Modifier,
    categoryResults: List<CategorySearchResult>,
    results: List<SearchResult>,
    notInSources: List<TmdbTitle>,
    notInSourcesError: String?,
    onTmdbTitleClick: (TmdbTitle) -> Unit,
    onTmdbTitleActions: (TmdbTitle) -> Unit,
    wishlistKeys: Set<WantedTitles.Key>,
    // The results' sources, empty under two; the chosen one (null: every source).
    sources: List<SearchSourceCount>,
    selectedSource: Long?,
    onSourceChange: (Long?) -> Unit,
    // The results' decades and genres, and the ones chosen.
    facets: SearchFacets,
    facetChoice: SearchFacetChoice,
    onFacetChoiceChange: (SearchFacetChoice) -> Unit,
    // The content-type pill (null: All; SearchSession.NOT_IN_SOURCES: only TMDB's titles).
    selectedTypeFilter: String?,
    onTypeFilterChange: (String?) -> Unit,
    excludedCountByType: Map<String, Int>,
    isSearching: Boolean,
    searchProgress: String?,
    devStats: String?,
    sourceErrors: String?,
    contentType: String,
    child: Boolean,
    searchHistory: List<String>,
    returnFocus: NavReturnFocus,
    mayOpenKeyboard: Boolean,
    onSearchSubmit: (String) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearSearch: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onResultLongPress: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit,
    onCategoryLongPress: (CategorySearchResult) -> Unit,
    onSearchEverything: ((query: String) -> Unit)? = null,
) {
    val searchFocusRequester = remember { FocusRequester() }

    // Down from the search field should reach the first recent-search chip. Left to a spatial
    // focus search it lands on the "Recent Searches" header's clear-all button instead, which sits
    // between the field and the chips. Hoisted so the field can point at it; only wired up while
    // the history is actually on screen, since aiming `down` at an unattached requester would stop
    // Down working at all.
    val historyFocusRequester = remember { FocusRequester() }
    // The chosen type pill, else the filter icon: Down from the field lands there while the row shows.
    val filterFocusRequester = remember { FocusRequester() }
    // The filter icon itself: focus goes back to it when its dialog closes.
    val filterIconFocus = remember { FocusRequester() }
    // While the filter icon or its dialog has focus, the results don't jump to their first item.
    var filterFocused by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var filterClosed by remember { mutableStateOf(false) }

    // Sync with incoming query only if local is empty (prevents erasing user input)
    LaunchedEffect(query) {
        if (fieldText.isEmpty() && query.isNotEmpty()) {
            onFieldTextChange(query)
        }
    }

    // Results or empty state — show results whenever they exist, even if text field is cleared
    val hasResults = categoryResults.isNotEmpty() || results.isNotEmpty() || notInSources.isNotEmpty() || isSearching
    val showsHistory = !hasResults && query.isEmpty() && searchHistory.isNotEmpty()
    // Results from two or more sources, or with decades or genres (never with the recent searches,
    // which show without results).
    val showsFilter = sources.isNotEmpty() || !facets.isEmpty
    // The type pills, as the phone's chips: a search of everything with more than one kind of result.
    val typeCounts =
        remember(categoryResults, results, notInSources) {
            buildGroupedSearchResults(categoryResults, results)
                .map { (type, cats, streams) -> type to cats.size + streams.size }
                .filter { it.second > 0 } +
                listOfNotNull((SearchSession.NOT_IN_SOURCES to notInSources.size).takeIf { notInSources.isNotEmpty() })
        }
    val showsTypes = contentType == "ALL" && typeCounts.size > 1
    // A pill with no results left is not chosen: every kind shows.
    val typeFilter = selectedTypeFilter?.takeIf { type -> showsTypes && typeCounts.any { it.first == type } }
    val showsRow = showsFilter || showsTypes

    // Entry (Part II Phase 7, F-S-1/F-S-2): the field without the keyboard when there are recent
    // searches, so Down reaches them; straight into the keyboard for a first search. Not on a
    // Back from a result — NavReturnFocusEffect lands on that result instead.
    var editing by remember {
        mutableStateOf(
            mayOpenKeyboard && !holdFocus && returnFocus.key == null && query.isEmpty() && !hasResults && searchHistory.isEmpty(),
        )
    }
    LaunchedEffect(Unit) {
        if (returnFocus.key == null && !holdFocus && !editing) searchFocusRequester.requestFocusWithRetry()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TvSearchField(
            modifier =
                when {
                    showsHistory -> Modifier.focusProperties { down = historyFocusRequester }
                    showsRow -> Modifier.focusProperties { down = filterFocusRequester }
                    else -> Modifier
                }.then(fieldModifier),
            query = fieldText,
            onQueryChange = onFieldTextChange,
            onSearchSubmit = { onSearchSubmit(fieldText) },
            onClear = {
                onFieldTextChange("")
                onClearSearch()
            },
            placeholder = searchPlaceholder(contentType, child),
            focusRequester = searchFocusRequester,
            editing = editing,
            onEditingChange = { editing = it },
            showClearButton = fieldText.isNotEmpty() || results.isNotEmpty() || categoryResults.isNotEmpty(),
            restingFieldModifier = Modifier.leftToRail(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        if (!hasResults && query.isEmpty()) {
            if (searchHistory.isNotEmpty()) {
                SearchHistorySection(
                    history = searchHistory,
                    onItemClick = { term ->
                        onFieldTextChange(term)
                        onHistoryItemClick(term)
                    },
                    onItemRemove = onHistoryItemRemove,
                    onClearAll = onClearHistory,
                    firstItemFocusRequester = historyFocusRequester,
                    fieldFocusRequester = searchFocusRequester,
                )
            } else {
                TvEmptyState(message = searchPlaceholder(contentType, child), icon = CinemaIcons.Search)
            }
        } else {
            if (showsRow) {
                val facetLabels = rememberSearchFacetLabels()
                SearchFilterRow(
                    types = if (showsTypes) typeCounts else emptyList(),
                    selectedType = typeFilter,
                    onSelectType = { type -> onTypeFilterChange(if (type == typeFilter) null else type) },
                    showsFilter = showsFilter,
                    summary = facetLabels.summary(sources.firstOrNull { it.providerId == selectedSource }?.name, facets, facetChoice),
                    active = selectedSource != null || facetChoice.isActive,
                    onOpen = { filterOpen = true },
                    focusRequester = filterFocusRequester,
                    iconFocusRequester = filterIconFocus,
                    fieldFocus = searchFocusRequester,
                    onFocusChange = { filterFocused = it },
                )
                if (filterOpen) {
                    TvSearchFilterDialog(
                        sources = sources,
                        selectedSource = selectedSource,
                        onSelectSource = onSourceChange,
                        facets = facets,
                        choice = facetChoice,
                        onChoiceChange = onFacetChoiceChange,
                        labels = facetLabels,
                        onDismiss = {
                            filterOpen = false
                            filterClosed = true
                        },
                    )
                }
                // Focus goes back to the icon when the dialog closes.
                LaunchedEffect(filterClosed) {
                    if (filterClosed) {
                        filterIconFocus.requestFocusWithRetry()
                        filterClosed = false
                    }
                }
            }
            // Up out of the results' top goes to the filter icon; left to the geometric search, it
            // reached the field over the row.
            Box(
                modifier =
                    if (showsRow) {
                        Modifier
                            .focusProperties {
                                onExit = { if (requestedFocusDirection == FocusDirection.Up) filterFocusRequester.requestFocus() }
                            }.focusGroup()
                    } else {
                        Modifier
                    },
            ) {
                SearchResultsList(
                    categoryResults = categoryResults.filter { typeFilter == null || it.contentType == typeFilter },
                    results = results.filter { typeFilter == null || it.contentType == typeFilter },
                    notInSources =
                        notInSources.filter {
                            typeFilter == null || typeFilter == SearchSession.NOT_IN_SOURCES ||
                                it.contentType == typeFilter
                        },
                    filterKey = listOf(typeFilter, selectedSource, facetChoice),
                    notInSourcesError = notInSourcesError,
                    onTmdbTitleClick = onTmdbTitleClick,
                    onTmdbTitleActions = onTmdbTitleActions,
                    wishlistKeys = wishlistKeys,
                    excludedCountByType = excludedCountByType,
                    query = query,
                    queryContentType = contentType,
                    isSearching = isSearching,
                    searchProgress = searchProgress,
                    devStats = devStats,
                    sourceErrors = sourceErrors,
                    returnFocus = returnFocus,
                    holdFocus = holdFocus || filterFocused || filterOpen,
                    onResultClick = onResultClick,
                    onResultLongPress = onResultLongPress,
                    onCategoryClick = onCategoryClick,
                    onCategoryLongPress = onCategoryLongPress,
                    onSearchEverything = onSearchEverything,
                )
            }
        }
    }
}

@Composable
private fun SearchHistorySection(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onItemRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    fieldFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaText(text = stringResource(R.string.epg_browser_recent_searches), role = TextRole.Title)
            CinemaIconButton(
                onClick = onClearAll,
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Delete,
                        contentDescription = stringResource(R.string.epg_browser_clear_all_description),
                        modifier = Modifier.size(TvDimensions.iconSmall),
                    )
                },
            )
        }
        // Wraps rather than scrolling sideways: the whole history should be visible at a glance,
        // and a D-pad user should not have to scroll a rail to reach the last entry.
        FlowRow(
            // Up out of the first line goes to the field. Left to the geometric search, a chip at
            // the left edge picks the Catalogue tab over the far wider field (closer centres).
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        fieldFocusRequester?.let { field ->
                            Modifier
                                .focusProperties {
                                    onExit = { if (requestedFocusDirection == FocusDirection.Up) field.requestFocus() }
                                }.focusGroup()
                        } ?: Modifier,
                    ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val chipLook = TvCardLooks.chip()
            history.forEachIndexed { index, term ->
                TvCard(
                    onClick = { onItemClick(term) },
                    // The first chip is at the screen's left edge: Left from it opens the TV
                    // navigation rail.
                    modifier =
                        if (index == 0) {
                            (firstItemFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).leftToRail()
                        } else {
                            Modifier
                        },
                    look = chipLook,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            imageVector = CinemaIcons.Search,
                            contentDescription = null,
                            tint = CinemaTheme.colors.textSecondary,
                            modifier = Modifier.size(TvDimensions.iconSmall),
                        )
                        CinemaText(text = term, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    categoryResults: List<CategorySearchResult>,
    results: List<SearchResult>,
    notInSources: List<TmdbTitle>,
    notInSourcesError: String?,
    onTmdbTitleClick: (TmdbTitle) -> Unit,
    onTmdbTitleActions: (TmdbTitle) -> Unit,
    wishlistKeys: Set<WantedTitles.Key>,
    excludedCountByType: Map<String, Int>,
    query: String,
    queryContentType: String,
    isSearching: Boolean,
    searchProgress: String?,
    devStats: String?,
    sourceErrors: String?,
    returnFocus: NavReturnFocus,
    holdFocus: Boolean,
    onResultClick: (SearchResult) -> Unit,
    onResultLongPress: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit,
    onCategoryLongPress: (CategorySearchResult) -> Unit,
    onSearchEverything: ((query: String) -> Unit)? = null,
    // The pill and filter choices: a change starts the list at its top.
    filterKey: Any? = null,
) {
    // Back from a result's details hands focus to that result (keyed by its LazyColumn key), at the
    // scroll position the list had when it was opened.
    val listState = rememberLazyListState()
    NavReturnFocusEffect(returnFocus, listState = listState)
    // The list keeps its place by its first item's key, which another choice moves down the list
    // (All after Movies opened on the Movies header): a new choice shows the top.
    var shownFilterKey by remember { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (filterKey != shownFilterKey) {
            shownFilterKey = filterKey
            listState.scrollToItem(0)
        }
    }

    // Stable within a query's results — only add missing keys, never discard existing
    // FocusRequesters, so focus targeting survives recomposition mid-query. Keyed to
    // categoryResults/results so a new query drops the old query's requesters instead of
    // accumulating one per result id ever seen this session.
    val focusRequesters = remember(categoryResults, results) { java.util.concurrent.ConcurrentHashMap<String, FocusRequester>() }
    val firstItemFocusRequester = remember { FocusRequester() }

    var expandedGroups by rememberSaveable { mutableStateOf(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }
    // The content rows' focus look, built once per results list rather than once per row.
    val rowStyle = TvCardLooks.listRow()

    // Auto-focus logic: when results appear for the first time for a new query, focus the first item
    // — not when Back has just rebuilt the list and NavReturnFocusEffect is about to hand focus to
    // the result that was opened.
    // TMDB's section arrives after the catalogue's: it takes the first focus only when the catalogue found nothing.
    val catalogueEmpty = categoryResults.isEmpty() && results.isEmpty()
    LaunchedEffect(categoryResults, results, isSearching, notInSources.isNotEmpty()) {
        if (returnFocus.key == null && !holdFocus && !isSearching && (!catalogueEmpty || notInSources.isNotEmpty())) {
            firstItemFocusRequester.requestFocusWithRetry()
        }
    }

    fun toggleGroup(contentType: String) {
        expandedGroups = expandedGroups.toggled(contentType)
    }

    if (isSearching && categoryResults.isEmpty() && results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            MitadyLoading(
                role = TextRole.Display,
                tone = TextTone.Accent,
            )
        }
    } else if (catalogueEmpty && notInSources.isEmpty() && notInSourcesError == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CinemaText(
                    text =
                        if (queryContentType == "ALL") {
                            stringResource(R.string.search_no_results_query_format_tv, query)
                        } else {
                            stringResource(R.string.search_no_results_type_format_tv, localizedContentTypeLabel(queryContentType), query)
                        },
                    tone = TextTone.Secondary,
                )
                // A section's Search (from its rail item) found nothing: one press widens it.
                if (queryContentType != "ALL" && onSearchEverything != null) {
                    CinemaSecondaryButton(
                        onClick = { onSearchEverything(query) },
                        text = stringResource(R.string.search_everything),
                    )
                }
            }
            sourceErrors?.let { SourceErrorsLine(it, Modifier.align(Alignment.BottomCenter)) }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchProgress != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSearching) {
                            MitadyLoading(
                                role = TextRole.Body,
                                tone = TextTone.Accent,
                            )
                        }
                        CinemaText(text = if (isSearching) " ($searchProgress)" else searchProgress, tone = TextTone.Secondary)
                    }
                }
            }

            if (devStats != null) {
                CinemaText(
                    text = devStats,
                    role = TextRole.Caption,
                    tone = TextTone.Tertiary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xs),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }

            // Pre-compute grouped results outside LazyColumn to avoid O(N×types) per recomposition
            val groupedByType =
                remember(categoryResults, results) {
                    buildGroupedSearchResults(categoryResults, results)
                }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                if (queryContentType == "ALL") {
                    var isFirstItem = true
                    groupedByType.forEach { (type, typeCats, typeStreams) ->

                        if (typeCats.isNotEmpty() || typeStreams.isNotEmpty()) {
                            val isExpanded = expandedGroups.contains(type)
                            item(key = "header_$type", contentType = "header") {
                                CollapsibleHeader(
                                    title = localizedContentTypeLabel(type),
                                    count = typeCats.size + typeStreams.size,
                                    hiddenCount = excludedCountByType[type] ?: 0,
                                    isExpanded = isExpanded,
                                    onToggle = { toggleGroup(type) },
                                )
                            }

                            if (isExpanded) {
                                itemsIndexed(
                                    typeCats,
                                    key = { _, it -> "cat_${it.listKey}" },
                                    contentType = { _, _ -> "category" },
                                ) { index, catResult ->
                                    val returnKey = "cat_${catResult.listKey}"
                                    CategoryResultItem(
                                        result = catResult,
                                        rowStyle = rowStyle,
                                        onClick = {
                                            returnFocus.leaveFrom(returnKey, listState)
                                            onCategoryClick(catResult)
                                        },
                                        onLongPress = { onCategoryLongPress(catResult) },
                                        modifier =
                                            if (isFirstItem &&
                                                index == 0
                                            ) {
                                                Modifier.focusRequester(firstItemFocusRequester)
                                            } else {
                                                Modifier
                                            }.navReturnFocusTarget(returnFocus, returnKey),
                                    )
                                    if (isFirstItem && index == 0) isFirstItem = false
                                }
                                itemsIndexed(
                                    typeStreams,
                                    key = { _, it -> "stream_${it.listKey}" },
                                    contentType = { _, _ -> "stream" },
                                ) { index, result ->
                                    val returnKey = "stream_${result.listKey}"
                                    SearchResultItem(
                                        result = result,
                                        rowStyle = rowStyle,
                                        onClick = {
                                            returnFocus.leaveFrom(returnKey, listState)
                                            onResultClick(result)
                                        },
                                        modifier = Modifier.navReturnFocusTarget(returnFocus, returnKey),
                                        onLongPress = { onResultLongPress(result) },
                                        focusRequester =
                                            if (isFirstItem &&
                                                index == 0
                                            ) {
                                                firstItemFocusRequester
                                            } else {
                                                focusRequesters.requesterFor(result.listKey)
                                            },
                                    )
                                    if (isFirstItem && index == 0) isFirstItem = false
                                }
                            }
                        }
                    }
                } else {
                    // Specific content type search - no need for collapsible groups
                    if (categoryResults.isNotEmpty()) {
                        item(key = "category_header", contentType = "header") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_tab_categories),
                                count = categoryResults.size,
                                hiddenCount = 0,
                            )
                        }
                        itemsIndexed(
                            categoryResults,
                            key = { _, it -> "cat_${it.listKey}" },
                            contentType = { _, _ -> "category" },
                        ) { index, catResult ->
                            val returnKey = "cat_${catResult.listKey}"
                            CategoryResultItem(
                                result = catResult,
                                rowStyle = rowStyle,
                                onClick = {
                                    returnFocus.leaveFrom(returnKey, listState)
                                    onCategoryClick(catResult)
                                },
                                onLongPress = { onCategoryLongPress(catResult) },
                                modifier =
                                    (if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                        .navReturnFocusTarget(returnFocus, returnKey),
                            )
                        }
                    }

                    if (results.isNotEmpty()) {
                        item(key = "stream_header", contentType = "header") {
                            SearchSectionHeader(
                                title = localizedContentTypeLabel(queryContentType),
                                count = results.size,
                                hiddenCount = excludedCountByType[queryContentType] ?: 0,
                            )
                        }
                        itemsIndexed(results, key = {
                            _,
                            it,
                            ->
                            it.listKey
                        }, contentType = { _, _ -> "stream" }) { index, result ->
                            val returnKey = result.listKey
                            SearchResultItem(
                                result = result,
                                rowStyle = rowStyle,
                                onClick = {
                                    returnFocus.leaveFrom(returnKey, listState)
                                    onResultClick(result)
                                },
                                modifier = Modifier.navReturnFocusTarget(returnFocus, returnKey),
                                onLongPress = { onResultLongPress(result) },
                                focusRequester =
                                    if (categoryResults.isEmpty() &&
                                        index == 0
                                    ) {
                                        firstItemFocusRequester
                                    } else {
                                        focusRequesters.requesterFor(result.listKey)
                                    },
                            )
                        }
                    }
                }
                notInSourcesSection(
                    titles = notInSources,
                    devError = notInSourcesError,
                    rowStyle = rowStyle,
                    firstFocus = firstItemFocusRequester.takeIf { catalogueEmpty },
                    returnFocus = returnFocus,
                    listState = listState,
                    onClick = onTmdbTitleClick,
                    onOpenActions = onTmdbTitleActions,
                    wishlistKeys = wishlistKeys,
                )
            }
            sourceErrors?.let { SourceErrorsLine(it) }
        }
    }
}

/** Developer mode: the raw error of each source left out of the results (its search failed). */
@Composable
private fun SourceErrorsLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    CinemaText(
        text = text,
        tone = TextTone.Danger,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().padding(top = Spacing.xs),
    )
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
private fun SearchSectionHeader(
    title: String,
    count: Int,
    hiddenCount: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CinemaText(text = title, role = TextRole.Title)
        Spacer(Modifier.weight(1f))
        CinemaText(text = searchCountLabel(count, hiddenCount), tone = TextTone.Secondary)
    }
}

@Composable
private fun CollapsibleHeader(
    title: String,
    count: Int,
    hiddenCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    TvCard(
        onClick = onToggle,
        // Group headers and result rows span the list from the screen's left edge: Left from any
        // of them opens the TV navigation rail.
        modifier =
            Modifier
                .leftToRail()
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
        // A section title that folds its group: no fill at rest, the content rows' focus look
        // (light lift, white outline) when focused.
        look = TvCardLooks.foldingHeader(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaText(text = title, role = TextRole.Title)
            Spacer(Modifier.weight(1f))
            CinemaText(
                text = searchCountLabel(count, hiddenCount),
                tone = TextTone.Secondary,
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Icon(
                imageVector = if (isExpanded) CinemaIcons.KeyboardArrowUp else CinemaIcons.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                tint = CinemaTheme.colors.textSecondary,
                modifier = Modifier.size(TvDimensions.iconMedium),
            )
        }
    }
}

@Composable
private fun CategoryResultItem(
    result: CategorySearchResult,
    rowStyle: TvCardLook,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        modifier =
            modifier
                .leftToRail()
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight)
                .tvLongPress(onLongPress),
        look = rowStyle,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaText(
                text = result.categoryName,
                role = TextRole.Heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // At the row's end, as on a title's row.
            result.sourceName?.let { SourceBadge(it, providerId = result.providerId) }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    rowStyle: TvCardLook,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
) {
    TvCard(
        onClick = onClick,
        modifier =
            modifier
                .leftToRail()
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight)
                .tvLongPress(onLongPress)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        look = rowStyle,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A channel's logo is drawn whole (see StreamList). 16:9 at the row's height, not a
            // poster-width strip squashed into it (TV UI audit #14).
            val isChannel = result.contentType == ContentType.LIVE_TV
            // Same title as the browse lists: the provider's "EN - " / "4K-NF - " tags as badges, on the second line.
            val parsedTitle =
                remember(result.streamName, result.categoryName) {
                    parseDisplayTitle(result.streamName).withoutCodeOf(result.categoryName)
                }
            val shownTitle = parsedTitle.titleWithoutYear.ifBlank { result.streamName }
            CinemaThumbnail(
                url = result.thumbnailUrl,
                fallbackLetter = shownTitle.firstOrNull(Char::isLetterOrDigit),
                contentType = if (isChannel) ThumbnailContentType.LIVE_TV else ThumbnailContentType.DEFAULT,
                overlayGradient = !isChannel,
                modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f),
            )
            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CinemaText(
                        text = shownTitle,
                        role = TextRole.Heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Its source (when the search covers several) ends the line, in the same place on every row.
                    result.sourceName?.let { SourceBadge(it, providerId = result.providerId) }
                }
                // The category it is in, as a plain secondary line ("kids' movies", not "Category: kids' movies"),
                // after the title's badges.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TitleBadges(parsedTitle)
                    CinemaText(
                        // The year the title gave, before the category, so a long category can't push it off.
                        text = listOfNotNull(parsedTitle.year?.toString(), result.categoryName).joinToString(" · "),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * TV-specific long press modifier for D-pad Center key.
 * Triggers onKeyUp ONLY if a long-press was detected via onKeyDown repeat counts.
 */

/** The focus requester of the result row [key], made on first use; the map is remembered by the screen. */
private fun MutableMap<String, FocusRequester>.requesterFor(key: String): FocusRequester = getOrPut(key) { FocusRequester() }

private fun Modifier.tvLongPress(onLongPress: () -> Unit): Modifier =
    composed {
        var longPressDetected by remember { mutableStateOf(false) }

        onPreviewKeyEvent { event ->
            val isDpadCenter =
                event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter

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

/**
 * Search's "Not in your sources" section (wanted titles plan → Search): TMDB's films and shows that
 * no source of the profile has, under the catalogue's results, each opening its TMDB page. The
 * heading is not a focus stop, as the other section headings. [devError] is TMDB's raw error, in
 * developer mode. [firstFocus] goes on the first row when the catalogue found nothing.
 */
private fun LazyListScope.notInSourcesSection(
    titles: List<TmdbTitle>,
    devError: String?,
    rowStyle: TvCardLook,
    firstFocus: FocusRequester?,
    returnFocus: NavReturnFocus,
    listState: LazyListState,
    onClick: (TmdbTitle) -> Unit,
    onOpenActions: (TmdbTitle) -> Unit,
    wishlistKeys: Set<WantedTitles.Key>,
) {
    if (titles.isEmpty() && devError == null) return
    item(key = "tmdb_header", contentType = "header") {
        Column {
            SearchSectionHeader(title = stringResource(R.string.wanted_not_in_sources), count = titles.size, hiddenCount = 0)
            devError?.let { SourceErrorsLine(it) }
        }
    }
    itemsIndexed(titles, key = { _, it -> "tmdb_${it.contentType}_${it.tmdbId}" }, contentType = { _, _ -> "tmdb" }) { index, title ->
        val returnKey = "tmdb_${title.contentType}_${title.tmdbId}"
        TmdbTitleRow(
            title = title,
            onWishlist = title.key in wishlistKeys,
            onOpenActions = { onOpenActions(title) },
            rowStyle = rowStyle,
            onClick = {
                returnFocus.leaveFrom(returnKey, listState)
                onClick(title)
            },
            modifier =
                (if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                    .navReturnFocusTarget(returnFocus, returnKey),
        )
    }
}

/** One "Not in your sources" result: TMDB's poster, the title, its year and kind, and the "Not available" mark. */
@Composable
private fun TmdbTitleRow(
    title: TmdbTitle,
    // On the Wishlist: a filled bookmark in place of the "Not available" cloud.
    onWishlist: Boolean,
    // Hold OK or Menu: Add to / Remove from Wishlist.
    onOpenActions: () -> Unit,
    rowStyle: TvCardLook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        modifier =
            modifier
                .leftToRail()
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight)
                .tvLongPress(onOpenActions)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                        onOpenActions()
                        true
                    } else {
                        false
                    }
                },
        look = rowStyle,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaThumbnail(
                url = TmdbApiService.posterUrl(title.posterPath),
                fallbackLetter = title.title.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.DEFAULT,
                overlayGradient = true,
                modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                CinemaText(text = title.title, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (onWishlist) CinemaIcons.Bookmark else CinemaIcons.CloudOff,
                contentDescription = stringResource(if (onWishlist) R.string.wanted_title else R.string.wanted_not_available),
                tint = if (onWishlist) CinemaTheme.colors.accent else CinemaTheme.colors.textSecondary,
                modifier = Modifier.size(TvDimensions.iconMedium),
            )
        }
    }
}
