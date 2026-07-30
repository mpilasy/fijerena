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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
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
    var focusedItem by remember { mutableStateOf<MediaItem?>(null) }
    var previewTarget by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(focusedItem) {
        val f = focusedItem ?: return@LaunchedEffect
        delay(600)
        previewTarget = f
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
            focusedItem = seed
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

    val target = previewTarget
    if (target == null) {
        // Nothing focused/settled yet (e.g. streams still loading) — show the list only.
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
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
                onStreamFocused = { item -> focusedItem = item },
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

    // The preview pane's channel list is always watch history — regardless of which category
    // (if any) was actually browsed/searched/EPG'd into to get here — with the currently
    // previewed channel included and highlighted (see lastPlayedItemId = target.id below), not
    // filtered out like the full-screen Last Watched flyout does. Independent of
    // categoryViewModel's own selectedCategoryId/streams so browsing a real category still works
    // normally everywhere else (the full-screen Category flyout, EPG/search entry, etc.).
    var lastWatchedStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var lastWatchedStreamsLoading by remember { mutableStateOf(true) }
    val composableScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        lastWatchedStreams = categoryViewModel.getLastWatchedSnapshot()
        lastWatchedStreamsLoading = false
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
    LaunchedEffect(target.id) {
        loader.loadStreamLight(target)
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
                snapshotFlow { playback.playbackState.value }
                    .first { it is PlaybackState.Playing }
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
        // Refresh the preview's history list on demote (fullScreen leaving composition) so it
        // reflects what was just watched — promoting/demoting never leaves this screen (no nav
        // pop, no fresh CategoryViewModel), so nothing else would ever re-fetch it.
        DisposableEffect(Unit) {
            onDispose {
                composableScope.launch {
                    lastWatchedStreams = categoryViewModel.getLastWatchedSnapshot()
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
                    focusedItem = newItem
                    previewTarget = newItem
                    loader.loadStream(newItem)
                },
                onNextChannel = {
                    neighborChannel(streams, target.id, +1)?.let { newItem ->
                        focusedItem = newItem
                        previewTarget = newItem
                        loader.loadStream(newItem)
                    }
                },
                onPreviousChannel = {
                    neighborChannel(streams, target.id, -1)?.let { newItem ->
                        focusedItem = newItem
                        previewTarget = newItem
                        loader.loadStream(newItem)
                    }
                },
            )
        }
    } else {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
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

                Text(
                    text = success?.streamName ?: target.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                )

                val nowProg = success?.currentEpgProgram
                if (nowProg != null) {
                    Text(
                        text = "Now: ${nowProg.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaTextPrimary,
                        maxLines = 2,
                    )
                    val nowSec = System.currentTimeMillis() / 1000L
                    val fraction =
                        if (nowProg.duration > 0L) {
                            ((nowSec - nowProg.startTime).toFloat() / nowProg.duration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
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
                        text = "Up next: ${nextProg.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary,
                        maxLines = 1,
                    )
                }
            }

            LiveTvChannelList(
                streams = ImmutableMediaList(lastWatchedStreams),
                streamsLoading = lastWatchedStreamsLoading,
                // Hardcoded, not the real browsed/searched/EPG'd-into selection — the panel's
                // list and title always reflect history here, regardless of entry path (see
                // lastWatchedStreams above). categoryMap always has this id (a virtual category
                // added by rebuildVirtualCategories), so the title still resolves to "Last
                // Watched" correctly.
                selectedCategoryId = CategoryViewModel.LAST_WATCHED_CATEGORY_ID,
                categoryMap = categoryMap,
                // Highlight whatever's actually previewing, which on this always-history list is
                // also just... its entry in the history, if present.
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
                        lastWatchedStreamsLoading = true
                        lastWatchedStreams = categoryViewModel.getLastWatchedSnapshot()
                        lastWatchedStreamsLoading = false
                    }
                },
                onStreamPromote = { item ->
                    // Selecting a different channel (e.g. OK pressed before the debounce settled):
                    // commit to it on the SAME loader/engine before promoting — still only one
                    // connection ever.
                    if (item.id != target.id) {
                        focusedItem = item
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
                onStreamFocused = { item -> focusedItem = item },
                modifier = Modifier.weight(0.34f).fillMaxHeight(),
            )
        }
    }
}

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

private fun MediaItem.toFavoriteMenuTarget(contentType: String, favoriteIds: ImmutableStringSet) =
    FavoriteMenuTarget.Stream(
        itemId = id,
        itemName = name,
        categoryId = categoryId,
        contentType = contentType,
        isFavorite = favoriteIds.contains(id),
    )

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
    )
}
