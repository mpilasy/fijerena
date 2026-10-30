package org.njarasoa.fijerena.feature.person

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.person.PersonViewModel
import org.njarasoa.fijerena.core.ui.person.personFactsLine
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.feature.contentselection.components.TvFavoritesRow
import org.njarasoa.fijerena.ui.components.TvContentText
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * A person's page on TV (docs/plans/20261030_cast-tab-plan.md, Design 3): the photo, the name, the
 * birth and death dates and place, the biography (a few lines; OK shows all of it), then the
 * person's Movies and TV Shows that the profile's sources have, as Home's rows. Each row starts with
 * the switch for the titles no source has (one switch for both rows, off on each opening, none for
 * a child), and keeps the switch while it has no card. A card opens the title on its own source, or
 * a TMDB-only card its TMDB page ([onTitleSelected], with `MOVIES` or `TV_SHOWS`).
 *
 * Focus: on the first card on entry, else on the biography, else on a row's switch; Back from a
 * title returns to the card left.
 */
@Composable
fun PersonScreen(
    tmdbId: String,
    name: String,
    crew: Boolean,
    onTitleSelected: (item: MediaItem, contentType: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PersonViewModel =
        viewModel(
            key = "person_${tmdbId}_$crew",
            factory = remember(tmdbId, crew) { PersonViewModel.Factory(context, tmdbId.toIntOrNull(), crew) },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showMissing by viewModel.showMissing.collectAsStateWithLifecycle()
    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize().background(CinemaTheme.colors.background)) {
        when (val current = state) {
            PersonViewModel.State.Loading -> {
                CinemaSpinner(modifier = Modifier.align(Alignment.Center))
            }

            is PersonViewModel.State.Error -> {
                TvErrorState(
                    message = stringResource(R.string.person_load_error) + current.raw?.let { "\n\n[dev] $it" }.orEmpty(),
                    onRetry = viewModel::retry,
                    onBack = onBack,
                    backLabel = stringResource(R.string.common_back),
                )
            }

            is PersonViewModel.State.Loaded -> {
                PersonContent(
                    state = current,
                    name = name,
                    canShowMissing = viewModel.canShowMissing,
                    showMissing = showMissing,
                    onShowMissing = viewModel::setShowMissing,
                    onTitleSelected = onTitleSelected,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun PersonContent(
    state: PersonViewModel.State.Loaded,
    name: String,
    canShowMissing: Boolean,
    showMissing: Boolean,
    onShowMissing: (Boolean) -> Unit,
    onTitleSelected: (item: MediaItem, contentType: String) -> Unit,
    onBack: () -> Unit,
) {
    val person = state.person
    val rows = state.rows
    val listState = rememberLazyListState()
    val returnFocus = rememberNavReturnFocus()
    val biographyFocus = remember { FocusRequester() }
    val movieFocus = remember { FocusRequester() }
    val showFocus = remember { FocusRequester() }
    val movieListState = rememberLazyListState()
    val showListState = rememberLazyListState()
    val biography = person.biography?.takeIf { it.isNotBlank() }
    var biographyOpen by rememberSaveable { mutableStateOf(false) }

    val movies = rows?.movies.orEmpty()
    val shows = rows?.shows.orEmpty()
    // A row with no card keeps its switch while the person has titles of its kind (as Trending).
    val movieSwitch = canShowMissing && state.hasMovies
    val showSwitch = canShowMissing && state.hasShows

    // Entry focus once the rows are matched: the first card, else the biography, else a switch.
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(rows != null) {
        if (rows == null || entered || returnFocus.isReturn) return@LaunchedEffect
        entered = true
        when {
            movies.isNotEmpty() -> movieFocus.requestFocusWithRetry()
            shows.isNotEmpty() -> showFocus.requestFocusWithRetry()
            biography != null -> biographyFocus.requestFocusWithRetry()
            movieSwitch -> movieFocus.requestFocusWithRetry()
            showSwitch -> showFocus.requestFocusWithRetry()
        }
    }
    NavReturnFocusEffect(
        returnFocus,
        listState = listState,
        fallback = if (movies.isNotEmpty()) movieFocus else showFocus,
        // The cards come with the rows.
        prepare = { snapshotFlow { rows != null }.first { it } },
    )

    val switch: @Composable (Modifier) -> Unit = { switchModifier ->
        TvIconAction(
            onClick = { onShowMissing(!showMissing) },
            icon = if (showMissing) CinemaIcons.VisibilityOff else CinemaIcons.Visibility,
            label = stringResource(if (showMissing) R.string.chart_hide_missing else R.string.chart_show_missing),
            modifier = switchModifier,
        )
    }
    val titleRow: @Composable (String, List<MediaItem>, String, Boolean, FocusRequester, LazyListState) -> Unit =
        { title, items, contentType, hasSwitch, focus, rowListState ->
            val prefix = "person:$contentType:"
            TvFavoritesRow(
                title = title,
                items = items,
                thumbnailType = if (contentType == ContentType.TV_SHOWS) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
                onItemSelected = { item ->
                    returnFocus.leaveFrom(prefix + item.sourceKey, listState)
                    onTitleSelected(item, contentType)
                },
                onOpenActions = null,
                firstItemFocus = focus,
                restoreFocus = returnFocus.requesterWhere { it.startsWith(prefix) },
                listState = rowListState,
                itemModifier = { item -> Modifier.navReturnFocusTarget(returnFocus, prefix + item.sourceKey) },
                // CT-D5: the release year on each card; a TMDB-only card has only its year.
                statusOf = { item ->
                    item.metadata.year
                        ?.toString()
                        ?.takeIf { item.metadata.releaseDate.isNullOrBlank() }
                },
                leading = if (hasSwitch) switch else null,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            )
        }

    LazyColumn(
        state = listState,
        // Back on the root, before the focused control: see NAVIGATION_GUIDE → TV Back on Detail Screens.
        modifier =
            Modifier.fillMaxSize().focusable().onPreviewKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else {
                    false
                }
            },
        contentPadding =
            PaddingValues(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical,
            ),
    ) {
        item(key = "header") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                CinemaThumbnail(
                    url = TmdbApiService.profileUrl(person.profilePath, TmdbApiService.PROFILE_SIZE_H632),
                    fallbackLetter = (person.name ?: name).firstOrNull(),
                    modifier = Modifier.width(TvDimensions.posterWidth).aspectRatio(PHOTO_ASPECT),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
                    CinemaText(
                        text = person.name?.takeIf { it.isNotBlank() } ?: name,
                        role = TextRole.Display,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    personFactsLine(person)?.let { CinemaText(text = it, tone = TextTone.Secondary) }
                    // One focus stop (rule 3: no text Up/Down skips); OK shows the whole text or a few lines again.
                    biography?.let { text ->
                        TvCard(
                            onClick = { biographyOpen = !biographyOpen },
                            look = TvCardLooks.listRow(),
                            modifier = Modifier.fillMaxWidth().focusRequester(biographyFocus).leftToRail(),
                        ) {
                            TvContentText(
                                text = text,
                                maxLines = if (biographyOpen) Int.MAX_VALUE else BIOGRAPHY_LINES,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(Spacing.sm),
                            )
                        }
                    }
                }
            }
        }
        if (rows == null) {
            item(key = "matching") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                    CinemaSpinner()
                }
            }
        } else {
            if (movies.isEmpty() && shows.isEmpty() && !showMissing) {
                item(key = "none") {
                    CinemaText(
                        text = stringResource(R.string.person_none_in_library),
                        tone = TextTone.Secondary,
                        modifier = Modifier.padding(top = Spacing.xl),
                    )
                }
            }
            item(key = "movies") {
                titleRow(
                    stringResource(R.string.provider_movies_label),
                    movies,
                    ContentType.MOVIES,
                    movieSwitch,
                    movieFocus,
                    movieListState,
                )
            }
            item(key = "shows") {
                titleRow(
                    stringResource(R.string.provider_tv_shows_label),
                    shows,
                    ContentType.TV_SHOWS,
                    showSwitch,
                    showFocus,
                    showListState,
                )
            }
        }
    }
}

/** A person's photo: TMDB's portrait shape. */
private const val PHOTO_ASPECT = 2f / 3f

/** The biography's lines before OK shows all of it (CT-D6). */
private const val BIOGRAPHY_LINES = 4
