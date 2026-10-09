@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.feature.category.components.LiveTvSplitLayout
import org.njarasoa.fijerena.feature.category.components.LoadingScreen
import org.njarasoa.fijerena.feature.category.components.TwoColumnLayout
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.rail.HideTvNavRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * TV two-column layout: Categories on left, Streams on right.
 */
@Composable
fun TvCategoryGridScreen(
    contentType: String,
    initialCategoryId: String? = null,
    initialStreamId: String? = null,
    /**
     * Live TV only. True: this entry is the preview alone, opened on [initialStreamId] from Search,
     * the TV Guide or the EPG Browser; Back leaves it. False: the Live TV browse entry, with the
     * preview as a layer over it (LT7) — open on entry when [initialStreamId] is set.
     */
    showPreviewPane: Boolean = true,
    /**
     * Live TV browse, back from another section: a preview layer saved open (under the TV Guide its
     * full screen opened) stays closed — coming back never starts video on its own
     * (docs/plans/archive/20261008_tv-nav-rail-plan.md → Risks).
     */
    closeSavedLivePreview: Boolean = false,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, target: BrowseTarget) -> Unit,
    /** The TV Guide for a list; with a channel when opened from the player (its row gets entry focus). */
    onEpgClick: (categoryId: String, categoryName: String, focusChannelId: String?) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {},
    /** Leaves for Home — after a remote Stop of the Live TV preview (see LiveTvSplitLayout). */
    onHome: () -> Unit = {},
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
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteCategoryIds by viewModel.favoriteCategoryIds.collectAsStateWithLifecycle()
    val watchProgress by viewModel.watchProgress.collectAsStateWithLifecycle()
    val watchedIds by viewModel.watchedIds.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
    val epgIndexState by epgIndexer.state.collectAsStateWithLifecycle()

    // Refresh last played item when screen resumes (e.g. back from player)
    // Uses repeatOnLifecycle to avoid recomposing the entire screen on every lifecycle transition
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // The first RESUMED is this screen's own initial composition, which just ran the load
        // that populates uiState/favoriteIds/watchProgress/watchedIds from scratch — re-running
        // the same per-item refresh here duplicates that work. Only a *later* RESUMED means an
        // actual return from elsewhere, which is what this refresh exists for.
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

    val immutableFavoriteIds = remember(favoriteIds) { ImmutableStringSet(favoriteIds) }
    val immutableFavoriteCategoryIds = remember(favoriteCategoryIds) { ImmutableStringSet(favoriteCategoryIds) }
    val immutableWatchProgress = remember(watchProgress) { ImmutableWatchProgress(watchProgress) }
    val immutableWatchedIds = remember(watchedIds) { ImmutableStringSet(watchedIds) }

    CategoryGridContent(
        uiState = uiState,
        nowPlaying = nowPlaying,
        supportsNativeEpg = supportsNativeEpg,
        favoriteIds = immutableFavoriteIds,
        favoriteCategoryIds = immutableFavoriteCategoryIds,
        watchProgress = immutableWatchProgress,
        watchedIds = immutableWatchedIds,
        epgIndexState = epgIndexState,
        configuration = configuration,
        catViewModel = viewModel,
        onStreamSelected = onStreamSelected,
        onEpgClick = onEpgClick,
        onBack = onBack,
        onHome = onHome,
        contentType = contentType,
        initialStreamId = initialStreamId,
        showPreviewPane = showPreviewPane,
        closeSavedLivePreview = closeSavedLivePreview,
    )
}

@Composable
private fun CategoryGridContent(
    uiState: CategoryViewModel.UiState,
    nowPlaying: ImmutableNowPlaying,
    supportsNativeEpg: Boolean,
    favoriteIds: ImmutableStringSet,
    favoriteCategoryIds: ImmutableStringSet,
    watchProgress: ImmutableWatchProgress,
    watchedIds: ImmutableStringSet,
    epgIndexState: EpgIndexState,
    configuration: android.content.res.Configuration,
    catViewModel: CategoryViewModel,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, target: BrowseTarget) -> Unit,
    onEpgClick: (categoryId: String, categoryName: String, focusChannelId: String?) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    contentType: String,
    initialStreamId: String? = null,
    showPreviewPane: Boolean = true,
    closeSavedLivePreview: Boolean = false,
) {
    val scale = LocalUiScale.current
    val safeMarginModifier =
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical,
            )

    // Live TV is one nav entry (LT7). The preview — and full screen, promoted inside it — is a layer
    // over the browse list, as the dock is on mobile, not an entry of its own: open while
    // livePreviewChannelId names the channel it opened on (Home → Live TV: the last channel, passed
    // as initialStreamId; browse: the channel OK was pressed on), closed by Back. An entry with
    // showPreviewPane is the preview alone and has no browse under it.
    // Browse is not composed under the preview: its rows would stay in the focus tree, and its own
    // focus effects keep running, behind an opaque preview or the full-screen player. Its saved
    // state is kept for it instead (browseState), and it shares this CategoryViewModel, so the list
    // the channel was picked from is the preview's ChannelContext (LT2) and Back rebuilds browse
    // without reloading the categories.
    val isLiveTv = contentType == org.njarasoa.fijerena.core.player.domain.ContentType.LIVE_TV
    val isLiveBrowse = isLiveTv && !showPreviewPane
    var livePreviewChannelId by rememberSaveable { mutableStateOf(initialStreamId.takeIf { isLiveBrowse }) }
    val showLivePreview = isLiveTv && (showPreviewPane || livePreviewChannelId != null)
    // The channel the preview plays now, after any retune or zap: Back hands it to browse, which
    // lands on it (LT6), and a preview rebuilt after process death comes back on it. Cleared when
    // the preview closes, so the next one starts on the channel it is opened on.
    var livePlayingChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    // Each opening of the layer is keyed by this count, so a layer dropped while saved open
    // (closeSavedLivePreview) leaves its saved state — full screen, the guide's return channel —
    // behind instead of handing it to the next opening.
    var livePreviewOpening by rememberSaveable { mutableIntStateOf(0) }
    // Once, before anything reads them: the layer must not compose (and start playing) first.
    var dropSavedPreview by remember { mutableStateOf(closeSavedLivePreview) }
    if (dropSavedPreview) {
        dropSavedPreview = false
        if (livePreviewChannelId != null) {
            livePreviewChannelId = null
            livePlayingChannelId = null
            livePreviewOpening++
        }
    }
    // Plain remember, as the nav hand-off it replaces was taken once: a return from Search or the
    // TV Guide rebuilds browse without it.
    var returnedLiveChannelId by remember { mutableStateOf<String?>(null) }
    val browseState = rememberSaveableStateHolder()
    // The layer's ViewModels go when it closes — in an effect, so after its own teardown
    // (stopAndRelease) has run.
    val previewViewModels: LivePreviewViewModels = viewModel()
    LaunchedEffect(showLivePreview) {
        if (!showLivePreview) previewViewModels.clear()
    }
    val closeLivePreview: () -> Unit = {
        returnedLiveChannelId = livePlayingChannelId
        livePlayingChannelId = null
        livePreviewChannelId = null
        // What resuming the browse entry did on Back from a preview entry, and Recent reloaded as
        // the fresh browse under Home's preview loaded it — after the preview recorded its channels.
        // Live TV's Recent keeps its order for the visit (docs/plans/archive/20261009_tv-recents-favorites-plan.md
        // → B): the channels the preview recorded come in at the top, the rows already there stay put.
        catViewModel.refreshLastPlayedItem()
        catViewModel.refreshWatchStateOnResume()
        val browsed = (catViewModel.uiState.value as? CategoryViewModel.UiState.Success)?.selectedCategoryId
        if (browsed == CategoryViewModel.RECENT_CATEGORY_ID) catViewModel.loadStreams(browsed)
    }

    // Live TV entered again — from another section, Home, or back from the TV Guide or Search, each
    // a fresh composition over the kept ViewModel — re-sorts Recent, last watched first; while you
    // are in the section it keeps its order (plan → B). Nothing to do on the first entry: the
    // ViewModel is still loading.
    LaunchedEffect(Unit) {
        if (isLiveBrowse && !showLivePreview) catViewModel.reloadRecentOnEntry()
    }

    // The ViewModel's list is the preview's ChannelContext (LT2), and a ViewModel recreated after
    // process death starts on its default list (Recent): the preview would come back on a channel
    // that list doesn't have, with no video. So the list is kept with this screen's saved state and
    // put back once if the recreated ViewModel lands elsewhere.
    var savedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionChecked by remember { mutableStateOf(false) }
    val selectedCategoryId = (uiState as? CategoryViewModel.UiState.Success)?.selectedCategoryId
    LaunchedEffect(selectedCategoryId) {
        if (!isLiveTv || selectedCategoryId == null) return@LaunchedEffect
        if (!selectionChecked) {
            selectionChecked = true
            val saved = savedCategoryId
            if (saved != null && saved != selectedCategoryId) {
                catViewModel.loadStreams(saved)
                return@LaunchedEffect
            }
        }
        savedCategoryId = selectedCategoryId
    }

    // 5% padding for TV overscan safety — applied per-branch rather than around the whole
    // `when`, since LiveTvSplitLayout's promoted full-screen player must NOT inherit it (it
    // renders inside this same composable, in place, to avoid a second PlaybackViewModel/ANR —
    // see LiveTvSplitLayout's doc comment). LiveTvSplitLayout applies this same margin itself,
    // but only around its split/browsing UI, not the full-screen player.
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedContent(
            targetState = uiState,
            // Keyed on the sealed subtype, not the state instance itself — Success carries fresh
            // data (stream lists, streamsLoading) on nearly every emission, and a plain
            // targetState comparison would refire the crossfade on every one of those instead of
            // only on Loading/Success/Error swaps.
            contentKey = { it::class },
            transitionSpec = {
                fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs)) togetherWith
                    fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
            },
            label = "category_state_crossfade",
        ) { state ->
            when (state) {
                is CategoryViewModel.UiState.Loading -> {
                    AmbientBackdrop(modifier = Modifier.fillMaxSize())
                    Box(modifier = safeMarginModifier) {
                        LoadingScreen()
                    }
                }

                is CategoryViewModel.UiState.Success -> {
                    val immutableCategories = remember(state.categories) { ImmutableCategoryList(state.categories) }
                    val immutableStreams = remember(state.streams) { state.streams?.let { ImmutableMediaList(it) } }
                    if (showLivePreview) {
                        // The preview and its full screen take the screen, and Left has a job there.
                        HideTvNavRail()
                        val ctx = LocalContext.current
                        val devMode =
                            remember {
                                org.njarasoa.fijerena.core.network
                                    .AppSettings(ctx.applicationContext)
                                    .isDevMode
                            }
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides rememberLayerOwner(previewViewModels.store()),
                        ) {
                            key(livePreviewOpening) {
                                LiveTvSplitLayout(
                                    categoryViewModel = catViewModel,
                                    categories = immutableCategories,
                                    selectedCategoryId = state.selectedCategoryId,
                                    streams = immutableStreams,
                                    streamsLoading = state.streamsLoading,
                                    categoriesRefreshing = state.categoriesRefreshing,
                                    lastPlayedItemId = state.lastPlayedItemId,
                                    nowPlaying = nowPlaying,
                                    contentType = contentType,
                                    isDevMode = devMode,
                                    favoriteIds = favoriteIds,
                                    favoriteCategoryIds = favoriteCategoryIds,
                                    watchProgress = watchProgress,
                                    watchedIds = watchedIds,
                                    onCategorySelected = { categoryId -> catViewModel.loadStreams(categoryId) },
                                    onStreamSelected = onStreamSelected,
                                    onRefreshCategories = { catViewModel.refreshCategories() },
                                    onRefreshStreams = { categoryId -> catViewModel.refreshStreams(categoryId) },
                                    onBack = if (isLiveBrowse) closeLivePreview else onBack,
                                    onHome = onHome,
                                    initialStreamId = if (isLiveBrowse) livePlayingChannelId ?: livePreviewChannelId else initialStreamId,
                                    onPlayingChannel = { streamId -> livePlayingChannelId = streamId },
                                    // The player's Guide button (GD5), only when the source has a guide.
                                    onOpenGuide = onEpgClick.takeIf { supportsNativeEpg || epgIndexState is EpgIndexState.Indexed },
                                )
                            }
                        }
                    } else {
                        AmbientBackdrop(modifier = Modifier.fillMaxSize())
                        browseState.SaveableStateProvider(BROWSE_STATE_KEY) {
                            Box(modifier = safeMarginModifier) {
                                TwoColumnLayout(
                                    categoryViewModel = catViewModel,
                                    categories = immutableCategories,
                                    selectedCategoryId = state.selectedCategoryId,
                                    streams = immutableStreams,
                                    streamsLoading = state.streamsLoading,
                                    categoriesRefreshing = state.categoriesRefreshing,
                                    lastPlayedItemId = state.lastPlayedItemId,
                                    returnedPlayingId = returnedLiveChannelId,
                                    nowPlaying = nowPlaying,
                                    contentType = contentType,
                                    favoriteIds = favoriteIds,
                                    favoriteCategoryIds = favoriteCategoryIds,
                                    watchProgress = watchProgress,
                                    watchedIds = watchedIds,
                                    supportsNativeEpg = supportsNativeEpg,
                                    epgIndexState = epgIndexState,
                                    onCategorySelected = { categoryId ->
                                        catViewModel.loadStreams(categoryId)
                                    },
                                    onStreamSelected = { streamId, streamName, categoryId, target ->
                                        // OK on a channel opens the preview layer on it (LT7). The
                                        // list it was picked from — the browsed category, Recent or
                                        // Favourites — is this CategoryViewModel's selection, so it
                                        // is the preview's ChannelContext (LT2).
                                        if (target is BrowseTarget.Channel) {
                                            livePreviewChannelId = target.streamId
                                        } else {
                                            onStreamSelected(streamId, streamName, categoryId, target)
                                        }
                                    },
                                    onRefreshCategories = {
                                        catViewModel.refreshCategories()
                                    },
                                    onRefreshStreams = { categoryId ->
                                        catViewModel.refreshStreams(categoryId)
                                    },
                                    onEpgClick = { categoryId, categoryName -> onEpgClick(categoryId, categoryName, null) },
                                    onBack = onBack,
                                )
                            }
                        }
                    }
                }

                is CategoryViewModel.UiState.Error -> {
                    AmbientBackdrop(modifier = Modifier.fillMaxSize())
                    Box(modifier = safeMarginModifier) {
                        TvErrorState(
                            message = state.message,
                            onRetry = { catViewModel.retry() },
                            onBack = onBack,
                        )
                    }
                }
            }
        }
    }
}

/** The browse layer's key in its SaveableStateHolder (LT7). */
private const val BROWSE_STATE_KEY = "browse"

/**
 * The Live TV preview layer's ViewModels (LT7) — its `PlaybackViewModel` and
 * `StreamLoaderViewModel` — in a store of their own, held by the entry. They live as long as the
 * layer is open, as they did when the preview was a nav entry: across a trip to the TV Guide and
 * back, and an activity recreate, but not past Back to browse — a loader left behind would still
 * write its channel into Recent after the watch delay, and the next opening would start from its
 * stale stream. Popping the entry clears them too.
 */
internal class LivePreviewViewModels : ViewModel() {
    private var store: ViewModelStore? = null

    fun store(): ViewModelStore = store ?: ViewModelStore().also { store = it }

    fun clear() {
        store?.clear()
        store = null
    }

    override fun onCleared() = clear()
}

/** A [ViewModelStoreOwner] over [store] that can build `AndroidViewModel`s (`PlaybackViewModel`). */
@Composable
private fun rememberLayerOwner(store: ViewModelStore): ViewModelStoreOwner {
    val application = LocalContext.current.applicationContext as Application
    return remember(store) {
        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
            override val viewModelStore = store
            override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        }
    }
}
