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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import org.njarasoa.fijerena.core.player.api.XtreamStreamUrl
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.CatchupAvailability
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.categoryKey
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.domain.sourceOr
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.elapsedFraction
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BadgedTitle
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.WaitIndicator
import org.njarasoa.fijerena.core.ui.components.WaitKind
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.sync.PlayHandoverEffect
import org.njarasoa.fijerena.core.ui.sync.RemoteStopEffect
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CurrentChannelPolicy
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.finalizeSessionAndAwait
import org.njarasoa.fijerena.core.ui.viewmodels.isSameChannel
import org.njarasoa.fijerena.core.ui.viewmodels.withCurrentChannel
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.belowTvClock
import org.njarasoa.fijerena.ui.player.PlayerScreen
import org.njarasoa.fijerena.ui.player.components.overlays.TvTuningOverlay
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Live-TV-only split layout: a small preview player + now/next EPG card on the left, the channel
 * list on the right. See the Live TV preview pane plan.
 *
 * The preview plays what OK picks, never what focus rests on: moving through the list or across its
 * tabs leaves the playing channel alone. OK on another row stops the channel playing at once and
 * tunes the preview to the new one; OK on the row playing goes full screen once it shows a picture
 * (UX-D6). A channel whose load fails, or that shows no picture in 10 s, is stopped and said to be
 * not available right now; OK then goes to the next channel of the list (UX-D2).
 *
 * Full screen does not navigate to another destination: it promotes the preview's playback (same
 * PlaybackViewModel, StreamLoaderViewModel and engine connection) to a full-screen PlayerScreen in
 * place. A second PlaybackViewModel (navigating to Screen.Player) caused a main-thread ANR
 * (QueuedWork.waitToFinish racing the new Service/MediaSession connection).
 */
@Composable
internal fun LiveTvSplitLayout(
    categoryViewModel: CategoryViewModel,
    categories: ImmutableCategoryList,
    selectedCategoryId: String?,
    /** The selection's key: id and source (multi-source plan, Phase 6). */
    selectedCategoryKey: String?,
    streams: ImmutableMediaList?,
    streamsLoading: Boolean,
    categoriesRefreshing: Boolean,
    lastPlayedItemId: String?,
    nowPlaying: ImmutableNowPlaying,
    contentType: String,
    favoriteIds: ImmutableStringSet,
    favoriteCategoryIds: ImmutableStringSet,
    watchProgress: ImmutableWatchProgress,
    watchedIds: ImmutableStringSet,
    onCategorySelected: (categoryId: String, source: Long) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, target: BrowseTarget, providerId: Long) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    initialStreamId: String? = null,
    /** [initialStreamId]'s source, when it was picked from a list that mixes sources; 0 = this list's. */
    initialStreamSource: Long = 0L,
    /** Called with the channel this screen plays (and its source) each time that changes, for Back (LT6). */
    onPlayingChannel: (streamId: String, providerId: Long) -> Unit = { _, _ -> },
    /**
     * Opens the TV Guide for a list on a channel's row — the full-screen OSD's Guide button (GD5).
     * Null (the source has no guide) leaves the button out.
     */
    onOpenGuide: ((categoryId: String, categoryName: String, focusChannelId: String?, providerId: Long) -> Unit)? = null,
    /**
     * The full-screen OSD's Start over: the programme on air from its start, from the channel's
     * archive (catch-up). Shown only when the archive holds it; null leaves the button out.
     */
    onStartOver: ((program: EpgProgram, channel: MediaItem) -> Unit)? = null,
    /**
     * A child profile's (the kids' Home plan, Phase 2): the Favourites list only, no Recent or
     * category tab, no row menu.
     */
    favoritesOnly: Boolean = false,
) {
    val context = LocalContext.current
    val categoryMap = remember(categories) { categories.associateBy { it.key } }
    // This list's own source; a row that leaves its source 0 is on it. Recent and Favourites mix
    // the profile's sources, each row on its own (multi-source plan, Phases 4–5).
    val listSource = categoryViewModel.sourceId

    // The channel the preview plays: set by the entry seed below and by OK on a row (P8 of the sources
    // guide profiles plan). Focus moves never change it.
    var previewTarget by remember { mutableStateOf<MediaItem?>(null) }

    // Seed the preview once, when the streams have loaded (hasSeeded stops it on later refreshes):
    // - initialStreamId set: the user picked this channel (EPG search, catalogue search, the category
    //   guide).
    // - Else the last-watched channel, so entry never lands on a bare list. (Home → Live TV passes the
    //   last channel as initialStreamId, and this preview is a layer over browse there, LT7.)
    var hasSeeded by remember { mutableStateOf(false) }
    // The channel full screen was showing when the OSD's Guide button left this screen (GD5): Back
    // from the guide rebuilds this screen, and it comes back to that channel, not the entry one.
    var guideReturnStreamId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(streams) {
        if (hasSeeded || previewTarget != null) return@LaunchedEffect
        val list = streams ?: return@LaunchedEffect

        // The channel on the source it was asked on, else the same id on any source of the list.
        fun channel(
            id: String,
            source: Long,
        ): MediaItem? = list.firstOrNull { it.id == id && it.sourceOr(listSource) == source } ?: list.firstOrNull { it.id == id }
        // The last channel only when no channel was asked for: a requested one this list does not have
        // (its category hidden by the profile) must not play the last channel instead (the guide watch
        // wrong channel plan, fix 1).
        val seed =
            if (guideReturnStreamId != null || initialStreamId != null) {
                guideReturnStreamId?.let { id -> channel(id, listSource) }
                    ?: initialStreamId?.let { id -> channel(id, initialStreamSource.takeIf { it != 0L } ?: listSource) }
            } else {
                lastPlayedItemId?.let { id -> channel(id, listSource) }
            }
        // A channel asked for by id and not in this list yet waits for the next one: after process
        // death the list comes back as Recent first, then the category the preview was on.
        if (seed == null && (guideReturnStreamId != null || initialStreamId != null)) return@LaunchedEffect
        hasSeeded = true
        // A separator row is a heading, never played, also when recorded as watched (TV UI audit #24).
        // Asked to open on one, the preview closes as Back would: browse, not an empty preview.
        when {
            seed == null -> Unit
            !seed.isSeparatorRow -> previewTarget = seed
            seed.id == initialStreamId -> onBack()
        }
    }

    // No setContentType(LIVE_TV) effect: StreamingPlaybackService.playStream() picks the live buffer
    // profile from metadata.isLive, and that effect could crash with ServiceDestroyedException (the
    // rock solid stability resilience plan, F-03).

    // Saveable: Back from the TV Guide opened by the OSD's Guide button returns to full screen.
    var fullScreen by rememberSaveable { mutableStateOf(false) }

    // Back: in full screen, go back to the split; the preview keeps playing. Else onBack: close this
    // layer to the browse list under it (LT7), or go back to the search/EPG screen that opened it.
    // BackHandler, not a key-event intercept: consuming the key does not stop the NavController's own
    // back callback, so an intercept would pop twice and leave the app.
    androidx.activity.compose.BackHandler {
        if (fullScreen) fullScreen = false else onBack()
    }
    // For Diagnostics' dead D-pad entries (DeadKeyDetector): which layer was up.
    androidx.compose.runtime.DisposableEffect(fullScreen) {
        org.njarasoa.fijerena.ui.DeadKeyDetector.layer = if (fullScreen) "Live TV full screen" else "Live TV preview"
        onDispose { org.njarasoa.fijerena.ui.DeadKeyDetector.layer = null }
    }

    // 5% TV overscan safe margin, applied here and not by the caller, so the full-screen player that
    // renders inside this composable stays edge to edge.
    val safeMarginModifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)

    val previewPaneShape = CinemaTheme.shapes.card

    // The context list (ChannelContext, LT2): the list the channel was chosen from (the browsed
    // category, Recent or Favourites). TvCategoryGridScreen passes that list's id as this entry's
    // category, so categoryViewModel's selection is that list when it is a real category; Home → Live
    // TV lands on Recent. The panel shows it, docked and over full screen (LT3), and Up/Down zap through
    // it in full screen, until a tab switches it.
    // Saveable, so the tab survives an activity recreate; keyed on the entry's category because the
    // first Success arrives before it is resolved.
    val pickedCategory =
        remember(selectedCategoryKey, categoryMap) {
            selectedCategoryId
                ?.takeUnless { it in CategoryViewModel.VIRTUAL_CATEGORY_IDS }
                ?.let { id ->
                    val category = selectedCategoryKey?.let { categoryMap[it] }
                    ChannelContext.Category(id = id, name = category?.name ?: id, source = category?.providerId ?: 0L)
                }
        }
    var channelContext by
        rememberSaveable(selectedCategoryKey, stateSaver = ChannelContext.Saver) {
            mutableStateOf(
                if (favoritesOnly || selectedCategoryId == CategoryViewModel.FAVORITES_CATEGORY_ID) {
                    ChannelContext.Favorites
                } else {
                    pickedCategory ?: ChannelContext.Recent
                },
            )
        }
    val contextTabs =
        remember(pickedCategory) {
            if (favoritesOnly) {
                listOf(
                    ChannelContext.Favorites,
                )
            } else {
                listOfNotNull(pickedCategory, ChannelContext.Recent, ChannelContext.Favorites)
            }
        }

    // Newest first, as the repository orders it (the Live TV Recent order plan): the
    // order changes only when a channel is written, which follows a change of channel.
    val publishedRecentStreams by categoryViewModel.recentItems.collectAsStateWithLifecycle()
    val recentStreams = publishedRecentStreams.orEmpty()
    var favoriteStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteStreamsLoading by remember { mutableStateOf(true) }
    val composableScope = rememberCoroutineScope()
    // Again after each favourite change: a row removed from the Favourites tab goes, and its Undo
    // puts it back in its place (the list is sorted by name).
    val favoritesVersion by categoryViewModel.favoritesVersion.collectAsStateWithLifecycle()
    LaunchedEffect(favoritesVersion) {
        favoriteStreams = categoryViewModel.getFavoritesSnapshot()
        favoriteStreamsLoading = false
    }

    val target = previewTarget
    // Browse, rebuilt when Back closes this layer, lands on this channel (LT6): the one tuned now,
    // after any zap, not the one that opened the preview.
    val currentOnPlayingChannel by rememberUpdatedState(onPlayingChannel)
    LaunchedEffect(target?.sourceKey) {
        target?.let { currentOnPlayingChannel(it.id, it.sourceOr(listSource)) }
    }
    // FIRST: the playing channel is the most recent, so it is the top row before its delayed write
    // lands. Not done for Favourites: the current channel may not be one. Remembered, not built inline:
    // a new `streams` identity on each recomposition re-runs StreamList's `remember(streams)` blocks,
    // which restarts the entrance animation and a per-frame invalidateMeasurement loop.
    val recentWithCurrent =
        remember(recentStreams, target?.sourceKey) {
            ImmutableMediaList(recentStreams.withCurrentChannel(target, CurrentChannelPolicy.FIRST, listSource))
        }
    val favoriteList = remember(favoriteStreams) { ImmutableMediaList(favoriteStreams) }
    // Up/Down in full screen walk the list as it was at the first zap: with the playing channel on
    // top of Recent, the live list would send Down back to the channel just left. Taken again
    // after a pick, a tab change, or leaving full screen.
    var zapOrder by remember(channelContext, fullScreen) { mutableStateOf<List<MediaItem>?>(null) }

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
                composableScope.launch { categoryViewModel.refreshRecentItems() }
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

    // The panel opens on a list that has the playing channel, so its row takes focus and OK keeps it;
    // else OK on the first row of another category tuned an unwanted channel. Checked when the channel
    // changes, not on a tab picked by hand: first the channel's own category, then Recent, which always
    // has it.
    var contextCheckStep by remember(target?.sourceKey) { mutableStateOf(0) }
    LaunchedEffect(target?.sourceKey, contextCheckStep, contextStreams, contextLoading) {
        val playing = target ?: return@LaunchedEffect
        if (favoritesOnly || contextCheckStep >= CONTEXT_CHECK_DONE || contextLoading || contextStreams == null) return@LaunchedEffect
        if (contextStreams.any { it.isSameChannel(playing, listSource) }) {
            contextCheckStep = CONTEXT_CHECK_DONE
            return@LaunchedEffect
        }
        val source = playing.sourceOr(listSource)
        val ownCategory = playing.categoryId.takeIf { it.isNotBlank() && categoryMap.containsKey(categoryKey(it, source)) }
        if (contextCheckStep == 0 && ownCategory != null && selectedCategoryKey != categoryKey(ownCategory, source)) {
            contextCheckStep = 1
            onCategorySelected(ownCategory, source)
        } else {
            contextCheckStep = CONTEXT_CHECK_DONE
            channelContext = ChannelContext.Recent
        }
    }

    if (target == null) {
        // Nothing playing yet (streams loading, or no channel to seed with): the list only, until OK picks
        // a channel.
        AmbientBackdrop(modifier = Modifier.fillMaxSize())
        Row(modifier = safeMarginModifier, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Box(modifier = Modifier.weight(0.66f).fillMaxHeight())
            LiveTvChannelPanel(
                tabs = contextTabs,
                rowActions = !favoritesOnly,
                context = channelContext,
                onContextSelected = { channelContext = it },
                streams = contextStreams,
                streamsLoading = contextLoading,
                lastPlayedItemId = lastPlayedItemId,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                favoriteIds = favoriteIds,
                watchProgress = watchProgress,
                watchedIds = watchedIds,
                onCategorySelected = onCategorySelected,
                onStreamSelected = onStreamSelected,
                onStreamPromote = { item -> previewTarget = item },
                onRefresh = refreshContext,
                // Its tab row starts under the screens' clock.
                modifier = Modifier.weight(0.34f).fillMaxHeight().belowTvClock(),
            )
        }
        return
    }

    // One playback connection for this screen, shared by the preview and the full-screen player.
    // Created once per visit and pointed at each new channel with loadStream(Light), never recreated.
    val playback: PlaybackViewModel = viewModel()
    val previewPlaybackState by playback.playbackState.collectAsStateWithLifecycle()

    // The channel plays from its own source (multi-source plan, Phase 5): a pick from Recent or
    // Favourites on another source gets that source's loader, made on the channel. Up/Down never
    // leaves the playing channel's source (neighborChannel), so a zap keeps the loader.
    val targetSource = target.sourceOr(listSource)
    val loader: StreamLoaderViewModel =
        viewModel(
            key = "loader:$targetSource",
            factory =
                StreamLoaderViewModelFactory(
                    context = context,
                    initialStreamId = target.id,
                    initialStreamName = target.name,
                    categoryId = target.categoryId,
                    contentType = contentType,
                    providerId = targetSource,
                ),
        )
    // The loader a pick on another source left: nothing of it may land (its delayed Recent write would
    // record a channel no longer playing). A loader of this source used earlier in the layer is still
    // on its old channel: it loads this one (the effect below skips full screen).
    var activeLoader by remember { mutableStateOf<StreamLoaderViewModel?>(null) }
    LaunchedEffect(loader) {
        val previous = activeLoader
        activeLoader = loader
        if (previous != null && previous !== loader) {
            previous.abandon()
            if (loader.requestedStreamId != target.id) {
                if (fullScreen) loader.loadStream(target) else loader.loadStreamLight(target)
            }
        }
    }
    val streamState by loader.state.collectAsStateWithLifecycle()
    val success = streamState as? StreamLoaderViewModel.StreamState.Success

    // Zap feedback (LT5): "Tuning · <channel>" from the moment the target changes until the engine
    // plays that channel. Display state only, derived from what the loader and engine publish. The
    // engine keeps the old channel until the loader resolves the new one, so "playing" means the
    // engine's stream is the target's resolved URL and it reached Playing. tunedId remembers that, so a
    // later rebuffer of the same channel does not show as a tune. Hidden on a loader or playback error,
    // which have their own UI.
    val playingMetadata by playback.currentMetadata.collectAsStateWithLifecycle()
    // sameStream, not ==: the engine may have moved the stream to another login of the source.
    val targetPlaying =
        success != null &&
            success.streamId == target.id &&
            XtreamStreamUrl.sameStream(playingMetadata.streamUrl, success.streamUrl) &&
            previewPlaybackState is PlaybackState.Playing
    var tunedId by remember { mutableStateOf<String?>(null) }
    // The channel this screen gave up on: its load failed, or it showed no picture in time (below).
    var unavailableId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(targetPlaying, target.id) {
        if (targetPlaying) {
            tunedId = target.id
            unavailableId = null
        }
    }
    val unavailable = unavailableId == target.id && !targetPlaying
    val tuningName =
        target.name.takeIf {
            target.id != tunedId &&
                !targetPlaying &&
                !unavailable &&
                streamState !is StreamLoaderViewModel.StreamState.Error &&
                previewPlaybackState !is PlaybackState.Error
        }

    // A new pick stops the playing channel at once, so its picture never shows under the new name
    // (UX-D2). Now, not through the view model's coroutine, which could land after the new channel
    // started. [tuneTick] lets the play effect below start a channel whose Success the loader still
    // holds (a quick pick away and back).
    var tuneTick by remember { mutableIntStateOf(0) }
    val tune: (MediaItem) -> Unit = { item ->
        if (!item.isSameChannel(target, listSource)) {
            StreamingPlaybackService.getInstance()?.stop()
            tuneTick++
        }
        previewTarget = item
    }

    // Gives up on the target: stops it, drops its load and its Recent write, and says so.
    val giveUp: () -> Unit = {
        StreamingPlaybackService.getInstance()?.stop()
        loader.abandon()
        unavailableId = target.id
    }

    // Point the loader at each new previewed channel with the light path (no channel-switcher list
    // fetch; the preview does not show that list). Watch history is still recorded after the usual
    // delay. Skipped in full screen: its handlers already call loader.loadStream(), and two loads on
    // the same loadJob would race. Also skipped for the channel the loader was made with, which it is
    // already resolving.
    LaunchedEffect(target.sourceKey) {
        if (!fullScreen && loader.requestedStreamId != target.id) {
            loader.loadStreamLight(target)
        }
    }

    LaunchedEffect(success?.streamId, tuneTick) {
        // Only the target's own Success: an older one still held while a new pick loads must not
        // start the channel just stopped.
        val s = success?.takeIf { it.streamId == target.id }
        // A promote upgrades the dock's light load to a full load (onStreamPromote below), which flips this
        // state to Loading and back: two key changes that would restart a playing stream. Skip when this URL
        // already plays.
        val alreadyPlayingThis =
            s != null &&
                playback.currentMetadata.value.streamUrl == s.streamUrl &&
                playback.playbackState.value !is PlaybackState.Idle &&
                playback.playbackState.value !is PlaybackState.Error
        if (s != null && !alreadyPlayingThis) {
            playback.playStream(previewMetadata(s), s.resumePosition)
        }
    }

    // The guide lookup lands after playback started, and the programme changes while it plays: patch
    // it and the channel icon into what plays, for live sync's "now playing" (see
    // PlaybackViewModel.updateMetadata).
    LaunchedEffect(success?.streamUrl, success?.currentEpgProgram?.title, success?.logoUrl) {
        val s = success ?: return@LaunchedEffect
        playback.updateMetadata(s.streamUrl) { it.copy(programTitle = s.currentEpgProgram?.title, logoUrl = s.logoUrl) }
    }

    // Another device of the sync group stopped this playback (preview or full screen): finalise and
    // release as leaving Live TV does, then go Home (the live sync now playing plan, Remote Stop).
    RemoteStopEffect {
        finalizeSessionAndAwait(playback.playbackState.value, loader)
        playback.stopAndRelease()
        onHome()
    }

    // Dead-stream watchdog (UX-D2): a channel with no picture 10 s after the pick is stopped, not left
    // to buffer in the background (that churn caused main-thread ANRs), and the video says so instead of
    // "Tuning" for ever. A failed load says so at once.
    val currentTargetPlaying by rememberUpdatedState(targetPlaying)
    // Picture lost after it came: the engine waits or fails again (not a pause, not a stop).
    val currentPictureLost by rememberUpdatedState(
        previewPlaybackState is PlaybackState.Buffering || previewPlaybackState is PlaybackState.Error,
    )
    LaunchedEffect(target.sourceKey) {
        unavailableId = null
        // targetPlaying reads Compose state (the collected flows), so snapshotFlow sees it change.
        // A channel that plays a moment and then fails over and over gets the same 10 s each time.
        while (true) {
            val played = withTimeoutOrNull(CHANNEL_START_TIMEOUT_MS) { snapshotFlow { currentTargetPlaying }.first { it } }
            if (played == null) {
                giveUp()
                break
            }
            snapshotFlow { currentPictureLost }.first { it }
        }
    }
    LaunchedEffect(streamState, target.id) {
        val error = streamState as? StreamLoaderViewModel.StreamState.Error ?: return@LaunchedEffect
        if (error.streamId == null || error.streamId == target.id) giveUp()
    }

    // Pause when this screen is not RESUMED; resume on return. On final disposal (leaving Live TV) use
    // stopAndRelease, not stop: this is the only place that tears the preview player down, and stop()
    // kept the native decoder/renderer buffers in memory.
    // Resume means play again, at the live edge: ON_PAUSE pauses the preview and stops it after 30 s
    // away (screensaver, HDMI input switch). Only when it was playing as the screen paused: a user's own
    // pause in full screen stays. See the rock solid stability resilience plan, F-16.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSuccess by rememberUpdatedState(success)
    var resumeOnReturn by remember { mutableStateOf(false) }
    // Start over handed the one player to the catch-up screen: leaving this screen must then
    // neither pause nor release it (the catch-up is already playing on it). Not saveable: the
    // screen composes afresh on return, and the preview plays again.
    var handedOff by remember { mutableStateOf(false) }

    // Send to TV's Play: as RemoteStopEffect's save, but the player goes on to the sent item.
    PlayHandoverEffect {
        finalizeSessionAndAwait(playback.playbackState.value, loader)
        StreamingPlaybackService.getInstance()?.stop()
        handedOff = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        if (!handedOff) {
                            val state = playback.playbackState.value
                            resumeOnReturn = state is PlaybackState.Playing || state is PlaybackState.Buffering
                            playback.onFocusLost(false)
                        }
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
            if (!handedOff) playback.stopAndRelease()
        }
    }

    if (fullScreen) {
        // Refresh the preview's Favourites list when full screen closes, so it shows what was just
        // (un)favourited: nothing else fetches it again, as this screen stays. Recent needs none: the
        // player's delayed history write publishes the shared list again.
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
                isFavorite = favoriteIds.contains(sourceKey(targetSource, target.id)),
                onToggleFavorite =
                    if (favoritesOnly) {
                        null
                    } else {
                        { categoryViewModel.toggleFavoriteStream(target.id, target.name, target.categoryId, contentType, targetSource) }
                    },
                currentEpgProgram = success?.currentEpgProgram,
                nextEpgProgram = success?.nextEpgProgram,
                currentStreamId = success?.streamId,
                tuningChannelName = tuningName,
                tuningLogoUrl = target.thumbnailUrl,
                unavailableChannelName = target.name.takeIf { unavailable },
                // Guide (GD5): the TV Guide of the list being zapped through, on this channel's row.
                onShowGuide =
                    onOpenGuide?.let { open ->
                        val contextId = channelContext.id
                        val contextName = channelContext.label()
                        val showGuide: () -> Unit = {
                            guideReturnStreamId = target.id
                            // On the playing channel's source: its guide, its list.
                            open(contextId, contextName, target.id, targetSource)
                        }
                        showGuide
                    },
                // Start over: the programme on air, from the channel's archive. The preview's player
                // stops first (not released: that ends the playback service the catch-up starts on);
                // the catch-up player starts its own stream.
                onStartOver =
                    onStartOver?.let { start ->
                        success
                            ?.currentEpgProgram
                            ?.takeIf { CatchupAvailability.isPlayable(success.archiveDays, it, System.currentTimeMillis() / 1000) }
                            ?.let { program ->
                                val startOver: () -> Unit = {
                                    // Now, not through the view model's coroutine, which could land
                                    // after the catch-up has started on the same player.
                                    StreamingPlaybackService.getInstance()?.stop()
                                    handedOff = true
                                    start(program, target)
                                }
                                startOver
                            }
                    },
                // The same panel as the split's, over the video (LT3): same tabs, rows and row
                // actions, and its tabs switch channelContext — so the zap order below follows.
                channelPanel = { close ->
                    // Opened on a tab with no rows, focus stayed on the tab row: open on the tab that has the playing
                    // channel instead (its category, else Recent), on its row. Switched before the panel composes: the
                    // empty tab would take focus and, with focus-follow-select, select itself again.
                    val reopenOn =
                        remember {
                            if (!contextLoading && contextStreams?.isEmpty() == true) {
                                contextTabs.firstOrNull { tab -> streamsOf(tab)?.any { it.isSameChannel(target, listSource) } == true }
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
                            rowActions = !favoritesOnly,
                            context = channelContext,
                            onContextSelected = { channelContext = it },
                            streams = contextStreams,
                            streamsLoading = contextLoading,
                            lastPlayedItemId = target.id,
                            nowPlaying = nowPlaying,
                            contentType = contentType,
                            categoryViewModel = categoryViewModel,
                            favoriteIds = favoriteIds,
                            watchProgress = watchProgress,
                            watchedIds = watchedIds,
                            onCategorySelected = onCategorySelected,
                            onStreamSelected = onStreamSelected,
                            onStreamPromote = { newItem ->
                                // A channel switch in full screen commits to watching: use the full loadStream, on the same
                                // loader/engine. A channel of another source gets its own loader, which starts on it.
                                tune(newItem)
                                zapOrder = null
                                if (newItem.sourceOr(listSource) == targetSource) loader.loadStream(newItem)
                                close()
                            },
                            onRefresh = refreshContext,
                            overlay = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
                onNextChannel = {
                    val order = zapOrder ?: contextStreams
                    zapOrder = order
                    neighborChannel(order, target, +1, listSource)?.let { newItem ->
                        tune(newItem)
                        loader.loadStream(newItem, zapped = true)
                    }
                },
                onPreviousChannel = {
                    val order = zapOrder ?: contextStreams
                    zapOrder = order
                    neighborChannel(order, target, -1, listSource)?.let { newItem ->
                        tune(newItem)
                        loader.loadStream(newItem, zapped = true)
                    }
                },
            )
        }
    } else {
        // No blur: the preview's video is drawn over it, and a weak GPU stutters the video.
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = target.thumbnailUrl, blur = false)
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
                            .panel(PanelFill.Surface),
                ) {
                    // TextureView, not SurfaceView: next to the scrolling channel list, SurfaceView stalls the main
                    // thread and ANRs. Full screen uses PlayerScreen's own surface, so promoting does a detach/reattach
                    // (one frame of glitch) but full-screen OSD actions do not jank the video. See the tv ui performance
                    // plan's "do not touch" note on this TextureView.
                    EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize(), useTextureView = true)
                    // The preview has no controls or error UI, so a stalled or stopped stream would look like a live
                    // frozen frame. Show the state, with the channel name while it tunes (LT5).
                    if (unavailable) {
                        TvTuningOverlay(channelName = target.name, compact = true, unavailable = true)
                    } else if (tuningName != null) {
                        TvTuningOverlay(
                            channelName = tuningName,
                            logoUrl = target.thumbnailUrl,
                            compact = true,
                        )
                    } else if (previewPlaybackState !is PlaybackState.Playing) {
                        // A wait after the channel played: the same ring, without the dimming.
                        val name = parseDisplayTitle(target.name).title.ifBlank { target.name }
                        WaitIndicator(
                            kind = WaitKind.Live,
                            modifier = Modifier.align(Alignment.Center),
                            logoUrl = target.thumbnailUrl,
                            name = name,
                            contentDescription = stringResource(R.string.live_tuning_format, name),
                        )
                    }
                }

                // Below the video (LT5), one hierarchy (TV UI audit #4): the channel's name, its category, Now with
                // its progress, Next, then the key hint. Guide lines are the target's only: during a retune the
                // loader still holds the previous channel's. No "LIVE PREVIEW" badge: the video with the hint under
                // it marks the layer (docs/UX_FLOW_AUDIT.md, "Live TV back-stopover").
                // TODO: the channel number goes before the name once metadata carries one.
                val resolved = success?.takeIf { it.streamId == target.id }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    // The clean name with its badges, as in the player.
                    BadgedTitle(
                        raw = resolved?.streamName ?: target.name,
                        role = TextRole.Title,
                        tone = TextTone.Primary,
                    )
                    val categoryName =
                        target.categoryId
                            .takeUnless { it in CategoryViewModel.VIRTUAL_CATEGORY_IDS }
                            ?.let { categoryMap[it]?.name }
                    if (categoryName != null) {
                        CinemaText(text = categoryName, tone = TextTone.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                val nowProg = resolved?.currentEpgProgram
                val nextProg = resolved?.nextEpgProgram
                if (nowProg != null || nextProg != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (nowProg != null) {
                            CinemaText(
                                text = stringResource(R.string.epg_now_prefix, nowProg.title),
                                role = TextRole.Heading,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val fraction = nowProg.elapsedFraction()
                            CinemaProgressBar(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        }
                        if (nextProg != null) {
                            CinemaText(
                                text = stringResource(R.string.live_preview_next_format, nextProg.title),
                                role = TextRole.Heading,
                                tone = TextTone.Secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                CinemaText(
                    text = stringResource(R.string.live_preview_hint),
                    tone = TextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LiveTvChannelPanel(
                tabs = contextTabs,
                rowActions = !favoritesOnly,
                context = channelContext,
                onContextSelected = { channelContext = it },
                streams = contextStreams,
                streamsLoading = contextLoading,
                // Highlight the previewed channel, if it is in the current list.
                lastPlayedItemId = target.id,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                favoriteIds = favoriteIds,
                watchProgress = watchProgress,
                watchedIds = watchedIds,
                onCategorySelected = onCategorySelected,
                onStreamSelected = onStreamSelected,
                onRefresh = refreshContext,
                onStreamPromote = { item ->
                    // OK on another row plays it here: the target change points the same loader/engine at it
                    // (loadStreamLight above). OK on the playing row goes full screen once it shows a picture; before
                    // that OK does nothing (UX-D6). On a channel that did not start, OK goes to the next channel (UX-D2).
                    if (!item.isSameChannel(target, listSource)) {
                        tune(item)
                    } else if (unavailable) {
                        neighborChannel(zapOrder ?: contextStreams, target, +1, listSource)?.let(tune)
                    } else if (targetPlaying) {
                        // Upgrade the dock's light load to a full load, so full screen has the category and last-watched
                        // lists and records watch history (see loadStreamLight). Playback is keyed on the streamId value,
                        // which does not change, so this does not restart the stream.
                        loader.loadStream(item)
                        // Opening full screen is a choice: Recent now, not after the watch delay.
                        loader.addToRecentNow()
                        fullScreen = true
                    }
                },
                // Its tab row starts under the screens' clock.
                modifier = Modifier.weight(0.34f).fillMaxHeight().belowTvClock(),
            )
        }
    }
}

/** [LiveTvSplitLayout]'s check that the panel's list has the playing channel: finished. */
private const val CONTEXT_CHECK_DONE = 2

/**
 * The channel next to [current] in [streams], wrapping around, for full-screen Up/Down. Uses the
 * displayed list, not the loader's index, which [StreamLoaderViewModel.loadStreamLight] does not
 * keep in sync. Skips separator rows (TV UI audit #24) and channels of other sources than
 * [current]'s: Up/Down stays on the playing channel's source (multi-source plan, Q4). A row that
 * leaves its source 0 is on [listSource].
 */

internal fun neighborChannel(
    streams: List<MediaItem>?,
    current: MediaItem,
    direction: Int,
    listSource: Long = 0L,
): MediaItem? {
    val source = current.sourceOr(listSource)
    val list = streams?.filter { !it.isSeparatorRow && it.sourceOr(listSource) == source }?.takeIf { it.isNotEmpty() }
    val neighbor =
        list?.let {
            val currentIndex = it.indexOfFirst { item -> item.isSameChannel(current, listSource) }.takeIf { index -> index != -1 } ?: 0
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
        logoUrl = s.logoUrl,
        programTitle = s.currentEpgProgram?.title,
        sourceName = s.sourceName,
    )

/**
 * How long a picked channel may take to show a picture before the screen gives up on it (UX-D2).
 * A slow free channel took 5 s to its first byte.
 */
private const val CHANNEL_START_TIMEOUT_MS = 10_000L
