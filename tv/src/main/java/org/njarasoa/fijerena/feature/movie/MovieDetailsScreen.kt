@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.movie

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaItem
import kotlinx.coroutines.launch
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
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * Movie details screen for VOD content.
 *
 * Features:
 * - Displays movie information (title, plot, cast, genre, rating, duration)
 * - Large Play button
 * - D-pad friendly navigation
 * - Loads movie data from MediaRepository
 */
@Composable
fun MovieDetailsScreen(
    movieId: String,
    movieName: String,
    categoryId: String,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }
    val viewModel: MovieDetailsViewModel =
        viewModel(
            factory =
                remember(movieId, categoryId) {
                    MovieDetailsViewModelFactory(context.applicationContext, movieId, categoryId)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        when (val state = uiState) {
            is MovieDetailsViewModel.UiState.Loading -> {
                LoadingScreen()
            }
            is MovieDetailsViewModel.UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onBack = onBack,
                )
            }
            is MovieDetailsViewModel.UiState.Success -> {
                MovieDetailsContent(
                    movieDetail = state.movieDetail,
                    relatedTitles = relatedTitles,
                    movieId = movieId,
                    movieName = movieName,
                    isFavorite = state.isFavorite,
                    resumePositionMs = state.resumePositionMs,
                    resumeDurationMs = state.resumeDurationMs,
                    categoryName = state.categoryName,
                    onPlayMovie = onPlayMovie,
                    onCategorySelected = { onCategorySelected(categoryId) },
                    onToggleFavorite = { viewModel.toggleFavorite(state.movieDetail.name.ifEmpty { movieName }) },
                    onRefresh = { viewModel.refreshMovieInfo() },
                    onBack = onBack,
                    onRelatedTitleSelected = onRelatedTitleSelected,
                )
            }
        }
    }
}

@Composable
private fun MovieDetailsContent(
    movieDetail: MovieDetail,
    relatedTitles: RelatedTitles,
    movieId: String,
    movieName: String,
    isFavorite: Boolean,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    categoryName: String?,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val extension = movieDetail.extension ?: "mp4"
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val displaySmall = typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val bodyLarge = typography.bodyLarge.copy(fontSize = typography.bodyLarge.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    // Focus requester for Play button
    val playButtonFocusRequester = remember { FocusRequester() }

    // Request focus on Play/Resume button when screen loads or resume data arrives
    LaunchedEffect(resumePositionMs) {
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    val refreshScope = rememberCoroutineScope()

    // Track refresh state for animation
    var isRefreshing by remember { mutableStateOf(false) }
    var targetRotation by remember { mutableStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (isRefreshing) {
                targetRotation = (targetRotation + 360f) % 3600f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

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
                .focusable()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                ) {
                    Text(
                        text = movieDetail.name.ifEmpty { movieName },
                        style = scaledStyles.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Favorite button
                    CinemaIconButton(
                        onClick = onToggleFavorite,
                        icon = {
                            Icon(
                                imageVector = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                contentDescription = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add),
                                tint = if (isFavorite) CinemaAccent else CinemaTextPrimary,
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        }
                    )
                    // Refresh button
                    CinemaIconButton(
                        onClick = {
                            refreshScope.launch {
                                isRefreshing = true
                                onRefresh()
                                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
                                isRefreshing = false
                            }
                        },
                        enabled = !isRefreshing,
                        icon = {
                            Icon(
                                imageVector = CinemaIcons.Refresh,
                                contentDescription = stringResource(R.string.movie_refresh_info),
                                modifier =
                                    Modifier
                                        .size(TvDimensions.iconSmall.scaled(scale))
                                        .rotate(rotation),
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
            Text(
                text = providerName,
                style = scaledStyles.titleSmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

        // Movie content: poster + metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale)),
        ) {
            // Cover image
            CinemaThumbnail(
                url = movieDetail.coverUrl,
                fallbackLetter = movieDetail.name.firstOrNull(),
                contentType = ThumbnailContentType.MOVIE,
                modifier =
                    Modifier
                        .width(TvDimensions.posterWidth.scaled(scale))
                        .height(TvDimensions.posterHeightLarge.scaled(scale)),
            )

            // Metadata in glass panel
            GlassPanel(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(Spacing.lg.scaled(scale))) {
                    // Metadata header row: rating | year | duration | ends at
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        movieDetail.metadata.contentRating?.let { contentRating ->
                            Text(
                                text = contentRating,
                                style = scaledStyles.titleMedium,
                                color = CinemaTextSecondary,
                                modifier =
                                    Modifier
                                        .background(
                                            CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                                            RoundedCornerShape(CornerRadius.small),
                                        ).padding(horizontal = Spacing.sm.scaled(scale), vertical = Spacing.xs.scaled(scale)),
                            )
                        }
                        movieDetail.metadata.rating?.let { rating ->
                            Text(
                                text = "★ ${formatRating(rating)}",
                                style = scaledStyles.titleMedium,
                                color = CinemaAccent,
                            )
                        }
                        movieDetail.metadata.year?.let { year ->
                            Text(
                                text = "$year",
                                style = scaledStyles.titleMedium,
                                color = CinemaTextSecondary,
                            )
                        }
                        movieDetail.metadata.duration?.let { duration ->
                            Text(
                                text = formatDuration(duration),
                                style = scaledStyles.titleMedium,
                                color = CinemaTextSecondary,
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
                                style = scaledStyles.titleMedium,
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium),
                            )
                        }
                    }

                    // Genre tags
                    movieDetail.metadata.genre?.let { genre ->
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = genre,
                            style = scaledStyles.bodyMedium,
                            color = CinemaAccent,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

                    // Play / Resume buttons
                    val hasResume = resumePositionMs > 0L
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasResume) {
                            val resumeTimeText = formatTime(resumePositionMs)
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false)
                                },
                                text = stringResource(R.string.movie_resume_from_format, resumeTimeText),
                                modifier =
                                    Modifier
                                        .focusRequester(playButtonFocusRequester),
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, true)
                                },
                                text = stringResource(R.string.movie_start_beginning),
                            )
                        } else {
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlayMovie(movieId, movieDetail.name.ifEmpty { movieName }, extension, false)
                                },
                                text = stringResource(R.string.movie_play_action),
                                modifier =
                                    Modifier
                                        .focusRequester(playButtonFocusRequester),
                            )
                        }
                        movieDetail.metadata.trailerUrl?.let { trailer ->
                            val trailerContext = LocalContext.current
                            CinemaSecondaryButton(
                                onClick = { openExternalUrl(trailerContext, trailer) },
                                text = stringResource(R.string.details_watch_trailer),
                            )
                        }
                    }

                    // Plot/Description
                    movieDetail.metadata.plot?.let { plot ->
                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                        Text(
                            text = plot,
                            style = scaledStyles.bodyLarge,
                            color = CinemaTextPrimary,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Cast, Director
                    movieDetail.metadata.cast?.let { cast ->
                        Text(
                            text = stringResource(R.string.movie_cast_format, cast),
                            style = scaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    }
                    movieDetail.metadata.director?.let { director ->
                        Text(
                            text = stringResource(R.string.movie_director_format, director),
                            style = scaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    }
                    movieDetail.metadata.tmdbId?.let { tmdbId ->
                        Text(
                            text = stringResource(R.string.details_tmdb_format, tmdbId),
                            style = scaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    }

                    // Technical stream info (Jellyfin-style labeled rows)
                    val hasVideoInfo =
                        movieDetail.videoInfo != null &&
                            (movieDetail.videoInfo!!.width != null || movieDetail.videoInfo!!.codecName != null)
                    if (hasVideoInfo ||
                        movieDetail.audioTracks.isNotEmpty() ||
                        movieDetail.subtitleTracks.isNotEmpty() ||
                        movieDetail.extension != null
                    ) {
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
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
                                TechInfoRow(label = stringResource(R.string.tech_video_label), value = videoText)
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
                                            audio.channels?.let { ch ->
                                                parts.add(
                                                    channelLabel(
                                                        ch,
                                                        mono = context.getString(R.string.audio_channel_mono),
                                                        stereo = context.getString(R.string.audio_channel_stereo),
                                                        surround51 = context.getString(R.string.audio_channel_5_1),
                                                        surround71 = context.getString(R.string.audio_channel_7_1),
                                                        custom = { context.getString(R.string.audio_channel_custom, it) },
                                                    ),
                                                )
                                            }
                                            if (audio.isDefault) parts.add(stringResource(R.string.tech_default_label))
                                            parts.joinToString(" · ")
                                        }
                                    text.ifBlank { null }
                                }
                            if (audioTexts.isNotEmpty()) {
                                TechInfoRow(label = stringResource(R.string.tech_audio_label), value = audioTexts.joinToString("\n"))
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
                                TechInfoRow(label = stringResource(R.string.tech_subtitle_label), value = subTexts.joinToString("\n"))
                            }
                        }
                        movieDetail.extension?.let { ext ->
                            TechInfoRow(label = stringResource(R.string.tech_container_label), value = ext.uppercase())
                        }
                    }

                    // Category this movie belongs to — OK opens its stream list
                    if (categoryName != null) {
                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                        CinemaSecondaryButton(
                            onClick = onCategorySelected,
                            text = stringResource(R.string.details_category_format, categoryName),
                        )
                    }

                    // Last thing on the screen, below the technical rows and the category: the
                    // rows are a place to go next, so they sit after everything about this title.
                    RelatedTitlesRow(
                        title = stringResource(R.string.details_more_like_this),
                        items = relatedTitles.recommended,
                        onItemClick = onRelatedTitleSelected,
                        modifier = Modifier.padding(top = Spacing.lg.scaled(scale)),
                    )
                    RelatedTitlesRow(
                        title = stringResource(R.string.details_similar_titles),
                        items = relatedTitles.similar,
                        onItemClick = onRelatedTitleSelected,
                        modifier = Modifier.padding(top = Spacing.lg.scaled(scale)),
                    )
                } // GlassPanel Column
            } // GlassPanel
        } // Outer Row (poster + metadata)
    }
    }
}

@Composable
private fun LoadingScreen() {
    val scale = LocalUiScale.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator.scaled(scale)),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = stringResource(R.string.movie_loading_details),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize =
                            MaterialTheme.typography.titleLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
) {
    val scale = LocalUiScale.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xl.scaled(scale)),
        ) {
            Text(
                text = stringResource(R.string.movie_error_loading),
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontSize =
                            MaterialTheme.typography.displayMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaError,
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = message,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize =
                            MaterialTheme.typography.bodyLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
            CinemaSecondaryButton(
                onClick = onBack,
                text = stringResource(R.string.movie_back_to_movies),
            )
        }
    }
}

@Composable
private fun TechInfoRow(
    label: String,
    value: String,
) {
    val scale = LocalUiScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize =
                        MaterialTheme.typography.bodyMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize =
                        MaterialTheme.typography.bodyMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextPrimary,
        )
    }
}


