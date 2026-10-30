package org.njarasoa.fijerena.feature.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.person.PersonViewModel
import org.njarasoa.fijerena.core.ui.person.personFactsLine
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.feature.home.HomeRow
import org.njarasoa.fijerena.feature.home.TitleCard
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.ExpandablePlot
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.mobileClockTopBarInsets
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A person's page on the phone (docs/plans/20261030_cast-tab-plan.md, Design 3): the photo, the
 * name, the birth and death dates and place, the biography (a few lines; a tap shows all of it),
 * then the person's Movies and TV Shows that the profile's sources have, as Home's rows. Each row's
 * title line ends with the switch for the titles no source has (one switch for both rows, off on
 * each opening, none for a child); a row keeps its switch while it has no card. A tap on a card
 * opens the title on its own source, or a TMDB-only card its TMDB page ([onTitleSelected]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobilePersonScreen(
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
    val shownName = (state as? PersonViewModel.State.Loaded)?.person?.name?.takeIf { it.isNotBlank() } ?: name

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = mobileClockTopBarInsets(),
                title = { CinemaText(shownName, role = TextRole.Title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(CinemaTheme.colors.background).padding(padding)) {
            when (val current = state) {
                PersonViewModel.State.Loading -> {
                    CinemaSpinner(modifier = Modifier.align(Alignment.Center))
                }

                is PersonViewModel.State.Error -> {
                    RetryWhenOnline(viewModel::retry)
                    MobileEmptyState(
                        message = stringResource(R.string.person_load_error),
                        secondary = current.raw?.let { "[dev] $it" },
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = viewModel::retry,
                    )
                }

                is PersonViewModel.State.Loaded -> {
                    PersonContent(
                        state = current,
                        name = shownName,
                        canShowMissing = viewModel.canShowMissing,
                        showMissing = showMissing,
                        onShowMissing = viewModel::setShowMissing,
                        onTitleSelected = onTitleSelected,
                    )
                }
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
) {
    val person = state.person
    val rows = state.rows
    val movies = rows?.movies.orEmpty()
    val shows = rows?.shows.orEmpty()
    // The eye at the end of a row's title, as on Home's Trending rows.
    val switch: @Composable () -> Unit = {
        IconAction(
            onClick = { onShowMissing(!showMissing) },
            icon = if (showMissing) CinemaIcons.VisibilityOff else CinemaIcons.Visibility,
            label = stringResource(if (showMissing) R.string.chart_hide_missing else R.string.chart_show_missing),
            // Not the default content colour: the page has no surface under it. The accent while it is on.
            tint = if (showMissing) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
        )
    }
    val titleRow: @Composable (String, List<MediaItem>, String, Boolean) -> Unit = { title, items, contentType, hasSwitch ->
        HomeRow(title, items, { it.sourceKey }, action = if (hasSwitch) switch else null, showEmpty = hasSwitch) { item ->
            TitleCard(
                title = item.name,
                // CT-D5: the release year on each card.
                subtitle = item.metadata.releaseDate?.take(YEAR_LENGTH) ?: item.metadata.year?.toString(),
                thumbnailUrl = item.thumbnailUrl,
                thumbnailType = if (contentType == ContentType.TV_SHOWS) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
                progress = null,
                sourceName = null,
                providerId = item.providerId,
                onLongClick = null,
                notAvailable = item.id.startsWith(TmdbOnlyItem.PREFIX),
            ) { onTitleSelected(item, contentType) }
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = Spacing.md, bottom = Spacing.md + LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CinemaThumbnail(
                    url = TmdbApiService.profileUrl(person.profilePath, TmdbApiService.PROFILE_SIZE_H632),
                    fallbackLetter = name.firstOrNull(),
                    modifier = Modifier.height(MobileDimensions.posterHeightLarge).aspectRatio(PHOTO_ASPECT),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
                    CinemaText(text = name, role = TextRole.Title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    personFactsLine(person)?.let { CinemaText(text = it, tone = TextTone.Secondary) }
                }
            }
            person.biography?.takeIf { it.isNotBlank() }?.let {
                ExpandablePlot(plot = it, collapsedLines = BIOGRAPHY_LINES, modifier = Modifier.fillMaxWidth())
            }
        }
        if (rows == null) {
            CinemaSpinner(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            if (movies.isEmpty() && shows.isEmpty() && !showMissing) {
                CinemaText(
                    text = stringResource(R.string.person_none_in_library),
                    tone = TextTone.Secondary,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
            titleRow(stringResource(R.string.provider_movies_label), movies, ContentType.MOVIES, canShowMissing && state.hasMovies)
            titleRow(stringResource(R.string.provider_tv_shows_label), shows, ContentType.TV_SHOWS, canShowMissing && state.hasShows)
        }
    }
}

/** A person's photo: TMDB's portrait shape. */
private const val PHOTO_ASPECT = 2f / 3f

/** The biography's lines before a tap shows all of it (CT-D6). */
private const val BIOGRAPHY_LINES = 4

private const val YEAR_LENGTH = 4
