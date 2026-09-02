@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.feature.category.components.tvLongPress
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.firstSeasonWithUnwatchedEpisode
import org.njarasoa.fijerena.core.player.domain.flattenedEpisodes
import org.njarasoa.fijerena.core.player.domain.resumeAnchorEpisodeId
import org.njarasoa.fijerena.core.player.domain.seasonNumberContaining
import org.njarasoa.fijerena.core.player.domain.seriesYearRange
import org.njarasoa.fijerena.core.player.domain.sortedSeasons
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.RatingBadge
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleLogoOrText
import org.njarasoa.fijerena.core.ui.components.WatchedBadge
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity

/**
 * Episode selection screen for TV shows.
 *
 * Features:
 * - Displays series information (title, plot)
 * - Lists all episodes grouped by season
 * - Inline episode detail panel when an episode is selected
 * - D-pad friendly navigation
 * - Loads episode data from MediaRepository
 */
@Composable
fun EpisodeSelectionScreen(
    seriesId: String,
    seriesName: String,
    categoryId: String,
    initialEpisodeId: String? = null,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }
    val viewModel: SeriesDetailsViewModel =
        viewModel(
            factory =
                remember(seriesId, categoryId) {
                    SeriesDetailsViewModelFactory(context.applicationContext, seriesId, categoryId, seriesName)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()

    // Retained across a background refresh so the episode list stays on screen with just a
    // spinning refresh icon, instead of the whole thing dropping to a full-screen loading state
    // and losing scroll position and the expanded season. Mirrors the mobile screen, which
    // already did this.
    var lastSuccess by remember { mutableStateOf<SeriesDetailsViewModel.UiState.Success?>(null) }
    (uiState as? SeriesDetailsViewModel.UiState.Success)?.let { lastSuccess = it }
    val isRefreshing = uiState is SeriesDetailsViewModel.UiState.Loading && lastSuccess != null

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        val state = uiState
        val shown = lastSuccess
        when {
            state is SeriesDetailsViewModel.UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onBack = onBack,
                )
            }
            shown != null -> {
                EpisodeListContent(
                    seriesDetail = shown.seriesDetail,
                    relatedTitles = relatedTitles,
                    tmdbTitle = tmdbTitle,
                    logoUrl = logoUrl,
                    alternateStreams = alternateStreams,
                    seriesName = shown.streamName,
                    categoryId = shown.categoryId,
                    mediaRepository = viewModel.mediaRepository!!,
                    initialEpisodeId = initialEpisodeId,
                    isFavorite = shown.isFavorite,
                    categoryName = shown.categoryName,
                    isRefreshing = isRefreshing,
                    onToggleFavorite = { viewModel.toggleFavorite(shown.streamName) },
                    onEpisodeSelected = onEpisodeSelected,
                    onCategorySelected = { onCategorySelected(shown.categoryId) },
                    onRefresh = { viewModel.refreshSeriesInfo() },
                    onBack = onBack,
                    onRelatedTitleSelected = onRelatedTitleSelected,
                    onAlternateStreamSelected = { viewModel.switchToAlternateStream(it) },
                )
            }
            else -> {
                LoadingScreen()
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesDetail: SeriesDetail,
    relatedTitles: RelatedTitles,
    tmdbTitle: String?,
    logoUrl: String?,
    alternateStreams: List<MediaItem>,
    seriesName: String,
    categoryId: String,
    mediaRepository: MediaRepository,
    initialEpisodeId: String? = null,
    isFavorite: Boolean,
    categoryName: String?,
    isRefreshing: Boolean,
    onToggleFavorite: () -> Unit,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val listState = rememberLazyListState()
    val scale = LocalUiScale.current
    val episodeCardStyle = episodeCardStyle()
    val watchedToggleScope = rememberCoroutineScope()
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val displaySmall = typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val headlineSmall = typography.headlineSmall.copy(fontSize = typography.headlineSmall.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val bodyLarge = typography.bodyLarge.copy(fontSize = typography.bodyLarge.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
                val labelMedium = typography.labelMedium.copy(fontSize = typography.labelMedium.fontSize.scaled(scale))
                val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
            }
        }

    // Selected episode for detail panel — only set by an explicit tap (including on the
    // Continue Watching resume episode below); arriving here never auto-opens it.
    var selectedEpisode by remember { mutableStateOf<DomainEpisodeItem?>(null) }

    // Handle back press: dismiss detail panel first, then navigate back. Two handlers, not one
    // with a branch inside — the base-list case (selectedEpisode == null) needs its own explicit
    // BackHandler too. Fallback only in practice — the real fix is the LazyColumn's
    // onPreviewKeyEvent below (see its comment); confirmed on a real Shield that this
    // BackHandler alone never fires on the first press while a focused TV Button has focus.
    BackHandler(enabled = selectedEpisode != null) {
        selectedEpisode = null
    }
    BackHandler(enabled = selectedEpisode == null) {
        onBack()
    }

    // Track refresh state for animation
    var targetRotation by remember { mutableStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    // isRefreshing is now a parameter, so the old `while (isRefreshing)` guard would never
    // observe it changing. The effect key does that job: a false value cancels the loop.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (true) {
                targetRotation = (targetRotation + 360f) % 3600f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    val sortedSeasons =
        remember(seriesDetail) {
            seriesDetail.sortedSeasons { num -> context.getString(R.string.series_season_name_format, num) }
        }

    // Pre-sort episodes per season — avoid re-sorting inside LazyColumn on every recomposition
    val sortedEpisodesBySeason =
        remember(seriesDetail) {
            seriesDetail.episodes.mapValues { (_, eps) -> eps.sortedBy { it.episodeNumber } }
        }
    val totalEpisodes =
        remember(seriesDetail) {
            seriesDetail.episodes.values.sumOf { it.size }
        }
    val hasMultipleSeasons = sortedSeasons.size > 1

    // Episode to come back to: the route's Continue Watching argument at first, then whichever
    // episode was last sent to the player from here. Saveable, because navigating to the player
    // disposes this composable — a plain remember would drop it and land the user back on
    // season 1 at the top of the list.
    var resumeEpisodeId by rememberSaveable { mutableStateOf(initialEpisodeId) }

    // Season containing the resume episode, if any — takes priority over both the
    // "first season" and "next unwatched" defaults below, so backing out of its detail panel
    // (or out of the player) lands on that season expanded, everything else collapsed.
    val resumeSeasonNumber =
        remember(seriesDetail, resumeEpisodeId) {
            resumeEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
        }

    // One season visible at a time, switched via tabs (D-pad left/right moves focus between
    // them, which selects immediately — see SeasonTab below) instead of an accordion. Resume
    // season wins on first load, same priority the accordion used to give it.
    var selectedSeasonNumber by rememberSaveable(seriesDetail.id) {
        mutableStateOf(resumeSeasonNumber ?: sortedSeasons.firstOrNull()?.seasonNumber)
    }
    // Set by a manual tab focus/click, so the auto-select effect below doesn't clobber a season
    // the user already picked while the playback-position lookup was in flight. Also seeded true
    // when a resume season is already known, so the "next unwatched" guess below never overrides
    // the season the user actually asked to resume.
    var hasManuallySelectedSeason by remember(seriesDetail.id) { mutableStateOf(resumeSeasonNumber != null) }

    val currentSeasonIndex = sortedSeasons.indexOfFirst { it.seasonNumber == selectedSeasonNumber }
    val previousSeason = if (currentSeasonIndex > 0) sortedSeasons[currentSeasonIndex - 1] else null
    val nextSeason =
        if (currentSeasonIndex in sortedSeasons.indices && currentSeasonIndex < sortedSeasons.lastIndex) {
            sortedSeasons[currentSeasonIndex + 1]
        } else {
            null
        }

    // Focus requester for primary Play / Resume button
    val playButtonFocusRequester = remember { FocusRequester() }

    // Focus requester for the stream name row, so switching to an alternate stream can keep
    // focus there instead of it falling back to the window root (see streamSwitchSignal below).
    val streamNameFocusRequester = remember { FocusRequester() }
    // True once the row actually reports itself focused — lets the reassertion loop below stop
    // as soon as it has actually won, instead of guessing how many frames that takes.
    var streamRowFocused by remember { mutableStateOf(false) }
    // Bumped in onSelect, independent of resumeEpisodeId: selecting a dropdown item destroys
    // that focused node, and Compose has nothing left to restore to, so focus falls to the
    // window root and D-pad input goes nowhere until this claims it back for the row. Also
    // doubles as "a switch has happened this screen instance" — see the resumeEpisodeId effect
    // below, which reads it as a sticky flag, not a one-shot: switchToAlternateStream reloads
    // seriesDetail from cache and then from the network, so the resume anchor can land on this
    // screen more than once for a single switch (and the network leg can take seconds, per
    // loadSeriesDetail's comment on the Law & Order case), so a single-shot flag consumed by the
    // first of those firings left the later one free to steal focus back to Play.
    var streamSwitchSignal by remember { mutableStateOf(0) }

    // D-pad focus target for the resume episode card — requested below once it's on screen, so
    // OK is immediately playable without the user having to navigate to it first.
    val resumeCardFocusRequester = remember { FocusRequester() }

    // Explicit Down target for the Category chip, the last focusable item above the season
    // tabs: Compose's default directional search from there lands on the first episode card,
    // not the tab row (the row's small height loses out to the much taller card right below
    // it in the nearest-candidate heuristic — focusGroup()/enter alone doesn't override that,
    // since the row is never chosen as a search candidate to begin with). SeasonTabs attaches
    // this to whichever tab is currently selected.
    val seasonTabsFocusRequester = remember { FocusRequester() }

    // D-pad focus target for the first episode card of whichever season is selected — landing
    // spot after a Left/Right season switch triggered from inside the episode list (see the
    // onPreviewKeyEvent below), so browsing episodes across a season boundary stays fluid instead
    // of stranding focus on a card whose key just vanished from the list.
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    // Set true by that same Left/Right switch, consumed by the LaunchedEffect(selectedSeasonNumber)
    // below. A plain "always refocus the first episode on season change" would also fire for a
    // season picked from the tab row itself, yanking focus down off the tab the user is still on —
    // this scopes the refocus to the one path that actually needs it, same signal-plus-effect shape
    // as streamSwitchSignal above.
    var seasonSwitchedFromEpisodeList by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSeasonNumber) {
        if (!seasonSwitchedFromEpisodeList) return@LaunchedEffect
        seasonSwitchedFromEpisodeList = false
        firstEpisodeFocusRequester.requestFocus()
    }

    // Scroll to the resume episode, so "highlighted" also means visible without the user having
    // to scroll to find it — but only when it's actually the reason this season is selected. A
    // manual tab focus/click must never yank the list back to the resume spot (or anywhere
    // else); it stays exactly where the user left it.
    LaunchedEffect(resumeEpisodeId) {
        val targetId = resumeEpisodeId ?: return@LaunchedEffect
        if (seriesDetail.seasonNumberContaining(targetId) != selectedSeasonNumber) return@LaunchedEffect
        val seasonEpisodes = sortedEpisodesBySeason[selectedSeasonNumber?.toString()] ?: return@LaunchedEffect
        val episodeIndex = seasonEpisodes.indexOfFirst { it.id == targetId }
        if (episodeIndex < 0) return@LaunchedEffect
        val headerItemCount = 1 + if (hasMultipleSeasons) 1 else 0
        listState.animateScrollToItem(headerItemCount + episodeIndex)
    }

    // Per-episode resume fraction, drives the progress bar on each card. Re-read on every
    // entry into this screen — including coming back from the player — so a just-watched
    // episode's bar is current.
    var episodeProgress by remember(seriesDetail) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var episodePlaybackPositions by remember(seriesDetail) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var watchedEpisodeIds by remember(seriesDetail) { mutableStateOf<Set<String>>(emptySet()) }

    // Re-reads progress/watched (including TMDB siblings) for every episode of this series.
    // Called after a manual mark, not just on initial load — a single-episode optimistic patch
    // would miss a sibling that the mark just made completed, and wouldn't restore a resume bar
    // an unmark brings back. Doesn't touch resumeEpisodeId/selectedSeasonNumber: a manual toggle
    // should not re-anchor or re-select a season out from under the user.
    suspend fun refreshEpisodeWatchState() {
        val allEpisodeIds = mutableListOf<String>()
        for (episodes in seriesDetail.episodes.values) {
            for (ep in episodes) {
                allEpisodeIds.add(ep.id)
            }
        }
        val allWatched = mediaRepository.getPlaybackPositionsSuspend(allEpisodeIds, ContentType.TV_SHOWS)
        episodeProgress =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, it) }
                }
            }
        episodePlaybackPositions =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, watched.playbackPosition) }
                }
            }
        watchedEpisodeIds =
            buildSet {
                for ((id, watched) in allWatched) {
                    if (watched.isCompleted) add(id)
                }
                addAll(mediaRepository.getSiblingCompletedEpisodeIds(seriesDetail.id))
            }
    }

    // Playback positions for every episode: the progress bars and watched checks above, plus
    // auto-expanding the season holding the next unwatched/in-progress episode.
    LaunchedEffect(seriesDetail) {
        // ⚡ Bolt: Avoid flatten().map to prevent intermediate list allocations
        val allEpisodeIds = mutableListOf<String>()
        for (episodes in seriesDetail.episodes.values) {
            for (ep in episodes) {
                allEpisodeIds.add(ep.id)
            }
        }

        val allWatched = mediaRepository.getPlaybackPositionsSuspend(allEpisodeIds, ContentType.TV_SHOWS)

        episodeProgress =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, it) }
                }
            }
        episodePlaybackPositions =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, watched.playbackPosition) }
                }
            }
        watchedEpisodeIds =
            buildSet {
                for ((id, watched) in allWatched) {
                    if (watched.isCompleted) add(id)
                }
                // TMDB dedup (Phase 5): completing one language/quality variant of an episode
                // completes them all. Checks only — never adds a resume bar or moves the anchor
                // below, same as the movies side of this.
                addAll(mediaRepository.getSiblingCompletedEpisodeIds(seriesDetail.id))
            }

        // Anchor on the episode worth watching next. The route (or a play from this screen)
        // names the episode last played; entering from the series list names none, so fall back
        // to the newest playback timestamp. Either way the anchor moves on when that episode is
        // already finished — re-evaluated on every entry, so backing out of an episode the user
        // just completed lands on the following one.
        seriesDetail
            .resumeAnchorEpisodeId(
                sortedSeasons = sortedSeasons,
                lastPlayedEpisodeId = resumeEpisodeId ?: allWatched.maxByOrNull { it.value.timestamp }?.key,
                isCompleted = { allWatched[it]?.isCompleted == true },
            )?.let { resumeEpisodeId = it }

        if (!hasMultipleSeasons) return@LaunchedEffect

        // The derived anchor's season beats the "first season with anything unwatched" guess:
        // a viewer mid-season 13 doesn't want season 1 opened because they skipped an episode.
        val targetSeason =
            resumeEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
                ?: firstSeasonWithUnwatchedEpisode(
                    sortedSeasons = sortedSeasons,
                    episodesBySeason = sortedEpisodesBySeason,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )
        if (targetSeason != null && !hasManuallySelectedSeason) {
            selectedSeasonNumber = targetSeason
        }
    }

    // Flat ordered list of all episodes across seasons (for prev/next navigation)
    val flatEpisodes =
        remember(seriesDetail, sortedSeasons) {
            seriesDetail.flattenedEpisodes(sortedSeasons)
        }

    val anchorEpisode =
        remember(flatEpisodes, resumeEpisodeId) {
            flatEpisodes.firstOrNull { it.id == resumeEpisodeId } ?: flatEpisodes.firstOrNull()
        }
    val anchorResumePosMs = anchorEpisode?.id?.let { episodePlaybackPositions[it] } ?: 0L
    val hasResume = anchorResumePosMs > 0L

    // Request focus on Play/Resume button when screen loads or anchor arrives — unless the user
    // has switched to an alternate stream at some point on this screen, in which case focus
    // stays on the stream name row so the D-pad doesn't silently land on Play.
    LaunchedEffect(resumeEpisodeId) {
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (selectedEpisode != null) {
            val current = selectedEpisode!!
            val currentIdx =
                remember(flatEpisodes, current.id) {
                    flatEpisodes.indexOfFirst { it.id == current.id }
                }
            val previousEpisode = flatEpisodes.getOrNull(currentIdx - 1)
            val nextEpisode = flatEpisodes.getOrNull(currentIdx + 1)
            // Show episode detail panel
            EpisodeDetailPanel(
                episode = current,
                seriesDetail = seriesDetail,
                seriesName = seriesName,
                categoryId = categoryId,
                providerName = providerName,
                mediaRepository = mediaRepository,
                previousEpisode = previousEpisode,
                nextEpisode = nextEpisode,
                onNavigate = { next -> selectedEpisode = next },
                onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                    resumeEpisodeId = episodeId
                    onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
                },
                onBack = { selectedEpisode = null },
            )
        } else {
            // Show series details & episode list
            LazyColumn(
                state = listState,
                contentPadding =
                    PaddingValues(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                // Confirmed on a real Shield (logcat, MovieDetailsScreen's identical bug): the
                // first Back press while a focused TV Button has focus reaches Compose's key
                // dispatch fine, but something between there and the BackHandler(selectedEpisode
                // == null) above marks it handled — that BackHandler never fires on the first
                // press, only the second. Intercept here instead: onPreviewKeyEvent runs
                // top-down, before any descendant (including the focused Button) gets a look, so
                // this always wins the race. Matches the same pattern TvDpadEscape.kt uses.
                modifier =
                    Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                        if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                            onBack()
                            true
                        } else {
                            false
                        }
                    },
            ) {
                // Hero Section: Header + Poster + GlassPanel Metadata
                item(key = "series_hero", contentType = "hero") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header with back button, title, favorite, refresh, provider name
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
                                    // TMDB's branded logo art when it has one, else TMDB's
                                    // original title falling back to the provider's own series
                                    // name (when TMDB has no match, or the lookup hasn't landed).
                                    val seriesTitleText = tmdbTitle ?: seriesDetail.name.ifEmpty { seriesName }
                                    TitleLogoOrText(
                                        contentDescription = seriesTitleText,
                                        logoUrl = logoUrl,
                                        logoHeight = TvDimensions.osdLogoHeight.scaled(scale),
                                    ) {
                                        Text(
                                            text = seriesTitleText,
                                            style = scaledStyles.displaySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
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
                                        },
                                    )
                                    // Refresh button
                                    CinemaIconButton(
                                        onClick = { onRefresh() },
                                        enabled = !isRefreshing,
                                        icon = {
                                            Icon(
                                                imageVector = CinemaIcons.Refresh,
                                                contentDescription = stringResource(R.string.series_refresh_info),
                                                modifier =
                                                    Modifier
                                                        .size(TvDimensions.iconSmall.scaled(scale))
                                                        .rotate(rotation),
                                            )
                                        },
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

                        // Series content: poster + metadata
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale)),
                        ) {
                            // Cover image
                            CinemaThumbnail(
                                url = seriesDetail.coverUrl,
                                fallbackLetter = seriesName.firstOrNull(),
                                contentType = ThumbnailContentType.TV_SHOW,
                                modifier =
                                    Modifier
                                        .width(TvDimensions.posterWidth.scaled(scale))
                                        .height(TvDimensions.posterHeightLarge.scaled(scale)),
                            )

                            // Metadata in glass panel
                            GlassPanel(modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(Spacing.lg.scaled(scale))) {
                                    // Metadata header row: Content rating | Star rating | Year | Seasons/Episodes count | Ends at
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        seriesDetail.metadata.contentRating?.let { contentRating ->
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
                                        seriesDetail.metadata.rating?.let { rating ->
                                            RatingBadge(
                                                rating = rating,
                                                style = scaledStyles.titleMedium,
                                                textColor = CinemaAccent,
                                            )
                                        }
                                        val presentLabel = stringResource(R.string.series_present)
                                        val yearRange = seriesDetail.seriesYearRange(presentLabel)
                                        yearRange?.let {
                                            Text(
                                                text = it,
                                                style = scaledStyles.titleMedium,
                                                color = CinemaTextSecondary,
                                            )
                                        }
                                        val countText =
                                            if (sortedSeasons.size > 1) {
                                                stringResource(R.string.series_seasons_and_episodes_format, sortedSeasons.size, totalEpisodes)
                                            } else {
                                                stringResource(R.string.series_total_episodes_format, totalEpisodes)
                                            }
                                        Text(
                                            text = countText,
                                            style = scaledStyles.titleMedium,
                                            color = CinemaTextSecondary,
                                        )
                                    }

                                    // Genre tags
                                    seriesDetail.metadata.genre?.let { genre ->
                                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                        Text(
                                            text = genre,
                                            style = scaledStyles.bodyMedium,
                                            color = CinemaAccent,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

                                    // Play / Resume / Trailer buttons
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (hasResume) {
                                            val resumeButtonText =
                                                if (anchorEpisode != null) {
                                                    stringResource(
                                                        R.string.series_resume_episode_time_format,
                                                        anchorEpisode.seasonNumber ?: 1,
                                                        anchorEpisode.episodeNumber,
                                                        formatTime(anchorResumePosMs),
                                                    )
                                                } else {
                                                    stringResource(R.string.movie_resume_from_format, formatTime(anchorResumePosMs))
                                                }
                                            CinemaPrimaryButton(
                                                onClick = {
                                                    anchorEpisode?.let { ep ->
                                                        onEpisodeSelected(ep.id, ep.title, ep.extension ?: "mp4", false)
                                                    }
                                                },
                                                text = resumeButtonText,
                                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                                            )
                                            CinemaSecondaryButton(
                                                onClick = {
                                                    anchorEpisode?.let { ep ->
                                                        onEpisodeSelected(ep.id, ep.title, ep.extension ?: "mp4", true)
                                                    }
                                                },
                                                text = stringResource(R.string.movie_start_beginning),
                                            )
                                        } else {
                                            val playButtonText =
                                                if (anchorEpisode != null) {
                                                    stringResource(
                                                        R.string.series_play_episode_format,
                                                        anchorEpisode.seasonNumber ?: 1,
                                                        anchorEpisode.episodeNumber,
                                                    )
                                                } else {
                                                    stringResource(R.string.series_play_episode_action)
                                                }
                                            CinemaPrimaryButton(
                                                onClick = {
                                                    anchorEpisode?.let { ep ->
                                                        onEpisodeSelected(ep.id, ep.title, ep.extension ?: "mp4", false)
                                                    }
                                                },
                                                text = playButtonText,
                                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                                            )
                                        }
                                        seriesDetail.metadata.trailerUrl?.let { trailer ->
                                            CinemaSecondaryButton(
                                                onClick = { openExternalUrl(context, trailer) },
                                                text = stringResource(R.string.details_watch_trailer),
                                            )
                                        }
                                    }

                                    // Plot/Description
                                    seriesDetail.metadata.plot?.let { plot ->
                                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                                        Text(
                                            text = plot,
                                            style = scaledStyles.bodyLarge,
                                            color = CinemaTextPrimary,
                                            maxLines = 6,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    // Cast, Director
                                    seriesDetail.metadata.cast?.let { cast ->
                                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                                        Text(
                                            text = stringResource(R.string.movie_cast_format, cast),
                                            style = scaledStyles.bodySmall,
                                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    seriesDetail.metadata.director?.let { director ->
                                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                                        Text(
                                            text = stringResource(R.string.movie_director_format, director),
                                            style = scaledStyles.bodySmall,
                                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                        )
                                    }

                                    // StreamNamePicker dropdown
                                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                                    StreamNamePicker(
                                        currentName = seriesName,
                                        alternates = alternateStreams,
                                        onSelect = {
                                            streamSwitchSignal++
                                            onAlternateStreamSelected(it)
                                        },
                                        textStyle = scaledStyles.bodySmall,
                                        focusRequester = streamNameFocusRequester,
                                        onFocusedChanged = { streamRowFocused = it },
                                    )

                                    // TMDB ID
                                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                                    Text(
                                        text = stringResource(R.string.details_tmdb_format, seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none)),
                                        style = scaledStyles.bodySmall,
                                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                    )

                                    // Category button
                                    if (categoryName != null) {
                                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                                        CinemaSecondaryButton(
                                            onClick = onCategorySelected,
                                            text = stringResource(R.string.details_category_format, categoryName),
                                            modifier =
                                                if (hasMultipleSeasons) {
                                                    // `focusProperties { down = ... }` (tried first) never took: D-pad Down
                                                    // from here landed on the first episode card every time, skipping the
                                                    // season tabs row between them, on a real Shield as well as here —
                                                    // not a one-off emulator timing thing. Root cause unconfirmed (the tab
                                                    // row's own focus targets are fine once actually reached: Left/Right
                                                    // between tabs and later Up/Down into the row both work). Intercepting
                                                    // the key directly and requesting the tab's FocusRequester ourselves
                                                    // sidesteps whatever's misfiring in the search/entry path instead of
                                                    // depending on it.
                                                    Modifier.onPreviewKeyEvent { event ->
                                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                                            seasonTabsFocusRequester.requestFocus()
                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                } else {
                                                    Modifier
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))
                        Text(
                            text = stringResource(R.string.series_episodes_header),
                            style = scaledStyles.headlineSmall,
                            color = CinemaTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    }
                }

                // Season tabs — pinned in place as the episode list scrolls under it (stickyHeader,
                // not a plain item), so it reads as the control for what's below rather than
                // scrolling away with it, same as the mobile row. D-pad left/right moves focus
                // between tabs, which selects immediately (see SeasonTab); Left/Right from inside
                // the episode list below does the same thing (see the onPreviewKeyEvent there).
                if (hasMultipleSeasons) {
                    stickyHeader(key = "season_tabs", contentType = "header") {
                        SeasonTabs(
                            seasons = sortedSeasons,
                            selectedSeason = selectedSeasonNumber,
                            onSeasonSelected = {
                                // Guarded: focus-follow-select (see SeasonTab) fires this the
                                // instant D-pad focus *enters* the row, landing on the already-
                                // selected tab — a no-op season change that used to still flip
                                // hasManuallySelectedSeason and write selectedSeasonNumber to its
                                // own value. That write was enough to recompose this row out from
                                // under the very focus-search transaction bringing it in, which is
                                // why the first Up/Down into the tabs always missed them and
                                // landed on the episode list instead. Skipping the no-op leaves
                                // that transaction undisturbed; a real season change (left/right
                                // to a different tab) still goes through normally.
                                if (it != selectedSeasonNumber) {
                                    hasManuallySelectedSeason = true
                                    selectedSeasonNumber = it
                                }
                            },
                            entryFocusRequester = seasonTabsFocusRequester,
                        )
                    }
                    // Gap before the episode cards, as its own plain (non-sticky) item — inside the
                    // stickyHeader above, it would pin along with the tabs and show cards through
                    // its unbacked height as they scroll past.
                    item(key = "season_tabs_gap", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    }
                }

                val currentSeasonEpisodes = sortedEpisodesBySeason[selectedSeasonNumber?.toString()] ?: emptyList()
                itemsIndexed(currentSeasonEpisodes, key = { _, episode -> episode.id }, contentType = { _, _ -> "episode" }) { index, episode ->
                    val isContinueWatching = episode.id == resumeEpisodeId
                    EpisodeCard(
                        episode = episode,
                        cardStyle = episodeCardStyle,
                        isContinueWatching = isContinueWatching,
                        watchProgress = episodeProgress[episode.id] ?: 0f,
                        isWatched = episode.id in watchedEpisodeIds,
                        focusRequester = if (isContinueWatching) resumeCardFocusRequester else null,
                        // Second requester on this card, alongside `focusRequester` above when
                        // both apply — landing spot for the Left/Right season switch below,
                        // always the current season's first episode regardless of resume state.
                        additionalFocusRequester = if (index == 0) firstEpisodeFocusRequester else null,
                        modifier =
                            if (hasMultipleSeasons) {
                                // Left/Right switches season from anywhere in the episode list,
                                // the D-pad equivalent of the mobile swipe — same explicit-intercept
                                // approach as the Category chip's Down into the tab row (plain
                                // focus search across this LazyColumn boundary isn't reliable
                                // either; see seasonTabsFocusRequester above), and the same
                                // signal-plus-LaunchedEffect indirection to land on the new
                                // season's first episode only once it actually exists.
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    val targetSeason =
                                        when (event.key) {
                                            Key.DirectionLeft -> previousSeason
                                            Key.DirectionRight -> nextSeason
                                            else -> null
                                        } ?: return@onPreviewKeyEvent false
                                    hasManuallySelectedSeason = true
                                    seasonSwitchedFromEpisodeList = true
                                    selectedSeasonNumber = targetSeason.seasonNumber
                                    true
                                }
                            } else {
                                Modifier
                            },
                        onClick = {
                            selectedEpisode = episode
                        },
                        onLongPress = {
                            // Manual watched/unwatched mark (Phase 6,
                            // docs/plans/watch-state-durable-storage-plan.md). Optimistic:
                            // flips this episode's own badge immediately rather than
                            // waiting on the write; the full re-read after it lands is what
                            // catches a TMDB sibling this mark just completed too (Phase 5)
                            // and restores the resume bar on an unmark — a single-item
                            // patch would miss both.
                            val nowWatched = episode.id !in watchedEpisodeIds
                            watchedEpisodeIds =
                                if (nowWatched) watchedEpisodeIds + episode.id else watchedEpisodeIds - episode.id
                            watchedToggleScope.launch {
                                mediaRepository.setWatched(episode.id, ContentType.TV_SHOWS, nowWatched)
                                refreshEpisodeWatchState()
                            }
                        },
                    )
                }

                // Last rows of the list: Related titles
                if (relatedTitles.recommended.isNotEmpty()) {
                    item(key = "recommended", contentType = "related") {
                        RelatedTitlesRow(
                            title = stringResource(R.string.details_more_like_this),
                            items = relatedTitles.recommended,
                            onItemClick = onRelatedTitleSelected,
                            modifier = Modifier.padding(top = Spacing.md.scaled(scale)),
                        )
                    }
                }
                if (relatedTitles.similar.isNotEmpty()) {
                    item(key = "similar", contentType = "related") {
                        RelatedTitlesRow(
                            title = stringResource(R.string.details_similar_titles),
                            items = relatedTitles.similar,
                            onItemClick = onRelatedTitleSelected,
                            modifier = Modifier.padding(top = Spacing.md.scaled(scale)),
                        )
                    }
                }
            }
        }
    }
}

/** Direction the viewer stepped through episodes in, so focus can stay on that button. */
private enum class EpisodeStep { PREVIOUS, NEXT }

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
private fun EpisodeDetailPanel(
    episode: DomainEpisodeItem,
    seriesDetail: SeriesDetail,
    seriesName: String,
    categoryId: String,
    providerName: String,
    mediaRepository: MediaRepository,
    previousEpisode: DomainEpisodeItem?,
    nextEpisode: DomainEpisodeItem?,
    onNavigate: (DomainEpisodeItem) -> Unit,
    onPlay: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val extension = episode.extension ?: "mp4"
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val detailScaledStyles =
        remember(scale, typography) {
            object {
                val displaySmall = typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val headlineSmall = typography.headlineSmall.copy(fontSize = typography.headlineSmall.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val bodyLarge = typography.bodyLarge.copy(fontSize = typography.bodyLarge.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    // Focus requester for Play button
    val playButtonFocusRequester = remember { FocusRequester() }

    // Load resume position. Keyed on the episode so stepping to another one clears the previous
    // episode's value immediately rather than showing its resume time until the lookup lands —
    // and the lookup assigns unconditionally, so an episode with nothing to resume resets it
    // instead of leaving the last one's position behind.
    var resumePositionMs by remember(episode.id) { mutableStateOf(0L) }

    LaunchedEffect(episode.id) {
        val watched = mediaRepository.getPlaybackPositionSuspend(episode.id, ContentType.TV_SHOWS)
        resumePositionMs = watched?.resumeProgress()?.let { watched.playbackPosition } ?: 0L
    }

    // Which button moved us here, so stepping through episodes doesn't drop focus back onto
    // Play every time — pressing Next repeatedly would otherwise mean navigating back down to
    // the Next button after each episode. Reset when the panel closes, so opening an episode
    // from the list still starts on Play.
    var arrivedVia by remember { mutableStateOf<EpisodeStep?>(null) }
    val previousButtonFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }

    // Request focus on Play/Resume button when screen loads, resume data arrives, or episode
    // changes — or back onto the step button just used, when there is still an episode that way.
    LaunchedEffect(episode.id, resumePositionMs) {
        val target =
            when {
                arrivedVia == EpisodeStep.PREVIOUS && previousEpisode != null -> previousButtonFocusRequester
                arrivedVia == EpisodeStep.NEXT && nextEpisode != null -> nextButtonFocusRequester
                else -> playButtonFocusRequester
            }
        try {
            target.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.MediaNext ->
                            nextEpisode?.let {
                                arrivedVia = EpisodeStep.NEXT
                                onNavigate(it)
                                true
                            } ?: false
                        Key.MediaPrevious ->
                            previousEpisode?.let {
                                arrivedVia = EpisodeStep.PREVIOUS
                                onNavigate(it)
                                true
                            } ?: false
                        else -> false
                    }
                }
                .verticalScroll(rememberScrollState())
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
                Text(
                    text = seriesName,
                    style = detailScaledStyles.displaySmall,
                    color = CinemaTextPrimary,
                )
                // Season / episode label
                val seasonLabel = episode.seasonNumber?.let { stringResource(R.string.series_season_label, it) } ?: ""
                val episodeLabel = stringResource(R.string.series_episode_label, episode.episodeNumber)
                val subLabel =
                    listOfNotNull(
                        seasonLabel.ifEmpty { null },
                        episodeLabel,
                    ).joinToString(" · ")
                Text(
                    text = subLabel,
                    style = detailScaledStyles.titleMedium,
                    color = CinemaAccentLight,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
            Text(
                text = providerName,
                style = detailScaledStyles.titleSmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

        // Episode content: thumbnail + metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale)),
        ) {
            // Episode thumbnail
            CinemaThumbnail(
                url = episode.thumbnailUrl ?: seriesDetail.coverUrl,
                fallbackLetter = episode.title.firstOrNull(),
                contentType = ThumbnailContentType.TV_SHOW,
                modifier =
                    Modifier
                        .width(TvDimensions.posterWidth.scaled(scale))
                        .height(TvDimensions.posterHeightLarge.scaled(scale)),
            )

            // Metadata in glass panel
            GlassPanel(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(Spacing.lg.scaled(scale))) {
                    // Episode title
                    Text(
                        text = episode.title,
                        style = detailScaledStyles.headlineSmall,
                        color = CinemaTextPrimary,
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                    // Metadata row: content rating | rating | year | duration | ends at
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val contentRating = episode.metadata.contentRating ?: seriesDetail.metadata.contentRating
                        contentRating?.let {
                            Text(
                                text = it,
                                style = detailScaledStyles.titleMedium,
                                color = CinemaTextSecondary,
                                modifier =
                                    Modifier
                                        .background(
                                            CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                                            RoundedCornerShape(CornerRadius.small),
                                        ).padding(horizontal = Spacing.sm.scaled(scale), vertical = Spacing.xs.scaled(scale)),
                            )
                        }
                        // Prefer episode rating, fallback to series rating
                        val rating = episode.metadata.rating ?: seriesDetail.metadata.rating
                        rating?.let {
                            RatingBadge(
                                rating = it,
                                style = detailScaledStyles.titleMedium,
                                textColor = CinemaAccent,
                            )
                        }
                        val year = episode.metadata.year ?: episode.metadata.airDate?.take(4)?.toIntOrNull() ?: seriesDetail.metadata.year
                        year?.let {
                            Text(
                                text = "$it",
                                style = detailScaledStyles.titleMedium,
                                color = CinemaTextSecondary,
                            )
                        }
                        episode.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { duration ->
                            Text(
                                text = formatDuration(duration),
                                style = detailScaledStyles.titleMedium,
                                color = CinemaTextSecondary,
                            )
                        }
                        // "Ends at" based on remaining duration
                        val endsAtContext = LocalContext.current
                        val endsAtText =
                            remember(episode.metadata.duration, resumePositionMs) {
                                computeEndsAt(endsAtContext, episode.metadata.duration, resumePositionMs)
                            }
                        if (endsAtText != null) {
                            Text(
                                text = stringResource(R.string.movie_ends_at_format, endsAtText),
                                style = detailScaledStyles.titleMedium,
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium),
                            )
                        }
                    }

                    // Genre (from series)
                    seriesDetail.metadata.genre?.let { genre ->
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = genre,
                            style = detailScaledStyles.bodyMedium,
                            color = CinemaAccent,
                        )
                    }

                    // TMDB id: the episode's own when it has one, else the show's
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text =
                            stringResource(
                                R.string.details_tmdb_format,
                                episode.metadata.tmdbId ?: seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
                            ),
                        style = detailScaledStyles.bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )

                    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

                    // Play / Resume buttons + Favorite
                    val hasResume = resumePositionMs > 0L
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasResume) {
                            val resumeTimeText = formatTime(resumePositionMs)
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, false)
                                },
                                text = stringResource(R.string.movie_resume_from_format, resumeTimeText),
                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, true)
                                },
                                text = stringResource(R.string.movie_start_beginning),
                            )
                        } else {
                            CinemaPrimaryButton(
                                onClick = {
                                    onPlay(episode.id, episode.title, extension, false)
                                },
                                text = stringResource(R.string.series_play_episode_action),
                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                            )
                        }
                        // The trailer is the show's, not this episode's — Xtream and Jellyfin
                        // only ever carry one per series.
                        seriesDetail.metadata.trailerUrl?.let { trailer ->
                            val trailerContext = LocalContext.current
                            CinemaSecondaryButton(
                                onClick = { openExternalUrl(trailerContext, trailer) },
                                text = stringResource(R.string.details_watch_trailer),
                            )
                        }
                    }

                    // Step to the adjacent episode without going back to the list. These used to
                    // be a text hint for the remote's transport keys, which the Shield and Bravia
                    // remotes don't have — so they read as buttons that did nothing. The key
                    // handler above still works for remotes that do have them.
                    if (previousEpisode != null || nextEpisode != null) {
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            previousEpisode?.let { previous ->
                                CinemaSecondaryButton(
                                    onClick = {
                                        arrivedVia = EpisodeStep.PREVIOUS
                                        onNavigate(previous)
                                    },
                                    text = stringResource(R.string.player_prev_episode),
                                    modifier = Modifier.focusRequester(previousButtonFocusRequester),
                                )
                            }
                            nextEpisode?.let { next ->
                                CinemaSecondaryButton(
                                    onClick = {
                                        arrivedVia = EpisodeStep.NEXT
                                        onNavigate(next)
                                    },
                                    text = stringResource(R.string.player_next_episode),
                                    modifier = Modifier.focusRequester(nextButtonFocusRequester),
                                )
                            }
                        }
                    }

                    // Plot/Description
                    episode.metadata.plot?.let { plotText ->
                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                        Text(
                            text = plotText,
                            style = detailScaledStyles.bodyLarge,
                            color = CinemaTextPrimary,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Cast (episode-level, fallback to series)
                    val cast = episode.metadata.cast ?: seriesDetail.metadata.cast
                    cast?.let {
                        Text(
                            text = stringResource(R.string.movie_cast_format, it),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    }

                    // Director (episode-level, fallback to series)
                    val director = episode.metadata.director ?: seriesDetail.metadata.director
                    director?.let {
                        Text(
                            text = stringResource(R.string.movie_director_format, it),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }

                    // TMDB id: the episode's own when it has one, else the show's
                    val tmdbId = episode.metadata.tmdbId ?: seriesDetail.metadata.tmdbId
                    if (tmdbId != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = stringResource(R.string.details_tmdb_format, tmdbId),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }

                    // Container format
                    episode.extension?.takeIf { it.isNotBlank() }?.let { ext ->
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = "${stringResource(R.string.tech_container_label)} ${ext.uppercase()}",
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }

                    // Air date
                    episode.metadata.airDate?.takeIf { it.isNotBlank() }?.let {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = stringResource(R.string.series_aired_format, it),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }

                    // Bitrate
                    episode.metadata.bitrate?.takeIf { it > 0 }?.let {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = stringResource(R.string.series_bitrate_format, it),
                            style = detailScaledStyles.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }
                } // GlassPanel Column
            } // GlassPanel
        } // Outer Row (thumbnail + metadata)
    }
}

/**
 * One row of season pills. D-pad left/right moves focus between them via Compose's default
 * focus search inside this Row — [SeasonTab] selects as soon as it receives focus, so that
 * movement alone switches the visible season, the TV equivalent of the mobile swipe.
 */
@Composable
private fun SeasonTabs(
    seasons: List<SeasonInfo>,
    selectedSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
    entryFocusRequester: FocusRequester,
) {
    val scale = LocalUiScale.current
    // One FocusRequester per tab, explicitly wired to its left/right neighbor below — confirmed
    // on a real Shield and in the emulator that Compose's default geometry-based focus search is
    // unreliable for this row: D-pad down from the Category chip above landed on the *last* tab
    // (nearest horizontally, not first in reading order), and D-pad left/right between tabs
    // sometimes didn't move focus at all. Explicit wiring makes both deterministic.
    val focusRequesters = remember(seasons) { seasons.map { FocusRequester() } }
    val selectedIndex =
        remember(seasons, selectedSeason) {
            seasons.indexOfFirst { it.seasonNumber == selectedSeason }.coerceAtLeast(0)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Opaque: this row is pinned via stickyHeader, so episode cards scroll in
                // underneath it and need to actually be hidden, not show through.
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = Spacing.sm.scaled(scale))
                // Entering this row from outside (D-pad down from above, up from below) always
                // lands on the selected tab, not whichever one the default search prefers.
                // `onEnter` is only consulted once this Row is itself a focus-search candidate,
                // which requires `focusGroup()` — without it the row isn't a group at all and
                // `onEnter` silently never fires, leaving Up/Down to fall through to the default
                // (and per the original bug report, unreliable) leaf search.
                .focusGroup()
                .focusProperties {
                    onEnter = { focusRequesters.getOrNull(selectedIndex)?.requestFocus() }
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seasons.forEachIndexed { index, season ->
            SeasonTab(
                season = season,
                isSelected = season.seasonNumber == selectedSeason,
                onSelected = { onSeasonSelected(season.seasonNumber) },
                focusRequester = focusRequesters[index],
                previousTabFocusRequester = focusRequesters.getOrNull(index - 1),
                nextTabFocusRequester = focusRequesters.getOrNull(index + 1),
                // Second requester on the same node, alongside `focusRequester` above — only the
                // selected tab gets it, and it moves with selection as `index` changes across
                // recompositions. This is what the Category chip's explicit `down` (see caller)
                // actually resolves to.
                entryFocusRequester = if (index == selectedIndex) entryFocusRequester else null,
            )
        }
    }
}

@Composable
private fun SeasonTab(
    season: SeasonInfo,
    isSelected: Boolean,
    onSelected: () -> Unit,
    focusRequester: FocusRequester,
    previousTabFocusRequester: FocusRequester?,
    nextTabFocusRequester: FocusRequester?,
    entryFocusRequester: FocusRequester?,
) {
    val scale = LocalUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs),
        label = "season_tab_focus_scale",
    )

    // Focus-follow-select, not a separate OK press: the point of a tab row is to be as immediate
    // as the mobile swipe it stands in for. Deferred to a LaunchedEffect rather than called
    // straight from onFocusChanged below: onSelected mutates state as far up as
    // EpisodeListContent (hasManuallySelectedSeason/selectedSeasonNumber), and doing that
    // synchronously from inside the focus-change callback risks recomposing this row out from
    // under Compose's own focus-transfer machinery mid-transaction. A LaunchedEffect runs after
    // that transaction settles.
    LaunchedEffect(isFocused) {
        if (isFocused) onSelected()
    }

    val containerColor =
        when {
            isFocused && isSelected -> TvFocusTokens.focusedSelectedContainer
            isFocused -> TvFocusTokens.focusedContainer
            isSelected -> TvFocusTokens.selectedContainer
            else -> Color.Transparent
        }

    val textColor =
        when {
            isFocused && isSelected -> CinemaAccentLight
            isFocused -> CinemaTextPrimary
            isSelected -> CinemaAccent
            else -> CinemaTextSecondary
        }

    Box(
        modifier =
            Modifier
                .focusRequester(focusRequester)
                .then(entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusProperties {
                    previousTabFocusRequester?.let { left = it }
                    nextTabFocusRequester?.let { right = it }
                }
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
                .background(
                    color = containerColor,
                    shape = RoundedCornerShape(CornerRadius.medium),
                )
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = TvFocusTokens.focusBorderWidth,
                            color = CinemaAccentLight,
                            shape = RoundedCornerShape(CornerRadius.medium),
                        )
                    } else if (isSelected) {
                        Modifier.border(
                            width = TvFocusTokens.borderThin,
                            color = CinemaAccent.copy(alpha = CinemaAlpha.glassBorder),
                            shape = RoundedCornerShape(CornerRadius.medium),
                        )
                    } else {
                        Modifier
                    },
                )
                // No separate .focusable(): .clickable() below already creates a focus target,
                // and stacking a second one on the same node was the actual cause of tabs being
                // unreachable by D-pad Up/Down from outside the row (Left/Right worked because
                // those go through the explicit FocusRequester wiring above, bypassing search).
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onSelected() }
                .padding(horizontal = Spacing.md.scaled(scale), vertical = Spacing.sm.scaled(scale)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.series_season_label, season.seasonNumber),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale),
                ),
            color = textColor,
        )
    }
}

/**
 * Episode card styling, built once per list composition instead of once per card.
 * `CardDefaults.colors`/`.border` are `@Composable`, so they cannot be wrapped in `remember` —
 * hoisting the calls out of the item body is what stops a full style set being allocated per
 * visible card per recomposition. A data class so a fresh instance still compares equal and lets
 * cards skip.
 */
@Immutable
private data class EpisodeCardStyle(
    val colors: CardColors,
    val cardScale: CardScale,
    val glow: CardGlow,
    val shape: CardShape,
    val border: CardBorder,
    val continueWatchingBorder: CardBorder,
)

@Composable
private fun episodeCardStyle(): EpisodeCardStyle =
    EpisodeCardStyle(
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
            ),
        cardScale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium)),
        border = CardDefaults.border(),
        continueWatchingBorder =
            CardDefaults.border(
                border =
                    Border(
                        border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaAccent),
                        shape = RoundedCornerShape(CornerRadius.medium),
                    ),
            ),
    )

@Composable
private fun EpisodeCard(
    episode: DomainEpisodeItem,
    cardStyle: EpisodeCardStyle,
    isContinueWatching: Boolean = false,
    watchProgress: Float = 0f,
    isWatched: Boolean = false,
    focusRequester: FocusRequester? = null,
    // A second, independent FocusRequester on the same card — set alongside [focusRequester] when
    // this episode is both the resume card and the season's first, since either can be requested
    // on its own (resume-on-load vs. landing here after a season switch).
    additionalFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val cardScaledStyles =
        remember(scale, typography) {
            object {
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
                val labelMedium = typography.labelMedium.copy(fontSize = typography.labelMedium.fontSize.scaled(scale))
                val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
            }
        }
    Card(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDimensions.episodeCardHeight.scaled(scale))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (additionalFocusRequester != null) Modifier.focusRequester(additionalFocusRequester) else Modifier)
                .tvLongPress(onLongPress),
        colors = cardStyle.colors,
        shape = cardStyle.shape,
        scale = cardStyle.cardScale,
        border = if (isContinueWatching) cardStyle.continueWatchingBorder else cardStyle.border,
        glow = cardStyle.glow,
    ) {
        // Box, not a Column with a weighted row: the card is pinned to cardHeight and its content
        // already fills that, so a footer that consumes layout height gets pushed past the card's
        // clip bounds — it lays out (and shows up in semantics) but never paints.
        Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.md.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode thumbnail, with the watched check overlaid on it. The resume bar is a
            // card-width footer below this row instead — see the note there.
            Box(
                modifier =
                    Modifier.size(
                        width = TvDimensions.posterWidth.scaled(scale),
                        height = TvDimensions.posterHeight.scaled(scale),
                    ),
            ) {
                CinemaThumbnail(
                    url = episode.thumbnailUrl,
                    fallbackLetter = episode.title.firstOrNull(),
                    contentType = ThumbnailContentType.TV_SHOW,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isWatched) {
                    WatchedBadge(
                        size = TvDimensions.iconMedium.scaled(scale),
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.xxs.scaled(scale)),
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

            // Episode number
            Text(
                text = stringResource(R.string.series_episode_number_short, episode.episodeNumber),
                style = cardScaledStyles.titleMedium,
                color = CinemaAccentLight,
                modifier = Modifier.width(Spacing.xxl.scaled(scale)),
            )

            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

            // Episode title and plot
            Column(
                modifier = Modifier.weight(1f),
            ) {
                if (isContinueWatching) {
                    Text(
                        text = stringResource(R.string.series_continue_watching_badge),
                        style = cardScaledStyles.labelSmall,
                        color = CinemaAccent,
                    )
                }
                Text(
                    text = episode.title,
                    style = cardScaledStyles.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.metadata.plot?.let { plotText ->
                    Text(
                        text = plotText,
                        style = cardScaledStyles.bodySmall,
                        color = CinemaTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Duration
            episode.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { duration ->
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                Text(
                    text = duration,
                    style = cardScaledStyles.labelMedium,
                    color = CinemaTextSecondary,
                )
            }
        }

        // Resume progress, card-width along the bottom edge — same placement as the stream row in
        // StreamList, so a half-watched episode and a half-watched film read the same. Poster-width
        // was too short to be legible.
        if (watchProgress > 0f) {
            LinearProgressIndicator(
                progress = { watchProgress.coerceIn(0f, 1f) },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // Sit above the continue-watching border, which is drawn over the card's
                        // bottom edge — without this inset a bar of the same thickness as the
                        // stroke is completely hidden underneath it.
                        .padding(bottom = TvDimensions.borderFocused.scaled(scale))
                        .height(TvDimensions.resumeBarHeight.scaled(scale)),
                color = CinemaAccent,
                trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
            )
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
                text = stringResource(R.string.series_loading_episodes),
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
                text = stringResource(R.string.series_error_loading),
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
                text = stringResource(R.string.series_back_to_list),
            )
        }
    }
}

