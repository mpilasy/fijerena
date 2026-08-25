package org.njarasoa.fijerena.feature.movie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.model.channelLabel
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.resolutionLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions
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
                    MovieDetailsViewModelFactory(context.applicationContext, movieId, categoryId)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()
    val isFavorite = (uiState as? MovieDetailsViewModel.UiState.Success)?.isFavorite ?: false

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
                actions = {
                    CinemaIconButton(onClick = { viewModel.toggleFavorite(movieName) },
                        icon = {
                            Icon(
                                imageVector = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                contentDescription = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add),
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
                            )
                        }
                    )
                },
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
                            movieId = movieId,
                            movieName = movieName,
                            resumePositionMs = shown.resumePositionMs,
                            resumeDurationMs = shown.resumeDurationMs,
                            categoryName = shown.categoryName,
                            onPlayMovie = onPlayMovie,
                            onCategorySelected = { onCategorySelected(categoryId) },
                            onRelatedTitleSelected = onRelatedTitleSelected,
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
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
) {
    val extension = movieDetail.extension ?: "mp4"
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
    AmbientBackdrop(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = scrollState.value * 0.3f },
        imageUrl = movieDetail.coverUrl,
    )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(CinemaSpacing.md),
    ) {
        // Cover image
        CinemaThumbnail(
            url = movieDetail.coverUrl,
            fallbackLetter = movieDetail.name.firstOrNull(),
            contentType = ThumbnailContentType.MOVIE,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(MobileDimensions.posterHeightLarge),
        )

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Title — TMDB's clean title once it resolves, the provider's raw stream name until then
        Text(
            text = tmdbTitle ?: movieDetail.name,
            style = MaterialTheme.typography.headlineLarge,
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

        // Rating and duration on same row
        val hasRating = movieDetail.metadata.rating != null
        val hasContentRating = movieDetail.metadata.contentRating != null
        val hasDuration = movieDetail.metadata.duration != null
        if (hasRating || hasContentRating || hasDuration) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                movieDetail.metadata.contentRating?.let { contentRating ->
                    Text(
                        text = contentRating,
                        style = MaterialTheme.typography.labelLarge,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                    RoundedCornerShape(CinemaCornerRadius.small),
                                ).padding(horizontal = CinemaSpacing.sm, vertical = CinemaSpacing.xs),
                    )
                }
                movieDetail.metadata.rating?.let { rating ->
                    Text(
                        text = "★ ${formatRating(rating)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                movieDetail.metadata.duration?.let { duration ->
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
                // "Ends at" based on remaining duration
                val endsAtContext = LocalContext.current
                val endsAtText =
                    remember(movieDetail.metadata.duration, resumePositionMs) {
                        computeEndsAt(endsAtContext, movieDetail.metadata.duration, resumePositionMs)
                    }
                if (endsAtText != null) {
                    Text(
                        text = stringResource(R.string.movie_ends_at_format, endsAtText),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        // Play / Resume buttons
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
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaOutlinedButton(
                onClick = {
                    onPlayMovie(movieId, movieDetail.name, extension, true)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.movie_start_beginning))
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

        movieDetail.metadata.trailerUrl?.let { trailer ->
            val trailerContext = LocalContext.current
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaOutlinedButton(
                onClick = { openExternalUrl(trailerContext, trailer) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.details_watch_trailer))
            }
        }

        // Plot/Description
        movieDetail.metadata.plot?.let { plot ->
            Spacer(modifier = Modifier.height(CinemaSpacing.lg))
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(modifier = Modifier.height(CinemaSpacing.md))

        // Release date
        movieDetail.metadata.releaseDate?.let { releaseDate ->
            Text(
                text = stringResource(R.string.movie_released_format, releaseDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        }

        // Cast
        movieDetail.metadata.cast?.let { cast ->
            Text(
                text = stringResource(R.string.movie_cast_format, cast),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.overlayMedium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
        }

        // Director
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

        // The provider's own (often raw) stream name, now that the headline above is TMDB's title.
        // A dropdown when the local catalogue holds other instances of the same TMDB title.
        Spacer(modifier = Modifier.height(CinemaSpacing.md))
        StreamNamePicker(
            currentName = movieDetail.name,
            alternates = alternateStreams,
            onSelect = onRelatedTitleSelected,
        )

        // Category this movie belongs to — tap to browse it
        if (categoryName != null) {
            Spacer(modifier = Modifier.height(CinemaSpacing.lg))
            CinemaOutlinedButton(
                onClick = onCategorySelected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.details_category_format, categoryName))
            }
        }

        // Last thing on the screen, below the technical rows and the category: the rows are a
        // place to go next, so they sit after everything about this title.
        RelatedTitlesRow(
            title = stringResource(R.string.details_more_like_this),
            items = relatedTitles.recommended,
            onItemClick = onRelatedTitleSelected,
            modifier = Modifier.padding(top = CinemaSpacing.lg),
        )
        RelatedTitlesRow(
            title = stringResource(R.string.details_similar_titles),
            items = relatedTitles.similar,
            onItemClick = onRelatedTitleSelected,
            modifier = Modifier.padding(top = CinemaSpacing.lg),
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
                tint = textColor,
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

