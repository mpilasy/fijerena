package org.njarasoa.fijerena.feature.category

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.browseTarget
import org.njarasoa.fijerena.core.player.domain.browseTargetFor
import org.njarasoa.fijerena.core.player.domain.isCategoryMarker
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.elapsedFraction
import org.njarasoa.fijerena.core.player.model.extractYear
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.components.LanguageBadge
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.SkeletonList
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.sync.RemoteStopEffect
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.CurrentChannelPolicy
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.finalizeSessionAndAwait
import org.njarasoa.fijerena.core.ui.viewmodels.partitionVirtual
import org.njarasoa.fijerena.core.ui.viewmodels.rememberStableRecentOrder
import org.njarasoa.fijerena.core.ui.viewmodels.withCurrentChannel
import org.njarasoa.fijerena.feature.player.MobilePlayerContent
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.cards.cinemaCardHairlineBorder
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileCategoryListScreen(
    contentType: String,
    initialCategoryId: String? = null,
    initialStreamId: String? = null,
    onStreamSelected: (
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        target: BrowseTarget,
    ) -> Unit,
    onSearchClick: () -> Unit = {},
    onEpgClick: (categoryId: String, categoryName: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    /** Leaves for the Live TV tab's root — after a remote Stop of the Live TV dock. */
    onHome: () -> Unit = {},
    /**
     * Reports the Live TV dock to the phone's bottom bar: [stopDock] stops it (null while nothing is
     * docked), [coversScreen] is true while the video takes the screen (full screen,
     * picture-in-picture, the landscape split).
     */
    onDockChanged: (stopDock: (() -> Unit)?, coversScreen: Boolean) -> Unit = { _, _ -> },
    /**
     * Set on a bottom-bar tab's root: its top bar (the source's, see `MobileSourceTopBar`) in place
     * of the section title, back arrow and TV Guide, given this section's Search.
     */
    sourceTopBar: (@Composable (onSearch: (() -> Unit)?) -> Unit)? = null,
    viewModel: CategoryViewModel =
        viewModel(
            factory =
                CategoryViewModelFactory(
                    context = LocalContext.current.applicationContext,
                    contentType = contentType,
                    initialCategoryId = initialCategoryId,
                ),
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlayingMap by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val nowPlaying = remember(nowPlayingMap) { ImmutableNowPlaying(nowPlayingMap) }
    val supportsNativeEpg by viewModel.supportsNativeEpg.collectAsStateWithLifecycle()
    val watchedIdsSet by viewModel.watchedIds.collectAsStateWithLifecycle()
    val watchedIds = remember(watchedIdsSet) { ImmutableStringSet(watchedIdsSet) }
    val favoriteIdsSet by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteIds = remember(favoriteIdsSet) { ImmutableStringSet(favoriteIdsSet) }
    val watchProgressMap by viewModel.watchProgress.collectAsStateWithLifecycle()
    val watchProgress = remember(watchProgressMap) { ImmutableWatchProgress(watchProgressMap) }
    val favoriteCategoryIdsSet by viewModel.favoriteCategoryIds.collectAsStateWithLifecycle()
    val favoriteCategoryIds = remember(favoriteCategoryIdsSet) { ImmutableStringSet(favoriteCategoryIdsSet) }
    val context = LocalContext.current
    // Null-safe instead of a hard `context as ComponentActivity` cast (F-37).
    val activity = LocalActivity.current as? ComponentActivity
    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
    val epgIndexState by epgIndexer.state.collectAsStateWithLifecycle()

    // Refresh last played item when returning from player
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // The first RESUMED is this screen's own initial composition, which just ran the load
        // that populates uiState/favoriteIds/watchProgress/watchedIds from scratch — re-running
        // the same per-item refresh here duplicates that work (confirmed via logcat, TV side: two
        // full refreshPerItemData passes back to back on every entry). Only a *later* RESUMED means
        // an actual return from elsewhere, which is what this refresh exists for.
        var isFirstResume = true
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!isFirstResume) {
                viewModel.refreshLastPlayedItem()
                // A watched/favorite mark made on a screen this ViewModel doesn't own (movie details,
                // an episode list, search) has no way back to this instance's cache otherwise.
                viewModel.refreshWatchStateOnResume()
            }
            isFirstResume = false
        }
    }

    // Tap-driven equivalent of TV's preview pane (tv/.../LiveTvSplitLayout.kt): a
    // small always-playing mini-player docked above the channel list, promotable to full screen
    // in place (same engine, same ViewModel pair — never a second connection). Unlike TV, mobile
    // commits immediately on tap (no "light" preview step — a tap is already a
    // deliberate choice to watch) and does not auto-seed from the app-wide last-played channel on
    // a bare entry (no passive "focus" state to preview from on touch).
    val isLiveTv = contentType == ContentType.LIVE_TV
    var dockTarget by remember { mutableStateOf<MediaItem?>(null) }
    // Activity-scoped, not the default nav-scoped viewModel(): MainActivity's
    // onPictureInPictureModeChanged()/onUserLeaveHint() resolve PlaybackViewModel via
    // ViewModelProvider(this) (Activity-scoped) to decide whether to auto-enter PiP. A
    // default-scoped viewModel() here resolved to a *different* instance tied to this
    // destination's back-stack entry, so MainActivity was always checking a viewmodel that
    // never actually played anything — PiP could never trigger.
    val dockPlayback: PlaybackViewModel? =
        if (isLiveTv && dockTarget != null && activity != null) viewModel(viewModelStoreOwner = activity) else null
    var fullScreen by remember { mutableStateOf(false) }
    var hasSeededDock by remember { mutableStateOf(false) }
    // Orientation-driven, not a width threshold — a screenWidthDp cutoff happens to catch most
    // phones once rotated (landscape width is usually well over 600dp) but doesn't express intent
    // and misbehaves on small phones/foldables. LocalConfiguration is read live, so this flips as
    // the device rotates.
    val isLandscape =
        LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    BackHandler(enabled = isLiveTv && fullScreen) { fullScreen = false }
    // Dock auto-seeds on entry (below), so without this, Back from a docked preview would skip
    // straight past the bare category screen and out of Live TV — mirrors TV's silent bare
    // CategoryList push in TvNavHost.kt that guarantees the same stopover.
    BackHandler(enabled = isLiveTv && !fullScreen && dockTarget != null) {
        // Stop first, same as the dock's close button: clearing dockTarget alone unmounts the
        // dock (and its lifecycle observer with it) but left the stream playing, with nothing
        // left to ever stop it — even after leaving the app. dockPlayback, never a fresh
        // ViewModelProvider lookup: that would create a PlaybackViewModel (and start the
        // playback service) just to stop it.
        dockPlayback?.stop()
        dockTarget = null
    }
    // The toolbar's Back, Search and TV Guide leave this screen without going through the
    // BackHandler above. The dock's engine is Activity-scoped, so leaving it playing kept the
    // stream's audio going behind the next screen — and, its ON_STOP observer gone with this
    // screen, even after the app was backgrounded. Stop it on the way out, not on dispose: the
    // player screen shares the same engine and may already be starting its own stream by then.
    // See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-17.
    val stopDockThen: (() -> Unit) -> Unit = { leave ->
        if (isLiveTv && dockTarget != null) {
            dockPlayback?.stop()
        }
        leave()
    }

    // Auto-seed the dock so entry never lands on a bare list — mirrors TV's
    // LiveTvSplitLayout: an explicit initialStreamId (search/EPG deep link) wins, otherwise
    // fall back to the app-wide last-played channel so a bare "Live TV" tap from the main menu
    // still lands on something.
    if (isLiveTv && !hasSeededDock) {
        val seedState = uiState
        LaunchedEffect(seedState) {
            if (seedState is CategoryViewModel.UiState.Success) {
                val seedId = initialStreamId ?: seedState.lastPlayedItemId
                // Never a provider's separator row: a heading has nothing to play.
                seedState.streams?.firstOrNull { it.id == seedId && !it.isSeparatorRow }?.let { seed ->
                    hasSeededDock = true
                    dockTarget = seed
                }
            }
        }
    }

    val target = dockTarget

    // Auto-enter PiP (Android 12+) was hardcoded off at Activity creation and never turned back
    // on — MainActivity.onUserLeaveHint()'s manual fallback only runs below SDK 31, so without
    // this, leaving the app (Home button, app-switch) while the mini-player was actively playing
    // never entered PiP on any S+ device at all.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val dockPlaybackState = dockPlayback?.playbackState?.collectAsStateWithLifecycle()?.value
        LaunchedEffect(dockPlaybackState) {
            val isPlaying = dockPlaybackState is PlaybackState.Playing || dockPlaybackState is PlaybackState.Buffering
            activity?.setPictureInPictureParams(
                android.app.PictureInPictureParams
                    .Builder()
                    .setAutoEnterEnabled(isPlaying)
                    .build(),
            )
        }
        // The params are the Activity's, not this screen's: leaving Live TV (Back, a tab, Search
        // the guide) stops the dock after this screen has gone, so the effect above never sees
        // it stop and auto-enter stayed on — the next Home press put Search the guide or a list
        // into PiP with nothing playing. Off when the screen leaves, as MobilePlayerScreen does.
        DisposableEffect(Unit) {
            onDispose {
                if (dockPlayback?.isInPictureInPictureMode?.value == true) return@onDispose
                activity?.setPictureInPictureParams(
                    android.app.PictureInPictureParams
                        .Builder()
                        .setAutoEnterEnabled(false)
                        .build(),
                )
            }
        }
    }

    // While a preview is docked, the list below defaults to the shared Recent list — regardless
    // of which category/tab (if any) was actually browsed to get here — with the currently
    // previewed channel included and highlighted. Independent of the CategoryViewModel's own
    // selectedCategoryId/streams so normal category/tab browsing is untouched when nothing's
    // docked, and mirrors TV's LiveTvSplitLayout for the same reason. Not used for Movies/TV
    // Shows (target is always null there). The Recent | Favorites tabs above the list (see
    // DockListTabs) switch it over to Favorites instead.
    var listSource by remember { mutableStateOf(PreviewListSource.RECENT) }
    // Bumped by pull-to-refresh on the docked panel — the viewer asking for current truth, and so
    // the one place the frozen order below is allowed to re-sort.
    var recentOrderResetTick by remember { mutableStateOf(0) }
    val publishedRecentStreams by viewModel.recentItems.collectAsStateWithLifecycle()
    // Held in display order: a channel previewed past the watch delay is recorded while this
    // panel is on screen, and promoting it to the top live would shift the rows under the
    // viewer's thumb.
    val recentStreams =
        rememberStableRecentOrder(publishedRecentStreams.orEmpty(), resetKey = recentOrderResetTick)
    var favoriteStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteStreamsLoading by remember { mutableStateOf(true) }
    val composableScope = rememberCoroutineScope()
    if (target != null) {
        LaunchedEffect(Unit) {
            favoriteStreams = viewModel.getFavoritesSnapshot()
            favoriteStreamsLoading = false
        }
    }

    val dockLoader: StreamLoaderViewModel? =
        if (isLiveTv && target != null) {
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
        } else {
            null
        }
    val dockLoaderState = dockLoader?.state?.collectAsStateWithLifecycle()?.value
    val dockSuccess = dockLoaderState as? StreamLoaderViewModel.StreamState.Success
    // Mirrors TV's previewPlaybackState (LiveTvSplitLayout.kt) — used by the landscape split to
    // show a loading spinner over the video box while a channel is still resolving/buffering.
    val dockPlaybackState = dockPlayback?.playbackState?.collectAsStateWithLifecycle()?.value

    if (target != null && dockLoader != null) {
        LaunchedEffect(target.id) {
            // Skip re-resolving if this is already the loaded stream (e.g. promoting an
            // already-docked channel to full screen) — same call-site guard TV's
            // LiveTvSplitLayout.onStreamPromote uses instead of a VM/service-level dedup guard.
            if (dockSuccess?.streamId != target.id) {
                dockLoader.loadStream(target)
            }
        }
    }

    // Actually start playback for the resolved stream — without this the shared engine never
    // gets a media source while docked (MobilePlayerContent's own playStream() call only runs
    // once promoted to full screen), so the dock's EmbeddedPlayerSurface just shows black.
    if (dockPlayback != null) {
        LaunchedEffect(dockSuccess?.streamId) {
            val s = dockSuccess
            // Mirrors TV's LiveTvSplitLayout guard: skip if the engine is already playing this
            // exact URL, so a redundant re-resolve of the same channel can't restart the stream.
            val alreadyPlayingThis =
                s != null &&
                    dockPlayback.currentMetadata.value.streamUrl == s.streamUrl &&
                    dockPlayback.playbackState.value !is PlaybackState.Idle &&
                    dockPlayback.playbackState.value !is PlaybackState.Error
            if (s != null && !alreadyPlayingThis) {
                dockPlayback.playStream(
                    PlayerMetadata(
                        title = s.streamName,
                        channelName = s.streamName,
                        description = s.description,
                        streamUrl = s.streamUrl,
                        isLive = s.isLive,
                        headers = s.streamHeaders,
                        programTitle = s.currentEpgProgram?.title,
                    ),
                    s.resumePosition,
                )
            }
        }

        // The guide lookup lands after playback started, and the programme rolls over while it
        // plays: patch it into what is playing, for live sync's "now playing".
        LaunchedEffect(dockSuccess?.streamUrl, dockSuccess?.currentEpgProgram?.title) {
            val s = dockSuccess ?: return@LaunchedEffect
            dockPlayback.updateMetadata(s.streamUrl) { it.copy(programTitle = s.currentEpgProgram?.title) }
        }

        // Another device of the sync group stopped this playback — docked or promoted to full
        // screen alike: finalise, stop the dock as its close button does, then go to the Live TV
        // tab's root. See docs/plans/archive/20261001_live-sync-now-playing-plan.md → Remote Stop.
        if (dockLoader != null) {
            RemoteStopEffect {
                finalizeSessionAndAwait(dockPlayback.playbackState.value, dockLoader)
                stopDockThen(onHome)
            }
        }

        // Dead-stream watchdog: stop trying rather than let a bad channel buffer in the
        // background indefinitely — mirrors TV's LiveTvSplitLayout watchdog.
        LaunchedEffect(dockSuccess?.streamId) {
            val s = dockSuccess ?: return@LaunchedEffect
            val reachedPlaying =
                withTimeoutOrNull(8_000) {
                    // playbackState is a plain StateFlow, not Compose snapshot state — wrapping it
                    // in snapshotFlow{} never registers an observable read, so it emits once and
                    // never again, making first{} hang the full timeout regardless of whether
                    // playback actually started. first{} on the StateFlow directly works correctly.
                    dockPlayback.playbackState.first { it is PlaybackState.Playing }
                }
            if (reachedPlaying == null && dockSuccess.streamId == s.streamId && !fullScreen) {
                dockPlayback.stop()
            }
        }

        // Stop when the app isn't visible (backgrounded, PiP dismissed); restart on return — a
        // docked preview has no business burning battery/data while the app isn't visible.
        // ON_STOP, not ON_PAUSE, so going into PiP keeps it playing — see onAppStopped's kdoc.
        DisposableEffect(lifecycleOwner) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            dockPlayback.onAppStopped()
                        }

                        Lifecycle.Event.ON_RESUME -> {
                            dockPlayback.onAppResumed()
                        }

                        else -> {}
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    // Rotation follows the phone's physical orientation automatically only while the preview is
    // docked — the rest of the app is portrait-locked (AndroidManifest.xml). Placed before the
    // fullScreen early-return below so it stays mounted across promote/demote, not just while the
    // small dock is on screen. SENSOR (not USER) so it rotates on physical movement even if the
    // OS auto-rotate toggle is off — same reasoning as the full-screen player's
    // SENSOR_LANDSCAPE override in MobilePlayerScreen.kt, just not landscape-only here since the
    // dock has a real portrait layout too.
    if (isLiveTv && target != null) {
        val activity = context as? Activity
        DisposableEffect(activity) {
            val original = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            onDispose { activity?.requestedOrientation = original }
        }
    }

    val isInPip = dockPlayback?.isInPictureInPictureMode?.collectAsStateWithLifecycle()?.value == true
    // Before the full-screen return below, so the bar hears about full screen too. The bar stops
    // the dock when it leaves the Live TV tab, as the toolbar's Back does (stopDockThen above),
    // and hides where the top bar does too: the landscape split gives the video the full height.
    val dockCoversScreen = fullScreen || isInPip || (target != null && isLandscape)
    DisposableEffect(dockPlayback, dockCoversScreen) {
        onDockChanged(dockPlayback?.let { playback -> { playback.stop() } }, dockCoversScreen)
        onDispose { onDockChanged(null, false) }
    }

    if (target != null && dockPlayback != null && dockLoader != null && fullScreen) {
        // Refresh the favorites list on demote (fullScreen leaving composition) so it reflects
        // what was just (un)favorited — promoting/demoting never leaves this screen, so nothing
        // else would re-fetch it. The Recent list needs no equivalent: the player's own delayed
        // history write republishes the shared list this screen collects. Mirrors TV's
        // LiveTvSplitLayout for the same reason.
        DisposableEffect(Unit) {
            onDispose {
                viewModel.viewModelScope.launch {
                    favoriteStreams = viewModel.getFavoritesSnapshot()
                }
            }
        }
        MobilePlayerContent(
            viewModel = dockPlayback,
            loaderViewModel = dockLoader,
            contentType = contentType,
            onBack = { fullScreen = false },
        )
        return
    }

    // PiP entered from the dock: the window is just the video. Without this the whole dock
    // screen (preview card, Recent list) got squeezed into the PiP window. The promoted
    // full-screen branch above needs no equivalent — MobilePlayerContent already hides its own
    // chrome in PiP.
    if (isInPip) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize())
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = target?.thumbnailUrl)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Hidden in the landscape split — the title/EPG/search row eats vertical space the
                // video pane needs, and none of its actions (EPG, search, category nav) apply while
                // docked anyway (category chips are hidden here too, see below).
                if (!(isLiveTv && target != null && isLandscape)) {
                    val sectionActions: @Composable RowScope.() -> Unit = {
                        // EPG button - show for Live TV when native EPG or XMLTV file is available
                        if (contentType == ContentType.LIVE_TV) {
                            val state = uiState
                            if (state is CategoryViewModel.UiState.Success) {
                                val selectedCatId = state.selectedCategoryId
                                val selectedCatName = state.categories.find { it.id == selectedCatId }?.name
                                val hasEpgData =
                                    supportsNativeEpg ||
                                        epgIndexState is EpgIndexState.Indexed
                                if (selectedCatId != null && selectedCatName != null && hasEpgData) {
                                    // Names the category it opens (G-M4): the selected chip may
                                    // sit under the dock, out of sight. Long-press shows it.
                                    val guideLabel = stringResource(R.string.epg_guide_header_title_format, selectedCatName)
                                    TooltipBox(
                                        positionProvider =
                                            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                                        tooltip = { PlainTooltip { Text(guideLabel) } },
                                        state = rememberTooltipState(),
                                    ) {
                                        CinemaIconButton(
                                            onClick = { stopDockThen { onEpgClick(selectedCatId, selectedCatName) } },
                                            icon = {
                                                Icon(
                                                    CinemaIcons.DateRange,
                                                    guideLabel,
                                                    tint = CinemaTextPrimary,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        CinemaIconButton(
                            onClick = { stopDockThen(onSearchClick) },
                            icon = {
                                Icon(CinemaIcons.Search, stringResource(R.string.common_search), tint = CinemaTextPrimary)
                            },
                        )
                    }
                    if (sourceTopBar != null) {
                        sourceTopBar { stopDockThen(onSearchClick) }
                    } else {
                        TopAppBar(
                            title = {
                                Text(
                                    when (contentType) {
                                        ContentType.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
                                        ContentType.MOVIES -> stringResource(R.string.provider_movies_label)
                                        ContentType.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
                                        else -> contentType.replace("_", " ")
                                    },
                                )
                            },
                            navigationIcon = {
                                CinemaIconButton(
                                    onClick = { stopDockThen(onBack) },
                                    icon = {
                                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back), tint = CinemaTextPrimary)
                                    },
                                )
                            },
                            actions = sectionActions,
                        )
                    }
                }
            },
        ) { paddingValues ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                AnimatedContent(
                    targetState = uiState,
                    // Keyed on the sealed subtype, not the state instance — Success carries fresh
                    // data (stream lists, docked preview target) on nearly every emission, and a
                    // plain targetState comparison would refire the crossfade on every one of those
                    // instead of only on Loading/Success/Error swaps.
                    contentKey = { it::class },
                    transitionSpec = {
                        fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs)) togetherWith
                            fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
                    },
                    label = "category_state_crossfade",
                ) { state ->
                    when (state) {
                        is CategoryViewModel.UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(Spacing.sm),
                            ) {
                                SkeletonList(
                                    rowCount = 8,
                                    rowHeight = StreamCardMinHeight,
                                    thumbnailWidth = StreamThumbnailWidth,
                                    thumbnailHeight = StreamThumbnailHeight,
                                    verticalSpacing = LocalUiStyle.current.grid.spacing,
                                )
                            }
                        }

                        is CategoryViewModel.UiState.Success -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // EPG error/status banner (Live TV only)
                                if (contentType == ContentType.LIVE_TV) {
                                    val epgMessage =
                                        when (epgIndexState) {
                                            is EpgIndexState.Failed -> {
                                                stringResource(R.string.epg_indexing_failed)
                                            }

                                            is EpgIndexState.Indexing -> {
                                                stringResource(
                                                    R.string.epg_indexing_progress,
                                                    (epgIndexState as EpgIndexState.Indexing).progressPercent,
                                                )
                                            }

                                            else -> {
                                                null
                                            }
                                        }
                                    if (epgMessage != null) {
                                        Text(
                                            text = epgMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color =
                                                if (epgIndexState is EpgIndexState.Indexing) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                },
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = CinemaSpacing.md,
                                                    vertical = CinemaSpacing.xs,
                                                ),
                                        )
                                    }
                                }

                                // Horizontal category chips — hidden while a preview is docked so the
                                // video sits right below the top bar instead of being pushed down by
                                // navigation chrome the user isn't using in that moment.
                                if (!(isLiveTv && target != null)) {
                                    CategoryChipRow(
                                        categories = state.categories,
                                        selectedCategoryId = state.selectedCategoryId,
                                        contentType = contentType,
                                        favoriteCategoryIds = favoriteCategoryIds,
                                        categoryViewModel = viewModel,
                                        onCategorySelected = { categoryId ->
                                            viewModel.loadStreams(categoryId)
                                        },
                                    )

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline,
                                        thickness = MobileDimensions.dividerThin,
                                    )
                                }

                                // Streams list with pull-to-refresh — while a preview is docked, always
                                // Recent or favorites (see listSource above), regardless of the real
                                // selected category/tab. INCLUDE keeps the docked channel in the list
                                // even before its delayed history write lands.
                                val displayedStreams =
                                    if (target == null) {
                                        state.streams
                                    } else if (listSource == PreviewListSource.FAVORITES) {
                                        favoriteStreams
                                    } else {
                                        recentStreams.withCurrentChannel(target, CurrentChannelPolicy.INCLUDE)
                                    }
                                val displayedStreamsLoading =
                                    if (target == null) {
                                        state.streamsLoading
                                    } else if (listSource == PreviewListSource.FAVORITES) {
                                        favoriteStreamsLoading
                                    } else {
                                        publishedRecentStreams == null
                                    }
                                val displayedCategoryId =
                                    when {
                                        target == null -> state.selectedCategoryId
                                        listSource == PreviewListSource.FAVORITES -> CategoryViewModel.FAVORITES_CATEGORY_ID
                                        else -> CategoryViewModel.RECENT_CATEGORY_ID
                                    }
                                val streamsList: @Composable () -> Unit = {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        if (target != null) {
                                            DockListTabs(selected = listSource, onSelect = { listSource = it })
                                        }
                                        PullToRefreshBox(
                                            isRefreshing = displayedStreamsLoading,
                                            onRefresh = {
                                                if (target == null) {
                                                    state.selectedCategoryId?.let { viewModel.refreshStreams(it) }
                                                } else if (listSource == PreviewListSource.FAVORITES) {
                                                    composableScope.launch {
                                                        favoriteStreamsLoading = true
                                                        favoriteStreams = viewModel.getFavoritesSnapshot()
                                                        favoriteStreamsLoading = false
                                                    }
                                                } else {
                                                    composableScope.launch { viewModel.refreshRecentItems() }
                                                    recentOrderResetTick++
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                        ) {
                                            val toggleFavorite: (MediaItem) -> Unit = { toggled ->
                                                viewModel.toggleFavoriteStream(
                                                    itemId = toggled.id,
                                                    itemName = toggled.name,
                                                    categoryId = toggled.categoryId,
                                                    contentType = contentType,
                                                )
                                                if (target == null) {
                                                    // Only reload when Favorites is what's on screen (so an
                                                    // unfavorited row drops out) — refreshStreams selects the
                                                    // category it loads, so calling it from any other category
                                                    // yanked the view over to Favorites.
                                                    if (state.selectedCategoryId == CategoryViewModel.FAVORITES_CATEGORY_ID) {
                                                        viewModel.refreshStreams(CategoryViewModel.FAVORITES_CATEGORY_ID)
                                                    }
                                                } else {
                                                    composableScope.launch {
                                                        favoriteStreams = viewModel.getFavoritesSnapshot()
                                                    }
                                                }
                                            }
                                            // Keyed on docking: the docked list and the one left when the
                                            // dock closes may both be Recent, and a row left revealed
                                            // (its delete showing) must not carry over (phone UI audit #12).
                                            key(target == null) {
                                                StreamsList(
                                                    items = displayedStreams,
                                                    streamsLoading = displayedStreamsLoading,
                                                    selectedCategoryId = displayedCategoryId,
                                                    lastPlayedItemId = state.lastPlayedItemId,
                                                    nowPlaying = nowPlaying,
                                                    watchedIds = watchedIds,
                                                    watchProgress = watchProgress,
                                                    currentlyPlayingId = target?.id,
                                                    onItemSelected = { itemId, itemName, categoryId ->
                                                        val item = displayedStreams?.firstOrNull { it.id == itemId }
                                                        val selected =
                                                            item?.browseTarget(contentType) ?: browseTargetFor(contentType, itemId)
                                                        when {
                                                            // A row from "Recent Categories"/"Favorite Categories" browses, it doesn't play.
                                                            selected is BrowseTarget.CategoryRef -> {
                                                                viewModel.loadStreams(selected.categoryId)
                                                            }

                                                            isLiveTv && item != null -> {
                                                                // Dock locally instead of navigating away — mirrors
                                                                // TV's LiveTvChannelList.onStreamPromote interception.
                                                                dockTarget = item
                                                            }

                                                            else -> {
                                                                onStreamSelected(itemId, itemName, categoryId, contentType, selected)
                                                            }
                                                        }
                                                    },
                                                    contentType = contentType,
                                                    favoriteIds = favoriteIds,
                                                    onToggleFavorite = toggleFavorite,
                                                    onToggleWatched = { toggled ->
                                                        viewModel.toggleWatchedStream(toggled.id, contentType)
                                                    },
                                                    onRemoveItem =
                                                        if (viewModel.supportsRemoveFromRecent) {
                                                            { item -> viewModel.removeFromRecent(item.id, contentType, item.seriesId) }
                                                        } else {
                                                            null
                                                        },
                                                    onRemoveFavorite = toggleFavorite,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isLiveTv && target != null && isLandscape) {
                                    // Landscape split — mirrors tv/.../LiveTvSplitLayout.kt's side-by-side
                                    // layout as closely as possible: video+EPG pane left (0.66), channel
                                    // list right (0.34).
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                    ) {
                                        Column(
                                            modifier =
                                                Modifier
                                                    .weight(0.66f)
                                                    .fillMaxHeight()
                                                    .padding(horizontal = CinemaSpacing.md),
                                        ) {
                                            // True 16:9 without Modifier.aspectRatio(16f / 9f): the latter
                                            // mis-positions its content in this specific spot (a Row placed
                                            // after other siblings in the parent Column, each weighted+
                                            // fillMaxHeight child measured with a reduced-but-bounded height
                                            // constraint) — reproduced with a bare colored Box, confirmed
                                            // it's not related to EmbeddedPlayerSurface. TV's
                                            // LiveTvSplitLayout doesn't hit this because its Row has no
                                            // preceding siblings to reduce the incoming constraint.
                                            // BoxWithConstraints sidesteps it: it reads the actual measured
                                            // width and derives a plain .height() from it, the same
                                            // primitive the old fixed-120dp workaround used, just computed
                                            // instead of hardcoded — coerced against maxHeight too, so it
                                            // never overflows the column on unusually short screens.
                                            BoxWithConstraints(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(CinemaCornerRadius.medium))
                                                        .background(CinemaSurface)
                                                        .clickable(onClick = { fullScreen = true }),
                                            ) {
                                                val videoHeight = (maxWidth * 9f / 16f).coerceAtMost(maxHeight)
                                                Box(modifier = Modifier.fillMaxWidth().height(videoHeight)) {
                                                    // TextureView (not the default SurfaceView): this box
                                                    // sits next to the scrolling/recomposing channel list,
                                                    // and SurfaceView there stalls the main thread and
                                                    // ANRs. Full-screen playback (MobilePlayerContent) uses
                                                    // its own default surface instead of sharing this one —
                                                    // promoting/demoting does a real detach/reattach (one
                                                    // frame's glitch) rather than relocating this node, but
                                                    // it stops every OSD/flyout interaction in full-screen
                                                    // from janking the video for the rest of the session.
                                                    // Mirrors TV's LiveTvSplitLayout.
                                                    EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize(), useTextureView = true)

                                                    // The preview surface has no controls/error UI of its
                                                    // own, so a stalled or watchdog-killed stream would
                                                    // otherwise look identical to a live frozen frame —
                                                    // mirrors TV's LiveTvSplitLayout.
                                                    if (dockPlaybackState !is PlaybackState.Playing) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.align(Alignment.Center),
                                                            color = CinemaAccent,
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            dockPlayback?.stop()
                                                            dockTarget = null
                                                        },
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(CinemaSpacing.xs),
                                                    ) {
                                                        Icon(
                                                            CinemaIcons.Close,
                                                            contentDescription = stringResource(R.string.common_close),
                                                            tint = CinemaTextPrimary,
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                                            Text(
                                                text = dockSuccess?.streamName ?: target.name,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = CinemaTextPrimary,
                                                maxLines = 1,
                                            )
                                            val nowProg = dockSuccess?.currentEpgProgram
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
                                            val nextProg = dockSuccess?.nextEpgProgram
                                            if (nextProg != null) {
                                                Text(
                                                    text = stringResource(R.string.category_up_next_format, nextProg.title),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = CinemaTextSecondary,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(0.34f).fillMaxHeight()) {
                                            streamsList()
                                        }
                                    }
                                } else {
                                    // Portrait: same video+EPG / list split as landscape, just stacked
                                    // top/bottom instead of side-by-side (a plain Column weight split —
                                    // no Row-after-siblings + aspectRatio() involved, so none of the
                                    // mis-positioning above applies here).
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        if (isLiveTv && target != null) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .weight(0.5f)
                                                        // Unlike TV, the dock here already did a full
                                                        // loadStream() (see the top-of-function comment on
                                                        // why mobile commits immediately on tap) — real
                                                        // category/last-watched data and watch-history
                                                        // recording already happened at dock time, so
                                                        // promoting needs no extra reload.
                                                        .clickable(onClick = { fullScreen = true }),
                                            ) {
                                                // TextureView — see the landscape dock box above.
                                                EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize(), useTextureView = true)

                                                IconButton(
                                                    onClick = {
                                                        dockPlayback?.stop()
                                                        dockTarget = null
                                                    },
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(CinemaSpacing.xs),
                                                ) {
                                                    Icon(
                                                        CinemaIcons.Close,
                                                        contentDescription = stringResource(R.string.common_close),
                                                        tint = CinemaTextPrimary,
                                                    )
                                                }

                                                val palette = CinemaThemeHolder.current
                                                // Memoize brush to avoid allocating new Brush + listOf on
                                                // every recomposition — mirrors GradientOverlay.kt.
                                                val scrimBrush =
                                                    remember(palette.background) {
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                Color.Transparent,
                                                                palette.background.copy(alpha = CinemaAlpha.imageOverlay),
                                                            ),
                                                        )
                                                    }
                                                Column(
                                                    modifier =
                                                        Modifier
                                                            .align(Alignment.BottomStart)
                                                            .fillMaxWidth()
                                                            .background(scrimBrush)
                                                            .padding(CinemaSpacing.sm),
                                                ) {
                                                    Text(
                                                        text = dockSuccess?.streamName ?: target.name,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = CinemaTextPrimary,
                                                        maxLines = 1,
                                                    )
                                                    val nowProg = dockSuccess?.currentEpgProgram
                                                    if (nowProg != null) {
                                                        Text(
                                                            text = stringResource(R.string.epg_now_prefix, nowProg.title),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textHigh),
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            streamsList()
                                        }
                                    }
                                }
                            }
                        }

                        is CategoryViewModel.UiState.Error -> {
                            RetryWhenOnline { viewModel.retry() }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                    modifier = Modifier.padding(Spacing.xl),
                                ) {
                                    Text(
                                        text = stringResource(R.string.common_error),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    CinemaButton(onClick = { viewModel.retry() }) {
                                        Text(stringResource(R.string.common_retry))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Which list the docked Live TV preview's channel panel is showing, picked with [DockListTabs]. */
private enum class PreviewListSource { RECENT, FAVORITES }

/**
 * The docked preview's Recent | Favorites tabs (phone UI audit #12): a sideways swipe used to
 * switch the list, and fought the rows' own swipe actions. A sideways swipe on a row is its row
 * actions now; the tabs are the way to switch.
 */
@Composable
private fun DockListTabs(
    selected: PreviewListSource,
    onSelect: (PreviewListSource) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal, containerColor = Color.Transparent) {
        PreviewListSource.entries.forEach { source ->
            Tab(
                selected = source == selected,
                onClick = { onSelect(source) },
                text = {
                    Text(
                        stringResource(
                            when (source) {
                                PreviewListSource.RECENT -> R.string.category_recent_label
                                PreviewListSource.FAVORITES -> R.string.settings_import_favorites_label
                            },
                        ),
                    )
                },
            )
        }
    }
}

/** Anchor values for a stream row's swipe-reveal action strip (see [StreamsList]). */
private enum class SwipeReveal { CLOSED, FAVORITE_ACTIONS, DELETE_ACTION }

/** A single circular icon button inside a swipe-reveal action strip. */
@Composable
private fun SwipeActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(MobileDimensions.swipeActionCircleSize)
                .clip(CircleShape)
                .background(tint)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            // Contrasts against the tinted circle behind it — `tint` here would put a same-hue icon
            // on a same-hue background, all but invisible.
            tint = CinemaTextPrimary,
            modifier = Modifier.size(MobileDimensions.iconDefault),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChipRow(
    categories: List<org.njarasoa.fijerena.core.player.domain.MediaCategory>,
    selectedCategoryId: String?,
    contentType: String,
    favoriteCategoryIds: ImmutableStringSet = ImmutableStringSet(),
    categoryViewModel: CategoryViewModel,
    onCategorySelected: (String) -> Unit,
) {
    val (virtualCategories, regularCategories) =
        remember(categories) {
            categories.partitionVirtual()
        }

    val listState = rememberLazyListState()
    val chipColors =
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        )

    // Entrance animation plays once per item: LazyRow recycles item composition off the ends
    // of the scroll buffer, so without this guard the fade/slide replays on every scroll.
    // Keyed to categories so it resets (bounded) on category-list change instead of growing
    // unbounded for the composable's whole lifetime.
    val enteredCategoryIds = remember(categories) { mutableSetOf<String>() }

    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null && selectedCategoryId !in CategoryViewModel.VIRTUAL_CATEGORY_IDS) {
            val index = regularCategories.indexOfFirst { it.id == selectedCategoryId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Column {
        // Virtual categories row (Favorites, Last Watched)
        if (virtualCategories.isNotEmpty()) {
            LazyRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = CinemaSpacing.sm, bottom = CinemaSpacing.xs),
                contentPadding = PaddingValues(horizontal = CinemaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            ) {
                itemsIndexed(virtualCategories, key = {
                    _,
                    category,
                    ->
                    category.id
                }, contentType = { _, _ -> "category" }) { index, category ->
                    CinemaFilterChip(
                        selected = category.id == selectedCategoryId,
                        onClick = { onCategorySelected(category.id) },
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // Marquee only on the selected chip. Touch has no focus, so gating on
                                // selection is the nearest equivalent — otherwise every overflowing chip
                                // runs its own per-frame invalidateDraw loop for as long as it's visible.
                                modifier =
                                    if (category.id == selectedCategoryId) {
                                        Modifier.bounceMarquee()
                                    } else {
                                        Modifier
                                    },
                            )
                        },
                        colors = chipColors,
                        modifier =
                            // remember-scoped: bare add() returns false on the first recomposition
                            // of an already-visible chip, dropping staggeredEntrance mid-animation.
                            if (remember(category.id) { enteredCategoryIds.add(category.id) }) {
                                Modifier.staggeredEntrance(index)
                            } else {
                                Modifier
                            },
                    )
                }
            }
        }

        LazyRow(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = CinemaSpacing.sm),
            contentPadding = PaddingValues(horizontal = CinemaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            itemsIndexed(regularCategories, key = { _, category -> category.id }, contentType = { _, _ -> "category" }) { index, category ->
                val isFavCat = category.id in favoriteCategoryIds
                CinemaFilterChip(
                    selected = category.id == selectedCategoryId,
                    onClick = { onCategorySelected(category.id) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // See the virtual chips above.
                                modifier =
                                    if (category.id == selectedCategoryId) {
                                        Modifier.bounceMarquee()
                                    } else {
                                        Modifier
                                    },
                            )
                            // Long-press was unreliable here (a horizontally scrolling LazyRow
                            // reads any sideways jitter during the hold as a scroll attempt and
                            // cancels it) — an always-visible, independently tappable icon sidesteps
                            // that entirely, same fix as the TV category reveal.
                            Icon(
                                imageVector = if (isFavCat) CinemaIcons.Star else CinemaIcons.StarBorder,
                                contentDescription =
                                    stringResource(if (isFavCat) R.string.favorite_remove else R.string.favorite_add),
                                tint = if (isFavCat) MaterialTheme.colorScheme.primary else CinemaTextSecondary,
                                modifier =
                                    Modifier
                                        .size(MobileDimensions.iconSmall)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) {
                                            categoryViewModel.toggleFavoriteCategory(category.id, category.name, contentType)
                                        },
                            )
                        }
                    },
                    modifier =
                        // See above — remember-scoped so recomposition can't cancel it.
                        if (remember(category.id) { enteredCategoryIds.add(category.id) }) {
                            Modifier.staggeredEntrance(index)
                        } else {
                            Modifier
                        },
                    colors = chipColors,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamsList(
    items: List<org.njarasoa.fijerena.core.player.domain.MediaItem>?,
    streamsLoading: Boolean,
    selectedCategoryId: String?,
    lastPlayedItemId: String? = null,
    nowPlaying: ImmutableNowPlaying = ImmutableNowPlaying(),
    watchedIds: ImmutableStringSet = ImmutableStringSet(),
    watchProgress: ImmutableWatchProgress = ImmutableWatchProgress(),
    currentlyPlayingId: String? = null,
    contentType: String,
    favoriteIds: ImmutableStringSet = ImmutableStringSet(),
    onItemSelected: (itemId: String, itemName: String, categoryId: String) -> Unit,
    onToggleFavorite: (org.njarasoa.fijerena.core.player.domain.MediaItem) -> Unit = {},
    onToggleWatched: (org.njarasoa.fijerena.core.player.domain.MediaItem) -> Unit = {},
    onRemoveItem: ((org.njarasoa.fijerena.core.player.domain.MediaItem) -> Unit)? = null,
    onRemoveFavorite: ((org.njarasoa.fijerena.core.player.domain.MediaItem) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isWatchable = contentType == ContentType.MOVIES || contentType == ContentType.TV_SHOWS

    // Built once per list composition rather than once per row. CardDefaults.cardColors is
    // @Composable, so it can't be wrapped in remember — hoisting the call out of the item body
    // is what stops a CardColors being allocated per visible row per recomposition.
    val streamCardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

    // Entrance animation plays once per item: LazyColumn recycles item composition off the ends
    // of the scroll buffer, so without this guard the fade/slide replays on every scroll.
    // Keyed to items so it resets (bounded) on category switch instead of growing unbounded
    // across every stream id seen this session.
    val enteredStreamIds = remember(items) { mutableSetOf<String>() }

    // A provider's separator rows ("####### ETHIOPIA VIP #######") are headings: never played,
    // swiped or counted (as on the TV, TV UI audit #24).
    val itemCount = remember(items) { items?.count { !it.isSeparatorRow } ?: 0 }

    // One revealed row at a time (phone UI audit #12): swiping a row closes the one left open.
    // A new list starts with none.
    var openRowId by remember(selectedCategoryId) { mutableStateOf<String?>(null) }

    LaunchedEffect(items, lastPlayedItemId) {
        if (!items.isNullOrEmpty() && lastPlayedItemId != null) {
            val index = items.indexOfFirst { it.id == lastPlayedItemId }
            if (index > 0) {
                listState.animateScrollToItem(index)
            }
        }
    }
    when {
        selectedCategoryId == null -> {
            MobileEmptyState(message = selectCategoryText(contentType), icon = sectionIcon(contentType))
        }

        streamsLoading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.category_loading_streams),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
                SkeletonList(
                    rowCount = 8,
                    rowHeight = StreamCardMinHeight,
                    thumbnailWidth = StreamThumbnailWidth,
                    thumbnailHeight = StreamThumbnailHeight,
                    verticalSpacing = LocalUiStyle.current.grid.spacing,
                )
            }
        }

        items.isNullOrEmpty() -> {
            MobileEmptyState(message = noItemsText(contentType), icon = sectionIcon(contentType))
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                // Pinned, not a LazyColumn item, so it doesn't scroll away with the list. In the
                // section's words (phone UI audit P2); a list of categories counts categories.
                val isCategoryList =
                    selectedCategoryId == CategoryViewModel.FAVORITE_CATEGORIES_ID ||
                        selectedCategoryId == CategoryViewModel.RECENTLY_VIEWED_CATEGORIES_ID
                Text(
                    text =
                        if (isCategoryList) {
                            stringResource(R.string.category_count_format, itemCount)
                        } else {
                            itemCountText(contentType, itemCount)
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.xs, bottom = Spacing.xxs),
                )
                val isRecentList = selectedCategoryId == CategoryViewModel.RECENT_CATEGORY_ID
                val isFavoritesList = selectedCategoryId == CategoryViewModel.FAVORITES_CATEGORY_ID
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(LocalUiStyle.current.grid.spacing),
                ) {
                    itemsIndexed(
                        items,
                        key = { _, item -> item.id },
                        contentType = { _, item -> if (item.isSeparatorRow) "separator" else "stream" },
                    ) { index, item ->
                        if (item.isSeparatorRow) {
                            SeparatorRow(name = item.name)
                            return@itemsIndexed
                        }
                        val cardModifier =
                            if (remember(item.id) { enteredStreamIds.add(item.id) }) {
                                Modifier.staggeredEntrance(index)
                            } else {
                                Modifier
                            }

                        val streamCard = @Composable { modifier: Modifier ->
                            StreamCard(
                                item = item,
                                nowPlayingProgram = nowPlaying[item.id],
                                isCurrentlyPlaying = item.id == currentlyPlayingId,
                                isCurrent = item.id == (currentlyPlayingId ?: lastPlayedItemId),
                                isWatched = item.id in watchedIds,
                                watchProgress = watchProgress[item.id] ?: 0f,
                                cardColors = streamCardColors,
                                onClick = {
                                    onItemSelected(item.id, item.name, item.categoryId)
                                },
                                modifier = modifier,
                            )
                        }

                        val canSwipeDismiss = (isRecentList && onRemoveItem != null) || (isFavoritesList && onRemoveFavorite != null)
                        val isFavorite = item.id in favoriteIds
                        val isWatchedItem = item.id in watchedIds
                        val density = LocalDensity.current
                        val greenIconCount = if (isWatchable) 2 else 1
                        val favoriteRevealWidth =
                            CinemaSpacing.sm * 2 +
                                MobileDimensions.swipeActionCircleSize * greenIconCount +
                                CinemaSpacing.sm * (greenIconCount - 1)
                        val deleteRevealWidth = CinemaSpacing.sm * 2 + MobileDimensions.swipeActionCircleSize
                        val revealState =
                            remember(canSwipeDismiss, favoriteRevealWidth, deleteRevealWidth, selectedCategoryId) {
                                val anchors =
                                    with(density) {
                                        DraggableAnchors {
                                            SwipeReveal.CLOSED at 0f
                                            SwipeReveal.FAVORITE_ACTIONS at favoriteRevealWidth.toPx()
                                            if (canSwipeDismiss) {
                                                SwipeReveal.DELETE_ACTION at -deleteRevealWidth.toPx()
                                            }
                                        }
                                    }
                                AnchoredDraggableState(initialValue = SwipeReveal.CLOSED, anchors = anchors)
                            }
                        LaunchedEffect(revealState) {
                            snapshotFlow { revealState.targetValue }.collect { value ->
                                if (value != SwipeReveal.CLOSED) openRowId = item.id
                            }
                        }
                        LaunchedEffect(openRowId, revealState) {
                            if (openRowId != item.id && revealState.currentValue != SwipeReveal.CLOSED) {
                                revealState.animateTo(SwipeReveal.CLOSED)
                            }
                        }

                        Box(modifier = cardModifier) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .width(favoriteRevealWidth)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(CinemaCornerRadius.medium))
                                        .background(CinemaSurface)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) {
                                            scope.launch { revealState.animateTo(SwipeReveal.CLOSED) }
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                                    SwipeActionButton(
                                        icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                        contentDescription =
                                            stringResource(
                                                if (isFavorite) R.string.favorite_remove else R.string.favorite_add,
                                            ),
                                        tint = CinemaSuccess,
                                        onClick = {
                                            onToggleFavorite(item)
                                            scope.launch { revealState.animateTo(SwipeReveal.CLOSED) }
                                        },
                                    )
                                    if (isWatchable) {
                                        SwipeActionButton(
                                            icon = if (isWatchedItem) CinemaIcons.VisibilityOff else CinemaIcons.Visibility,
                                            contentDescription =
                                                stringResource(
                                                    if (isWatchedItem) R.string.watched_unmark else R.string.watched_mark,
                                                ),
                                            tint = CinemaAccent,
                                            onClick = {
                                                onToggleWatched(item)
                                                scope.launch { revealState.animateTo(SwipeReveal.CLOSED) }
                                            },
                                        )
                                    }
                                }
                            }

                            if (canSwipeDismiss) {
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(deleteRevealWidth)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(CinemaCornerRadius.medium))
                                            .background(CinemaSurface)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                            ) {
                                                scope.launch { revealState.animateTo(SwipeReveal.CLOSED) }
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SwipeActionButton(
                                        icon = CinemaIcons.Delete,
                                        contentDescription =
                                            stringResource(
                                                if (isRecentList) R.string.recent_remove else R.string.favorite_remove,
                                            ),
                                        tint = CinemaError,
                                        onClick = {
                                            if (isRecentList) onRemoveItem?.invoke(item) else onRemoveFavorite?.invoke(item)
                                            scope.launch { revealState.animateTo(SwipeReveal.CLOSED) }
                                        },
                                    )
                                }
                            }

                            streamCard(
                                Modifier
                                    .offset { IntOffset(revealState.requireOffset().roundToInt(), 0) }
                                    .anchoredDraggable(revealState, Orientation.Horizontal),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A row's thumbnail: 16:9 (phone UI audit P1), sized so the row holds a title and one more line. */
private val StreamThumbnailWidth = 96.dp
private val StreamThumbnailHeight = 54.dp

/** A row's height when its text fits beside the thumbnail; a longer title makes it taller. */
private val StreamCardMinHeight = StreamThumbnailHeight + CinemaSpacing.xs * 2

/**
 * A provider's separator row in a channel list ("####### ETHIOPIA VIP #######"): a heading, never
 * played, swiped or counted. The TV's rule (its `isSeparatorRow`, which lives in the tv module).
 */
private val MediaItem.isSeparatorRow: Boolean
    get() = mediaType == MediaType.LIVE_CHANNEL && isCategoryMarker

/** A separator row as what it is: a heading over the channels that follow, text only, not tappable. */
@Composable
private fun SeparatorRow(name: String) {
    Text(
        text =
            name
                .trim()
                .trim('#')
                .trim()
                .ifEmpty { name.trim() },
        style = MaterialTheme.typography.titleSmall,
        color = CinemaTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.sm),
    )
}

/** The section's icon, over its empty states. */
@Composable
private fun sectionIcon(contentType: String): ImageVector =
    when (contentType) {
        ContentType.MOVIES -> CinemaIcons.Movie
        ContentType.TV_SHOWS -> CinemaIcons.Tv
        else -> CinemaIcons.LiveTv
    }

/** "12 channels", "12 films" or "12 shows" — the section's own word, not "streams" (as on the TV). */
@Composable
private fun itemCountText(
    contentType: String,
    count: Int,
): String =
    when (contentType) {
        ContentType.MOVIES -> stringResource(R.string.browse_film_count_format, count)
        ContentType.TV_SHOWS -> stringResource(R.string.browse_show_count_format, count)
        else -> stringResource(R.string.browse_channel_count_format, count)
    }

/** An empty list, in the section's words. */
@Composable
private fun noItemsText(contentType: String): String =
    when (contentType) {
        ContentType.MOVIES -> stringResource(R.string.browse_no_films)
        ContentType.TV_SHOWS -> stringResource(R.string.browse_no_shows)
        else -> stringResource(R.string.category_no_channels)
    }

/** No category picked yet, in the section's words. */
@Composable
private fun selectCategoryText(contentType: String): String =
    when (contentType) {
        ContentType.MOVIES -> stringResource(R.string.browse_select_to_view_films)
        ContentType.TV_SHOWS -> stringResource(R.string.browse_select_to_view_shows)
        else -> stringResource(R.string.category_select_to_view_channels)
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamCard(
    item: org.njarasoa.fijerena.core.player.domain.MediaItem,
    nowPlayingProgram: EpgProgram? = null,
    isCurrentlyPlaying: Boolean = false,
    /** The channel playing, else the last played item: its title in the accent colour. */
    isCurrent: Boolean = false,
    isWatched: Boolean = false,
    watchProgress: Float = 0f,
    cardColors: CardColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CinemaCard(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = StreamCardMinHeight)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors = cardColors,
        border =
            if (isCurrentlyPlaying) {
                androidx.compose.foundation.BorderStroke(MobileDimensions.strokeWidth, CinemaAccent)
            } else {
                cinemaCardHairlineBorder()
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CinemaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            val parsedTitle = remember(item.name) { parseDisplayTitle(item.name) }
            // Provider data occasionally sends a blank name (e.g. "EN -  (US)" with nothing
            // between the dashes) — an empty row reads as broken, not a catalogue gap.
            val displayTitle = parsedTitle.title.ifBlank { stringResource(R.string.content_untitled) }
            val isChannel = item.mediaType == MediaType.LIVE_CHANNEL
            // 16:9, with the resume bar on the thumbnail's bottom edge rather than along the whole
            // card (phone UI audit P1, as on the TV).
            val thumbnailRadius = CinemaCornerRadius.medium
            val thumbnailShape = remember(thumbnailRadius) { RoundedCornerShape(thumbnailRadius) }
            Box(
                modifier =
                    Modifier
                        .size(width = StreamThumbnailWidth, height = StreamThumbnailHeight)
                        .clip(thumbnailShape),
            ) {
                CinemaThumbnail(
                    url = item.thumbnailUrl,
                    // The clean title's first letter or digit, so a name that opens with a tag,
                    // a space or a symbol still gets its letter tile.
                    fallbackLetter = displayTitle.firstOrNull { it.isLetterOrDigit() } ?: displayTitle.firstOrNull(),
                    // A channel's logo is drawn whole on a neutral tile; the shading under
                    // artwork would only dim it.
                    contentType = if (isChannel) ThumbnailContentType.LIVE_TV else ThumbnailContentType.DEFAULT,
                    overlayGradient = !isChannel,
                    modifier = Modifier.fillMaxSize(),
                )
                if (watchProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { watchProgress.coerceIn(0f, 1f) },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(MobileDimensions.resumeBarHeight),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                ) {
                    if (isWatched) {
                        Icon(
                            imageVector = CinemaIcons.CheckCircle,
                            contentDescription = stringResource(R.string.content_watched_badge),
                            tint = CinemaSuccess,
                            modifier = Modifier.size(MobileDimensions.iconSmall),
                        )
                    }
                    parsedTitle.badge?.let { LanguageBadge(it) }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        // The primary text colour; the accent only marks what's current
                        // (phone UI audit P1).
                        color = if (isCurrent) CinemaAccent else CinemaTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        // Only the row that's actually playing scrolls its title.
                        modifier = if (isCurrentlyPlaying) Modifier.bounceMarquee() else Modifier,
                    )
                }
                if (isCurrentlyPlaying) {
                    Text(
                        text = stringResource(R.string.category_now_playing_badge),
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaAccent,
                        maxLines = 1,
                    )
                }
                // Films and shows: year · length · rating, from what the row already carries (as on
                // the TV). The rating is a plain "8.2/10": a star means favourite.
                if (!isChannel) {
                    val rating =
                        item.metadata.rating
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() && it.toDoubleOrNull() != 0.0 }
                    val ratingText =
                        rating?.let {
                            if (it.toDoubleOrNull() != null) {
                                stringResource(R.string.details_rating_out_of_ten, formatRating(it))
                            } else {
                                it
                            }
                        }
                    val metaLine =
                        remember(item.metadata, ratingText) {
                            listOfNotNull(
                                extractYear(item.metadata.year, item.metadata.releaseDate, null)?.toString(),
                                item.metadata.duration
                                    ?.takeIf(::hasMeaningfulDuration)
                                    ?.let(::formatDuration),
                                ratingText,
                            ).joinToString(" · ")
                        }
                    if (metaLine.isNotEmpty()) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                nowPlayingProgram?.let { program ->
                    Text(
                        text = stringResource(R.string.epg_now_prefix, program.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isCurrentlyPlaying) Modifier.bounceMarquee() else Modifier,
                    )
                }
            }
        }
    }
}
