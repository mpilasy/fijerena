package org.njarasoa.fijerena.feature.movie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.cleanTitle
import org.njarasoa.fijerena.core.player.model.channelLabel
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.extractYear
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.resolutionLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.history.LocalShowSourceBadges
import org.njarasoa.fijerena.core.ui.history.rememberSourceName
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.trailer.rememberPlayTrailer
import org.njarasoa.fijerena.core.ui.utils.displayDate
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.core.ui.wanted.rememberWishlist
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaTabRow
import org.njarasoa.fijerena.ui.components.ExpandablePlot
import org.njarasoa.fijerena.ui.components.MetaBadge
import org.njarasoa.fijerena.ui.components.MetaLine
import org.njarasoa.fijerena.ui.components.MetaText
import org.njarasoa.fijerena.ui.components.MobileCategoryLinkRow
import org.njarasoa.fijerena.ui.components.MobileDetailHero
import org.njarasoa.fijerena.ui.components.MobileDetailRow
import org.njarasoa.fijerena.ui.components.OtherCopiesList
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.SavedCopyNote
import org.njarasoa.fijerena.ui.components.WishlistRemovePrompt
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.DetailIconAction
import org.njarasoa.fijerena.ui.components.detailBackBacking
import org.njarasoa.fijerena.ui.components.detailTopBarColors
import org.njarasoa.fijerena.ui.components.ratingOutOfTen
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean, categoryId: String) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
    // The route's source.
    providerId: Long = 0L,
) {
    val context = LocalContext.current
    val viewModel: MovieDetailsViewModel =
        viewModel(
            factory =
                remember(movieId, categoryId, providerId) {
                    MovieDetailsViewModelFactory(context.applicationContext, movieId, categoryId, movieName, providerId)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    val backdropUrl by viewModel.backdropUrl.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()
    val sourceNames by viewModel.sourceNames.collectAsStateWithLifecycle()
    val sourceName = rememberSourceName(providerId)
    val isFavorite = (uiState as? MovieDetailsViewModel.UiState.Success)?.isFavorite ?: false
    val isWatched = (uiState as? MovieDetailsViewModel.UiState.Success)?.isWatched ?: false

    // Retained across a refresh so pulling down leaves the details on screen under the spinner,
    // instead of blanking to a full-screen loading state. Mirrors the episode screen.
    var lastSuccess by remember { mutableStateOf<MovieDetailsViewModel.UiState.Success?>(null) }
    (uiState as? MovieDetailsViewModel.UiState.Success)?.let { lastSuccess = it }
    val isRefreshing = uiState is MovieDetailsViewModel.UiState.Loading && lastSuccess != null

    // The page opens on the backdrop under a clear top bar (TV visual refresh, Phase 5); the bar
    // turns solid with the title once the backdrop has scrolled up under it, or while there is no
    // page to show (loading, error).
    var heroCovered by remember { mutableStateOf(false) }
    val solidBar = heroCovered || lastSuccess == null

    Scaffold(
        topBar = {
            TopAppBar(
                colors = detailTopBarColors(solidBar),
                // The film's own title, as the episodes screen shows the show's (phone UI audit,
                // #2): TMDB's clean title once it resolves, the provider's name until then.
                title = {
                    if (solidBar) {
                        CinemaText(
                            text = tmdbTitle ?: cleanTitle(lastSuccess?.movieDetail?.name?.ifEmpty { null } ?: movieName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            role = TextRole.Title,
                            tone = TextTone.Inherit,
                        )
                    }
                },
                navigationIcon = {
                    CinemaIconButton(
                        modifier = Modifier.detailBackBacking(solidBar),
                        onClick = onBack,
                        icon = {
                            Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back), tint = CinemaTheme.colors.textPrimary)
                        },
                    )
                },
                // Favourite and Watched are in the icon row under the Play button (see
                // MovieDetailsContent), the Plex/Netflix "actions under the poster" layout, not a
                // top-bar icon cluster.
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (lastSuccess == null) paddingValues.calculateTopPadding() else CinemaSpacing.none,
                        bottom = paddingValues.calculateBottomPadding(),
                    ),
        ) {
            val state = uiState
            val shown = lastSuccess
            when {
                state is MovieDetailsViewModel.UiState.Error -> {
                    RetryWhenOnline { viewModel.loadMovieInfo() }
                    ErrorScreen(
                        message = state.message,
                        onBack = onBack,
                        otherCopies = {
                            OtherCopiesList(alternateStreams, sourceNames) {
                                if (viewModel.otherSourceOf(it) !=
                                    null
                                ) {
                                    onRelatedTitleSelected(it)
                                } else {
                                    viewModel.switchToAlternateStream(it)
                                }
                            }
                        },
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
                            onRetry = { viewModel.refreshMovieInfo() },
                            relatedTitles = relatedTitles,
                            tmdbTitle = tmdbTitle,
                            alternateStreams = alternateStreams,
                            sourceNames = sourceNames,
                            sourceName = sourceName,
                            movieId = shown.movieDetail.id,
                            movieName = shown.streamName,
                            resumePositionMs = shown.resumePositionMs,
                            resumeDurationMs = shown.resumeDurationMs,
                            categoryName = shown.categoryName,
                            logoUrl = logoUrl,
                            backdropUrl = backdropUrl,
                            onHeroCovered = { heroCovered = it },
                            isFavorite = isFavorite,
                            isWatched = isWatched,
                            onToggleFavorite = { viewModel.toggleFavorite(shown.streamName) },
                            onToggleWatched = if (viewModel.supportsWatchedMark) ({ viewModel.toggleWatched() }) else null,
                            onPlayMovie = {
                                id,
                                name,
                                extension,
                                fromStart,
                                ->
                                onPlayMovie(id, name, extension, fromStart, viewModel.currentCategoryId)
                            },
                            onCategorySelected = { onCategorySelected(shown.categoryId) },
                            onRelatedTitleSelected = onRelatedTitleSelected,
                            // A copy on another source opens on that source, as a related title does.
                            onAlternateStreamSelected = {
                                if (viewModel.otherSourceOf(it) != null) {
                                    onRelatedTitleSelected(it)
                                } else {
                                    viewModel.switchToAlternateStream(it)
                                }
                            },
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
    onRetry: () -> Unit,
    relatedTitles: RelatedTitles,
    tmdbTitle: String?,
    alternateStreams: List<MediaItem>,
    sourceNames: Map<Long, String>,
    // The source's name, a Source row in Expert mode.
    sourceName: String?,
    movieId: String,
    movieName: String,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    categoryName: String?,
    logoUrl: String?,
    backdropUrl: String?,
    onHeroCovered: (Boolean) -> Unit,
    isFavorite: Boolean,
    isWatched: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleWatched: (() -> Unit)?,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
) {
    val extension = movieDetail.extension ?: "mp4"

    Box(modifier = Modifier.fillMaxSize().background(CinemaTheme.colors.background)) {
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
            contentPadding =
                PaddingValues(
                    start = CinemaSpacing.md,
                    end = CinemaSpacing.md,
                    bottom =
                        CinemaSpacing.md + LocalDockInset.current,
                ),
        ) {
            item(key = "detail") {
                Column {
                    val movieTitleText = tmdbTitle ?: cleanTitle(movieDetail.name.ifEmpty { movieName })
                    MobileDetailHero(
                        title = movieTitleText,
                        backdropUrl = backdropUrl,
                        posterUrl = movieDetail.coverUrl,
                        logoUrl = logoUrl,
                        thumbnailContentType = ThumbnailContentType.MOVIE,
                        bleed = CinemaSpacing.md,
                        onCovered = onHeroCovered,
                    )

                    if (movieDetail.isSavedCopy) SavedCopyNote(onRetry = onRetry)

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    movieDetail.metadata.genre?.let { genre ->
                        CinemaText(
                            text = genre,
                            role = TextRole.Heading,
                            tone = TextTone.Accent,
                        )
                    }

                    // Single dot-separated facts line in TV's order ("1999 • 8.2/10 • R • 2h 16m"): content
                    // rating and resolution are small outlined badges, everything else plain text.
                    // The length is the provider's, else the one the player saw last time
                    // (phone UI audit, P4) — many providers send none.
                    val length =
                        movieDetail.metadata.duration?.takeIf(::hasMeaningfulDuration)
                            ?: (resumeDurationMs / 1000).takeIf { it > 0 }?.toString()
                    val endsAtContext = LocalContext.current
                    val endsAtText =
                        remember(length, resumePositionMs) {
                            computeEndsAt(endsAtContext, length, resumePositionMs)
                        }
                    val year =
                        extractYear(movieDetail.metadata.year, movieDetail.metadata.releaseDate, movieDetail.name.ifBlank { movieName })
                    val resolution =
                        movieDetail.videoInfo?.let { video -> video.width?.let { w -> video.height?.let { h -> resolutionLabel(w, h) } } }
                    val metaSegments =
                        listOfNotNull<@Composable () -> Unit>(
                            year?.let { { MetaText(it.toString()) } },
                            movieDetail.metadata.rating?.let { { MetaText(ratingOutOfTen(it)) } },
                            movieDetail.metadata.contentRating?.let { { MetaBadge(it) } },
                            length?.let { { MetaText(formatDuration(it)) } },
                            endsAtText?.let { { MetaText(stringResource(R.string.movie_ends_at_format, it)) } },
                            resolution?.let { { MetaBadge(it) } },
                        )
                    if (metaSegments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        MetaLine(segments = metaSegments)
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.lg))

                    val hasResume = resumePositionMs > 0L
                    if (hasResume) {
                        val resumeTimeText = formatTime(resumePositionMs)
                        CinemaButton(
                            onClick = {
                                onPlayMovie(movieId, movieDetail.name, extension, false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CinemaText(
                                stringResource(R.string.movie_resume_from_format, resumeTimeText),
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        }
                    } else {
                        CinemaButton(
                            onClick = {
                                onPlayMovie(movieId, movieDetail.name, extension, false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CinemaText(
                                stringResource(R.string.movie_play_action),
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        }
                    }

                    // Secondary actions row — only actions the app actually supports (no Cast/Shuffle).
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    val playTrailer = rememberPlayTrailer()
                    // The Wishlist icon and its ask-once prompt (wishlist UX plan, Phase 1); none for a child.
                    val wishlistTitle = tmdbTitle ?: cleanTitle(movieDetail.name.ifEmpty { movieName })
                    val wishlist =
                        rememberWishlist(
                            contentType = ContentType.MOVIES,
                            tmdbId = movieDetail.metadata.tmdbId,
                            title = wishlistTitle,
                            year = year,
                            watched = isWatched,
                        )
                    if (wishlist?.askRemove == true) WishlistRemovePrompt(wishlistTitle, wishlist.answer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        DetailIconAction(
                            icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                            label = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                            onClick = onToggleFavorite,
                            tint = if (isFavorite) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                        )
                        if (wishlist != null) {
                            DetailIconAction(
                                icon = if (wishlist.isOn) CinemaIcons.Bookmark else CinemaIcons.BookmarkBorder,
                                label = stringResource(if (wishlist.isOn) R.string.wanted_remove else R.string.wanted_add),
                                onClick = wishlist.toggle,
                                tint = if (wishlist.isOn) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                            )
                        }
                        // None where the source keeps watched state on its server (Jellyfin).
                        if (onToggleWatched != null) {
                            DetailIconAction(
                                icon = if (isWatched) CinemaIcons.CheckCircleFilled else CinemaIcons.CheckCircleOutline,
                                label = stringResource(if (isWatched) R.string.watched_unmark else R.string.watched_mark),
                                onClick = onToggleWatched,
                                tint = if (isWatched) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                            )
                        }
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
                                onClick = { playTrailer(trailer, movieDetail.metadata.tmdbTrailerUrl) },
                            )
                        }
                    }

                    movieDetail.metadata.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                        ExpandablePlot(plot = plot, modifier = Modifier.fillMaxWidth())
                    }

                    // Segmented detail sections (the ui ux transitions flow uplift plan,
                    // Phase 4 3c) — mirrors TV's own tabbed layout (the tv detail hero ui plan
                    // Phase 4): built from what this movie actually has, not a fixed list, so a title with no
                    // cast/related-titles doesn't show an empty tab for it.
                    val hasCast = !movieDetail.metadata.cast.isNullOrBlank()
                    val hasMoreLikeThis = relatedTitles.moreLikeThis.isNotEmpty() || relatedTitles.collection.isNotEmpty()
                    val tabs =
                        remember(hasCast, hasMoreLikeThis) {
                            buildList {
                                add(MovieDetailTab.DETAILS)
                                if (hasCast) add(MovieDetailTab.CAST)
                                // Last: it loads late, so it shifts no other tab when it appears.
                                if (hasMoreLikeThis) add(MovieDetailTab.MORE_LIKE_THIS)
                            }
                        }
                    // Kept by tab, not index: a tab appearing or going never moves the selection.
                    var pickedTab by rememberSaveable { mutableStateOf<MovieDetailTab?>(null) }
                    val selectedTab = pickedTab?.takeIf { it in tabs } ?: tabs.first()

                    Spacer(modifier = Modifier.height(CinemaSpacing.lg))
                    // A single-tab strip (a title with no cast/related-titles, leaving
                    // only Details) has nothing to switch between — skip the strip entirely rather than
                    // render dead chrome for a tab the user can't leave.
                    if (tabs.size > 1) {
                        CinemaTabRow(
                            selectedIndex = tabs.indexOf(selectedTab),
                            labels = tabs.map { movieDetailTabLabel(it) },
                            onSelect = { pickedTab = tabs[it] },
                        )
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    }
                    when (selectedTab) {
                        MovieDetailTab.DETAILS -> {
                            MovieDetailsTabContent(
                                movieDetail = movieDetail,
                                movieName = movieName,
                                alternateStreams = alternateStreams,
                                sourceNames = sourceNames,
                                sourceName = sourceName,
                                onAlternateStreamSelected = onAlternateStreamSelected,
                                categoryName = categoryName,
                                onCategorySelected = onCategorySelected,
                            )
                        }

                        MovieDetailTab.CAST -> {
                            CastChipsTabContent(cast = movieDetail.metadata.cast.orEmpty())
                        }

                        MovieDetailTab.MORE_LIKE_THIS -> {
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
                                            if (relatedTitles.collection.isNotEmpty()) {
                                                Modifier.padding(
                                                    top = CinemaSpacing.lg,
                                                )
                                            } else {
                                                Modifier
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Section tabs, in display order. */
private enum class MovieDetailTab { DETAILS, CAST, MORE_LIKE_THIS }

@Composable
private fun movieDetailTabLabel(tab: MovieDetailTab): String =
    when (tab) {
        MovieDetailTab.DETAILS -> stringResource(R.string.details_tab_details)
        MovieDetailTab.CAST -> stringResource(R.string.details_tab_cast)
        MovieDetailTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
    }

/** Details tab: label / value rows, then the category link. */
@Composable
private fun MovieDetailsTabContent(
    movieDetail: MovieDetail,
    movieName: String,
    alternateStreams: List<MediaItem>,
    sourceNames: Map<Long, String>,
    sourceName: String?,
    onAlternateStreamSelected: (MediaItem) -> Unit,
    categoryName: String?,
    onCategorySelected: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Label / value rows, every value starting at the same place (phone UI audit, #2).
        Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
            movieDetail.metadata.releaseDate?.let {
                MobileDetailRow(
                    label = stringResource(R.string.details_label_released),
                    value = displayDate(it),
                )
            }
            movieDetail.metadata.director?.let { MobileDetailRow(label = stringResource(R.string.details_label_director), value = it) }
            // The catalogue's raw name: alternates are listed under it, not under movieDetail.name.
            MobileDetailRow(label = stringResource(R.string.details_label_stream_name)) {
                StreamNamePicker(
                    currentName = movieName,
                    alternates = alternateStreams,
                    sourceNames = sourceNames,
                    onSelect = onAlternateStreamSelected,
                )
            }

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
                    MobileDetailRow(label = stringResource(R.string.tech_video_label), value = videoText)
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
                    MobileDetailRow(label = stringResource(R.string.tech_audio_label), value = audioTexts.joinToString("\n"))
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
                    MobileDetailRow(label = stringResource(R.string.tech_subtitle_label), value = subTexts.joinToString("\n"))
                }
            }
            movieDetail.extension?.let { ext ->
                MobileDetailRow(label = stringResource(R.string.tech_container_label), value = ext.uppercase())
            }
            // The TMDB id is for developers only (phone UI audit, #2).
            val context = LocalContext.current
            val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
            if (isDevMode) {
                MobileDetailRow(
                    label = stringResource(R.string.details_label_tmdb),
                    value = movieDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
                )
            }
            // Expert mode only, like the source badges.
            if (LocalShowSourceBadges.current) {
                sourceName?.let {
                    MobileDetailRow(
                        label = stringResource(R.string.details_label_source),
                        value = it,
                    )
                }
            }
            categoryName?.let { MobileCategoryLinkRow(categoryName = it, onClick = onCategorySelected) }
        }
    }
}

/** Cast tab: one comma-string split into plain chips — no data behind a real cast/crew model yet. */
@Composable
internal fun CastChipsTabContent(cast: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
    ) {
        cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { member ->
            CinemaBadge(
                text = member,
                role = TextRole.Body,
            )
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
            CinemaSpinner()
            CinemaText(
                stringResource(R.string.movie_loading_details),
                role = TextRole.Body,
                tone = TextTone.Inherit,
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
    otherCopies: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            // Scrolls when the other copies run past the screen.
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(CinemaSpacing.xl),
        ) {
            CinemaText(
                text = stringResource(R.string.movie_error_loading),
                role = TextRole.Display,
                tone = TextTone.Danger,
            )
            CinemaText(
                text = message,
                role = TextRole.Body,
                tone = TextTone.Inherit,
            )
            CinemaButton(kind = ButtonKind.Other, onClick = onBack) {
                CinemaText(
                    stringResource(R.string.common_back),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
            otherCopies()
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
    sourceNames: Map<Long, String>,
    onSelect: (MediaItem) -> Unit,
) {
    // As the other rows' values (MobileDetailRow): the row already says "Stream name:".

    if (alternates.isEmpty()) {
        CinemaText(
            text = currentName,
            role = TextRole.Body,
            tone = TextTone.Primary,
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
            CinemaText(
                text = currentName,
                role = TextRole.Body,
                tone = TextTone.Primary,
            )
            Icon(
                imageVector = CinemaIcons.ArrowDropDown,
                contentDescription = stringResource(R.string.details_other_instances),
                tint = CinemaTheme.colors.accent,
                modifier = Modifier.size(MobileDimensions.iconSmall),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    CinemaText(
                        currentName,
                        role = TextRole.Body,
                        tone = TextTone.Accent,
                    )
                },
                leadingIcon = { Icon(CinemaIcons.CheckCircle, contentDescription = null, tint = CinemaTheme.colors.accent) },
                onClick = { expanded = false },
            )
            alternates.forEach { alternate ->
                DropdownMenuItem(
                    text = {
                        CinemaText(
                            alternate.name,
                            role = TextRole.Label,
                            keepCase = true,
                            tone = TextTone.Inherit,
                        )
                    },
                    // With several sources, each copy's own (it opens there).
                    leadingIcon = sourceNames[alternate.providerId]?.let { name -> { SourceBadge(name) } },
                    onClick = {
                        expanded = false
                        onSelect(alternate)
                    },
                )
            }
        }
    }
}
