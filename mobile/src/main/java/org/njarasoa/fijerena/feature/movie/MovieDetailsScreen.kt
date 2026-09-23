package org.njarasoa.fijerena.feature.movie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.model.channelLabel
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.extractYear
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.resolutionLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.RatingBadge
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.MetaBadge
import org.njarasoa.fijerena.ui.components.MetaText
import org.njarasoa.fijerena.ui.components.MobileDetailHero
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.DetailIconAction
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: MovieDetailsViewModel =
        viewModel(
            factory =
                remember(movieId, categoryId) {
                    MovieDetailsViewModelFactory(context.applicationContext, movieId, categoryId, movieName)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    val backdropUrl by viewModel.backdropUrl.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()
    val isFavorite = (uiState as? MovieDetailsViewModel.UiState.Success)?.isFavorite ?: false
    val isWatched = (uiState as? MovieDetailsViewModel.UiState.Success)?.isWatched ?: false

    // Retained across a refresh so pulling down leaves the details on screen under the spinner,
    // instead of blanking to a full-screen loading state. Mirrors the episode screen.
    var lastSuccess by remember { mutableStateOf<MovieDetailsViewModel.UiState.Success?>(null) }
    (uiState as? MovieDetailsViewModel.UiState.Success)?.let { lastSuccess = it }
    val isRefreshing = uiState is MovieDetailsViewModel.UiState.Loading && lastSuccess != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.movie_details_title)) },
                navigationIcon = {
                    CinemaIconButton(onClick = onBack,
                        icon = {
                            Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back), tint = CinemaTextPrimary)
                        }
                    )
                },
                // Favorite/Watched moved into the icon row under the Play button (see
                // MovieDetailsContent) — matches the Plex/Netflix "actions under the poster"
                // layout instead of a top-bar icon cluster.
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            val state = uiState
            val shown = lastSuccess
            when {
                state is MovieDetailsViewModel.UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onBack = onBack,
                    )
                }
                shown != null -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshMovieInfo() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        MovieDetailsContent(
                            movieDetail = shown.movieDetail,
                            relatedTitles = relatedTitles,
                            tmdbTitle = tmdbTitle,
                            alternateStreams = alternateStreams,
                            movieId = shown.movieDetail.id,
                            movieName = shown.streamName,
                            resumePositionMs = shown.resumePositionMs,
                            resumeDurationMs = shown.resumeDurationMs,
                            categoryName = shown.categoryName,
                            logoUrl = logoUrl,
                            backdropUrl = backdropUrl,
                            isFavorite = isFavorite,
                            isWatched = isWatched,
                            onToggleFavorite = { viewModel.toggleFavorite(shown.streamName) },
                            onToggleWatched = { viewModel.toggleWatched() },
                            onPlayMovie = onPlayMovie,
                            onCategorySelected = { onCategorySelected(shown.categoryId) },
                            onRelatedTitleSelected = onRelatedTitleSelected,
                            onAlternateStreamSelected = { viewModel.switchToAlternateStream(it) },
                        )
                    }
                }
                else -> {
                    LoadingScreen()
                }
            }
        }
    }
}

@Composable
private fun MovieDetailsContent(
    movieDetail: MovieDetail,
    relatedTitles: RelatedTitles,
    tmdbTitle: String?,
    alternateStreams: List<MediaItem>,
    movieId: String,
    movieName: String,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    categoryName: String?,
    logoUrl: String?,
    backdropUrl: String?,
    isFavorite: Boolean,
    isWatched: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleWatched: () -> Unit,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
) {
    val extension = movieDetail.extension ?: "mp4"

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Lazy, not Column(verticalScroll): a scrolling Column measures every child, so the related
    // rows below paid their full layout cost while sitting off-screen — on the TV copy of this
    // screen that was 200ms of a 215ms measure pass, and this screen's player exit shows the same
    // ~167ms rebuild frame. Unlike the TV version, nothing moves visually here: the rows were
    // already top-level siblings rather than nested inside a panel.
    //
    // Everything above the related rows stays in one item: it is a single flowing block that is
    // largely on screen anyway, so splitting it would add churn without saving measurement.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CinemaSpacing.md),
    ) {
        item(key = "detail") {
        Column {
        val movieTitleText = tmdbTitle ?: movieDetail.name.ifEmpty { movieName }
        MobileDetailHero(
            title = movieTitleText,
            backdropUrl = backdropUrl,
            posterUrl = movieDetail.coverUrl,
            logoUrl = logoUrl,
            thumbnailContentType = ThumbnailContentType.MOVIE,
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Movie metadata - genre on its own line
        movieDetail.metadata.genre?.let { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Single dot-separated meta row: star rating and content rating/resolution stay their own
        // small pills (same as before), everything else is plain text — all joined by " · " into
        // one flowing line instead of each fact carrying its own separate spacing.
        val endsAtContext = LocalContext.current
        val endsAtText =
            remember(movieDetail.metadata.duration, resumePositionMs) {
                computeEndsAt(endsAtContext, movieDetail.metadata.duration, resumePositionMs)
            }
        val year = extractYear(movieDetail.metadata.year, movieDetail.metadata.releaseDate, movieDetail.name.ifBlank { movieName })
        val resolution =
            movieDetail.videoInfo?.let { video -> video.width?.let { w -> video.height?.let { h -> resolutionLabel(w, h) } } }
        val metaSegments =
            listOfNotNull<@Composable () -> Unit>(
                movieDetail.metadata.rating?.let { rating ->
                    { RatingBadge(rating = rating, textColor = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium) }
                },
                year?.let { { MetaText(it.toString()) } },
                movieDetail.metadata.contentRating?.let { { MetaBadge(it) } },
                movieDetail.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { { MetaText(formatDuration(it)) } },
                endsAtText?.let { { MetaText(stringResource(R.string.movie_ends_at_format, it)) } },
                resolution?.let { { MetaBadge(it) } },
            )
        if (metaSegments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Row(
                // Rating badge + a long meta line can add up to wider than a narrow phone screen;
                // a plain Row clips the tail instead of wrapping. Scroll rather than clip.
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                metaSegments.forEachIndexed { index, segment ->
                    if (index > 0) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        )
                    }
                    segment()
                }
            }
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        // Play / Resume button
        val hasResume = resumePositionMs > 0L
        if (hasResume) {
            val resumeTimeText = formatTime(resumePositionMs)
            CinemaButton(
                onClick = {
                    onPlayMovie(movieId, movieDetail.name, extension, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.movie_resume_from_format, resumeTimeText))
            }
        } else {
            CinemaButton(
                onClick = {
                    onPlayMovie(movieId, movieDetail.name, extension, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.movie_play_action))
            }
        }

        // Secondary actions row — only actions the app actually supports (no Cast/Shuffle).
        Spacer(modifier = Modifier.height(CinemaSpacing.md))
        val trailerContext = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DetailIconAction(
                icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                label = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                onClick = onToggleFavorite,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
            )
            DetailIconAction(
                icon = if (isWatched) CinemaIcons.CheckCircle else CinemaIcons.RadioButtonUnchecked,
                label = stringResource(if (isWatched) R.string.watched_unmark else R.string.watched_mark),
                onClick = onToggleWatched,
                tint = if (isWatched) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
            )
            if (hasResume) {
                DetailIconAction(
                    icon = CinemaIcons.Replay,
                    label = stringResource(R.string.movie_start_beginning),
                    onClick = { onPlayMovie(movieId, movieDetail.name, extension, true) },
                )
            }
            movieDetail.metadata.trailerUrl?.let { trailer ->
                DetailIconAction(
                    icon = CinemaIcons.Movie,
                    label = stringResource(R.string.details_watch_trailer),
                    onClick = { openExternalUrl(trailerContext, trailer) },
                )
            }
        }

        // Segmented detail sections (docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md,
        // Phase 4 3c) — mirrors TV's own tabbed layout (docs/plans/20260902_tv-detail-hero-ui-plan.md
        // Phase 4): built from what this movie actually has, not a fixed list, so a title with no
        // cast/related-titles/alternate-instances doesn't show an empty tab for it.
        val hasCast = !movieDetail.metadata.cast.isNullOrBlank()
        val hasMoreLikeThis = relatedTitles.moreLikeThis.isNotEmpty() || relatedTitles.collection.isNotEmpty()
        val hasVersions = alternateStreams.isNotEmpty()
        val tabs =
            remember(hasCast, hasMoreLikeThis, hasVersions) {
                buildList {
                    add(MovieDetailTab.OVERVIEW)
                    if (hasCast) add(MovieDetailTab.CAST)
                    if (hasMoreLikeThis) add(MovieDetailTab.MORE_LIKE_THIS)
                    if (hasVersions) add(MovieDetailTab.VERSIONS)
                }
            }
        var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
        val safeTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)

        Spacer(modifier = Modifier.height(CinemaSpacing.lg))
        PrimaryTabRow(selectedTabIndex = safeTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == safeTabIndex,
                    onClick = { selectedTabIndex = index },
                    text = { Text(movieDetailTabLabel(tab)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(CinemaSpacing.md))
        when (tabs.getOrNull(safeTabIndex)) {
            MovieDetailTab.OVERVIEW ->
                MovieOverviewTabContent(
                    movieDetail = movieDetail,
                    categoryName = categoryName,
                    onCategorySelected = onCategorySelected,
                )
            MovieDetailTab.CAST -> CastChipsTabContent(cast = movieDetail.metadata.cast.orEmpty())
            MovieDetailTab.MORE_LIKE_THIS ->
                Column {
                    if (relatedTitles.collection.isNotEmpty()) {
                        RelatedTitlesRow(
                            title = relatedTitles.collectionName ?: stringResource(R.string.details_collection_fallback),
                            items = relatedTitles.collection,
                            onItemClick = onRelatedTitleSelected,
                        )
                    }
                    if (relatedTitles.moreLikeThis.isNotEmpty()) {
                        RelatedTitlesRow(
                            title = stringResource(R.string.details_more_like_this),
                            items = relatedTitles.moreLikeThis,
                            onItemClick = onRelatedTitleSelected,
                            modifier =
                                if (relatedTitles.collection.isNotEmpty()) Modifier.padding(top = CinemaSpacing.lg) else Modifier,
                        )
                    }
                }
            MovieDetailTab.VERSIONS ->
                // The catalogue's raw name, not movieDetail.name — some providers' detail API
                // returns a cleaned-up name inconsistent with the raw name alternates are listed
                // under, so use the same source as alternates to keep the picker consistent.
                StreamNamePicker(currentName = movieName, alternates = alternateStreams, onSelect = onAlternateStreamSelected)
            null -> Unit
        }

        }
        }
    }
    }
}

/** Section tabs built from what a movie actually has — [OVERVIEW] is the only one always present. */
private enum class MovieDetailTab { OVERVIEW, CAST, MORE_LIKE_THIS, VERSIONS }

@Composable
private fun movieDetailTabLabel(tab: MovieDetailTab): String =
    when (tab) {
        MovieDetailTab.OVERVIEW -> stringResource(R.string.details_tab_overview)
        MovieDetailTab.CAST -> stringResource(R.string.details_tab_cast)
        MovieDetailTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
        MovieDetailTab.VERSIONS -> stringResource(R.string.details_tab_versions)
    }

/**
 * Overview tab: plot, release date, director, technical stream info, then the TMDB id and the
 * category button — everything that was diagnostics/context rather than headline facts on the old
 * flat layout. Cast lives in its own tab now (see [CastChipsTabContent]); alternate stream
 * instances live in the Versions tab.
 */
@Composable
private fun MovieOverviewTabContent(
    movieDetail: MovieDetail,
    categoryName: String?,
    onCategorySelected: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        movieDetail.metadata.plot?.let { plot ->
            Text(text = plot, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(CinemaSpacing.md))
        }

        movieDetail.metadata.releaseDate?.let { releaseDate ->
            Text(
                text = stringResource(R.string.movie_released_format, releaseDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        }

        movieDetail.metadata.director?.let { director ->
            Text(
                text = stringResource(R.string.movie_director_format, director),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        }

        // Technical stream info (labeled rows)
        val hasVideoInfo =
            movieDetail.videoInfo != null &&
                (movieDetail.videoInfo!!.width != null || movieDetail.videoInfo!!.codecName != null)
        if (hasVideoInfo ||
            movieDetail.audioTracks.isNotEmpty() ||
            movieDetail.subtitleTracks.isNotEmpty() ||
            movieDetail.extension != null
        ) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            movieDetail.videoInfo?.let { video ->
                val videoText =
                    video.displayTitle ?: run {
                        val parts = mutableListOf<String>()
                        video.width?.let { w ->
                            video.height?.let { h ->
                                parts.add(resolutionLabel(w, h))
                            }
                        }
                        video.codecName?.let { codec -> parts.add(codec.uppercase()) }
                        video.videoRange?.let { range -> parts.add(range) }
                        video.width?.let { w ->
                            video.height?.let { h ->
                                parts.add("$w×$h")
                            }
                        }
                        parts.joinToString(" · ")
                    }
                if (videoText.isNotBlank()) {
                    MobileTechInfoRow(label = stringResource(R.string.tech_video_label), value = videoText)
                }
            }
            if (movieDetail.audioTracks.isNotEmpty()) {
                val audioTexts =
                    movieDetail.audioTracks.mapNotNull { audio ->
                        val text =
                            audio.displayTitle ?: run {
                                val parts = mutableListOf<String>()
                                audio.language?.let { lang -> if (lang.isNotBlank()) parts.add(lang) }
                                audio.codecName?.let { codec -> parts.add(codec.uppercase()) }
                                audio.channels?.let { ch -> parts.add(channelLabel(ch)) }
                                if (audio.isDefault) parts.add(stringResource(R.string.tech_default_label))
                                parts.joinToString(" · ")
                            }
                        text.ifBlank { null }
                    }
                if (audioTexts.isNotEmpty()) {
                    MobileTechInfoRow(label = stringResource(R.string.tech_audio_label), value = audioTexts.joinToString("\n"))
                }
            }
            if (movieDetail.subtitleTracks.isNotEmpty()) {
                val subTexts =
                    movieDetail.subtitleTracks.mapNotNull { sub ->
                        val text =
                            sub.displayTitle ?: run {
                                val parts = mutableListOf<String>()
                                sub.language?.let { lang -> if (lang.isNotBlank()) parts.add(lang) }
                                sub.codecName?.let { codec -> parts.add(codec.uppercase()) }
                                if (sub.isDefault) parts.add(stringResource(R.string.tech_default_label))
                                parts.joinToString(" · ")
                            }
                        text.ifBlank { null }
                    }
                if (subTexts.isNotEmpty()) {
                    MobileTechInfoRow(label = stringResource(R.string.tech_subtitle_label), value = subTexts.joinToString("\n"))
                }
            }
            movieDetail.extension?.let { ext ->
                MobileTechInfoRow(label = stringResource(R.string.tech_container_label), value = ext.uppercase())
            }
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.sm))

        Text(
            text = stringResource(R.string.details_tmdb_format, movieDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
        )

        if (categoryName != null) {
            Spacer(modifier = Modifier.height(CinemaSpacing.lg))
            CinemaOutlinedButton(
                onClick = onCategorySelected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.details_category_format, categoryName))
            }
        }
    }
}

/** Cast tab: one comma-string split into plain chips — no data behind a real cast/crew model yet. */
@Composable
private fun CastChipsTabContent(cast: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
    ) {
        cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { member ->
            CinemaBadge(text = member, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.movie_loading_details))
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            modifier = Modifier.padding(CinemaSpacing.xl),
        ) {
            Text(
                text = stringResource(R.string.movie_error_loading),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
            CinemaButton(onClick = onBack) {
                Text(stringResource(R.string.common_back))
            }
        }
    }
}

/**
 * The "Stream name: X" row. Plain text when [alternates] is empty — most titles have no other
 * cached instance. Becomes a tappable dropdown once there is at least one other local catalogue
 * entry sharing the same TMDB id, letting the user switch to that instance's detail screen.
 */
@Composable
private fun StreamNamePicker(
    currentName: String,
    alternates: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
) {
    val textStyle = MaterialTheme.typography.bodySmall
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium)

    if (alternates.isEmpty()) {
        Text(
            text = stringResource(R.string.details_stream_name_format, currentName),
            style = textStyle,
            color = textColor,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    // The row often sits near the bottom of the visible screen, past the last text before the
    // category button; a Popup can't render below the screen edge, so without this the menu
    // flips far above the row to find room instead of opening flush beneath it.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.clickable {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    expanded = true
                },
        ) {
            Text(
                text = stringResource(R.string.details_stream_name_format, currentName),
                style = textStyle,
                color = textColor,
            )
            Icon(
                imageVector = CinemaIcons.ArrowDropDown,
                contentDescription = stringResource(R.string.details_other_instances),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(currentName, color = MaterialTheme.colorScheme.primary) },
                leadingIcon = { Icon(CinemaIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { expanded = false },
            )
            alternates.forEach { alternate ->
                DropdownMenuItem(
                    text = { Text(alternate.name) },
                    onClick = {
                        expanded = false
                        onSelect(alternate)
                    },
                )
            }
        }
    }
}

@Composable
private fun MobileTechInfoRow(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
        )
    }
}

