package org.njarasoa.fijerena.feature.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
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
    var favoriteMenuTarget by remember { mutableStateOf<MobileFavoriteMenuTarget?>(null) }

    // Show the context menu dialog when a target is set
    favoriteMenuTarget?.let { target ->
        MobileFavoriteContextMenuDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is MobileFavoriteMenuTarget.Category -> {
                        viewModel.toggleFavoriteCategory(
                            target.categoryId,
                            target.categoryName,
                            target.contentType,
                        )
                    }
                    is MobileFavoriteMenuTarget.Stream -> {
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
    val isWideLayout = LocalConfiguration.current.screenWidthDp >= 600

    // One-time "long-press to favorite" hint — favoriting has no other visible affordance here.
    // Shown once ever (marked seen as soon as it's shown), auto-dismisses after a few seconds.
    // Safe to render unconditionally below: the full-screen player path returns early above
    // (line ~365), so this composition is only ever reached while browsing/docked.
    val hintContext = LocalContext.current
    var showFavoriteHint by remember {
        mutableStateOf(!org.njarasoa.fijerena.core.network.AppSettings(hintContext.applicationContext).hasSeenFavoriteHint)
    }
    LaunchedEffect(showFavoriteHint) {
        if (showFavoriteHint) {
            org.njarasoa.fijerena.core.network.AppSettings(hintContext.applicationContext).hasSeenFavoriteHint = true
            delay(4000)
            showFavoriteHint = false
        }
    }

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

    // While a preview is docked, the list below is always watch history — regardless of which
    // category/tab (if any) was actually browsed to get here — with the currently previewed
    // channel included and highlighted. Independent of the CategoryViewModel's own
    // selectedCategoryId/streams so normal category/tab browsing is untouched when nothing's
    // docked, and mirrors TV's LiveTvSplitLayout for the same reason. Not used for Movies/TV
    // Shows (target is always null there).
    var lastWatchedStreams by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var lastWatchedStreamsLoading by remember { mutableStateOf(true) }
    val composableScope = rememberCoroutineScope()
    if (target != null) {
        LaunchedEffect(Unit) {
            lastWatchedStreams = viewModel.getLastWatchedSnapshot()
            lastWatchedStreamsLoading = false
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

    if (target != null && dockPlayback != null && dockLoader != null && fullScreen) {
        // Refresh the history list on demote (fullScreen leaving composition) so it reflects
        // what was just watched — promoting/demoting never leaves this screen, so nothing else
        // would ever re-fetch it. Mirrors TV's LiveTvSplitLayout for the same reason.
        DisposableEffect(Unit) {
            onDispose {
                composableScope.launch {
                    lastWatchedStreams = viewModel.getLastWatchedSnapshot()
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
            TopAppBar(
                title = { Text(contentType.replace("_", " ")) },
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
                                        MobileFavoriteMenuTarget.Category(
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
                        // history (see lastWatchedStreams above), regardless of the real
                        // selected category/tab.
                        val displayedStreams = if (target != null) lastWatchedStreams else state.streams
                        val displayedStreamsLoading = if (target != null) lastWatchedStreamsLoading else state.streamsLoading
                        val streamsList: @Composable () -> Unit = {
                            PullToRefreshBox(
                                isRefreshing = displayedStreamsLoading,
                                onRefresh = {
                                    if (target != null) {
                                        composableScope.launch {
                                            lastWatchedStreamsLoading = true
                                            lastWatchedStreams = viewModel.getLastWatchedSnapshot()
                                            lastWatchedStreamsLoading = false
                                        }
                                    } else {
                                        state.selectedCategoryId?.let { viewModel.refreshStreams(it) }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                StreamsList(
                                    items = displayedStreams,
                                    streamsLoading = displayedStreamsLoading,
                                    selectedCategoryId = if (target != null) CategoryViewModel.LAST_WATCHED_CATEGORY_ID else state.selectedCategoryId,
                                    lastPlayedItemId = state.lastPlayedItemId,
                                    nowPlaying = nowPlaying,
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
                                        // Category reference items (from "Favorite Categories" / "Recent Categories")
                                        // should toggle the category favorite, not create a stream favorite
                                        val realCategoryId = item.providerData["categoryId"]
                                        if (item.providerData["isCategoryRef"] == "true" && realCategoryId != null) {
                                            favoriteMenuTarget =
                                                MobileFavoriteMenuTarget.Category(
                                                    categoryId = realCategoryId,
                                                    categoryName = item.name,
                                                    contentType = contentType,
                                                    isFavorite = viewModel.isFavoriteCategory(realCategoryId, contentType),
                                                )
                                        } else {
                                            favoriteMenuTarget =
                                                MobileFavoriteMenuTarget.Stream(
                                                    itemId = item.id,
                                                    itemName = item.name,
                                                    categoryId = item.categoryId,
                                                    contentType = contentType,
                                                    isFavorite = viewModel.isFavorite(item.id, contentType),
                                                )
                                        }
                                    },
                                )
                            }
                        }

                        if (isLiveTv && target != null && isWideLayout) {
                            // Landscape/tablet split — mirrors tv/.../LiveTvSplitLayout.kt's
                            // side-by-side layout: video+EPG pane left, channel list right.
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .weight(0.5f)
                                            .fillMaxHeight()
                                            .padding(horizontal = CinemaSpacing.md),
                                ) {
                                    // Fixed height rather than Modifier.aspectRatio(16f / 9f): the
                                    // latter mis-positions its content in this specific spot (a
                                    // Row placed after other siblings in the parent Column, each
                                    // weighted+fillMaxHeight child measured with a reduced-but-
                                    // bounded height constraint) — reproduced with a bare colored
                                    // Box, confirmed it's not related to EmbeddedPlayerSurface.
                                    // TV's LiveTvSplitLayout doesn't hit this because its Row has
                                    // no preceding siblings to reduce the incoming constraint.
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(CinemaCornerRadius.medium))
                                                .background(CinemaSurface)
                                                .clickable(onClick = { fullScreen = true }),
                                    ) {
                                        videoSurface()
                                    }
                                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                                    // Marks this as the docked preview, distinct from the bare
                                    // list underneath — see docs/UX_FLOW_AUDIT.md, "Live TV
                                    // back-stopover".
                                    Text(
                                        text = "LIVE PREVIEW",
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
                                    val nextProg = dockSuccess?.nextEpgProgram
                                    if (nextProg != null) {
                                        Text(
                                            text = "Up next: ${nextProg.title}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CinemaTextSecondary,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
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
                                                contentDescription = "Close",
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
                                                text = "LIVE PREVIEW",
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
                                                    text = "Now: ${nowProg.title}",
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
            text = "Long-press a channel or category to add it to Favorites",
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextPrimary,
            modifier = Modifier.padding(horizontal = CinemaSpacing.lg, vertical = CinemaSpacing.sm),
        )
    }
}

// Extracted as a top-level constant to avoid allocating a new Set on every recomposition
private val VIRTUAL_CATEGORY_IDS =
    setOf(
        CategoryViewModel.FAVORITES_CATEGORY_ID,
        CategoryViewModel.FAVORITE_CATEGORIES_ID,
        CategoryViewModel.LAST_WATCHED_CATEGORY_ID,
        CategoryViewModel.CONTINUE_WATCHING_CATEGORY_ID,
        CategoryViewModel.RECENTLY_VIEWED_CATEGORIES_ID,
    )

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
    // Single-pass partition instead of two separate filter() calls
    val (virtualCategories, regularCategories) =
        remember(categories) {
            categories.partition { it.id in VIRTUAL_CATEGORY_IDS }
        }

    val listState = rememberLazyListState()
    val chipColors =
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        )

    // Entrance animation plays once per item: LazyRow recycles item composition off the ends
    // of the scroll buffer, so without this guard the fade/slide replays on every scroll.
    val enteredCategoryIds = remember { mutableSetOf<String>() }

    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null && selectedCategoryId !in VIRTUAL_CATEGORY_IDS) {
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
    currentlyPlayingId: String? = null,
    onItemSelected: (itemId: String, itemName: String, categoryId: String) -> Unit,
    onItemLongPress: (org.njarasoa.fijerena.core.player.domain.MediaItem) -> Unit = {},
) {
    val listState = rememberLazyListState()

    // Entrance animation plays once per item: LazyColumn recycles item composition off the ends
    // of the scroll buffer, so without this guard the fade/slide replays on every scroll.
    val enteredStreamIds = remember { mutableSetOf<String>() }

    LaunchedEffect(items, lastPlayedItemId) {
        if (!items.isNullOrEmpty() && lastPlayedItemId != null) {
            val index = items.indexOfFirst { it.id == lastPlayedItemId }
            if (index > 0) {
                // +1 to account for the header item
                listState.animateScrollToItem(index + 1)
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(LocalUiStyle.current.grid.spacing),
            ) {
                item(contentType = "header") {
                    Text(
                        text = stringResource(R.string.category_streams_count, items.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                    )
                }
                itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "stream" }) { index, item ->
                    StreamCard(
                        item = item,
                        nowPlayingProgram = nowPlaying[item.id],
                        isCurrentlyPlaying = item.id == currentlyPlayingId,
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

/**
 * Data class representing a pending favorite action from a long-press on mobile.
 */
private sealed class MobileFavoriteMenuTarget {
    data class Category(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val isFavorite: Boolean,
    ) : MobileFavoriteMenuTarget()

    data class Stream(
        val itemId: String,
        val itemName: String,
        val categoryId: String,
        val contentType: String,
        val isFavorite: Boolean,
    ) : MobileFavoriteMenuTarget()
}

/**
 * Themed context menu dialog for favoriting categories/streams on mobile.
 */
@Composable
private fun MobileFavoriteContextMenuDialog(
    target: MobileFavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (itemName, isFavorite) =
        when (target) {
            is MobileFavoriteMenuTarget.Category -> target.categoryName to target.isFavorite
            is MobileFavoriteMenuTarget.Stream -> target.itemName to target.isFavorite
        }

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
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
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
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.bounceMarquee(),
                )
                if (isCurrentlyPlaying) {
                    Text(
                        text = "▶ Playing",
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
    }
}
