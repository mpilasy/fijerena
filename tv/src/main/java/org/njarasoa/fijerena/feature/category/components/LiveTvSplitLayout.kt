@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.elapsedFraction
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.toFavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.player.PlayerScreen
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Live-TV-only split layout: a small preview player + now/next EPG card on the left, the channel
 * list on the right. See docs/live-tv-preview-pane-plan.md.
 *
 * The preview is focus-driven and debounced (~600ms) — nothing plays until the user's focus settles
 * on a row, so scrolling never leaves a stream churning in the background. A watchdog also stops a
 * channel that fails to start within 8s rather than let it buffer indefinitely.
 *
 * Selecting a channel (OK) does not navigate to a separate full-screen destination. It PROMOTES the
 * existing preview's playback (same PlaybackViewModel, same StreamLoaderViewModel, same engine
 * connection) to a full-screen PlayerScreen in place. A second, independently-connecting
 * PlaybackViewModel here (i.e. actually navigating to Screen.Player) reliably caused a main-thread
 * ANR (Android's QueuedWork.waitToFinish flush racing the new Service/MediaSession connection) —
 * reusing the single connection removes the race entirely.
 */
@Composable
internal fun LiveTvSplitLayout(
    categoryViewModel: CategoryViewModel,
    categories: ImmutableCategoryList,
    selectedCategoryId: String?,
    streams: ImmutableMediaList?,
    streamsLoading: Boolean,
    categoriesRefreshing: Boolean,
    lastPlayedItemId: String?,
    nowPlaying: ImmutableNowPlaying,
    contentType: String,
    isDevMode: Boolean,
    favoriteIds: ImmutableStringSet,
    favoriteCategoryIds: ImmutableStringSet,
    watchProgress: ImmutableWatchProgress,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, providerData: Map<String, String>) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onBack: () -> Unit,
    initialStreamId: String? = null,
) {
    val context = LocalContext.current
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    // Long-press context menu (favorite add/remove), same as TwoColumnLayout.
    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }
    favoriteMenuTarget?.let { target ->
        FavoriteContextMenuDialog(
            target = target,
            onConfirm = {
                if (target is FavoriteMenuTarget.Stream) {
                    categoryViewModel.toggleFavoriteStream(
                        target.itemId,
                        target.itemName,
                        target.categoryId,
                        target.contentType,
                    )
                }
            },
            onDismiss = { favoriteMenuTarget = null },
        )
    }

    // Focus-driven, debounced preview: the highlighted channel becomes the preview target only
    // after focus settles (~600ms), so scrolling the list doesn't machine-gun the tuner or leave a
    // stream churning in the background. Nothing auto-plays on entry until the user lands on a row —
    // unless this screen was entered with a specific stream already in mind (see the seeding effect
    // below), in which case it plays immediately.
    // Focus is held in a StateFlow, not snapshot state, deliberately: as snapshot state it had to
    // be read in composition to key the debouncing LaunchedEffect, so every single D-pad move
    // invalidated this whole composable — video pane, EPG texts and the channel list all recomposed
    // per keypress, at 600ms before anything even wanted to change. Writing to a flow notifies the
    // collector without touching the snapshot system, so focus moves now cost no recomposition at
    // all and only previewTarget (which changes once, after the debounce) drives the UI.
    val focusedItemFlow = remember { MutableStateFlow<MediaItem?>(null) }
    var previewTarget by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(Unit) {
        // collectLatest, not debounce(): cancels the pending delay on each new focus, which is the
        // same semantics, without opting into the FlowPreview API.
        focusedItemFlow.collectLatest { item ->
            if (item == null) return@collectLatest
            delay(600)
            previewTarget = item
        }
    }

    // Seed the initial preview once the category's streams are loaded (runs once — hasSeeded
    // guards against re-firing on every streams refresh). Two cases:
    // - initialStreamId set: the user picked this exact channel to get here (EPG search, catalog
    //   search, the per-category EPG guide).
    // - No initialStreamId, but a lastPlayedItemId exists: entered from the main menu with
    //   nothing specific picked — auto-seed with the last-watched channel so entry never lands on
    //   a bare list. The caller (TvNavHost) is responsible for making sure there's a real "browse"
    //   screen underneath this one on the back stack for this case, since Back here always just
    //   pops rather than clearing back to a bare list in place.
    var hasSeeded by remember { mutableStateOf(false) }
    LaunchedEffect(streams) {
        if (hasSeeded || previewTarget != null) return@LaunchedEffect
        val list = streams ?: return@LaunchedEffect
        hasSeeded = true
        val seed = initialStreamId?.let { id -> list.firstOrNull { it.id == id } }
            ?: lastPlayedItemId?.let { id -> list.firstOrNull { it.id == id } }
        if (seed != null) {
            previewTarget = seed
            focusedItemFlow.value = seed
        }
    }

    var fullScreen by remember { mutableStateOf(false) }

    // Back: while full-screen, demote to the split (same behavior as the plan's "Back from
    // full-screen returns to split, preview keeps playing"). Otherwise leave this screen — where
    // that lands depends entirely on what TvNavHost pushed underneath (the inviting search/EPG
    // screen, the classic browse screen for main-menu entry, or nothing further for regular
    // category/stream browsing). BackHandler (not a key-event intercept) is required — consuming
    // the key event alone doesn't stop the NavController's own back callback, so an intercept
    // would double-pop out of the app.
    androidx.activity.compose.BackHandler {
        if (fullScreen) fullScreen = false else onBack()
    }

    // 5% TV overscan safe margin — applied here (not by the caller) so the promoted full-screen
    // player below, which renders in place inside this same composable, stays edge-to-edge.
    val safeMarginModifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)

    val target = previewTarget
    if (target == null) {
        // Nothing focused/settled yet (e.g. streams still loading) — show the list only.
        AmbientBackdrop(modifier = Modifier.fillMaxSize())
        Row(modifier = safeMarginModifier, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Box(modifier = Modifier.weight(0.66f).fillMaxHeight())
            LiveTvChannelList(
                streams = streams,
                streamsLoading = streamsLoading,
                selectedCategoryId = selectedCategoryId,
                categoryMap = categoryMap,
                lastPlayedItemId = lastPlayedItemId,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                isDevMode = isDevMode,
                favoriteIds = favoriteIds,
                watchProgress = watchProgress,
                onCategorySelected = onCategorySelected,
                onStreamSelected = onStreamSelected,
                onStreamPromote = { },
                onStreamLongPress = { item -> favoriteMenuTarget = item.toFavoriteMenuTarget(contentType, favoriteIds) },
                onStreamFocused = { item -> focusedItemFlow.value = item },
                onRefreshStreams = onRefreshStreams,
                modifier = Modifier.weight(0.34f).fillMaxHeight(),
            )
        }
        return
    }

    // Single playback connection for this screen, shared by the small preview AND the promoted
    // full-screen player — never a second one. Created once (per screen visit) and re-pointed via
    // loadStream(Light) as the target channel changes; never recreated when `target` changes.
    val playback: PlaybackViewModel = viewModel()
    val previewPlaybackState by playback.playbackState.collectAsStateWithLifecycle()

    // The preview pane's channel list defaults to watch history — regardless of which category
    // (if any) was actually browsed/searched/EPG'd into to get here — with the currently
    // previewed channel included and highlighted (see lastPlayedItemId = target.id below), not
    // filtered out like the full-screen Last Watched flyout does. Independent of
    // categoryViewModel's own selectedCategoryId/streams so browsing a real category still works
    // normally everywhere else (the full-screen Category flyout, EPG/search entry, etc.).
    // D-pad Left/Right (see the onPreviewKeyEvent below) toggles it over to Favorites instead.
    var listSource by remember { mutableStateOf(PreviewListSource.LAST_WATCHED) }
    var lastWatchedStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var lastWatchedStreamsLoading by remember { mutableStateOf(true) }
    var favoriteStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteStreamsLoading by remember { mutableStateOf(true) }
    val composableScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        lastWatchedStreams = categoryViewModel.getLastWatchedSnapshot()
        lastWatchedStreamsLoading = false
    }
    LaunchedEffect(Unit) {
        favoriteStreams = categoryViewModel.getFavoritesSnapshot()
        favoriteStreamsLoading = false
    }

    // One video surface, relocated (not recreated) between the small preview box and the
    // full-screen PlayerScreen via movableContentOf — keeps the same Android View/Surface alive
    // across promote/demote so ExoPlayer never has to detach/reattach. Earlier attempts at this
    // (with and without a fresh-surface-per-channel key) rendered two channels simultaneously
    // overlapping on a genuine channel switch — root-caused to StreamingPlaybackService.
    // playStream() not fully releasing the outgoing decoder session before starting the next
    // one (now fixed with an explicit player.stop() there), not to surface sharing itself. Both
    // call sites must use TextureView, since the preview position (next to a
    // scrolling/recomposing channel list) ANRs the main thread with a SurfaceView.
    val videoSurface = remember {
        movableContentOf {
            EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize(), useTextureView = true)
        }
    }

    val loader: StreamLoaderViewModel =
        viewModel(
            factory =
                StreamLoaderViewModelFactory(
                    context = context,
                    initialStreamId = target.id,
                    initialStreamName = target.name,
                    categoryId = target.categoryId,
                    contentType = contentType,
                ),
        )
    val streamState by loader.state.collectAsStateWithLifecycle()
    val success = streamState as? StreamLoaderViewModel.StreamState.Success

    // Re-point the loader whenever the previewed channel changes, via the lean resolution path
    // (skips the channel-switcher list refetch and watch-history write that a real "commit to
    // watching" does — a preview thumbnail isn't a real watch session).
    // Skipped while full-screen: the full-screen next/previous/select handlers already call the
    // full loader.loadStream() directly for the new target.id, so firing this too would race two
    // loads for the same channel on the same loader/loadJob (duplicate DB/EPG work per switch).
    LaunchedEffect(target.id) {
        if (!fullScreen) {
            loader.loadStreamLight(target)
        }
    }

    LaunchedEffect(success?.streamId) {
        val s = success
        // A promote-triggered upgrade from the dock's light load to a full load (see
        // onStreamPromote below) re-runs loadStream(), which briefly flips this state to
        // Loading and back — success?.streamId goes from the current id to null and back to the
        // same id, which is two key changes and would otherwise re-fire this effect and restart
        // an already-playing stream. Skip when we're already playing this exact URL.
        val alreadyPlayingThis =
            s != null &&
                playback.currentMetadata.value.streamUrl == s.streamUrl &&
                playback.playbackState.value !is PlaybackState.Idle &&
                playback.playbackState.value !is PlaybackState.Error
        if (s != null && !alreadyPlayingThis) {
            playback.playStream(
                PlayerMetadata(
                    title = s.streamName,
                    channelName = s.streamName,
                    description = s.description,
                    streamUrl = s.streamUrl,
                    isLive = s.isLive,
                    headers = s.streamHeaders,
                ),
                s.resumePosition,
            )
        }
    }

    // Dead-stream watchdog: stop trying rather than let a bad channel buffer in the background
    // indefinitely (background churn on a preview stream caused periodic main-thread ANRs).
    LaunchedEffect(success?.streamId) {
        val s = success ?: return@LaunchedEffect
        val reachedPlaying =
            withTimeoutOrNull(8_000) {
                // playbackState is a plain StateFlow, not Compose snapshot state — wrapping it in
                // snapshotFlow{} never registers an observable read, so it emits once and then
                // never again, making first{} hang the full timeout regardless of whether playback
                // actually started. first{} on the StateFlow directly works correctly.
                playback.playbackState.first { it is PlaybackState.Playing }
            }
        if (reachedPlaying == null && success?.streamId == s.streamId && !fullScreen) {
            playback.stop()
        }
    }

    // Pause when this screen isn't RESUMED; resume on return. Stop entirely on final disposal
    // (leaving Live TV altogether).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> playback.onFocusLost(false)
                    Lifecycle.Event.ON_RESUME -> playback.onFocusRegained()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playback.stop()
        }
    }

    if (fullScreen) {
        // Refresh both the preview's history and favorites lists on demote (fullScreen leaving
        // composition) so they reflect what was just watched/(un)favorited — promoting/demoting
        // never leaves this screen (no nav pop, no fresh CategoryViewModel), so nothing else
        // would ever re-fetch them.
        DisposableEffect(Unit) {
            onDispose {
                composableScope.launch {
                    lastWatchedStreams = categoryViewModel.getLastWatchedSnapshot()
                    favoriteStreams = categoryViewModel.getFavoritesSnapshot()
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerScreen(
                viewModel = playback,
                videoSurface = videoSurface,
                onBack = { fullScreen = false },
                isFavorite = favoriteIds.contains(target.id),
                onToggleFavorite = {
                    categoryViewModel.toggleFavoriteStream(target.id, target.name, target.categoryId, contentType)
                },
                currentEpgProgram = success?.currentEpgProgram,
                nextEpgProgram = success?.nextEpgProgram,
                currentStreamId = success?.streamId,
                categoryStreams = streams ?: ImmutableMediaList(),
                lastWatchedStreams =
                    remember(success?.lastWatchedStreams, target.id) {
                        ImmutableMediaList((success?.lastWatchedStreams ?: emptyList()).filter { it.id != target.id })
                    },
                onStreamSelected = { newItem ->
                    // Switching channels while already full-screen is a real "commit to watching" —
                    // use the full loadStream (with side effects), still on the SAME loader/engine.
                    focusedItemFlow.value = newItem
                    previewTarget = newItem
                    loader.loadStream(newItem)
                },
                onNextChannel = {
                    neighborChannel(streams, target.id, +1)?.let { newItem ->
                        focusedItemFlow.value = newItem
                        previewTarget = newItem
                        loader.loadStream(newItem)
                    }
                },
                onPreviousChannel = {
                    neighborChannel(streams, target.id, -1)?.let { newItem ->
                        focusedItemFlow.value = newItem
                        previewTarget = newItem
                        loader.loadStream(newItem)
                    }
                },
            )
        }
    } else {
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = target.thumbnailUrl)
        Row(modifier = safeMarginModifier, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Column(
                modifier = Modifier.weight(0.66f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(CornerRadius.medium))
                            .background(CinemaSurface),
                ) {
                    videoSurface()
                    // The preview surface has no controls/error UI of its own, so a stalled or
                    // watchdog-killed stream would otherwise look identical to a live frozen
                    // frame. Surface the state so it reads as "still loading", not "broken".
                    if (previewPlaybackState !is PlaybackState.Playing) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = CinemaAccent,
                        )
                    }
                }

                // Marks this as the nested "preview" layer of Live TV, distinct from the bare
                // browse screen underneath (TwoColumnLayout, no video) — so backing out one level
                // (video disappears, this label goes with it) reads as a real state change
                // instead of "Back did nothing". See docs/UX_FLOW_AUDIT.md, "Live TV back-stopover".
                Text(
                    text = stringResource(R.string.category_live_preview_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = CinemaAccent,
                )
                Text(
                    text = success?.streamName ?: target.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                )

                val nowProg = success?.currentEpgProgram
                if (nowProg != null) {
                    Text(
                        text = stringResource(R.string.epg_now_prefix, nowProg.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaTextPrimary,
                        maxLines = 2,
                    )
                    val fraction = nowProg.elapsedFraction()
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = CinemaAccent,
                        trackColor = CinemaSurface,
                    )
                }

                val nextProg = success?.nextEpgProgram
                if (nextProg != null) {
                    Text(
                        text = stringResource(R.string.category_up_next_format, nextProg.title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary,
                        maxLines = 1,
                    )
                }
            }

            // A freshly-picked channel (search/browse) hasn't necessarily hit watch history yet
            // (loadStreamLight never writes it, and even loadStream's write is delayed) — without
            // this, target could be entirely absent from the Last Watched list, leaving no row to
            // OK-press for promote. Prepend it so it's always reachable. Not done for Favorites —
            // there it's expected the current channel may simply not be one, same as any other
            // list the user browses to.
            val displayedStreams =
                remember(listSource, lastWatchedStreams, favoriteStreams, target.id) {
                    when (listSource) {
                        PreviewListSource.LAST_WATCHED ->
                            if (lastWatchedStreams.any { it.id == target.id }) {
                                lastWatchedStreams
                            } else {
                                listOf(target) + lastWatchedStreams
                            }
                        PreviewListSource.FAVORITES -> favoriteStreams
                    }
                }
            LiveTvChannelList(
                streams = ImmutableMediaList(displayedStreams),
                streamsLoading = if (listSource == PreviewListSource.FAVORITES) favoriteStreamsLoading else lastWatchedStreamsLoading,
                // Hardcoded, not the real browsed/searched/EPG'd-into selection — the panel's
                // list and title always reflect history/favorites here, regardless of entry path
                // (see listSource above). categoryMap always has both ids (virtual categories
                // added by rebuildVirtualCategories), so the title still resolves to "Last
                // Watched"/"Favorites" correctly.
                selectedCategoryId =
                    if (listSource == PreviewListSource.FAVORITES) {
                        CategoryViewModel.FAVORITES_CATEGORY_ID
                    } else {
                        CategoryViewModel.LAST_WATCHED_CATEGORY_ID
                    },
                categoryMap = categoryMap,
                // Highlight whatever's actually previewing, if present in the current list.
                lastPlayedItemId = target.id,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                isDevMode = isDevMode,
                favoriteIds = favoriteIds,
                watchProgress = watchProgress,
                onCategorySelected = onCategorySelected,
                onStreamSelected = onStreamSelected,
                onRefreshStreams = {
                    composableScope.launch {
                        when (listSource) {
                            PreviewListSource.LAST_WATCHED -> {
                                lastWatchedStreamsLoading = true
                                lastWatchedStreams = categoryViewModel.getLastWatchedSnapshot()
                                lastWatchedStreamsLoading = false
                            }
                            PreviewListSource.FAVORITES -> {
                                favoriteStreamsLoading = true
                                favoriteStreams = categoryViewModel.getFavoritesSnapshot()
                                favoriteStreamsLoading = false
                            }
                        }
                    }
                },
                onStreamPromote = { item ->
                    // Selecting a different channel (e.g. OK pressed before the debounce settled):
                    // commit to it on the SAME loader/engine before promoting — still only one
                    // connection ever.
                    if (item.id != target.id) {
                        focusedItemFlow.value = item
                        previewTarget = item
                    }
                    // Always upgrade from the dock's light load (empty categoryStreams/
                    // lastWatchedStreams, no watch-history recording — see loadStreamLight's doc
                    // comment) to a full load so the promoted full-screen view has real category/
                    // last-watched lists and actually records watch history. Safe to call even when
                    // it's the same channel already previewing: playback itself is driven by a
                    // separate LaunchedEffect keyed on the streamId *value*, which doesn't change
                    // here, so this only refreshes metadata — it does not restart the stream.
                    loader.loadStream(item)
                    fullScreen = true
                },
                onStreamLongPress = { item -> favoriteMenuTarget = item.toFavoriteMenuTarget(contentType, favoriteIds) },
                onStreamFocused = { item -> focusedItemFlow.value = item },
                modifier =
                    Modifier
                        .weight(0.34f)
                        .fillMaxHeight()
                        // Left/Right toggles Last Watched <-> Favorites regardless of which row
                        // in the list is focused — onPreviewKeyEvent intercepts ahead of the
                        // focused Card, which only handles OK/center, so nothing needs to consume
                        // this deeper in the tree.
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        listSource = PreviewListSource.LAST_WATCHED
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        listSource = PreviewListSource.FAVORITES
                                        true
                                    }
                                    else -> false
                                }
                            }
                        },
            )
        }
    }
}

/** Which list the Live TV preview pane's channel panel is showing, toggled via D-pad Left/Right. */
private enum class PreviewListSource { LAST_WATCHED, FAVORITES }

/**
 * Computes the channel next to [currentId] in [streams], wrapping around. Used for full-screen
 * Up/Down channel switching — computed off the currently-displayed list rather than the loader's
 * internal index, which [StreamLoaderViewModel.loadStreamLight] (the preview re-point path) never
 * keeps in sync with what's actually on screen.
 */
private fun neighborChannel(streams: ImmutableMediaList?, currentId: String, direction: Int): MediaItem? {
    val list = streams ?: return null
    if (list.isEmpty()) return null
    val currentIndex = list.indexOfFirst { it.id == currentId }.takeIf { it != -1 } ?: 0
    val nextIndex = (currentIndex + direction).mod(list.size)
    return list[nextIndex]
}

/** The channel list pane, shared between the "no preview yet" and "split" render paths. */
@Composable
private fun LiveTvChannelList(
    streams: ImmutableMediaList?,
    streamsLoading: Boolean,
    selectedCategoryId: String?,
    categoryMap: Map<String, org.njarasoa.fijerena.core.player.domain.MediaCategory>,
    lastPlayedItemId: String?,
    nowPlaying: ImmutableNowPlaying,
    contentType: String,
    categoryViewModel: CategoryViewModel,
    isDevMode: Boolean,
    favoriteIds: ImmutableStringSet,
    watchProgress: ImmutableWatchProgress,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, providerData: Map<String, String>) -> Unit,
    onStreamPromote: (MediaItem) -> Unit,
    onStreamLongPress: (MediaItem) -> Unit,
    onStreamFocused: (MediaItem) -> Unit,
    onRefreshStreams: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StreamList(
        streams = streams,
        streamsLoading = streamsLoading,
        selectedCategoryId = selectedCategoryId,
        selectedCategoryName = selectedCategoryId?.let { categoryMap[it]?.name },
        lastPlayedItemId = lastPlayedItemId,
        nowPlaying = nowPlaying,
        contentType = contentType,
        categoryViewModel = categoryViewModel,
        isDevMode = isDevMode,
        favoriteIds = favoriteIds,
        watchProgress = watchProgress,
        onStreamSelected = { streamId, streamName, categoryId, providerData ->
            if (providerData["isCategoryRef"] == "true") {
                providerData["categoryId"]?.let { onCategorySelected(it) }
            } else {
                val item = streams?.firstOrNull { it.id == streamId }
                if (item != null) {
                    onStreamPromote(item)
                } else {
                    // Not resolvable from the current list (shouldn't normally happen) — fall back to
                    // the caller's own handling.
                    onStreamSelected(streamId, streamName, categoryId, providerData)
                }
            }
        },
        onStreamLongPress = onStreamLongPress,
        onStreamFocused = onStreamFocused,
        onRefreshStreams = onRefreshStreams,
        modifier = modifier,
        thumbnailScale = 0.5f,
    )
}
