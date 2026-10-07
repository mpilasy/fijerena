@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.elapsedFraction
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.sync.RemoteStopEffect
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CurrentChannelPolicy
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.finalizeSessionAndAwait
import org.njarasoa.fijerena.core.ui.viewmodels.rememberStableRecentOrder
import org.njarasoa.fijerena.core.ui.viewmodels.withCurrentChannel
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.player.PlayerScreen
import org.njarasoa.fijerena.ui.player.components.overlays.TvTuningOverlay
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Live-TV-only split layout: a small preview player + now/next EPG card on the left, the channel
 * list on the right. See docs/live-tv-preview-pane-plan.md.
 *
 * The preview plays what OK picks, never what focus rests on: moving through the list or across its
 * tabs leaves the playing channel alone. OK on another row tunes the preview to it; OK on the row
 * already playing goes full screen. A watchdog also stops a channel that fails to start within 8s
 * rather than let it buffer indefinitely.
 *
 * Full screen does not navigate to a separate destination. It PROMOTES the existing preview's
 * playback (same PlaybackViewModel, same StreamLoaderViewModel, same engine
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
    watchedIds: ImmutableStringSet,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, target: BrowseTarget) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    initialStreamId: String? = null,
    /** Called with the channel this screen plays each time that changes, for Back (LT6). */
    onPlayingChannel: (streamId: String) -> Unit = {},
    /**
     * Opens the TV Guide for a list on a channel's row — the full-screen OSD's Guide button (GD5).
     * Null (the source has no guide) leaves the button out.
     */
    onOpenGuide: ((categoryId: String, categoryName: String, focusChannelId: String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    // The channel the preview plays: set by the entry seed below and by OK on a row (P8 of
    // docs/plans/archive/20261003_sources-guide-profiles-plan.md) — focus moves never change it, so moving
    // through the list doesn't tune anything.
    var previewTarget by remember { mutableStateOf<MediaItem?>(null) }

    // Seed the initial preview once the category's streams are loaded (runs once — hasSeeded
    // guards against re-firing on every streams refresh). Two cases:
    // - initialStreamId set: the user picked this exact channel to get here (EPG search, catalog
    //   search, the per-category EPG guide).
    // - No initialStreamId, but a lastPlayedItemId exists: nothing specific picked — auto-seed
    //   with the last-watched channel so entry never lands on a bare list. (Home → Live TV passes
    //   the last channel as initialStreamId, and this preview is a layer over browse there — LT7.)
    var hasSeeded by remember { mutableStateOf(false) }
    // The channel full screen was showing when the OSD's Guide button left this screen (GD5): Back
    // from the guide rebuilds this screen, and it comes back to that channel, not the entry one.
    var guideReturnStreamId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(streams) {
        if (hasSeeded || previewTarget != null) return@LaunchedEffect
        val list = streams ?: return@LaunchedEffect
        // The last channel only when no channel was asked for: a requested one this list doesn't have
        // (its category hidden by the profile, so the list is Recent) must not play the last channel
        // watched instead (docs/plans/archive/20261007_guide-watch-wrong-channel-plan.md, fix 1).
        val seed =
            if (guideReturnStreamId != null || initialStreamId != null) {
                guideReturnStreamId?.let { id -> list.firstOrNull { it.id == id } }
                    ?: initialStreamId?.let { id -> list.firstOrNull { it.id == id } }
            } else {
                lastPlayedItemId?.let { id -> list.firstOrNull { it.id == id } }
            }
        // A channel asked for by id and not in this list yet waits for the next one: after process
        // death the list comes back as Recent first, then the category the preview was on.
        if (seed == null && (guideReturnStreamId != null || initialStreamId != null)) return@LaunchedEffect
        hasSeeded = true
        if (seed != null) previewTarget = seed
    }

    // No setContentType(LIVE_TV) effect here: StreamingPlaybackService.playStream() picks the
    // buffer profile from metadata.isLive itself, so the preview and the promoted player are always
    // on the live profile. (The effect that used to do it could crash the app with an uncaught
    // ServiceDestroyedException — see docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md F-03.)

    // Saveable: Back from the TV Guide opened by the OSD's Guide button returns to full screen.
    var fullScreen by rememberSaveable { mutableStateOf(false) }

    // Back: while full-screen, demote to the split (same behavior as the plan's "Back from
    // full-screen returns to split, preview keeps playing"). Otherwise onBack: close this layer
    // to the browse list under it (LT7), or leave the entry for the search/EPG screen that pushed
    // it. BackHandler (not a key-event intercept) is required — consuming
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

    val previewRadius = CornerRadius.medium
    val previewPaneShape = remember(previewRadius) { RoundedCornerShape(previewRadius) }

    // The context list (ChannelContext, LT2). The channel was chosen from one list — the browsed
    // category, Recent or Favourites: TvCategoryGridScreen passes that list's id as this entry's
    // category, so categoryViewModel's selectedCategoryId/streams *are* that list when it is a
    // real category; Home → Live TV lands on Recent. That one list is what the panel shows —
    // docked here and over the video in full screen (LT3) — and what Up/Down zap through in full
    // screen, until a tab switches it.
    // Saveable so the tab choice survives an activity recreate; keyed on the entry's category
    // because the first Success state arrives before it is resolved.
    val pickedCategory =
        remember(selectedCategoryId, categoryMap) {
            selectedCategoryId
                ?.takeUnless { it in CategoryViewModel.VIRTUAL_CATEGORY_IDS }
                ?.let { ChannelContext.Category(id = it, name = categoryMap[it]?.name ?: it) }
        }
    var channelContext by
        rememberSaveable(selectedCategoryId, stateSaver = ChannelContext.Saver) {
            mutableStateOf(
                if (selectedCategoryId == CategoryViewModel.FAVORITES_CATEGORY_ID) {
                    ChannelContext.Favorites
                } else {
                    pickedCategory ?: ChannelContext.Recent
                },
            )
        }
    val contextTabs =
        remember(pickedCategory) { listOfNotNull(pickedCategory, ChannelContext.Recent, ChannelContext.Favorites) }

    // Bumped by the panel's own refresh action — the viewer asking for current truth, and so the
    // one place the frozen order below is allowed to re-sort.
    var recentOrderResetTick by remember { mutableStateOf(0) }
    val publishedRecentStreams by categoryViewModel.recentItems.collectAsStateWithLifecycle()
    // Held in display order: a channel previewed past the watch delay is recorded while this
    // panel is on screen, and promoting it to the top live would shift every row under the
    // viewer's cursor.
    val recentStreams =
        rememberStableRecentOrder(publishedRecentStreams.orEmpty(), resetKey = recentOrderResetTick)
    var favoriteStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteStreamsLoading by remember { mutableStateOf(true) }
    val composableScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        favoriteStreams = categoryViewModel.getFavoritesSnapshot()
        favoriteStreamsLoading = false
    }

    val target = previewTarget
    // Browse, rebuilt when Back closes this layer, lands on this channel (LT6) — the one tuned
    // now, after any zap or retune, not the one that opened the preview.
    val currentOnPlayingChannel by rememberUpdatedState(onPlayingChannel)
    LaunchedEffect(target?.id) {
        target?.id?.let { currentOnPlayingChannel(it) }
    }
    // INCLUDE keeps the current channel reachable even before the delayed history write lands,
    // so there's always a row to OK-press for promote, and a zap neighbour. Not done for
    // Favorites — there it's expected the current channel may simply not be one, same as any
    // other list the user browses to. Remembered, not built inline: an unremembered wrapper
    // hands StreamList a new `streams` identity on every recomposition of this screen, which
    // re-runs its `remember(streams)` blocks — re-arming the entrance animation for every
    // visible row and, with it, a per-frame invalidateMeasurement loop.
    val recentWithCurrent =
        remember(recentStreams, target?.id) {
            ImmutableMediaList(recentStreams.withCurrentChannel(target, CurrentChannelPolicy.INCLUDE))
        }
    val favoriteList = remember(favoriteStreams) { ImmutableMediaList(favoriteStreams) }

    fun streamsOf(tab: ChannelContext): ImmutableMediaList? =
        when (tab) {
            is ChannelContext.Category -> streams
            ChannelContext.Recent -> recentWithCurrent
            ChannelContext.Favorites -> favoriteList
        }
    val contextStreams: ImmutableMediaList? = streamsOf(channelContext)
    val contextLoading =
        when (channelContext) {
            is ChannelContext.Category -> streamsLoading
            ChannelContext.Recent -> publishedRecentStreams == null
            ChannelContext.Favorites -> favoriteStreamsLoading
        }
    val refreshContext: () -> Unit = {
        when (val current = channelContext) {
            is ChannelContext.Category -> {
                onRefreshStreams(current.id)
            }

            ChannelContext.Recent -> {
                composableScope.launch {
                    categoryViewModel.refreshRecentItems()
                    recentOrderResetTick++
                }
            }

            ChannelContext.Favorites -> {
                composableScope.launch {
                    favoriteStreamsLoading = true
                    favoriteStreams = categoryViewModel.getFavoritesSnapshot()
                    favoriteStreamsLoading = false
                }
            }
        }
    }

    if (target == null) {
        // Nothing playing yet (streams still loading, or no channel to seed with) — the list only,
        // until OK picks a channel.
        AmbientBackdrop(modifier = Modifier.fillMaxSize())
        Row(modifier = safeMarginModifier, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Box(modifier = Modifier.weight(0.66f).fillMaxHeight())
            LiveTvChannelPanel(
                tabs = contextTabs,
                context = channelContext,
                onContextSelected = { channelContext = it },
                streams = contextStreams,
                streamsLoading = contextLoading,
                lastPlayedItemId = lastPlayedItemId,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                isDevMode = isDevMode,
                favoriteIds = favoriteIds,
                watchProgress = watchProgress,
                watchedIds = watchedIds,
                onCategorySelected = onCategorySelected,
                onStreamSelected = onStreamSelected,
                onStreamPromote = { item -> previewTarget = item },
                onRefresh = refreshContext,
                onLeftFromFirstTab = onBack,
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

    // Zap feedback (LT5): "Tuning · <channel>" from the moment the target changes — an OK on
    // another row in the preview, Up/Down or a panel pick in full screen —
    // until the engine plays *that* channel. Only display state: derived from what the loader and
    // engine already publish, it changes nothing about how a channel is loaded or played. The
    // engine keeps the old channel (or its state) until the loader resolves the new one, so
    // "playing" means the engine's stream is the target's resolved URL and it reached Playing.
    // tunedId remembers that, so a later rebuffer of the same channel is not shown as a tune.
    // Hidden on a loader or playback error, which have their own UI.
    val playingMetadata by playback.currentMetadata.collectAsStateWithLifecycle()
    val targetPlaying =
        success != null &&
            success.streamId == target.id &&
            playingMetadata.streamUrl == success.streamUrl &&
            previewPlaybackState is PlaybackState.Playing
    var tunedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(targetPlaying, target.id) {
        if (targetPlaying) tunedId = target.id
    }
    val tuningName =
        target.name.takeIf {
            target.id != tunedId &&
                !targetPlaying &&
                streamState !is StreamLoaderViewModel.StreamState.Error &&
                previewPlaybackState !is PlaybackState.Error
        }

    // Re-point the loader whenever the previewed channel changes, via the lean resolution path
    // (skips the channel-switcher list refetch that a real "commit to watching" does — a preview
    // doesn't render that list). Watch history is still recorded on the usual delay.
    // Skipped while full-screen: the full-screen next/previous/select handlers already call the
    // full loader.loadStream() directly for the new target.id, so firing this too would race two
    // loads for the same channel on the same loader/loadJob (duplicate DB/EPG work per switch).
    // Also skipped for the channel the loader was constructed with, which it is already
    // resolving — otherwise every entry into the preview resolved the same stream and EPG twice.
    LaunchedEffect(target.id) {
        if (!fullScreen && loader.requestedStreamId != target.id) {
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
            playback.playStream(previewMetadata(s), s.resumePosition)
        }
    }

    // The guide lookup lands after playback started, and the programme rolls over while it plays:
    // patch it into what is playing, for live sync's "now playing" (see PlaybackViewModel.updateMetadata).
    LaunchedEffect(success?.streamUrl, success?.currentEpgProgram?.title) {
        val s = success ?: return@LaunchedEffect
        playback.updateMetadata(s.streamUrl) { it.copy(programTitle = s.currentEpgProgram?.title) }
    }

    // Another device of the sync group stopped this playback — preview or promoted full-screen
    // alike: finalise and release as leaving Live TV does, then go Home. See
    // docs/plans/archive/20261001_live-sync-now-playing-plan.md → Remote Stop.
    RemoteStopEffect {
        finalizeSessionAndAwait(playback.playbackState.value, loader)
        playback.stopAndRelease()
        onHome()
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
        if (reachedPlaying == null && success.streamId == s.streamId && !fullScreen) {
            playback.stop()
        }
    }

    // Pause when this screen isn't RESUMED; resume on return. Release entirely on final disposal
    // (leaving Live TV altogether) — stopAndRelease, not stop: this preview player runs
    // continuously as long as any Live TV screen is on-screen (that's the whole point of the
    // preview), so this is the one place that actually tears it down. Leaving it on plain stop()
    // kept the native decoder/renderer buffers resident indefinitely after leaving Live TV,
    // verified on-device: service still alive, memory unchanged, after fully backing out to home.
    //
    // Resume on return means play again, at the live edge: ON_PAUSE pauses the preview and, after
    // 30 s away (screensaver, HDMI input switch), stops it — and ON_RESUME used to only cancel that
    // timer, so a short absence came back to a frozen frame and a long one to a black, idle pane.
    // Only when it was playing as the screen paused: a user's own pause in full screen stays put.
    // See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-16.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSuccess by rememberUpdatedState(success)
    var resumeOnReturn by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        val state = playback.playbackState.value
                        resumeOnReturn = state is PlaybackState.Playing || state is PlaybackState.Buffering
                        playback.onFocusLost(false)
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        playback.onFocusRegained()
                        val s = currentSuccess
                        if (resumeOnReturn && s != null) playback.playStream(previewMetadata(s))
                        resumeOnReturn = false
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playback.stopAndRelease()
        }
    }

    if (fullScreen) {
        // Refresh the preview's favorites list on demote (fullScreen leaving composition) so it
        // reflects what was just (un)favorited — promoting/demoting never leaves this screen (no
        // nav pop, no fresh CategoryViewModel), so nothing else would re-fetch it. The Recent
        // list needs no equivalent: the player's own delayed history write republishes the
        // shared list, which the panel collects, docked or in full screen.
        DisposableEffect(Unit) {
            onDispose {
                composableScope.launch {
                    favoriteStreams = categoryViewModel.getFavoritesSnapshot()
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerScreen(
                viewModel = playback,
                onBack = { fullScreen = false },
                isFavorite = favoriteIds.contains(target.id),
                onToggleFavorite = {
                    categoryViewModel.toggleFavoriteStream(target.id, target.name, target.categoryId, contentType)
                },
                currentEpgProgram = success?.currentEpgProgram,
                nextEpgProgram = success?.nextEpgProgram,
                currentStreamId = success?.streamId,
                tuningChannelName = tuningName,
                // Guide (GD5): the TV Guide of the list being zapped through, on this channel's row.
                onShowGuide =
                    onOpenGuide?.let { open ->
                        val contextId = channelContext.id
                        val contextName = channelContext.label()
                        val showGuide: () -> Unit = {
                            guideReturnStreamId = target.id
                            open(contextId, contextName, target.id)
                        }
                        showGuide
                    },
                // The same panel as the split's, over the video (LT3): same tabs, rows and row
                // actions, and its tabs switch channelContext — so the zap order below follows.
                channelPanel = { close ->
                    // Opened on a tab with no rows (no favourites, or the last one removed), focus
                    // was left on the tab row: open on the tab that has the playing channel
                    // instead — the category it came from, else Recent — on its row. Switched
                    // before the panel composes: its empty tab would take focus and, focus
                    // following selection, select itself again.
                    val reopenOn =
                        remember {
                            if (!contextLoading && contextStreams?.isEmpty() == true) {
                                contextTabs.firstOrNull { tab -> streamsOf(tab)?.any { it.id == target.id } == true }
                            } else {
                                null
                            }
                        }
                    var panelReady by remember { mutableStateOf(reopenOn == null) }
                    LaunchedEffect(Unit) {
                        if (reopenOn != null) {
                            channelContext = reopenOn
                            panelReady = true
                        }
                    }
                    if (panelReady) {
                        LiveTvChannelPanel(
                            tabs = contextTabs,
                            context = channelContext,
                            onContextSelected = { channelContext = it },
                            streams = contextStreams,
                            streamsLoading = contextLoading,
                            lastPlayedItemId = target.id,
                            nowPlaying = nowPlaying,
                            contentType = contentType,
                            categoryViewModel = categoryViewModel,
                            isDevMode = isDevMode,
                            favoriteIds = favoriteIds,
                            watchProgress = watchProgress,
                            watchedIds = watchedIds,
                            onCategorySelected = onCategorySelected,
                            onStreamSelected = onStreamSelected,
                            onStreamPromote = { newItem ->
                                // Switching channels while already full-screen is a real "commit to
                                // watching" — use the full loadStream (with side effects), still on
                                // the SAME loader/engine.
                                previewTarget = newItem
                                loader.loadStream(newItem)
                                close()
                            },
                            onRefresh = refreshContext,
                            overlay = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
                onNextChannel = {
                    neighborChannel(contextStreams, target.id, +1)?.let { newItem ->
                        previewTarget = newItem
                        loader.loadStream(newItem)
                    }
                },
                onPreviousChannel = {
                    neighborChannel(contextStreams, target.id, -1)?.let { newItem ->
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
                            .clip(previewPaneShape)
                            .background(CinemaSurface),
                ) {
                    // TextureView (not the default SurfaceView): this box sits next to the
                    // scrolling/recomposing channel list, and SurfaceView there stalls the main
                    // thread and ANRs. Full-screen playback above uses PlayerScreen's own
                    // default surface instead of sharing this one — promoting/demoting now does
                    // a real detach/reattach (one frame's worth of glitch) rather than relocating
                    // this node, but it stops every OSD/flyout interaction in full-screen from
                    // janking the video for the rest of the session. See
                    // docs/plans/archive/20260826_tv-ui-performance-plan.md's dropped "do not touch" note on this
                    // TextureView for why that tradeoff reverted.
                    EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize(), useTextureView = true)
                    // The preview surface has no controls/error UI of its own, so a stalled or
                    // watchdog-killed stream would otherwise look identical to a live frozen
                    // frame. Surface the state so it reads as "still loading", not "broken" —
                    // naming the channel while it is being tuned (LT5).
                    if (tuningName != null) {
                        TvTuningOverlay(channelName = tuningName, compact = true)
                    } else if (previewPlaybackState !is PlaybackState.Playing) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = CinemaAccent,
                        )
                    }
                }

                // Below the video (LT5): name · category, Now with its progress and Next when the
                // guide has them, and the one key hint. The guide lines are the target's only —
                // while a retune resolves, the loader still holds the previous channel's.
                // No "LIVE PREVIEW" badge any more: it marked this as the preview layer, distinct
                // from the bare browse list behind it, and the video with the hint line under it
                // already does (docs/UX_FLOW_AUDIT.md, "Live TV back-stopover").
                // TODO: the channel number goes before the name once metadata carries one.
                val resolved = success?.takeIf { it.streamId == target.id }
                Text(
                    text = resolved?.streamName ?: target.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val categoryName =
                    target.categoryId
                        .takeUnless { it in CategoryViewModel.VIRTUAL_CATEGORY_IDS }
                        ?.let { categoryMap[it]?.name }
                if (categoryName != null) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val nowProg = resolved?.currentEpgProgram
                if (nowProg != null) {
                    Text(
                        text = stringResource(R.string.epg_now_prefix, nowProg.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val fraction = nowProg.elapsedFraction()
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = CinemaAccent,
                        trackColor = CinemaSurface,
                    )
                }

                val nextProg = resolved?.nextEpgProgram
                if (nextProg != null) {
                    Text(
                        text = stringResource(R.string.live_preview_next_format, nextProg.title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.live_preview_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary,
                    maxLines = 1,
                )
            }

            LiveTvChannelPanel(
                tabs = contextTabs,
                context = channelContext,
                onContextSelected = { channelContext = it },
                streams = contextStreams,
                streamsLoading = contextLoading,
                // Highlight whatever's actually previewing, if present in the current list.
                lastPlayedItemId = target.id,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                isDevMode = isDevMode,
                favoriteIds = favoriteIds,
                watchProgress = watchProgress,
                watchedIds = watchedIds,
                onCategorySelected = onCategorySelected,
                onStreamSelected = onStreamSelected,
                onRefresh = refreshContext,
                onStreamPromote = { item ->
                    // OK on another row plays it here: the target change re-points the SAME
                    // loader/engine (loadStreamLight above) — still only one connection ever.
                    // OK on the row playing goes full screen.
                    if (item.id != target.id) {
                        previewTarget = item
                    } else {
                        // Upgrade from the dock's light load (empty categoryStreams/
                        // recent-channel list — see loadStreamLight's doc
                        // comment) to a full load so the promoted full-screen view has real category/
                        // last-watched lists and actually records watch history. Safe for the
                        // channel already previewing: playback itself is driven by a
                        // separate LaunchedEffect keyed on the streamId *value*, which doesn't change
                        // here, so this only refreshes metadata — it does not restart the stream.
                        loader.loadStream(item)
                        fullScreen = true
                    }
                },
                onLeftFromFirstTab = onBack,
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
private fun neighborChannel(
    streams: ImmutableMediaList?,
    currentId: String,
    direction: Int,
): MediaItem? {
    val list = streams?.takeIf { it.isNotEmpty() }
    val neighbor =
        list?.let {
            val currentIndex = it.indexOfFirst { item -> item.id == currentId }.takeIf { index -> index != -1 } ?: 0
            it[(currentIndex + direction).mod(it.size)]
        }
    return neighbor
}

private fun previewMetadata(s: StreamLoaderViewModel.StreamState.Success) =
    PlayerMetadata(
        title = s.streamName,
        channelName = s.streamName,
        description = s.description,
        streamUrl = s.streamUrl,
        isLive = s.isLive,
        headers = s.streamHeaders,
        programTitle = s.currentEpgProgram?.title,
    )
