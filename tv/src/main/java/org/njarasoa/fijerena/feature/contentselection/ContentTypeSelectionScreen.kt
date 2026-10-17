@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.njarasoa.fijerena.feature.contentselection

import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.history.SourcedFavoriteCategory
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.LiveRowEntry
import org.njarasoa.fijerena.core.ui.home.ProfileSections
import org.njarasoa.fijerena.core.ui.home.favoriteChannelsRow
import org.njarasoa.fijerena.core.ui.home.mergeLiveRow
import org.njarasoa.fijerena.core.ui.home.rememberProfileSyncStates
import org.njarasoa.fijerena.core.ui.home.syncSummary
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.feature.category.components.FavoriteContextMenuDialog
import org.njarasoa.fijerena.feature.category.components.MinimalBringIntoView
import org.njarasoa.fijerena.feature.category.components.RowMenuList
import org.njarasoa.fijerena.feature.contentselection.components.HomeClock
import org.njarasoa.fijerena.feature.contentselection.components.SourcesStatusSheet
import org.njarasoa.fijerena.feature.contentselection.components.SyncSummaryLine
import org.njarasoa.fijerena.feature.contentselection.components.TvContinueWatchingShelf
import org.njarasoa.fijerena.feature.contentselection.components.TvFavoriteCategoriesRow
import org.njarasoa.fijerena.feature.contentselection.components.TvFavoritesRow
import org.njarasoa.fijerena.feature.contentselection.components.TvLiveRow
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvUndoBar
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.LocalTvNavRail
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.components.rememberUndoBarState
import org.njarasoa.fijerena.ui.components.undoOnRemoteKeys
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.navigation.ContentType as NavContentType
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

@Composable
fun ContentTypeSelectionScreen(
    onEpgBrowser: () -> Unit = {},
    // The merged sections, once read (with none: nothing is called).
    onCapabilitiesResolved: (Set<String>) -> Unit = {},
    onContinueWatchingSelected: (ContinueWatchingItem) -> Unit = {},
    // A channel from the Live row, the list it zaps through (CategoryViewModel's virtual
    // "favorites" or "recent" category id) and the source it is on.
    onLiveChannelSelected: (streamId: String, contextCategoryId: String, providerId: Long) -> Unit = { _, _, _ -> },
    // A favourite movie (ContentType.MOVIES) or show (ContentType.TV_SHOWS) from its Home row.
    onFavoriteSelected: (item: MediaItem, contentType: String) -> Unit = { _, _ -> },
    /** A Favourite categories card: its section's list, on that category of its source. */
    onFavoriteCategorySelected: (categoryId: String, contentType: String, providerId: Long) -> Unit = { _, _, _ -> },
    // A "Browse <source>" entry of a profile with no Xtream source (O1): that source's sections.
    onBrowse: (BrowseSource) -> Unit = {},
    // A profile with no source: Add Source.
    onAddSource: () -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    // What the profile browses (multi-source plan, Phase 6): its Xtream sources' sections, merged,
    // and a Browse entry per other source; null until read. There is no source in use.
    var sections by remember { mutableStateOf<ProfileSections?>(null) }
    val supportedContentTypes = sections?.merged.orEmpty()

    // The header's one line for all the profile's sources (O3), and its sheet.
    val syncStates = rememberProfileSyncStates()
    val syncSummary = remember(syncStates) { syncSummary(syncStates) }
    var showSourcesSheet by remember { mutableStateOf(false) }
    val firstSyncs by ProviderSyncRunner.firstSyncs.collectAsStateWithLifecycle()

    // The rows' lists: every Xtream source of the profile's, merged (multi-source plan, Phases 4
    // and 6). Each card acts on, and opens on, its own source. Stored data only: an offline source
    // holds nothing up.
    var mergedLists by remember { mutableStateOf<MergedLists?>(null) }
    // Read again whenever the profile's sources change (one turned on or off, added or deleted),
    // and only then: the sections, the Browse entries and every row follow.
    LaunchedEffect(Unit) {
        ProviderRepository(context.applicationContext).profileSourceSet(appSettings.activeProfileId).collect {
            val loaded = ProfileSections.load(context.applicationContext)
            sections = loaded
            mergedLists = MergedLists.forSections(context.applicationContext)
            if (loaded.merged.isNotEmpty()) onCapabilitiesResolved(loaded.merged)
        }
    }
    val sourceNames = mergedLists?.sourceNames.orEmpty()
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }

    // Art of the row card focused last (Phase 6). Read only inside HomeBackdrop, so a focus move
    // recomposes the backdrop, not the page; focus on a tile or the header keeps it.
    var focusedArtUrl by remember { mutableStateOf<String?>(null) }
    val artOnFocus: (String?) -> Modifier = { url ->
        Modifier.onFocusChanged { if (it.hasFocus && !url.isNullOrBlank()) focusedArtUrl = url }
    }
    var continueWatchingItems by remember { mutableStateOf<List<ContinueWatchingItem>>(emptyList()) }

    // False until the shelf's first load for this repository, so entry focus can wait for it.
    var continueWatchingLoaded by remember { mutableStateOf(false) }

    // The Live row (TV home overhaul plan, Phase 4): loaded like the shelf, its Now lines each minute.
    var liveRowEntries by remember { mutableStateOf<List<LiveRowEntry>>(emptyList()) }
    // Favourite channels have their own row, after Channels, in their own order.
    var favoriteChannelEntries by remember { mutableStateOf<List<LiveRowEntry>>(emptyList()) }
    var liveRowLoaded by remember { mutableStateOf(false) }
    var liveNowPlaying by remember { mutableStateOf<Map<String, EpgProgram>>(emptyMap()) }

    // Favourite movies and shows rows (Phase 5), loaded with the Live row.
    var favoriteMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteShows by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    // Every section's favourite categories, after them.
    var favoriteCategories by remember { mutableStateOf<List<SourcedFavoriteCategory>>(emptyList()) }

    // The Undo of a card's removal (Hold OK / Menu on a card, below).
    val undoBar = rememberUndoBarState()

    // Show EPG Browser button when EPG index has data. Collected live (not a one-shot
    // `remember`) so a source that finishes indexing while this screen is on-screen shows the
    // icon immediately, instead of waiting for the composable to be torn down and rebuilt.
    val epgIndexState by remember {
        org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
            .getInstance(context.applicationContext)
            .state
    }.collectAsStateWithLifecycle()
    val hasEpgData = epgIndexState is org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState.Indexed

    // Pull a recently-watched poster for the ambient backdrop wash — falls back to the plain
    // gradient (AmbientBackdrop's default) if there's no watch history yet or the provider
    // doesn't support it. From the profile's first Xtream source.
    LaunchedEffect(mergedLists) {
        val mp = mergedLists?.primary?.getProvider() ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            backdropImageUrl =
                listOf(ContentType.MOVIES, ContentType.TV_SHOWS, ContentType.LIVE_TV).firstNotNullOfOrNull { contentType ->
                    mp
                        .getRecentlyPlayed(contentType)
                        ?.getOrNull()
                        ?.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
                        ?.thumbnailUrl
                }
        }
    }

    // "Jump Back In" shelf — reload whenever the repository changes (provider switch) and again
    // on every ON_RESUME, so returning from playback immediately reflects updated progress.
    LaunchedEffect(mergedLists) {
        // A removal's Undo belongs to the source it was made on.
        undoBar.dismiss()
        val merged = mergedLists ?: return@LaunchedEffect
        continueWatchingItems = merged.continueWatching()
        continueWatchingLoaded = true
    }
    LaunchedEffect(mergedLists, supportedContentTypes) {
        val merged = mergedLists ?: return@LaunchedEffect
        liveRowEntries = if (ContentType.LIVE_TV in supportedContentTypes) loadLiveRow(merged) else emptyList()
        favoriteChannelEntries = if (ContentType.LIVE_TV in supportedContentTypes) loadFavoriteChannels(merged) else emptyList()
        favoriteMovies = loadFavorites(merged, ContentType.MOVIES, supportedContentTypes)
        favoriteShows = loadFavorites(merged, ContentType.TV_SHOWS, supportedContentTypes)
        favoriteCategories = loadFavoriteCategories(merged, supportedContentTypes)
        liveRowLoaded = true
    }
    LaunchedEffect(mergedLists, liveRowEntries, favoriteChannelEntries) {
        val merged = mergedLists ?: return@LaunchedEffect
        val items = (liveRowEntries + favoriteChannelEntries).map { it.item }.distinctBy { it.sourceKey }
        if (items.isEmpty()) return@LaunchedEffect
        while (true) {
            // Each channel from its own source's guide.
            val now = HashMap<String, EpgProgram>()
            for ((source, channels) in items.groupBy { it.providerId }) now += merged.repoFor(source).getNowPlayingFromIndex(channels)
            liveNowPlaying = now
            val nowMs = System.currentTimeMillis()
            delay(DateUtils.MINUTE_IN_MILLIS - nowMs % DateUtils.MINUTE_IN_MILLIS)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mergedLists) {
        val merged = mergedLists
        val observer =
            LifecycleEventObserver { _, event ->
                // A source synced meanwhile (Live TV shows once one has channels): the sections are
                // read again. A change to the profile's sources is followed above.
                if (event == Lifecycle.Event.ON_RESUME && sections != null) {
                    coroutineScope.launch { sections = ProfileSections.load(context.applicationContext) }
                }
                if (event == Lifecycle.Event.ON_RESUME && merged != null) {
                    coroutineScope.launch {
                        continueWatchingItems = merged.continueWatching()
                    }
                    if (ContentType.LIVE_TV in supportedContentTypes) {
                        coroutineScope.launch {
                            liveRowEntries = loadLiveRow(merged)
                            favoriteChannelEntries = loadFavoriteChannels(merged)
                        }
                    }
                    coroutineScope.launch {
                        favoriteMovies = loadFavorites(merged, ContentType.MOVIES, supportedContentTypes)
                        favoriteShows = loadFavorites(merged, ContentType.TV_SHOWS, supportedContentTypes)
                        favoriteCategories = loadFavoriteCategories(merged, supportedContentTypes)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back from whatever a card or header button opened lands on that card or button, not on the
    // first focusable (the "Switch Source" chip). A Continue Watching card waits for the shelf to
    // reload and is scrolled into view first; a card that left the shelf (finished) falls back to
    // the first hero card.
    val returnFocus = rememberNavReturnFocus()
    val shelfListState = rememberLazyListState()

    // The first card of each row: where focus enters a row it has not been in yet (HomeRow).
    val shelfFirstFocus = remember { FocusRequester() }
    val liveRowFirstFocus = remember { FocusRequester() }
    val liveRowListState = rememberLazyListState()
    val favoriteChannelsFirstFocus = remember { FocusRequester() }
    val favoriteChannelsListState = rememberLazyListState()
    val favoriteMoviesFirstFocus = remember { FocusRequester() }
    val favoriteMoviesListState = rememberLazyListState()
    val favoriteShowsFirstFocus = remember { FocusRequester() }
    val favoriteShowsListState = rememberLazyListState()
    // A favourite categories row per section, each under that section's favourites.
    val categoryShelves = HomeShelf.entries.filter { it.categoriesContentType != null }
    val categoriesFirstFocus = remember { categoryShelves.associateWith { FocusRequester() } }
    val categoriesListState = categoryShelves.associateWith { rememberLazyListState() }
    val categoriesOf: (HomeShelf) -> List<SourcedFavoriteCategory> = { shelf ->
        favoriteCategories.filter { it.category.contentType == shelf.categoriesContentType }
    }
    // The first card of the first row that has one: where focus opens and where Down from the header
    // goes. Null with no row at all; the sections are on the navigation rail then.
    val firstRowFocus =
        when {
            continueWatchingItems.isNotEmpty() -> shelfFirstFocus

            liveRowEntries.isNotEmpty() -> liveRowFirstFocus

            favoriteChannelEntries.isNotEmpty() -> favoriteChannelsFirstFocus

            categoriesOf(
                HomeShelf.FAVORITE_CHANNEL_CATEGORIES,
            ).isNotEmpty() -> categoriesFirstFocus.getValue(HomeShelf.FAVORITE_CHANNEL_CATEGORIES)

            favoriteMovies.isNotEmpty() -> favoriteMoviesFirstFocus

            categoriesOf(
                HomeShelf.FAVORITE_MOVIE_CATEGORIES,
            ).isNotEmpty() -> categoriesFirstFocus.getValue(HomeShelf.FAVORITE_MOVIE_CATEGORIES)

            favoriteShows.isNotEmpty() -> favoriteShowsFirstFocus

            categoriesOf(
                HomeShelf.FAVORITE_SHOW_CATEGORIES,
            ).isNotEmpty() -> categoriesFirstFocus.getValue(HomeShelf.FAVORITE_SHOW_CATEGORIES)

            else -> null
        }
    val rail = LocalTvNavRail.current
    // The first button of the panel shown in place of the rows without merged sections.
    val sourcesPanelFocus = remember { FocusRequester() }
    // Read by the entry focus below after it has waited for the rows: the value from its launch
    // was from before they loaded (null: no focus at all after a profile switch).
    val currentFirstRowFocus by rememberUpdatedState(firstRowFocus)
    LaunchedEffect(Unit) {
        // Back hands focus to the control that was left (NavReturnFocusEffect below).
        if (returnFocus.isReturn) return@LaunchedEffect
        // Wait for the shelf and the Live row, which take entry focus when they have something
        // (TV home overhaul plan, Phase 3).
        withTimeoutOrNull(ENTRY_FOCUS_WAIT_MS) {
            snapshotFlow { sections?.merged?.isEmpty() == true || (continueWatchingLoaded && liveRowLoaded) }.first { it }
        }
        // No merged sections: the panel's first button (Browse <source>, or Add Source).
        if (sections?.merged?.isEmpty() == true) {
            sourcesPanelFocus.requestFocusWithRetry(fallback = rail?.entry)
            return@LaunchedEffect
        }
        // No row at all (a new source, nothing watched yet): the sections are on the rail, so
        // focus starts there.
        // After a profile or source switch the rows can come later than that wait: keep waiting
        // for one, then the rail (hidden by the profile picker a moment ago, so retried until it
        // is back).
        val target =
            currentFirstRowFocus
                ?: withTimeoutOrNull(LATE_ROWS_WAIT_MS) { snapshotFlow { currentFirstRowFocus }.first { it != null } }
        if (target != null) target.requestFocusWithRetry() else rail?.entry?.requestFocusWithRetry()
    }
    NavReturnFocusEffect(returnFocus, fallback = firstRowFocus) { key ->
        if (key.startsWith(RETURN_CONTINUE_WATCHING_PREFIX)) {
            val itemId = key.removePrefix(RETURN_CONTINUE_WATCHING_PREFIX)
            val items =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { continueWatchingItems }.first { items -> items.any { it.sourceKey == itemId } }
                }
            val index = items?.indexOfFirst { it.sourceKey == itemId } ?: -1
            if (index >= 0) shelfListState.scrollToItem(index)
        }
        for ((prefix, row) in listOf(
            RETURN_LIVE_ROW_PREFIX to liveRowListState,
            RETURN_FAVORITE_CHANNELS_PREFIX to favoriteChannelsListState,
        )) {
            if (!key.startsWith(prefix)) continue
            val itemId = key.removePrefix(prefix)
            val entries =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { if (prefix == RETURN_LIVE_ROW_PREFIX) liveRowEntries else favoriteChannelEntries }
                        .first { entries -> entries.any { it.item.sourceKey == itemId } }
                }
            val index = entries?.indexOfFirst { it.item.sourceKey == itemId } ?: -1
            if (index >= 0) row.scrollToItem(index)
        }
        for ((prefix, row) in listOf(
            RETURN_FAVORITE_MOVIES_PREFIX to favoriteMoviesListState,
            RETURN_FAVORITE_SHOWS_PREFIX to favoriteShowsListState,
        )) {
            if (!key.startsWith(prefix)) continue
            val itemId = key.removePrefix(prefix)
            val items =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { if (prefix == RETURN_FAVORITE_MOVIES_PREFIX) favoriteMovies else favoriteShows }
                        .first { items -> items.any { it.sourceKey == itemId } }
                }
            val index = items?.indexOfFirst { it.sourceKey == itemId } ?: -1
            if (index >= 0) row.scrollToItem(index)
        }
    }
    val leaveTo: (String, () -> Unit) -> Unit = { key, navigate ->
        returnFocus.leaveFrom(key)
        navigate()
    }

    // Hold OK / Menu on a card (docs/plans/archive/20261009_tv-recents-favorites-plan.md → A, "Home's
    // cards"): the list rows' menu. A removal takes the card out of its row at once, focus going
    // to the card that took its place (cardAfterChange), with the Undo bar; Menu while it shows
    // puts the card back, focus on it.
    var cardMenu by remember { mutableStateOf<HomeCardMenu?>(null) }
    // A Favourite categories card's menu: Remove from favourite categories, with its Undo.
    var categoryMenu by remember { mutableStateOf<SourcedFavoriteCategory?>(null) }
    // One requester per card, attached for good: attaching one only while a request was pending,
    // then taking it off, changed the focused card's modifiers and focus fell back to the row's
    // first card (the removal's neighbour got focus, then lost it).
    val cardFocus = remember { mutableMapOf<HomeCard, FocusRequester>() }
    var cardFocusRequest by remember { mutableStateOf<CardFocusRequest?>(null) }
    val shelfKeys: (HomeShelf) -> List<String> = { shelf ->
        when (shelf) {
            HomeShelf.CONTINUE_WATCHING -> continueWatchingItems.map { it.sourceKey }

            HomeShelf.CHANNELS -> liveRowEntries.map { it.item.sourceKey }

            HomeShelf.FAVORITE_CHANNELS -> favoriteChannelEntries.map { it.item.sourceKey }

            HomeShelf.FAVORITE_MOVIES -> favoriteMovies.map { it.sourceKey }

            HomeShelf.FAVORITE_SHOWS -> favoriteShows.map { it.sourceKey }

            HomeShelf.FAVORITE_CHANNEL_CATEGORIES,
            HomeShelf.FAVORITE_MOVIE_CATEGORIES,
            HomeShelf.FAVORITE_SHOW_CATEGORIES,
            -> categoriesOf(shelf).map { it.key }
        }
    }
    val listStateOf: (HomeShelf) -> LazyListState = { shelf ->
        when (shelf) {
            HomeShelf.CONTINUE_WATCHING -> shelfListState

            HomeShelf.CHANNELS -> liveRowListState

            HomeShelf.FAVORITE_CHANNELS -> favoriteChannelsListState

            HomeShelf.FAVORITE_MOVIES -> favoriteMoviesListState

            HomeShelf.FAVORITE_SHOWS -> favoriteShowsListState

            HomeShelf.FAVORITE_CHANNEL_CATEGORIES,
            HomeShelf.FAVORITE_MOVIE_CATEGORIES,
            HomeShelf.FAVORITE_SHOW_CATEGORIES,
            -> categoriesListState.getValue(shelf)
        }
    }
    val focusAfterChange: (HomeShelf, List<String>, String) -> Unit = { shelf, oldKeys, key ->
        cardFocusRequest = CardFocusRequest(cardAfterChange(shelf, oldKeys, key, HomeShelf.entries.associateWith(shelfKeys)))
    }
    // The card asked for, scrolled into its row when off screen; no card left: the rail. Never
    // nothing focused.
    LaunchedEffect(cardFocusRequest) {
        val request = cardFocusRequest ?: return@LaunchedEffect
        // After the card menu has closed: its window hands focus back to Home as it goes, and a
        // request made while it was still up landed, then fell back to the row's first card.
        snapshotFlow { cardMenu == null && categoryMenu == null }.first { it }
        repeat(2) { withFrameNanos { } }
        val card = request.card
        if (card == null) {
            rail?.entry?.requestFocusWithRetry()
        } else {
            val listState = listStateOf(card.shelf)
            val index = shelfKeys(card.shelf).indexOf(card.key)
            if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
            val requester = cardFocus.getOrPut(card) { FocusRequester() }
            requester.requestFocusWithRetry(fallback = rail?.entry)
            // Coming from outside the row (the menu had focus), the request enters the row's
            // focusRestorer, which hands it to the card it remembers — the one just removed — or
            // the first card. Asked again from inside the row, it lands on the card.
            withFrameNanos { }
            requester.requestFocus()
        }
        cardFocusRequest = null
    }
    val cardFocusTarget: (HomeShelf, String) -> Modifier = { shelf, key ->
        Modifier.focusRequester(cardFocus.getOrPut(HomeCard(shelf, key)) { FocusRequester() })
    }

    // Takes the card out of its row (returns how to put it back where it was).
    fun <T> takeOut(
        before: List<T>,
        key: String,
        keyOf: (T) -> String,
        current: () -> List<T>,
        set: (List<T>) -> Unit,
    ): () -> Unit {
        set(before.filterNot { keyOf(it) == key })
        return { set(current().withCardPutBack(before, key, keyOf)) }
    }

    fun takeOut(
        shelf: HomeShelf,
        key: String,
    ): () -> Unit =
        when (shelf) {
            HomeShelf.CONTINUE_WATCHING -> {
                takeOut(continueWatchingItems, key, { it.sourceKey }, { continueWatchingItems }) {
                    continueWatchingItems =
                        it
                }
            }

            HomeShelf.CHANNELS -> {
                takeOut(liveRowEntries, key, { it.item.sourceKey }, { liveRowEntries }) { liveRowEntries = it }
            }

            HomeShelf.FAVORITE_CHANNELS -> {
                takeOut(favoriteChannelEntries, key, { it.item.sourceKey }, { favoriteChannelEntries }) {
                    favoriteChannelEntries =
                        it
                }
            }

            HomeShelf.FAVORITE_MOVIES -> {
                takeOut(favoriteMovies, key, { it.sourceKey }, { favoriteMovies }) { favoriteMovies = it }
            }

            HomeShelf.FAVORITE_SHOWS -> {
                takeOut(favoriteShows, key, { it.sourceKey }, { favoriteShows }) { favoriteShows = it }
            }

            HomeShelf.FAVORITE_CHANNEL_CATEGORIES,
            HomeShelf.FAVORITE_MOVIE_CATEGORIES,
            HomeShelf.FAVORITE_SHOW_CATEGORIES,
            -> {
                takeOut(favoriteCategories, key, { it.key }, { favoriteCategories }) { favoriteCategories = it }
            }
        }

    // A card leaving its row: out at once, focus on its neighbour, then [remove] (which returns
    // its own undo); the Undo bar puts the card back, focus on it, and runs that undo.
    fun removeCard(
        shelf: HomeShelf,
        key: String,
        name: String,
        remove: suspend () -> (suspend () -> Unit),
    ) {
        val oldKeys = shelfKeys(shelf)
        val putBack = takeOut(shelf, key)
        focusAfterChange(shelf, oldKeys, key)
        var undo: (suspend () -> Unit)? = null
        val removing = coroutineScope.launchGuarded("Home.removeCard") { undo = remove() }
        undoBar.show(parseDisplayTitle(name).title.ifBlank { name }) {
            val keysNow = shelfKeys(shelf)
            putBack()
            focusAfterChange(shelf, keysNow, key)
            coroutineScope.launchGuarded("Home.undoRemoveCard") {
                removing.join()
                undo?.invoke()
            }
        }
    }

    val reloadFavorites: suspend (MergedLists, String) -> Unit = { merged, contentType ->
        when (contentType) {
            ContentType.LIVE_TV -> favoriteChannelEntries = loadFavoriteChannels(merged)
            ContentType.MOVIES -> favoriteMovies = loadFavorites(merged, contentType, supportedContentTypes)
            ContentType.TV_SHOWS -> favoriteShows = loadFavorites(merged, contentType, supportedContentTypes)
        }
    }
    // The shelf again after a watched mark; a card it no longer has hands focus on (an episode's
    // card turns into its show's "Up next" and stays).
    val reloadContinueWatching: suspend (MergedLists, String?) -> Unit = { merged, focusedKey ->
        val oldKeys = shelfKeys(HomeShelf.CONTINUE_WATCHING)
        continueWatchingItems = merged.continueWatching()
        if (focusedKey != null && focusedKey in oldKeys && focusedKey !in shelfKeys(HomeShelf.CONTINUE_WATCHING)) {
            focusAfterChange(HomeShelf.CONTINUE_WATCHING, oldKeys, focusedKey)
        }
    }

    // Opens the menu once the card's favourite and watched states are known.
    val openCardMenu: (HomeShelf, HomeCardItem) -> Unit = { shelf, ref ->
        // The card's own source.
        mergedLists?.repoFor(ref.providerId)?.let { repo ->
            coroutineScope.launchGuarded("Home.openCardMenu") {
                val isFavorite = shelf.isFavorites || repo.isFavoriteSuspend(ref.id, ref.contentType)
                // Null hides "Mark watched", as on a source that keeps watched state on its server.
                val isWatched =
                    when {
                        ref.watchedItemId == null || !repo.supportsMarkWatched -> {
                            null
                        }

                        shelf == HomeShelf.CONTINUE_WATCHING -> {
                            false
                        }

                        else -> {
                            repo.getPlaybackPositions(listOf(ref.watchedItemId), ref.contentType)[ref.watchedItemId]?.isCompleted ==
                                true
                        }
                    }
                // A favourite show's "Mark show watched / unwatched"
                // (docs/plans/archive/20261011_season-show-watched-plan.md Phase 3): null hides it, as on a
                // source that keeps watched state on its server.
                val isShowWatched =
                    if (shelf == HomeShelf.FAVORITE_SHOWS && repo.supportsMarkWatched) {
                        repo.episodesWatchState(ref.id, null) == true
                    } else {
                        null
                    }
                cardMenu =
                    HomeCardMenu(
                        shelf = shelf,
                        ref = ref,
                        target =
                            FavoriteMenuTarget.Stream(
                                itemId = ref.id,
                                itemName = ref.name,
                                categoryId = ref.categoryId,
                                contentType = ref.contentType,
                                isFavorite = isFavorite,
                                isWatched = isWatched,
                                // A source that keeps its own history (Jellyfin) has no Remove from Recent.
                                isInRecent = !shelf.isFavorites && repo.supportsRemoveFromRecent,
                                isShowWatched = isShowWatched,
                            ),
                    )
            }
        }
    }
    categoryMenu?.let { favorite ->
        val repo = mergedLists?.repoFor(favorite.providerId)
        val category = favorite.category
        FavoriteContextMenuDialog(
            target =
                FavoriteMenuTarget.Category(
                    categoryId = category.categoryId,
                    categoryName = category.categoryName,
                    contentType = category.contentType,
                    isFavorite = true,
                ),
            onConfirm = {
                if (repo != null) {
                    removeCard(
                        categoryShelves.first { it.categoriesContentType == category.contentType },
                        favorite.key,
                        category.categoryName,
                    ) {
                        withContext(Dispatchers.IO) { repo.removeFavoriteCategory(category.categoryId, category.contentType) }
                        suspend {
                            withContext(Dispatchers.IO) {
                                repo.addFavoriteCategory(category.categoryId, category.categoryName, category.contentType)
                            }
                        }
                    }
                }
            },
            onDismiss = { categoryMenu = null },
        )
    }
    cardMenu?.let { menu ->
        val merged = mergedLists
        val ref = menu.ref
        // The card's own source acts on it (multi-source plan, Phase 4).
        val repo = merged?.repoFor(ref.providerId)
        val markShowFailed = stringResource(R.string.watched_mark_many_failed)
        FavoriteContextMenuDialog(
            target = menu.target,
            onConfirm = {
                when {
                    repo == null || merged == null -> {}

                    menu.shelf.isFavorites -> {
                        removeCard(menu.shelf, ref.key, ref.name) {
                            repo.removeFavoriteSuspend(ref.id, ref.contentType)
                            suspend { repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType) }
                        }
                    }

                    menu.target.isFavorite -> {
                        // The card stays in its row; its favourite leaves the favourites row below.
                        coroutineScope.launchGuarded("Home.removeFavorite") {
                            repo.removeFavoriteSuspend(ref.id, ref.contentType)
                            reloadFavorites(merged, ref.contentType)
                        }
                        undoBar.show(parseDisplayTitle(ref.name).title.ifBlank { ref.name }) {
                            coroutineScope.launchGuarded("Home.undoRemoveFavorite") {
                                repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType)
                                reloadFavorites(merged, ref.contentType)
                            }
                        }
                    }

                    else -> {
                        coroutineScope.launchGuarded("Home.addFavorite") {
                            repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType)
                            reloadFavorites(merged, ref.contentType)
                        }
                    }
                }
            },
            onDismiss = { cardMenu = null },
            onToggleWatched =
                ref.watchedItemId?.let { watchedItemId ->
                    {
                        when {
                            repo == null || merged == null -> {}

                            // A film watched leaves Continue Watching: a removal, with its Undo.
                            menu.shelf == HomeShelf.CONTINUE_WATCHING && ref.contentType == ContentType.MOVIES -> {
                                removeCard(menu.shelf, ref.key, ref.name) {
                                    repo.setWatched(watchedItemId, ref.contentType, true)
                                    suspend { repo.setWatched(watchedItemId, ref.contentType, false) }
                                }
                            }

                            else -> {
                                coroutineScope.launchGuarded("Home.toggleWatched") {
                                    repo.setWatched(watchedItemId, ref.contentType, menu.target.isWatched != true)
                                    reloadContinueWatching(merged, ref.key.takeIf { menu.shelf == HomeShelf.CONTINUE_WATCHING })
                                }
                            }
                        }
                    }
                },
            onRemoveFromRecent =
                if (menu.target.isInRecent && repo != null) {
                    {
                        removeCard(menu.shelf, ref.key, ref.name) {
                            val removal = repo.removeFromRecent(ref.id, ref.contentType)
                            suspend { removal?.let { repo.restoreRecent(it) } }
                        }
                    }
                } else {
                    null
                },
            list = if (menu.shelf.isFavorites) RowMenuList.FAVORITES else RowMenuList.RECENT,
            onToggleShowWatched =
                menu.target.isShowWatched?.let { isShowWatched ->
                    {
                        if (repo != null && merged != null) {
                            coroutineScope.launchGuarded("Home.setShowWatched") {
                                repo
                                    .setEpisodesWatched(ref.id, null, !isShowWatched)
                                    .onSuccess { reloadContinueWatching(merged, null) }
                                    .onFailure { e ->
                                        val message = markShowFailed + "\n" + friendlyErrorMessage(e, context, appSettings.isDevMode)
                                        android.widget.Toast
                                            .makeText(context, message, android.widget.Toast.LENGTH_LONG)
                                            .show()
                                    }
                            }
                        }
                    }
                },
        )
    }

    val scale = LocalUiScale.current
    // A section's favourite categories, under its favourites; nothing when it has none.
    val favoriteCategoriesRow: @Composable (HomeShelf, String) -> Unit = { shelf, title ->
        val categories = categoriesOf(shelf)
        if (categories.isNotEmpty()) {
            TvFavoriteCategoriesRow(
                title = title,
                categories = categories,
                onCategorySelected = { category ->
                    leaveTo(RETURN_FAVORITE_CATEGORIES_PREFIX + category.key) {
                        onFavoriteCategorySelected(
                            category.category.categoryId,
                            category.category.contentType,
                            category.providerId,
                        )
                    }
                },
                onOpenActions = { category -> categoryMenu = category },
                firstItemFocus = categoriesFirstFocus.getValue(shelf),
                listState = categoriesListState.getValue(shelf),
                itemModifier = { category ->
                    Modifier
                        .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_CATEGORIES_PREFIX + category.key)
                        .then(cardFocusTarget(shelf, category.key))
                },
                sourceNames = sourceNames,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xl.scaled(scale)),
            )
        }
    }

    // Menu undoes the last removal while its bar shows.
    Box(modifier = Modifier.fillMaxSize().undoOnRemoteKeys(undoBar)) {
        HomeBackdrop(fallbackUrl = backdropImageUrl, focusedUrl = { focusedArtUrl })
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.lg.scaled(scale)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.staggeredEntrance(0),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        CinemaText(text = stringResource(R.string.login_app_name), role = TextRole.Title)
                        HomeClock()
                    }
                    Row(
                        // Applies to every button in the row (none is a focus group). The leftover
                        // width, end-aligned: at a large Text & grid size the source pill gives way
                        // (ellipsised) and the icon buttons always stay on screen.
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(start = Spacing.md)
                                .then(
                                    if (firstRowFocus == null) {
                                        Modifier
                                    } else {
                                        Modifier.focusProperties { down = firstRowFocus }
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    ) {
                        // One line for all the profile's sources (O3): focusable, opening the sources
                        // sheet, only when there is something to say. The header's first focusable
                        // item hands Left to the navigation rail: the line when it opens, else
                        // Search the guide.
                        val summaryOpens = syncSummary.opensSheet
                        if (summaryOpens) {
                            var summaryFocused by remember { mutableStateOf(false) }
                            val summaryScale by animateFloatAsState(
                                targetValue = if (summaryFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
                                animationSpec =
                                    tween(
                                        durationMillis = org.njarasoa.fijerena.core.ui.theme.CinemaAnimation.focusDurationMs,
                                    ),
                                label = "sources_summary_scale",
                            )
                            val summaryDescription = stringResource(R.string.home_sources_status_title)
                            GlassPanel(
                                modifier =
                                    Modifier
                                        .weight(1f, fill = false)
                                        .scale(summaryScale)
                                        .border(
                                            width = TvFocusTokens.focusBorderWidth,
                                            color =
                                                if (summaryFocused) {
                                                    CinemaTheme.colors.accentSoft
                                                } else {
                                                    CinemaTheme.colors.clear
                                                },
                                            shape = CinemaTheme.shapes.dialog,
                                        ).leftToRail()
                                        .onFocusChanged { summaryFocused = it.isFocused }
                                        .clickable(role = Role.Button) { showSourcesSheet = true }
                                        .semantics { contentDescription = summaryDescription },
                            ) {
                                SyncSummaryLine(
                                    summary = syncSummary,
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                )
                            }
                        }
                        if (hasEpgData) {
                            CinemaIconButton(
                                onClick = { leaveTo(RETURN_EPG_BROWSER, onEpgBrowser) },
                                modifier =
                                    Modifier
                                        .then(if (summaryOpens) Modifier else Modifier.leftToRail())
                                        .navReturnFocusTarget(returnFocus, RETURN_EPG_BROWSER),
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.MenuBook,
                                        contentDescription = stringResource(R.string.epg_browser_title),
                                        tint = CinemaTheme.colors.textPrimary,
                                    )
                                },
                            )
                        }
                    }
                }

                val loadedSections = sections
                if (loadedSections != null && loadedSections.merged.isEmpty()) {
                    // No Xtream source: a Browse entry per source (O1); no source at all: Add Source.
                    HomeSourcesPanel(
                        message =
                            stringResource(
                                if (loadedSections.hasNoSources) R.string.home_no_sources else R.string.home_only_browse_sources,
                            ),
                        actions =
                            if (loadedSections.hasNoSources) {
                                listOf(stringResource(R.string.provider_add_title) to onAddSource)
                            } else {
                                loadedSections.browse.map { source ->
                                    stringResource(R.string.browse_source, source.name) to
                                        { onBrowse(source) }
                                }
                            },
                        firstActionFocus = sourcesPanelFocus,
                        scale = scale,
                        modifier = Modifier.weight(1f),
                    )
                } else if (firstRowFocus == null && loadedSections?.xtreamIds.orEmpty().any { it in firstSyncs }) {
                    // The source's first catalogue sync: nothing to show yet, and blank would read as
                    // an empty library. Not focusable; the sections stay on the navigation rail.
                    TvEmptyState(
                        message = stringResource(R.string.home_library_still_loading),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // The rows (TV home overhaul plan, Phase 2; the section tiles went 2026-10-09, the
                    // navigation rail opens the sections). Scrollable: on a lower-density TV the rows
                    // can run past the bottom edge. Scrolled only as far as the focused card needs:
                    // Android TV's default pulls it a third of the way down.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (continueWatchingItems.isNotEmpty()) {
                                TvContinueWatchingShelf(
                                    items = continueWatchingItems,
                                    firstItemFocus = shelfFirstFocus,
                                    onItemSelected = { item ->
                                        leaveTo(RETURN_CONTINUE_WATCHING_PREFIX + item.sourceKey) { onContinueWatchingSelected(item) }
                                    },
                                    onOpenActions = { item -> openCardMenu(HomeShelf.CONTINUE_WATCHING, item.toCardItem()) },
                                    listState = shelfListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_CONTINUE_WATCHING_PREFIX + item.sourceKey)
                                            .then(cardFocusTarget(HomeShelf.CONTINUE_WATCHING, item.sourceKey))
                                            .then(artOnFocus(item.thumbnailUrl))
                                    },
                                    sourceNames = sourceNames,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }

                            if (liveRowEntries.isNotEmpty()) {
                                TvLiveRow(
                                    title = stringResource(R.string.home_live_row_title),
                                    entries = liveRowEntries,
                                    nowPlaying = liveNowPlaying,
                                    onEntrySelected = { entry ->
                                        leaveTo(RETURN_LIVE_ROW_PREFIX + entry.item.sourceKey) {
                                            onLiveChannelSelected(entry.item.id, zapListId(entry), entry.item.providerId)
                                        }
                                    },
                                    firstItemFocus = liveRowFirstFocus,
                                    onOpenActions = { entry ->
                                        openCardMenu(HomeShelf.CHANNELS, entry.item.toCardItem(ContentType.LIVE_TV))
                                    },
                                    listState = liveRowListState,
                                    itemModifier = { entry ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_LIVE_ROW_PREFIX + entry.item.sourceKey)
                                            .then(cardFocusTarget(HomeShelf.CHANNELS, entry.item.sourceKey))
                                            .then(artOnFocus(entry.item.thumbnailUrl))
                                    },
                                    sourceNames = sourceNames,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }

                            if (favoriteChannelEntries.isNotEmpty()) {
                                TvLiveRow(
                                    title = stringResource(R.string.home_favorite_channels),
                                    entries = favoriteChannelEntries,
                                    nowPlaying = liveNowPlaying,
                                    onEntrySelected = { entry ->
                                        leaveTo(RETURN_FAVORITE_CHANNELS_PREFIX + entry.item.sourceKey) {
                                            onLiveChannelSelected(entry.item.id, zapListId(entry), entry.item.providerId)
                                        }
                                    },
                                    firstItemFocus = favoriteChannelsFirstFocus,
                                    onOpenActions = { entry ->
                                        openCardMenu(HomeShelf.FAVORITE_CHANNELS, entry.item.toCardItem(ContentType.LIVE_TV))
                                    },
                                    listState = favoriteChannelsListState,
                                    itemModifier = { entry ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_CHANNELS_PREFIX + entry.item.sourceKey)
                                            .then(cardFocusTarget(HomeShelf.FAVORITE_CHANNELS, entry.item.sourceKey))
                                            .then(artOnFocus(entry.item.thumbnailUrl))
                                    },
                                    sourceNames = sourceNames,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }

                            favoriteCategoriesRow(
                                HomeShelf.FAVORITE_CHANNEL_CATEGORIES,
                                stringResource(R.string.home_favorite_channel_categories),
                            )

                            if (favoriteMovies.isNotEmpty()) {
                                TvFavoritesRow(
                                    title = stringResource(R.string.home_favorite_movies),
                                    items = favoriteMovies,
                                    thumbnailType = ThumbnailContentType.MOVIE,
                                    onItemSelected = { item ->
                                        leaveTo(
                                            RETURN_FAVORITE_MOVIES_PREFIX + item.sourceKey,
                                        ) { onFavoriteSelected(item, ContentType.MOVIES) }
                                    },
                                    firstItemFocus = favoriteMoviesFirstFocus,
                                    onOpenActions = { item ->
                                        openCardMenu(
                                            HomeShelf.FAVORITE_MOVIES,
                                            item.toCardItem(ContentType.MOVIES, watchedItemId = item.id),
                                        )
                                    },
                                    listState = favoriteMoviesListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_MOVIES_PREFIX + item.sourceKey)
                                            .then(cardFocusTarget(HomeShelf.FAVORITE_MOVIES, item.sourceKey))
                                            .then(artOnFocus(item.thumbnailUrl))
                                    },
                                    sourceNames = sourceNames,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }

                            favoriteCategoriesRow(
                                HomeShelf.FAVORITE_MOVIE_CATEGORIES,
                                stringResource(R.string.home_favorite_movie_categories),
                            )

                            if (favoriteShows.isNotEmpty()) {
                                TvFavoritesRow(
                                    title = stringResource(R.string.home_favorite_shows),
                                    items = favoriteShows,
                                    thumbnailType = ThumbnailContentType.TV_SHOW,
                                    onItemSelected = { item ->
                                        leaveTo(
                                            RETURN_FAVORITE_SHOWS_PREFIX + item.sourceKey,
                                        ) { onFavoriteSelected(item, ContentType.TV_SHOWS) }
                                    },
                                    firstItemFocus = favoriteShowsFirstFocus,
                                    onOpenActions = { item ->
                                        openCardMenu(HomeShelf.FAVORITE_SHOWS, item.toCardItem(ContentType.TV_SHOWS))
                                    },
                                    listState = favoriteShowsListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_SHOWS_PREFIX + item.sourceKey)
                                            .then(cardFocusTarget(HomeShelf.FAVORITE_SHOWS, item.sourceKey))
                                            .then(artOnFocus(item.thumbnailUrl))
                                    },
                                    sourceNames = sourceNames,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }

                            favoriteCategoriesRow(
                                HomeShelf.FAVORITE_SHOW_CATEGORIES,
                                stringResource(R.string.home_favorite_show_categories),
                            )
                        }
                    }
                }
                // Under the rows, taking its own room, not over the bottom row's cards.
                TvUndoBar(undoBar, Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.sm))
            }

            if (showSourcesSheet) {
                SourcesStatusSheet(states = syncStates, onDismiss = { showSourcesSheet = false })
            }
        }
    }
}

// Keys for the controls that navigate away from Home — see rememberNavReturnFocus.
private const val RETURN_EPG_BROWSER = "epgBrowser"
private const val RETURN_CONTINUE_WATCHING_PREFIX = "cw:"
private const val RETURN_LIVE_ROW_PREFIX = "live:"
private const val RETURN_FAVORITE_CHANNELS_PREFIX = "favLive:"
private const val RETURN_FAVORITE_MOVIES_PREFIX = "favMovie:"
private const val RETURN_FAVORITE_SHOWS_PREFIX = "favShow:"
private const val RETURN_FAVORITE_CATEGORIES_PREFIX = "favCat:"

/** What a card's menu acts on. */
private class HomeCardItem(
    val id: String,
    val name: String,
    val categoryId: String,
    val contentType: String,
    /** What Mark as watched marks: the film, or the episode a show's card resumes; null: no such action. */
    val watchedItemId: String? = null,
    /** The card's source, which its actions go to. */
    val providerId: Long,
    /** The card's key in its row ([sourceKey]). */
    val key: String,
)

/** A Live, favourite movie or favourite show card's menu target. */
private fun MediaItem.toCardItem(
    contentType: String,
    watchedItemId: String? = null,
): HomeCardItem = HomeCardItem(id, name, categoryId, contentType, watchedItemId, providerId, sourceKey)

/** A Continue Watching card: Mark as watched on a film and on an episode to resume, not on an "Up next" one. */
private fun ContinueWatchingItem.toCardItem(): HomeCardItem =
    HomeCardItem(
        id = id,
        name = name,
        categoryId = categoryId,
        contentType = contentType,
        providerId = providerId,
        key = sourceKey,
        watchedItemId =
            when (val browseTarget = target) {
                is BrowseTarget.Series -> browseTarget.resumeEpisodeId?.raw
                is BrowseTarget.Episode -> browseTarget.episodeId.raw
                else -> id
            }.takeUnless { upNext },
    )

/** The card whose actions menu is open, and what the menu shows. */
private class HomeCardMenu(
    val shelf: HomeShelf,
    val ref: HomeCardItem,
    val target: FavoriteMenuTarget.Stream,
)

/** Focus to move to [card] (null: the navigation rail). A class, not a data class: each request is new. */
private class CardFocusRequest(
    val card: HomeCard?,
)

/** The active profile's favourites of [contentType], none when the source doesn't have that type. */
private suspend fun loadFavorites(
    merged: MergedLists,
    contentType: String,
    supportedContentTypes: Set<String>,
): List<MediaItem> = if (contentType in supportedContentTypes) merged.favorites(contentType) else emptyList()

/** The favourite categories rows: every section the profile's sources have, Live TV, Movies, TV Shows in that order. */
private suspend fun loadFavoriteCategories(
    merged: MergedLists,
    supportedContentTypes: Set<String>,
): List<SourcedFavoriteCategory> =
    withContext(Dispatchers.IO) {
        merged.favoriteCategories(
            listOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS).filter { it in supportedContentTypes },
        )
    }

/**
 * The Channels row for the active profile: last watched — the newest of its sources' last channels
 * (multi-source plan, Phase 5) — then Recent.
 */
private suspend fun loadLiveRow(merged: MergedLists): List<LiveRowEntry> =
    merged.lastWatchedChannel().let { last ->
        mergeLiveRow(lastItemId = last?.id, recent = merged.recent(ContentType.LIVE_TV), lastSource = last?.providerId)
    }

/** The list a Live card zaps through once opened: Favorites from Favorite channels, Recent from Channels. */
private fun zapListId(entry: LiveRowEntry): String =
    if (entry.fromFavorites) CategoryViewModel.FAVORITES_CATEGORY_ID else CategoryViewModel.RECENT_CATEGORY_ID

/** The Favorite channels row for the active profile. */
private suspend fun loadFavoriteChannels(merged: MergedLists): List<LiveRowEntry> =
    favoriteChannelsRow(merged.favorites(ContentType.LIVE_TV))

/** How long Back waits for the Continue Watching shelf to reload before giving up on its card. */
private const val RETURN_SHELF_WAIT_MS = 2_000L

/** How long a fresh open waits for the rows before focusing anyway. */
private const val ENTRY_FOCUS_WAIT_MS = 2_000L

/** How much longer focus waits for a row that comes late (a source switch) before going to the rail. */
private const val LATE_ROWS_WAIT_MS = 6_000L

/**
 * Home's backdrop: the focused card's art once focus has rested on it for [BACKDROP_SETTLE_MS]
 * (so scrolling along a row doesn't flash every card's art), else [fallbackUrl]. [focusedUrl] is
 * read here, not by the caller, so only this recomposes when focus moves.
 */
@Composable
private fun HomeBackdrop(
    fallbackUrl: String?,
    focusedUrl: () -> String?,
) {
    val target = focusedUrl()
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(target) {
        delay(BACKDROP_SETTLE_MS)
        shown = target
    }
    AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = shown ?: fallbackUrl)
}

/** How long focus rests on a card before the backdrop switches to its art. */
private const val BACKDROP_SETTLE_MS = 250L

/**
 * In place of the rows when the profile has no merged sections: [message] over one button per
 * action — a "Browse <source>" per source (O1), or Add Source with none. Buttons stack, so Up/Down
 * walk them; each hands Left to the navigation rail.
 */
@Composable
private fun HomeSourcesPanel(
    message: String,
    actions: List<Pair<String, () -> Unit>>,
    firstActionFocus: FocusRequester,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CinemaText(text = message, role = TextRole.Title)
        actions.forEachIndexed { index, (label, onClick) ->
            Spacer(modifier = Modifier.height(if (index == 0) Spacing.lg.scaled(scale) else Spacing.sm.scaled(scale)))
            CinemaPrimaryButton(
                onClick = onClick,
                text = label,
                modifier = (if (index == 0) Modifier.focusRequester(firstActionFocus) else Modifier).leftToRail(),
            )
        }
    }
}
