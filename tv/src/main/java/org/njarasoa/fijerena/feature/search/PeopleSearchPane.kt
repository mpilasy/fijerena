package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.tmdb.FoundPerson
import org.njarasoa.fijerena.core.network.tmdb.PeopleSearchResults
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.viewmodels.PeopleSearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLook
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvSearchField
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Search's People type (the people search plan): the field the other types share, then TMDB's
 * actors, films and shows, people first, each group under a heading that takes no focus. The
 * search runs once typing stops (the catalogue search's wait) and on the keyboard's Search; only
 * the keyboard's Search moves focus to the first result, so results that come while typing do not
 * take the keyboard away. Down from the field goes to the first result, Up from it to the field.
 * Each result records itself in [returnFocus], so Back from the page it opened lands on it again.
 *
 * A person opens their actor page ([onPersonSelected]); a film or show a source has opens there
 * ([onTitleSelected], as the catalogue's results do); one no source has opens its TMDB page
 * ([onTmdbTitleSelected]), and hold OK or Menu on it adds it to the Wishlist ([onTmdbTitleActions]).
 * Its view model is made the first time the type shows, so a search of the catalogue never asks
 * TMDB. [holdFocus]: a type switch keeps focus on the type selector.
 */
@Composable
internal fun PeopleSearchPane(
    query: String,
    onQueryChange: (String) -> Unit,
    holdFocus: Boolean,
    fieldModifier: Modifier,
    returnFocus: NavReturnFocus,
    wishlistKeys: Set<WantedTitles.Key>,
    onPersonSelected: (tmdbId: String, name: String) -> Unit,
    onTitleSelected: (item: MediaItem, contentType: String) -> Unit,
    onTmdbTitleSelected: (TmdbTitle) -> Unit,
    onTmdbTitleActions: (TmdbTitle) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PeopleSearchViewModel = viewModel(factory = remember { PeopleSearchViewModel.Factory(context.applicationContext) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    // Set by the keyboard's Search: the next results take focus.
    var jumpToResults by remember { mutableStateOf(false) }
    // Straight into the keyboard for a first search; else the field, unless Back hands focus to a
    // result or a type switch keeps it on the selector.
    var editing by remember { mutableStateOf(!holdFocus && returnFocus.key == null && query.isEmpty()) }
    LaunchedEffect(Unit) {
        if (returnFocus.key == null && !holdFocus && !editing) fieldFocus.requestFocusWithRetry()
    }
    // The type shown with text it has not searched: at once. Then once typing stops.
    LaunchedEffect(Unit) { viewModel.search(query) }
    LaunchedEffect(query) {
        delay(CinemaAnimation.searchDebounceMs)
        viewModel.search(query)
    }
    val results = (state as? PeopleSearchViewModel.State.Loaded)?.results
    LaunchedEffect(results) {
        if (results != null && jumpToResults) {
            jumpToResults = false
            if (!results.isEmpty) firstResultFocus.requestFocusWithRetry()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TvSearchField(
            modifier =
                (if (results?.isEmpty == false) Modifier.focusProperties { down = firstResultFocus } else Modifier)
                    .then(fieldModifier),
            query = query,
            onQueryChange = onQueryChange,
            onSearchSubmit = {
                // The results on screen already are this query's: none come to jump to (Down reaches them).
                jumpToResults = (state as? PeopleSearchViewModel.State.Loaded)?.query != query.trim()
                viewModel.search(query)
            },
            onClear = {
                onQueryChange("")
                viewModel.search("")
            },
            placeholder = stringResource(R.string.search_people_placeholder),
            focusRequester = fieldFocus,
            editing = editing,
            onEditingChange = { editing = it },
            restingFieldModifier = Modifier.leftToRail(),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        when (val shown = state) {
            PeopleSearchViewModel.State.Idle -> {
                TvEmptyState(message = stringResource(R.string.search_people_placeholder), icon = CinemaIcons.Search)
            }

            is PeopleSearchViewModel.State.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MitadyLoading(role = TextRole.Display, tone = TextTone.Accent)
                }
            }

            is PeopleSearchViewModel.State.Failed -> {
                TvErrorState(
                    title = stringResource(R.string.search_people_unreachable),
                    message = shown.raw?.let { "[dev] $it" }.orEmpty(),
                    onRetry = viewModel::retry,
                )
            }

            is PeopleSearchViewModel.State.Loaded -> {
                if (shown.results.isEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CinemaText(
                            text = stringResource(R.string.search_no_results_query_format_tv, shown.query),
                            tone = TextTone.Secondary,
                        )
                    }
                } else {
                    PeopleResultsList(
                        results = shown.results,
                        firstFocus = firstResultFocus,
                        returnFocus = returnFocus,
                        wishlistKeys = wishlistKeys,
                        onPersonSelected = onPersonSelected,
                        onTitleSelected = onTitleSelected,
                        onTmdbTitleSelected = onTmdbTitleSelected,
                        onTmdbTitleActions = onTmdbTitleActions,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleResultsList(
    results: PeopleSearchResults,
    firstFocus: FocusRequester,
    returnFocus: NavReturnFocus,
    wishlistKeys: Set<WantedTitles.Key>,
    onPersonSelected: (tmdbId: String, name: String) -> Unit,
    onTitleSelected: (item: MediaItem, contentType: String) -> Unit,
    onTmdbTitleSelected: (TmdbTitle) -> Unit,
    onTmdbTitleActions: (TmdbTitle) -> Unit,
) {
    val listState = rememberLazyListState()
    NavReturnFocusEffect(returnFocus, listState = listState)
    val rowStyle = TvCardLooks.listRow()
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (results.people.isNotEmpty()) {
            item(key = "people_header", contentType = "header") {
                SearchSectionHeader(title = stringResource(R.string.search_tab_people), count = results.people.size, hiddenCount = 0)
            }
            itemsIndexed(results.people, key = {
                _,
                person,
                ->
                "person_${person.id}"
            }, contentType = { _, _ -> "person" }) { index, person ->
                val returnKey = "person_${person.id}"
                PersonResultRow(
                    person = person,
                    rowStyle = rowStyle,
                    onClick = {
                        returnFocus.leaveFrom(returnKey, listState)
                        onPersonSelected(person.id.toString(), person.name)
                    },
                    modifier =
                        (if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .navReturnFocusTarget(returnFocus, returnKey),
                )
            }
        }
        titleGroup(
            contentType = ContentType.MOVIES,
            titles = results.movies,
            firstFocus = firstFocus.takeIf { results.people.isEmpty() },
            rowStyle = rowStyle,
            listState = listState,
            returnFocus = returnFocus,
            wishlistKeys = wishlistKeys,
            onTitleSelected = onTitleSelected,
            onTmdbTitleSelected = onTmdbTitleSelected,
            onTmdbTitleActions = onTmdbTitleActions,
        )
        titleGroup(
            contentType = ContentType.TV_SHOWS,
            titles = results.shows,
            firstFocus = firstFocus.takeIf { results.people.isEmpty() && results.movies.isEmpty() },
            rowStyle = rowStyle,
            listState = listState,
            returnFocus = returnFocus,
            wishlistKeys = wishlistKeys,
            onTitleSelected = onTitleSelected,
            onTmdbTitleSelected = onTmdbTitleSelected,
            onTmdbTitleActions = onTmdbTitleActions,
        )
    }
}

/**
 * The Movies or TV Shows group: a source's title as the catalogue's result row, one no source has
 * as the "Not in your sources" row (the Not available mark). [firstFocus] goes on its first row
 * when the groups above it are empty.
 */
private fun LazyListScope.titleGroup(
    contentType: String,
    titles: List<MediaItem>,
    firstFocus: FocusRequester?,
    rowStyle: TvCardLook,
    listState: LazyListState,
    returnFocus: NavReturnFocus,
    wishlistKeys: Set<WantedTitles.Key>,
    onTitleSelected: (item: MediaItem, contentType: String) -> Unit,
    onTmdbTitleSelected: (TmdbTitle) -> Unit,
    onTmdbTitleActions: (TmdbTitle) -> Unit,
) {
    if (titles.isEmpty()) return
    item(key = "header_$contentType", contentType = "header") {
        SearchSectionHeader(
            title =
                stringResource(
                    if (contentType == ContentType.MOVIES) R.string.provider_movies_label else R.string.provider_tv_shows_label,
                ),
            count = titles.size,
            hiddenCount = 0,
        )
    }
    itemsIndexed(titles, key = {
        _,
        item,
        ->
        "${contentType}_${item.providerId}_${item.id}"
    }, contentType = { _, _ -> "title" }) { index, item ->
        val returnKey = "${contentType}_${item.providerId}_${item.id}"
        val target =
            (if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                .navReturnFocusTarget(returnFocus, returnKey)
        val tmdbTitle = TmdbOnlyItem.titleOf(item, contentType)?.let { it.copy(title = item.listTitle ?: it.title) }
        if (tmdbTitle != null) {
            TmdbTitleRow(
                title = tmdbTitle,
                onWishlist = tmdbTitle.key in wishlistKeys,
                onOpenActions = { onTmdbTitleActions(tmdbTitle) },
                rowStyle = rowStyle,
                onClick = {
                    returnFocus.leaveFrom(returnKey, listState)
                    onTmdbTitleSelected(tmdbTitle)
                },
                modifier = target,
            )
        } else {
            SearchResultItem(
                result = item.asSearchResult(contentType),
                rowStyle = rowStyle,
                onClick = {
                    returnFocus.leaveFrom(returnKey, listState)
                    onTitleSelected(item, contentType)
                },
                focusRequester = null,
                modifier = target,
            )
        }
    }
}

/**
 * A source's title found by the People type, in the catalogue's result row: its list title
 * (`MediaItem.listTitle`), and the year where the catalogue row shows the category.
 */
private fun MediaItem.asSearchResult(contentType: String): SearchResult =
    SearchResult(
        itemId = id,
        streamName = listTitle ?: name,
        categoryId = categoryId,
        categoryName = metadata.year?.toString().orEmpty(),
        contentType = contentType,
        thumbnailUrl = thumbnailUrl,
        mediaType = mediaType,
        providerId = providerId,
    )

/** A person found: the photo (a letter when TMDB has none), the name and the titles they are known for. */
@Composable
private fun PersonResultRow(
    person: FoundPerson,
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
                .height(TvDimensions.cardHeight),
        look = rowStyle,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaThumbnail(
                url = TmdbApiService.profileUrl(person.profilePath),
                fallbackLetter = person.name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.DEFAULT,
                contentDescription = person.name,
                modifier = Modifier.fillMaxHeight().aspectRatio(PHOTO_ASPECT_RATIO),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                CinemaText(text = person.name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person.knownFor.isNotEmpty()) {
                    CinemaText(
                        text = person.knownFor.joinToString(" · "),
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

// A person's photo, as on the cast cards.
private const val PHOTO_ASPECT_RATIO = 2f / 3f
