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
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.WaitIndicator
import org.njarasoa.fijerena.core.ui.components.WaitKind
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
    /** The selection's key: id and source (multi-source plan, Phase 6). */
    selectedCategoryKey: String?,
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
     * A child profile's (docs/plans/20261019_kids-home-plan.md, Phase 2): the Favourites list only,
     * no Recent or category tab, no row menu — it zaps through what its Home shows.
     */
    favoritesOnly: Boolean = false,
) {
    val context = LocalContext.current
    val categoryMap = remember(categories) { categories.associateBy { it.key } }
    // This list's own source; a row that leaves its source 0 is on it. Recent and Favourites mix
    // the profile's sources, each row on its own (multi-source plan, Phases 4–5).
    val listSource = categoryViewModel.sourceId

    // The channel the preview plays: set by the entry seed below and by OK on a row (P8 of
    // the sources guide profiles plan) — focus moves never change it, so moving
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

        // The channel on the source it was asked on, else the same id on any source of the list.
        fun channel(
            id: String,
            source: Long,
        ): MediaItem? = list.firstOrNull { it.id == id && it.sourceOr(listSource) == source } ?: list.firstOrNull { it.id == id }
        // The last channel only when no channel was asked for: a requested one this list doesn't have
        // (its category hidden by the profile, so the list is Recent) must not play the last channel
        // watched instead (the guide watch wrong channel plan, fix 1).
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
        // A separator row is a heading, never played — not even one recorded as watched before
        // they were headings (TV UI audit #24). Asked to open on one (Home → Live TV with it as
        // the last channel), the preview closes as Back would: browse, not an empty preview.
        when {
            seed == null -> Unit
            !seed.isSeparatorRow -> previewTarget = seed
            seed.id == initialStreamId -> onBack()
        }
    }

    // No setContentType(LIVE_TV) effect here: StreamingPlaybackService.playStream() picks the
    // buffer profile from metadata.isLive itself, so the preview and the promoted player are always
    // on the live profile. (The effect that used to do it could crash the app with an uncaught
    // ServiceDestroyedException — see the rock solid stability resilience plan F-03.)

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
    // For Diagnostics' dead D-pad entries (DeadKeyDetector): which layer was up.
    androidx.compose.runtime.DisposableEffect(fullScreen) {
        org.njarasoa.fijerena.ui.DeadKeyDetector.layer = if (fullScreen) "Live TV full screen" else "Live TV preview"
        onDispose { org.njarasoa.fijerena.ui.DeadKeyDetector.layer = null }
    }

    // 5% TV overscan safe margin — applied here (not by the caller) so the promoted full-screen
    // player below, which renders in place inside this same composable, stays edge-to-edge.
    val safeMarginModifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)

    val previewPaneShape = CinemaTheme.shapes.card

    // The context list (ChannelContext, LT2). The channel was chosen from one list — the browsed
    // category, Recent or Favourites: TvCategoryGridScreen passes that list's id as this entry's
    // category, so categoryViewModel's selectedCategoryId/streams *are* that list when it is a
    // real category; Home → Live TV lands on Recent. That one list is what the panel shows —
    // docked here and over the video in full screen (LT3) — and what Up/Down zap through in full
    // screen, until a tab switches it.
    // Saveable so the tab choice survives an activity recreate; keyed on the entry's category
    // because the first Success state arrives before it is resolved.
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
    // Again after each favourite change: a row removed from the Favourites tab goes, and its Undo
    // puts it back in its place (the list is sorted by name).
    val favoritesVersion by categoryViewModel.favoritesVersion.collectAsStateWithLifecycle()
    LaunchedEffect(favoritesVersion) {
        favoriteStreams = categoryViewModel.getFavoritesSnapshot()
        favoriteStreamsLoading = false
    }

    val target = previewTarget
    // Browse, rebuilt when Back closes this layer, lands on this channel (LT6) — the one tuned
    // now, after any zap or retune, not the one that opened the preview.
    val currentOnPlayingChannel by rememberUpdatedState(onPlayingChannel)
    LaunchedEffect(target?.sourceKey) {
        target?.let { currentOnPlayingChannel(it.id, it.sourceOr(listSource)) }
    }
    // INCLUDE keeps the current channel reachable even before the delayed history write lands,
    // so there's always a row to OK-press for promote, and a zap neighbour. Not done for
    // Favorites — there it's expected the current channel may simply not be one, same as any
    // other list the user browses to. Remembered, not built inline: an unremembered wrapper
    // hands StreamList a new `streams` identity on every recomposition of this screen, which
    // re-runs its `remember(streams)` blocks — re-arming the entrance animation for every
    // visible row and, with it, a per-frame invalidateMeasurement loop.
    val recentWithCurrent =
        remember(recentStreams, target?.sourceKey) {
            ImmutableMediaList(recentStreams.withCurrentChannel(target, CurrentChannelPolicy.INCLUDE, listSource))
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

    // The panel opens on a list that has the playing channel, so its row takes focus and OK keeps
    // it: a Live TV restored on another category (or the list a channel was picked from no longer
    // holding it) opened on that category's first row, and OK tuned a channel nobody wanted. Checked
    // when the channel changes, not on a tab picked by hand: first the channel's own category, then
    // Recent, which always has the playing channel.
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
        // Nothing playing yet (streams still loading, or no channel to seed with) — the list only,
        // until OK picks a channel.
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
                isDevMode = isDevMode,
                favoriteIds = favoriteIds,
                watchProgress = watchProgress,
                watchedIds = watchedIds,
                onCategorySelected = onCategorySelected,
                onStreamSelected = onStreamSelected,
                onStreamPromote = { item -> previewTarget = item },
                onRefresh = refreshContext,
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
    // The loader a pick on another source left: nothing of it may land any more (its delayed
    // Recent write would record a channel no longer playing). One of this source used earlier in
    // the layer is still on its old channel: it loads this one (the effect below skips full screen).
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
    // The channel the preview's watchdog stopped for not starting in time (below).
    var stalledId by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(target.sourceKey) {
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
    // patch it into what is playing, for live sync's "now playing"; the channel icon likewise (see PlaybackViewModel.updateMetadata).
    LaunchedEffect(success?.streamUrl, success?.currentEpgProgram?.title, success?.logoUrl) {
        val s = success ?: return@LaunchedEffect
        playback.updateMetadata(s.streamUrl) { it.copy(programTitle = s.currentEpgProgram?.title, logoUrl = s.logoUrl) }
    }

    // Another device of the sync group stopped this playback — preview or promoted full-screen
    // alike: finalise and release as leaving Live TV does, then go Home. See
    // the live sync now playing plan → Remote Stop.
    RemoteStopEffect {
        finalizeSessionAndAwait(playback.playbackState.value, loader)
        playback.stopAndRelease()
        onHome()
    }

    // Dead-stream watchdog: stop trying rather than let a bad channel buffer in the background
    // indefinitely (background churn on a preview stream caused periodic main-thread ANRs). 15 s:
    // a slow free channel took 5 s to its first byte and was stopped at 8 s while it was coming.
    // The channel it stopped says so over the preview ([stalledId]) instead of "Tuning" forever.
    LaunchedEffect(success?.streamId) {
        val s = success ?: return@LaunchedEffect
        stalledId = null
        val reachedPlaying =
            withTimeoutOrNull(PREVIEW_START_TIMEOUT_MS) {
                // playbackState is a plain StateFlow, not Compose snapshot state — wrapping it in
                // snapshotFlow{} never registers an observable read, so it emits once and then
                // never again, making first{} hang the full timeout regardless of whether playback
                // actually started. first{} on the StateFlow directly works correctly.
                playback.playbackState.first { it is PlaybackState.Playing }
            }
        if (reachedPlaying == null && success.streamId == s.streamId && !fullScreen) {
            playback.stop()
            stalledId = s.streamId
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
    // See the rock solid stability resilience plan → F-16.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSuccess by rememberUpdatedState(success)
    var resumeOnReturn by remember { mutableStateOf(false) }
    // Start over handed the one player to the catch-up screen: leaving this screen must then
    // neither pause nor release it (the catch-up is already playing on it). Not saveable: the
    // screen composes afresh on return, and the preview plays again.
    var handedOff by remember { mutableStateOf(false) }
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
                    // Opened on a tab with no rows (no favourites, or the last one removed), focus
                    // was left on the tab row: open on the tab that has the playing channel
                    // instead — the category it came from, else Recent — on its row. Switched
                    // before the panel composes: its empty tab would take focus and, focus
                    // following selection, select itself again.
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
                            isDevMode = isDevMode,
                            favoriteIds = favoriteIds,
                            watchProgress = watchProgress,
                            watchedIds = watchedIds,
                            onCategorySelected = onCategorySelected,
                            onStreamSelected = onStreamSelected,
                            onStreamPromote = { newItem ->
                                // Switching channels while already full-screen is a real "commit to
                                // watching" — use the full loadStream (with side effects), still on
                                // the SAME loader/engine. A channel of another source gets its own
                                // loader, which starts on it.
                                previewTarget = newItem
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
                    neighborChannel(contextStreams, target, +1, listSource)?.let { newItem ->
                        previewTarget = newItem
                        loader.loadStream(newItem, zapped = true)
                    }
                },
                onPreviousChannel = {
                    neighborChannel(contextStreams, target, -1, listSource)?.let { newItem ->
                        previewTarget = newItem
                        loader.loadStream(newItem, zapped = true)
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
                            .background(CinemaTheme.colors.surface),
                ) {
                    // TextureView (not the default SurfaceView): this box sits next to the
                    // scrolling/recomposing channel list, and SurfaceView there stalls the main
                    // thread and ANRs. Full-screen playback above uses PlayerScreen's own
                    // default surface instead of sharing this one — promoting/demoting now does
                    // a real detach/reattach (one frame's worth of glitch) rather than relocating
                    // this node, but it stops every OSD/flyout interaction in full-screen from
                    // janking the video for the rest of the session. See
                    // the tv ui performance plan's dropped "do not touch" note on this
                    // TextureView for why that tradeoff reverted.
                    EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize(), useTextureView = true)
                    // The preview surface has no controls/error UI of its own, so a stalled or
                    // watchdog-killed stream would otherwise look identical to a live frozen
                    // frame. Surface the state so it reads as "still loading", not "broken" —
                    // naming the channel while it is being tuned (LT5).
                    if (tuningName != null) {
                        TvTuningOverlay(
                            channelName = tuningName,
                            logoUrl = target.thumbnailUrl,
                            compact = true,
                            stalled =
                                stalledId == target.id,
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

                // Below the video (LT5): name · category, Now with its progress and Next when the
                // guide has them, and the one key hint. The guide lines are the target's only —
                // while a retune resolves, the loader still holds the previous channel's.
                // No "LIVE PREVIEW" badge any more: it marked this as the preview layer, distinct
                // from the bare browse list behind it, and the video with the hint line under it
                // already does (docs/UX_FLOW_AUDIT.md, "Live TV back-stopover").
                // TODO: the channel number goes before the name once metadata carries one.
                // One hierarchy (TV UI audit #4): the channel's name, its category under it, then
                // Now (with its progress) and Next — and the key hint right below, where it
                // explains what is above it, not floating at the bottom of the screen.
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
                    if (!item.isSameChannel(target, listSource)) {
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
                modifier = Modifier.weight(0.34f).fillMaxHeight(),
            )
        }
    }
}

/** [LiveTvSplitLayout]'s check that the panel's list has the playing channel: finished. */
private const val CONTEXT_CHECK_DONE = 2

/**
 * Computes the channel next to [current] in [streams], wrapping around. Used for full-screen
 * Up/Down channel switching — computed off the currently-displayed list rather than the loader's
 * internal index, which [StreamLoaderViewModel.loadStreamLight] (the preview re-point path) never
 * keeps in sync with what's actually on screen. Separator rows are skipped: a heading is never
 * zapped to (TV UI audit #24). So are channels of other sources than [current]'s (Recent and
 * Favourites mix the profile's sources): Up/Down stays on the playing channel's source, in the
 * list's order, wrapping within it (multi-source plan, Q4). A row that leaves its source 0 is on
 * [listSource].
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

/** How long the preview waits for a channel to play before giving up on it. */
private const val PREVIEW_START_TIMEOUT_MS = 15_000L
