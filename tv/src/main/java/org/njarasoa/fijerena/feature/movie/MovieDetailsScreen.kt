@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.movie

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.resolutionLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.MovieDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity

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
                    MovieDetailsViewModelFactory(context.applicationContext, movieId, categoryId, movieName)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()

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
                    tmdbTitle = tmdbTitle,
                    alternateStreams = alternateStreams,
                    movieId = state.movieDetail.id,
                    movieName = state.streamName,
                    isFavorite = state.isFavorite,
                    isWatched = state.isWatched,
                    resumePositionMs = state.resumePositionMs,
                    resumeDurationMs = state.resumeDurationMs,
                    categoryName = state.categoryName,
                    onPlayMovie = onPlayMovie,
                    onCategorySelected = { onCategorySelected(state.categoryId) },
                    onToggleFavorite = { viewModel.toggleFavorite(state.streamName) },
                    onToggleWatched = { viewModel.toggleWatched() },
                    onRefresh = { viewModel.refreshMovieInfo() },
                    onBack = onBack,
                    onRelatedTitleSelected = onRelatedTitleSelected,
                    onAlternateStreamSelected = { viewModel.switchToAlternateStream(it) },
                )
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
    isFavorite: Boolean,
    isWatched: Boolean,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    categoryName: String?,
    onPlayMovie: (movieId: String, movieName: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatched: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
) {
    // Fallback for any state where the LazyColumn's onPreviewKeyEvent below isn't in the tree
    // yet (e.g. very first composition) — the real fix, and the one actually exercised in
    // practice, is that onPreviewKeyEvent.
    BackHandler {
        onBack()
    }

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
    // Focus requester for the stream name row, so switching to an alternate stream can keep
    // focus there instead of it falling back to the window root (see streamSwitchSignal below).
    val streamNameFocusRequester = remember { FocusRequester() }
    // True once the row actually reports itself focused — lets the reassertion loop below stop
    // as soon as it has actually won, instead of guessing how many frames that takes.
    var streamRowFocused by remember { mutableStateOf(false) }
    // Bumped in onSelect, independent of resumePositionMs: selecting a dropdown item destroys
    // that focused node, and Compose has nothing left to restore to, so focus falls to the
    // window root and D-pad input goes nowhere until this claims it back for the row. Also
    // doubles as "a switch has happened this screen instance" — see the resumePositionMs effect
    // below, which reads it as a sticky flag, not a one-shot: a single-shot flag consumed by the
    // first post-switch resumePositionMs change would leave a later one (e.g. a slow network
    // fetch resolving after a fast cache draw) free to steal focus back to Play. See the same
    // fix on EpisodeSelectionScreen's stream picker for the case that actually double-fires.
    var streamSwitchSignal by remember { mutableStateOf(0) }

    // Request focus on Play/Resume button when screen loads or resume data arrives — unless the
    // user has switched to an alternate stream at some point on this screen, in which case focus
    // stays on the stream name row so the D-pad doesn't silently land on Play.
    LaunchedEffect(resumePositionMs) {
        if (streamSwitchSignal == 0) {
            try {
                playButtonFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }

    LaunchedEffect(streamSwitchSignal) {
        if (streamSwitchSignal == 0) return@LaunchedEffect
        // The dropdown's own dismissal falls back to focusing the window root, asynchronously,
        // on its own timeline that can outlast a fixed number of frames on real hardware (a
        // guessed frame count is what left focus dead on a real Shield after this looked fixed
        // in testing) — poll instead, and stop the instant the row actually reports focused.
        var attempts = 0
        while (!streamRowFocused && attempts < 90) {
            try {
                streamNameFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
            withFrameNanos { }
            attempts++
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Lazy, not Column(verticalScroll): a scrolling Column measures every child, so the Similar
    // Titles row paid its full layout cost while sitting entirely off-screen — 85ms of a 215ms
    // measure pass on every rebuild of this screen, which is why backing out of the player was
    // slow. EpisodeSelectionScreen already builds these same rows as LazyColumn items.
    LazyColumn(
        // Confirmed on a real Shield (logcat): the first Back press while a focused TV Button
        // has focus reaches Compose's key dispatch fine (a non-consuming onPreviewKeyEvent here
        // logs it), but something between here and the BackHandler/OnBackPressedDispatcher
        // bridge marks it handled — BackHandler never fires on that first press, only the
        // second. Rather than chase the exact consumer, intercept here instead: onPreviewKeyEvent
        // runs top-down, before any descendant (including the focused Button) gets a look, so
        // this always wins the race. Matches the same pattern TvDpadEscape.kt uses for the same
        // class of problem.
        modifier = Modifier.fillMaxSize().focusable().onPreviewKeyEvent { event ->
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
        // Header with back button
        item(key = "header") {
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
                    // TMDB's clean title once it resolves, the provider's raw stream name until then
                    Text(
                        text = tmdbTitle ?: movieDetail.name.ifEmpty { movieName },
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
                    // Watched button (Phase 6, docs/plans/watch-state-durable-storage-plan.md)
                    CinemaIconButton(
                        onClick = onToggleWatched,
                        icon = {
                            Icon(
                                imageVector = if (isWatched) CinemaIcons.CheckCircle else CinemaIcons.RadioButtonUnchecked,
                                contentDescription = if (isWatched) stringResource(R.string.watched_unmark) else stringResource(R.string.watched_mark),
                                tint = if (isWatched) CinemaAccent else CinemaTextPrimary,
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
        }

        item(key = "content") {
        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))
        }

        // Movie content: poster + metadata
        item(key = "detail") {
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
                        movieDetail.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { duration ->
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

                    // The provider's own (often raw) stream name, now that the headline is TMDB's
                    // title. A dropdown when the local catalogue holds other instances of the same
                    // TMDB title.
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                    StreamNamePicker(
                        // The catalogue's raw name, not movieDetail.name — some providers'
                        // detail API returns a cleaned-up name inconsistent with the raw name
                        // alternates are listed under, so use the same source as alternates.
                        currentName = movieName,
                        alternates = alternateStreams,
                        onSelect = {
                            streamSwitchSignal++
                            onAlternateStreamSelected(it)
                        },
                        textStyle = scaledStyles.bodySmall,
                        focusRequester = streamNameFocusRequester,
                        onFocusedChanged = { streamRowFocused = it },
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text = stringResource(R.string.details_tmdb_format, movieDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none)),
                        style = scaledStyles.bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )

                    // Category this movie belongs to — OK opens its stream list
                    if (categoryName != null) {
                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                        CinemaSecondaryButton(
                            onClick = onCategorySelected,
                            text = stringResource(R.string.details_category_format, categoryName),
                        )
                    }

                } // GlassPanel Column
            } // GlassPanel
        } // Outer Row (poster + metadata)
        }

        // Hoisted out of the GlassPanel above so they can be items in their own right, and so the
        // one that is off-screen is never composed or measured until it is scrolled to.
        if (relatedTitles.recommended.isNotEmpty()) {
            item(key = "related-recommended") {
                RelatedTitlesRow(
                    title = stringResource(R.string.details_more_like_this),
                    items = relatedTitles.recommended,
                    onItemClick = onRelatedTitleSelected,
                    modifier = Modifier.padding(top = Spacing.lg.scaled(scale)),
                )
            }
        }
        if (relatedTitles.similar.isNotEmpty()) {
            item(key = "related-similar") {
                RelatedTitlesRow(
                    title = stringResource(R.string.details_similar_titles),
                    items = relatedTitles.similar,
                    onItemClick = onRelatedTitleSelected,
                    modifier = Modifier.padding(top = Spacing.lg.scaled(scale)),
                )
            }
        }
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
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    onFocusedChanged: (Boolean) -> Unit = {},
) {
    val textColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)

    if (alternates.isEmpty()) {
        Text(
            text = stringResource(R.string.details_stream_name_format, currentName),
            style = textStyle,
            color = textColor,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    // The row often sits near the bottom of the visible screen; a Popup can't render below the
    // screen edge, so without this the menu flips far above the row to find room instead of
    // opening flush beneath it.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .background(
                        color = if (isFocused) CinemaAccent.copy(alpha = CinemaAlpha.tint) else Color.Transparent,
                        shape = RoundedCornerShape(CornerRadius.medium),
                    )
                    .then(
                        if (isFocused) {
                            Modifier.border(
                                width = TvFocusTokens.focusBorderWidth,
                                color = CinemaAccentLight,
                                shape = RoundedCornerShape(CornerRadius.medium),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusedChanged(it.isFocused)
                    }
                    .clickable {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        expanded = true
                    }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
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
            // The menu is a Popup, so it gets its own window and loses the scaled density.
            ProvideUiScaledDensity {
                Column {
                    DropdownMenuItem(
                        text = { Text(currentName, color = CinemaAccent) },
                        leadingIcon = { Icon(CinemaIcons.CheckCircle, contentDescription = null, tint = CinemaAccent) },
                        onClick = { expanded = false },
                    )
                    alternates.forEach { alternate ->
                        DropdownMenuItem(
                            text = { Text(alternate.name, color = CinemaTextPrimary) },
                            onClick = {
                                expanded = false
                                onSelect(alternate)
                            },
                        )
                    }
                }
            }
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


