package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.PeopleSearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaSearchField
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Search's People type (the people search plan): the field the other types share, then TMDB's
 * actors, films and shows, people first, each group under its heading. The search runs once typing
 * stops (the catalogue search's wait) and at once on the keyboard's Search.
 *
 * A person opens their actor page ([onPersonSelected]); a film or show a source has opens there
 * ([onTitleSelected], as the catalogue's results do); one no source has opens its TMDB page
 * ([onTmdbTitleSelected]), and a long-press on it adds it to the Wishlist ([onTmdbTitleLongPress]).
 * Its view model is made the first time the type shows, so a search of the catalogue never asks TMDB.
 */
@Composable
internal fun MobilePeopleSearchPane(
    query: String,
    onQueryChange: (String) -> Unit,
    wishlistKeys: Set<WantedTitles.Key>,
    onPersonSelected: (tmdbId: String, name: String) -> Unit,
    onTitleSelected: (item: MediaItem, contentType: String) -> Unit,
    onTmdbTitleSelected: (TmdbTitle) -> Unit,
    onTmdbTitleLongPress: (TmdbTitle) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PeopleSearchViewModel = viewModel(factory = remember { PeopleSearchViewModel.Factory(context.applicationContext) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Here, not in the list: the list leaves while results load, and Back from a result must find
    // the place the user left (saved with the screen).
    val listState = rememberLazyListState()
    // The type shown with text it has not searched: at once. Then once typing stops.
    LaunchedEffect(Unit) { viewModel.search(query) }
    LaunchedEffect(query) {
        delay(CinemaAnimation.searchDebounceMs)
        viewModel.search(query)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    CinemaSearchField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        placeholder = stringResource(R.string.search_people_placeholder),
        leadingIcon = { Icon(imageVector = CinemaIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (state is PeopleSearchViewModel.State.Loading) {
                CinemaSpinner(modifier = Modifier.size(MobileDimensions.iconDefault))
            } else if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        viewModel.search("")
                    },
                ) {
                    Icon(
                        imageVector = CinemaIcons.Close,
                        contentDescription = stringResource(R.string.provider_clear_button),
                        tint = CinemaTheme.colors.textPrimary,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    viewModel.search(query)
                    keyboardController?.hide()
                },
            ),
    )
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        when (val shown = state) {
            PeopleSearchViewModel.State.Idle -> {
                CinemaText(
                    text = stringResource(R.string.search_people_placeholder),
                    modifier = Modifier.align(Alignment.Center),
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                )
            }

            is PeopleSearchViewModel.State.Loading -> {
                MitadyLoading(modifier = Modifier.align(Alignment.Center), role = TextRole.Title, tone = TextTone.Accent)
            }

            is PeopleSearchViewModel.State.Failed -> {
                RetryWhenOnline(viewModel::retry)
                MobileEmptyState(
                    message = stringResource(R.string.search_people_unreachable),
                    secondary = shown.raw?.let { "[dev] $it" },
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::retry,
                )
            }

            is PeopleSearchViewModel.State.Loaded -> {
                if (shown.results.isEmpty) {
                    CinemaText(
                        text = stringResource(R.string.search_no_results_query_format, shown.query),
                        modifier = Modifier.align(Alignment.Center),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                    )
                } else {
                    PeopleResultsList(
                        listState = listState,
                        results = shown.results,
                        wishlistKeys = wishlistKeys,
                        onPersonSelected = onPersonSelected,
                        onTitleSelected = onTitleSelected,
                        onTmdbTitleSelected = onTmdbTitleSelected,
                        onTmdbTitleLongPress = onTmdbTitleLongPress,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleResultsList(
    listState: LazyListState,
    results: PeopleSearchResults,
    wishlistKeys: Set<WantedTitles.Key>,
    onPersonSelected: (tmdbId: String, name: String) -> Unit,
    onTitleSelected: (item: MediaItem, contentType: String) -> Unit,
    onTmdbTitleSelected: (TmdbTitle) -> Unit,
    onTmdbTitleLongPress: (TmdbTitle) -> Unit,
) {
    // Built once for the list, as the catalogue's results do.
    val cardColors = CardDefaults.cardColors(containerColor = CinemaTheme.colors.surface)
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.md + LocalDockInset.current),
    ) {
        if (results.people.isNotEmpty()) {
            item(key = "people_header", contentType = "header") {
                SearchSectionHeader(title = stringResource(R.string.search_tab_people), count = results.people.size, hiddenCount = 0)
            }
            items(results.people, key = { "person_${it.id}" }, contentType = { "person" }) { person ->
                PersonResultCard(
                    person = person,
                    cardColors = cardColors,
                    onClick = { onPersonSelected(person.id.toString(), person.name) },
                )
            }
        }
        titleGroup(ContentType.MOVIES, results.movies, cardColors, wishlistKeys, onTitleSelected, onTmdbTitleSelected, onTmdbTitleLongPress)
        titleGroup(
            ContentType.TV_SHOWS,
            results.shows,
            cardColors,
            wishlistKeys,
            onTitleSelected,
            onTmdbTitleSelected,
            onTmdbTitleLongPress,
        )
    }
}

/** The Movies or TV Shows group: a source's title as the catalogue's result card, one no source has as the "Not in your sources" card. */
private fun LazyListScope.titleGroup(
    contentType: String,
    titles: List<MediaItem>,
    cardColors: CardColors,
    wishlistKeys: Set<WantedTitles.Key>,
    onTitleSelected: (item: MediaItem, contentType: String) -> Unit,
    onTmdbTitleSelected: (TmdbTitle) -> Unit,
    onTmdbTitleLongPress: (TmdbTitle) -> Unit,
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
    items(titles, key = { "${contentType}_${it.providerId}_${it.id}" }, contentType = { "title" }) { item ->
        val tmdbTitle = TmdbOnlyItem.titleOf(item, contentType)?.let { it.copy(title = item.listTitle ?: it.title) }
        if (tmdbTitle != null) {
            TmdbTitleCard(
                title = tmdbTitle,
                onWishlist = tmdbTitle.key in wishlistKeys,
                cardColors = cardColors,
                onClick = { onTmdbTitleSelected(tmdbTitle) },
                onLongClick = { onTmdbTitleLongPress(tmdbTitle) },
            )
        } else {
            SearchResultCard(
                result = item.asSearchResult(contentType),
                cardColors = cardColors,
                onClick = { onTitleSelected(item, contentType) },
            )
        }
    }
}

/**
 * A source's title found by the People type, in the catalogue's result card: its list title
 * (`MediaItem.listTitle`), and the year where the catalogue card shows the category.
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
private fun PersonResultCard(
    person: FoundPerson,
    cardColors: CardColors,
    onClick: () -> Unit,
) {
    CinemaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(MobileDimensions.streamCardHeight),
        colors = cardColors,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(CinemaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            CinemaThumbnail(
                url = TmdbApiService.profileUrl(person.profilePath),
                fallbackLetter = person.name.firstOrNull(Char::isLetterOrDigit),
                contentType = ThumbnailContentType.DEFAULT,
                contentDescription = person.name,
                modifier = Modifier.fillMaxHeight().aspectRatio(PHOTO_ASPECT_RATIO),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CinemaText(
                    text = person.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                )
                if (person.knownFor.isNotEmpty()) {
                    CinemaText(
                        text = person.knownFor.joinToString(" · "),
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

// A person's photo, as on the cast cards.
private const val PHOTO_ASPECT_RATIO = 2f / 3f
