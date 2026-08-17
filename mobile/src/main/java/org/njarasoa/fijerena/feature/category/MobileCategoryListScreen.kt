package org.njarasoa.fijerena.feature.category

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.elapsedFraction
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.rememberFavoriteHintVisible
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.model.toFavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.partitionVirtual
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.feature.player.MobilePlayerContent
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

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
        providerData: Map<String, String>,
    ) -> Unit,
    onSearchClick: () -> Unit = {},
    onEpgClick: (categoryId: String, categoryName: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
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
    val watchProgressMap by viewModel.watchProgress.collectAsStateWithLifecycle()
    val watchProgress = remember(watchProgressMap) { ImmutableWatchProgress(watchProgressMap) }
    val context = LocalContext.current
    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
    val epgIndexState by epgIndexer.state.collectAsStateWithLifecycle()

    // Refresh last played item when returning from player
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshLastPlayedItem()
        }
    }

    // Long-press favorite menu state
    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }

    // Show the context menu dialog when a target is set
    favoriteMenuTarget?.let { target ->
        MobileFavoriteContextMenuDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is FavoriteMenuTarget.Category -> {
                        viewModel.toggleFavoriteCategory(
                            target.categoryId,
                            target.categoryName,
                            target.contentType,
                        )
                    }
                    is FavoriteMenuTarget.Stream -> {
                        viewModel.toggleFavoriteStream(
                            target.itemId,
                            target.itemName,
                            target.categoryId,
                            target.contentType,
                        )
                    }
                }
            },
            onDismiss = { favoriteMenuTarget = null },
        )
    }

    // --- Live TV docked mini-player ---
    // Tap-driven equivalent of TV's focus-driven preview pane (tv/.../LiveTvSplitLayout.kt): a
    // small always-playing mini-player docked above the channel list, promotable to full screen
    // in place (same engine, same ViewModel pair — never a second connection). Unlike TV, mobile
    // commits immediately on tap (no debounced "light" preview step — a tap is already a
    // deliberate choice to watch) and does not auto-seed from the app-wide last-played channel on
    // a bare entry (no passive "focus" state to preview from on touch).
    val isLiveTv = contentType == ContentType.LIVE_TV
    var dockTarget by remember { mutableStateOf<MediaItem?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    var hasSeededDock by remember { mutableStateOf(false) }
    // Orientation-driven, not a width threshold — a screenWidthDp cutoff happens to catch most
    // phones once rotated (landscape width is usually well over 600dp) but doesn't express intent
    // and misbehaves on small phones/foldables. LocalConfiguration is read live, so this flips as
    // the device rotates.
    val isLandscape =
        LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    // One-time "long-press to favorite" hint — favoriting has no other visible affordance here.
    // Safe to render unconditionally below: the full-screen player path returns early above
    // (line ~365), so this composition is only ever reached while browsing/docked.
    val showFavoriteHint = rememberFavoriteHintVisible()

    BackHandler(enabled = isLiveTv && fullScreen) { fullScreen = false }
    // Dock auto-seeds on entry (below), so without this, Back from a docked preview would skip
    // straight past the bare category screen and out of Live TV — mirrors TV's silent bare
    // CategoryList push in TvNavHost.kt that guarantees the same stopover.
    BackHandler(enabled = isLiveTv && !fullScreen && dockTarget != null) { dockTarget = null }

    // Auto-seed the dock so entry never lands on a bare list — mirrors TV's
    // LiveTvSplitLayout: an explicit initialStreamId (search/EPG deep link) wins, otherwise
    // fall back to the app-wide last-played channel so a bare "Live TV" tap from the main menu
    // still lands on something.
    if (isLiveTv && !hasSeededDock) {
        val seedState = uiState
        LaunchedEffect(seedState) {
            if (seedState is CategoryViewModel.UiState.Success) {
                val seedId = initialStreamId ?: seedState.lastPlayedItemId
                seedState.streams?.firstOrNull { it.id == seedId }?.let { seed ->
                    hasSeededDock = true
                    dockTarget = seed
                }
            }
        }
    }

    val target = dockTarget
    val dockPlayback: PlaybackViewModel? = if (isLiveTv && target != null) viewModel() else null

    // While a preview is docked, the list below defaults to watch history — regardless of which
    // category/tab (if any) was actually browsed to get here — with the currently previewed
    // channel included and highlighted. Independent of the CategoryViewModel's own
    // selectedCategoryId/streams so normal category/tab browsing is untouched when nothing's
    // docked, and mirrors TV's LiveTvSplitLayout for the same reason. Not used for Movies/TV
    // Shows (target is always null there). Swipe left/right on the list (see
    // listSourceSwipeModifier below) toggles it over to Favorites instead.
    var listSource by remember { mutableStateOf(PreviewListSource.LAST_WATCHED) }
    var lastWatchedStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var lastWatchedStreamsLoading by remember { mutableStateOf(true) }
    var favoriteStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteStreamsLoading by remember { mutableStateOf(true) }
    val composableScope = rememberCoroutineScope()
    if (target != null) {
        LaunchedEffect(Unit) {
            lastWatchedStreams = viewModel.getLastWatchedSnapshot()
            lastWatchedStreamsLoading = false
        }
        LaunchedEffect(Unit) {
            favoriteStreams = viewModel.getFavoritesSnapshot()
            favoriteStreamsLoading = false
        }
    }

    // One video surface, relocated (not recreated) between the small dock box and the
    // full-screen MobilePlayerContent via movableContentOf — keeps the same Android
    // View/Surface alive across promote/demote so ExoPlayer never has to detach/reattach.
    // Earlier attempt at this rendered two channels simultaneously overlapping on a genuine
    // channel switch — root-caused to StreamingPlaybackService.playStream() not fully releasing
    // the outgoing decoder session before starting the next one (now fixed there with an
    // explicit player.stop()), not to surface sharing itself. Both call sites must use
    // TextureView, since the dock position (next to a scrolling/recomposing channel list) ANRs
    // the main thread with a SurfaceView.
    val videoSurface = remember {
        movableContentOf {
            EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize(), useTextureView = true)
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
                    ),
                    s.resumePosition,
                )
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
            if (reachedPlaying == null && dockSuccess?.streamId == s.streamId && !fullScreen) {
                dockPlayback.stop()
            }
        }

        // Pause when the app isn't RESUMED (backgrounded); resume on return — mirrors TV's
        // LiveTvSplitLayout, which does the same for the exact same reason (a docked preview
        // has no business burning battery/data while the app isn't visible).
        DisposableEffect(lifecycleOwner) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> dockPlayback.onFocusLost(false)
                        Lifecycle.Event.ON_RESUME -> dockPlayback.onFocusRegained()
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

    if (target != null && dockPlayback != null && dockLoader != null && fullScreen) {
        // Refresh both the history and favorites lists on demote (fullScreen leaving
        // composition) so they reflect what was just watched/(un)favorited — promoting/demoting
        // never leaves this screen, so nothing else would ever re-fetch them. Mirrors TV's
        // LiveTvSplitLayout for the same reason.
        DisposableEffect(Unit) {
            onDispose {
                composableScope.launch {
                    lastWatchedStreams = viewModel.getLastWatchedSnapshot()
                    favoriteStreams = viewModel.getFavoritesSnapshot()
                }
            }
        }
        MobilePlayerContent(
            viewModel = dockPlayback,
            loaderViewModel = dockLoader,
            contentType = contentType,
            videoSurface = videoSurface,
            onBack = { fullScreen = false },
        )
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
                        CinemaIconButton(onClick = onBack,
                            icon = {
                                Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back), tint = CinemaTextPrimary)
                            }
                        )
                    },
                    actions = {
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
                                    CinemaIconButton(onClick = { onEpgClick(selectedCatId, selectedCatName) },
                                        icon = {
                                            Icon(CinemaIcons.DateRange, stringResource(R.string.common_tv_guide), tint = CinemaTextPrimary)
                                        }
                                    )
                                }
                            }
                        }
                        CinemaIconButton(onClick = onSearchClick,
                            icon = {
                                Icon(CinemaIcons.Search, stringResource(R.string.common_search), tint = CinemaTextPrimary)
                            }
                        )
                    },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is CategoryViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is CategoryViewModel.UiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // EPG error/status banner (Live TV only)
                        if (contentType == ContentType.LIVE_TV) {
                            val epgMessage =
                                when (epgIndexState) {
                                    is EpgIndexState.Failed -> stringResource(R.string.epg_indexing_failed)
                                    is EpgIndexState.Indexing ->
                                        stringResource(R.string.epg_indexing_progress, (epgIndexState as EpgIndexState.Indexing).progressPercent)
                                    else -> null
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
                                categoryViewModel = viewModel,
                                onCategorySelected = { categoryId ->
                                    viewModel.loadStreams(categoryId)
                                },
                                onCategoryLongPress = { category ->
                                    favoriteMenuTarget =
                                        FavoriteMenuTarget.Category(
                                            categoryId = category.id,
                                            categoryName = category.name,
                                            contentType = contentType,
                                            isFavorite = viewModel.isFavoriteCategory(category.id, contentType),
                                        )
                                },
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                thickness = MobileDimensions.dividerThin,
                            )
                        }

                        // Streams list with pull-to-refresh — while a preview is docked, always
                        // history or favorites (see listSource above), regardless of the real
                        // selected category/tab.
                        val displayedStreams =
                            if (target == null) {
                                state.streams
                            } else if (listSource == PreviewListSource.FAVORITES) {
                                favoriteStreams
                            } else {
                                lastWatchedStreams
                            }
                        val displayedStreamsLoading =
                            if (target == null) {
                                state.streamsLoading
                            } else if (listSource == PreviewListSource.FAVORITES) {
                                favoriteStreamsLoading
                            } else {
                                lastWatchedStreamsLoading
                            }
                        // Swipe left/right toggles the docked panel between Last Watched and
                        // Favorites — mirrors TV's D-pad Left/Right on the same panel
                        // (LiveTvSplitLayout.kt). Only active while docked; normal category/tab
                        // browsing has no swipe. Threshold/accumulator pattern matches the
                        // full-screen player's own horizontal swipe handling
                        // (MobilePlayerScreen.kt) for consistency.
                        val listSourceSwipeModifier =
                            if (target == null) {
                                Modifier
                            } else {
                                Modifier.pointerInput(Unit) {
                                    var accumulator = 0f
                                    var fired = false
                                    detectHorizontalDragGestures(
                                        onDragStart = { accumulator = 0f; fired = false },
                                        onDragEnd = { accumulator = 0f; fired = false },
                                        onDragCancel = { accumulator = 0f; fired = false },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        accumulator += dragAmount
                                        if (!fired && kotlin.math.abs(accumulator) > 80f) {
                                            fired = true
                                            listSource =
                                                if (accumulator < 0) PreviewListSource.FAVORITES else PreviewListSource.LAST_WATCHED
                                        }
                                    }
                                }
                            }
                        val streamsList: @Composable () -> Unit = {
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
                                        composableScope.launch {
                                            lastWatchedStreamsLoading = true
                                            lastWatchedStreams = viewModel.getLastWatchedSnapshot()
                                            lastWatchedStreamsLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize().then(listSourceSwipeModifier),
                            ) {
                                StreamsList(
                                    items = displayedStreams,
                                    streamsLoading = displayedStreamsLoading,
                                    selectedCategoryId =
                                        when {
                                            target == null -> state.selectedCategoryId
                                            listSource == PreviewListSource.FAVORITES -> CategoryViewModel.FAVORITES_CATEGORY_ID
                                            else -> CategoryViewModel.LAST_WATCHED_CATEGORY_ID
                                        },
                                    panelTitle =
                                        if (target == null) {
                                            null
                                        } else if (listSource == PreviewListSource.FAVORITES) {
                                            stringResource(R.string.settings_import_favorites_label)
                                        } else {
                                            stringResource(R.string.player_last_watched)
                                        },
                                    lastPlayedItemId = state.lastPlayedItemId,
                                    nowPlaying = nowPlaying,
                                    watchedIds = watchedIds,
                                    watchProgress = watchProgress,
                                    currentlyPlayingId = target?.id,
                                    onItemSelected = { itemId, itemName, categoryId ->
                                        // Check if this is a category reference from "Recent Categories" or "Favorite Categories"
                                        val item = displayedStreams?.firstOrNull { it.id == itemId }
                                        val providerData = item?.providerData ?: emptyMap()
                                        if (providerData["isCategoryRef"] == "true") {
                                            val targetCategoryId = providerData["categoryId"]
                                            if (targetCategoryId != null) {
                                                viewModel.loadStreams(targetCategoryId)
                                            }
                                        } else if (isLiveTv && item != null) {
                                            // Dock locally instead of navigating away — mirrors
                                            // TV's LiveTvChannelList.onStreamPromote interception.
                                            dockTarget = item
                                        } else {
                                            onStreamSelected(itemId, itemName, categoryId, contentType, providerData)
                                        }
                                    },
                                    onItemLongPress = { item ->
                                        favoriteMenuTarget =
                                            item.toFavoriteMenuTarget(
                                                contentType = contentType,
                                                isFavorite = { viewModel.isFavorite(it, contentType) },
                                                isFavoriteCategory = { viewModel.isFavoriteCategory(it, contentType) },
                                            )
                                    },
                                )
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
                                            videoSurface()

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
                                                    tint = Color.White,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                                    // Marks this as the docked preview, distinct from the bare
                                    // list underneath — see docs/UX_FLOW_AUDIT.md, "Live TV
                                    // back-stopover".
                                    Text(
                                        text = stringResource(R.string.category_live_preview_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CinemaAccent,
                                    )
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
                                        videoSurface()

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
                                                tint = Color.White,
                                            )
                                        }

                                        Column(
                                            modifier =
                                                Modifier
                                                    .align(Alignment.BottomStart)
                                                    .fillMaxWidth()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                                        ),
                                                    )
                                                    .padding(CinemaSpacing.sm),
                                        ) {
                                            // Marks this as the docked preview, distinct from the
                                            // bare list underneath — see docs/UX_FLOW_AUDIT.md,
                                            // "Live TV back-stopover".
                                            Text(
                                                text = stringResource(R.string.category_live_preview_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.75f),
                                            )
                                            Text(
                                                text = dockSuccess?.streamName ?: target.name,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color.White,
                                                maxLines = 1,
                                            )
                                            val nowProg = dockSuccess?.currentEpgProgram
                                            if (nowProg != null) {
                                                Text(
                                                    text = stringResource(R.string.epg_now_prefix, nowProg.title),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White.copy(alpha = 0.85f),
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp),
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

    if (showFavoriteHint) {
        FavoriteHintBanner(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.xxl),
        )
    }
    }
}

/** Which list the docked Live TV preview's channel panel is showing, toggled via swipe left/right. */
private enum class PreviewListSource { LAST_WATCHED, FAVORITES }

/**
 * One-time hint pointing at the long-press-to-favorite gesture, which otherwise has zero
 * on-screen affordance. See [MobileCategoryListScreen] and `AppSettings.hasSeenFavoriteHint`.
 */
@Composable
private fun FavoriteHintBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = CinemaSurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(CinemaCornerRadius.large),
    ) {
        Text(
            text = stringResource(R.string.category_favorite_hint_mobile),
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextPrimary,
            modifier = Modifier.padding(horizontal = CinemaSpacing.lg, vertical = CinemaSpacing.sm),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChipRow(
    categories: List<org.njarasoa.fijerena.core.player.domain.MediaCategory>,
    selectedCategoryId: String?,
    contentType: String,
    categoryViewModel: CategoryViewModel,
    onCategorySelected: (String) -> Unit,
    onCategoryLongPress: (org.njarasoa.fijerena.core.player.domain.MediaCategory) -> Unit = {},
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
                itemsIndexed(virtualCategories, key = { _, category -> category.id }, contentType = { _, _ -> "category" }) { index, category ->
                    CinemaFilterChip(
                        selected = category.id == selectedCategoryId,
                        onClick = { onCategorySelected(category.id) },
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                modifier = Modifier.bounceMarquee(),
                            )
                        },
                        colors = chipColors,
                        modifier =
                            if (enteredCategoryIds.add(category.id)) {
                                Modifier.staggeredEntrance(index)
                            } else {
                                Modifier
                            },
                    )
                }
            }
        }

        // Regular categories row
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
                val isFavCat = categoryViewModel.isFavoriteCategory(category.id, contentType)
                CinemaFilterChip(
                    selected = category.id == selectedCategoryId,
                    onClick = { onCategorySelected(category.id) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isFavCat) {
                                Text(
                                    text = "\u2605",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = category.name,
                                maxLines = 1,
                                modifier = Modifier.bounceMarquee(),
                            )
                        }
                    },
                    modifier =
                        (
                            if (enteredCategoryIds.add(category.id)) {
                                Modifier.staggeredEntrance(index)
                            } else {
                                Modifier
                            }
                        ).combinedClickable(
                            onClick = { onCategorySelected(category.id) },
                            onLongClick = { onCategoryLongPress(category) },
                        ),
                    colors = chipColors,
                )
            }
        }
    }
}

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
    // Only set while docked (see MobileCategoryListScreen) — the category chip row above is
    // hidden there, so without this there's no way to tell Last Watched and Favorites apart
    // after a swipe. Left null everywhere else: normal browsing already shows the selected
    // chip.
    panelTitle: String? = null,
    onItemSelected: (itemId: String, itemName: String, categoryId: String) -> Unit,
    onItemLongPress: (org.njarasoa.fijerena.core.player.domain.MediaItem) -> Unit = {},
) {
    val listState = rememberLazyListState()

    // Entrance animation plays once per item: LazyColumn recycles item composition off the ends
    // of the scroll buffer, so without this guard the fade/slide replays on every scroll.
    // Keyed to items so it resets (bounded) on category switch instead of growing unbounded
    // across every stream id seen this session.
    val enteredStreamIds = remember(items) { mutableSetOf<String>() }

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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.category_select_category),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        streamsLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.category_loading_streams),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items.isNullOrEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.category_no_streams),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                // Pinned, not a LazyColumn item — otherwise it scrolls away with the list, and on
                // the docked preview panel that's the only thing telling Last Watched and
                // Favorites apart (see panelTitle above).
                Column(modifier = Modifier.padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 4.dp)) {
                    if (panelTitle != null) {
                        Text(
                            text = panelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(R.string.category_streams_count, items.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(LocalUiStyle.current.grid.spacing),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "stream" }) { index, item ->
                        StreamCard(
                            item = item,
                            nowPlayingProgram = nowPlaying[item.id],
                            isCurrentlyPlaying = item.id == currentlyPlayingId,
                            isWatched = item.id in watchedIds,
                            watchProgress = watchProgress[item.id] ?: 0f,
                            onClick = {
                                onItemSelected(item.id, item.name, item.categoryId)
                            },
                            onLongClick = { onItemLongPress(item) },
                            modifier =
                                if (enteredStreamIds.add(item.id)) {
                                    Modifier.staggeredEntrance(index)
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Themed context menu dialog for favoriting categories/streams on mobile.
 */
@Composable
private fun MobileFavoriteContextMenuDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()

    val actionText = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add)

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = itemName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(actionText)
            }
        },
        dismissButton = {
            CinemaDialogTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamCard(
    item: org.njarasoa.fijerena.core.player.domain.MediaItem,
    nowPlayingProgram: EpgProgram? = null,
    isCurrentlyPlaying: Boolean = false,
    isWatched: Boolean = false,
    watchProgress: Float = 0f,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CinemaCard(
        modifier =
            modifier
                .fillMaxWidth()
                .height(MobileDimensions.streamCardHeight)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border =
            if (isCurrentlyPlaying) {
                androidx.compose.foundation.BorderStroke(2.dp, CinemaAccent)
            } else {
                null
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(CinemaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            ) {
            // Poster thumbnail
            CinemaThumbnail(
                url = item.thumbnailUrl,
                fallbackLetter = item.name.firstOrNull(),
                contentType = ThumbnailContentType.DEFAULT,
                modifier =
                    Modifier.size(
                        width = MobileDimensions.posterWidth,
                        height = MobileDimensions.posterHeight,
                    ),
            )
            // Stream name + rating
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
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        modifier = Modifier.bounceMarquee(),
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
                item.metadata.rating?.let { rating ->
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.textMedium),
                        maxLines = 1,
                    )
                }
                // "What's On Now" for Live TV
                nowPlayingProgram?.let { program ->
                    Text(
                        text = stringResource(R.string.epg_now_prefix, program.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        modifier = Modifier.bounceMarquee(),
                    )
                }
            }
            }

            // Progress bar
            if (watchProgress > 0f) {
                LinearProgressIndicator(
                    progress = { watchProgress.coerceIn(0f, 1f) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(MobileDimensions.strokeWidth),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.focusedTint),
                )
            }
        }
    }
}
