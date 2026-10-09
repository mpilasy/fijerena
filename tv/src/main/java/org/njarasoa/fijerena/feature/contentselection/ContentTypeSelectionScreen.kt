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
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.home.LiveRowEntry
import org.njarasoa.fijerena.core.ui.home.favoriteChannelsRow
import org.njarasoa.fijerena.core.ui.home.mergeLiveRow
import org.njarasoa.fijerena.core.ui.home.sourceSyncStatus
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
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.feature.category.components.FavoriteContextMenuDialog
import org.njarasoa.fijerena.feature.category.components.MinimalBringIntoView
import org.njarasoa.fijerena.feature.category.components.RowMenuList
import org.njarasoa.fijerena.feature.contentselection.components.HomeClock
import org.njarasoa.fijerena.feature.contentselection.components.SourceSyncStatusLine
import org.njarasoa.fijerena.feature.contentselection.components.TvContinueWatchingShelf
import org.njarasoa.fijerena.feature.contentselection.components.TvFavoritesRow
import org.njarasoa.fijerena.feature.contentselection.components.TvLiveRow
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
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
import org.njarasoa.fijerena.ui.components.undoOnMenuKey
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
    onProviderChanged: () -> Unit = {},
    onCapabilitiesResolved: (Set<String>) -> Unit = {},
    onContinueWatchingSelected: (ContinueWatchingItem) -> Unit = {},
    // A channel from the Live row, and the list it zaps through (CategoryViewModel's virtual
    // "favorites" or "recent" category id).
    onLiveChannelSelected: (streamId: String, contextCategoryId: String) -> Unit = { _, _ -> },
    // A favourite movie (ContentType.MOVIES) or show (ContentType.TV_SHOWS) from its Home row.
    onFavoriteSelected: (item: MediaItem, contentType: String) -> Unit = { _, _ -> },
    onSignInRequired: (providerId: Long) -> Unit = {},
) {
    val context = LocalContext.current
    val signInResources = LocalResources.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var providerName by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("") }
    var supportedContentTypes by remember {
        mutableStateOf<Set<String>>(
            setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS),
        )
    }
    var showProviderPicker by remember { mutableStateOf(false) }
    var allProviders by remember { mutableStateOf<List<org.njarasoa.fijerena.core.network.provider.ProviderEntity>>(emptyList()) }
    var activeProviderId by remember { mutableStateOf(0L) }
    // The active provider is a Jellyfin server this profile hasn't signed in to: home shows a
    // sign-in panel in place of the library (docs/plans/archive/20260929_live-sync-plan.md → User profiles).
    var needsSignIn by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // The source pill's status (TV home overhaul plan, Phase 1): the active source's last catalogue
    // sync, re-read when a sync of it ends and on every ON_RESUME.
    var lastSyncedAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncError by remember { mutableStateOf<String?>(null) }
    var syncStatsReload by remember { mutableIntStateOf(0) }
    val runningSyncs by ProviderSyncRunner.running.collectAsStateWithLifecycle(initialValue = emptySet())
    val syncing = activeProviderId in runningSyncs

    // Category counts per content type: Pair(filtered, total) — null while loading

    var mediaProviderRef by remember { mutableStateOf<MediaProvider?>(null) }
    var mediaRepositoryRef by remember { mutableStateOf<MediaRepository?>(null) }
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

    LaunchedEffect(refreshTrigger) {
        val resolvedTypes =
            withContext(Dispatchers.IO) {
                val providerRepo = ProviderRepository(context.applicationContext)
                allProviders = providerRepo.getAllProvidersList()
                val activeProvider = providerRepo.getActiveProvider()
                if (activeProvider != null) {
                    providerName = activeProvider.name
                    providerType = activeProvider.type
                    activeProviderId = activeProvider.id
                    lastSyncedAtMs = activeProvider.lastSyncedAtMs
                    lastSyncError = activeProvider.lastSyncError
                    // A Jellyfin server this profile hasn't signed in to: each profile is its own
                    // Jellyfin user (docs/plans/archive/20260929_live-sync-plan.md → User profiles). No
                    // repository is built — it could only fail to authenticate — and the first time
                    // per process the sign-in screen opens by itself; after that the panel stays.
                    needsSignIn = !providerRepo.hasLogin(activeProvider)
                    if (needsSignIn) {
                        mediaRepositoryRef = null
                        mediaProviderRef = null
                        if (AppContainer.getInstance(context.applicationContext).shouldPromptSignIn(activeProvider.id)) {
                            val message = signInResources.getString(R.string.profile_jellyfin_sign_in_prompt, activeProvider.name)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast
                                    .makeText(context, message, android.widget.Toast.LENGTH_LONG)
                                    .show()
                                onSignInRequired(activeProvider.id)
                            }
                        }
                        null
                    } else {
                        // Reuse the app-wide managed repository/provider instead of creating an
                        // unmanaged standalone one: same cached auth session, and connect() has
                        // already been run for it.
                        val repo = AppContainer.getInstance(context.applicationContext).getMediaRepository(activeProvider.id)
                        mediaRepositoryRef = repo
                        val mediaProvider = repo.getProvider()
                        if (mediaProvider != null) {
                            supportedContentTypes = mediaProvider.capabilities.supportedContentTypes
                            mediaProviderRef = mediaProvider
                        }
                        mediaProvider?.capabilities?.supportedContentTypes
                    }
                } else {
                    needsSignIn = false
                    providerName = appSettings.providerName
                    null
                }
            }
        resolvedTypes?.let(onCapabilitiesResolved)
    }

    LaunchedEffect(activeProviderId, syncing, syncStatsReload) {
        if (activeProviderId == 0L || syncing) return@LaunchedEffect
        val provider = withContext(Dispatchers.IO) { ProviderRepository(context.applicationContext).getProviderById(activeProviderId) }
        if (provider != null) {
            lastSyncedAtMs = provider.lastSyncedAtMs
            lastSyncError = provider.lastSyncError
        }
    }

    // Pull a recently-watched poster for the ambient backdrop wash — falls back to the plain
    // gradient (AmbientBackdrop's default) if there's no watch history yet or the provider
    // doesn't support it.
    LaunchedEffect(mediaProviderRef) {
        val mp = mediaProviderRef ?: return@LaunchedEffect
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
    LaunchedEffect(mediaRepositoryRef) {
        // A removal's Undo belongs to the source it was made on.
        undoBar.dismiss()
        val repo = mediaRepositoryRef ?: return@LaunchedEffect
        continueWatchingItems = repo.getContinueWatchingItems()
        continueWatchingLoaded = true
    }
    LaunchedEffect(mediaRepositoryRef, supportedContentTypes) {
        val repo = mediaRepositoryRef ?: return@LaunchedEffect
        liveRowEntries = if (ContentType.LIVE_TV in supportedContentTypes) loadLiveRow(repo) else emptyList()
        favoriteChannelEntries = if (ContentType.LIVE_TV in supportedContentTypes) loadFavoriteChannels(repo) else emptyList()
        favoriteMovies = loadFavorites(repo, ContentType.MOVIES, supportedContentTypes)
        favoriteShows = loadFavorites(repo, ContentType.TV_SHOWS, supportedContentTypes)
        liveRowLoaded = true
    }
    LaunchedEffect(mediaRepositoryRef, liveRowEntries, favoriteChannelEntries) {
        val repo = mediaRepositoryRef ?: return@LaunchedEffect
        val items = (liveRowEntries + favoriteChannelEntries).map { it.item }.distinctBy { it.id }
        if (items.isEmpty()) return@LaunchedEffect
        while (true) {
            liveNowPlaying = repo.getNowPlayingFromIndex(items)
            val nowMs = System.currentTimeMillis()
            delay(DateUtils.MINUTE_IN_MILLIS - nowMs % DateUtils.MINUTE_IN_MILLIS)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mediaRepositoryRef) {
        val repo = mediaRepositoryRef
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) syncStatsReload++
                if (event == Lifecycle.Event.ON_RESUME && repo != null) {
                    coroutineScope.launch {
                        continueWatchingItems = repo.getContinueWatchingItems()
                    }
                    if (ContentType.LIVE_TV in supportedContentTypes) {
                        coroutineScope.launch {
                            liveRowEntries = loadLiveRow(repo)
                            favoriteChannelEntries = loadFavoriteChannels(repo)
                        }
                    }
                    coroutineScope.launch {
                        favoriteMovies = loadFavorites(repo, ContentType.MOVIES, supportedContentTypes)
                        favoriteShows = loadFavorites(repo, ContentType.TV_SHOWS, supportedContentTypes)
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
    // The first card of the first row that has one: where focus opens and where Down from the header
    // goes. Null with no row at all; the sections are on the navigation rail then.
    val firstRowFocus =
        when {
            continueWatchingItems.isNotEmpty() -> shelfFirstFocus
            liveRowEntries.isNotEmpty() -> liveRowFirstFocus
            favoriteChannelEntries.isNotEmpty() -> favoriteChannelsFirstFocus
            favoriteMovies.isNotEmpty() -> favoriteMoviesFirstFocus
            favoriteShows.isNotEmpty() -> favoriteShowsFirstFocus
            else -> null
        }
    val rail = LocalTvNavRail.current
    // Read by the entry focus below after it has waited for the rows: the value from its launch
    // was from before they loaded (null: no focus at all after a profile switch).
    val currentFirstRowFocus by rememberUpdatedState(firstRowFocus)
    LaunchedEffect(Unit) {
        // Back hands focus to the control that was left (NavReturnFocusEffect below).
        if (returnFocus.isReturn) return@LaunchedEffect
        // Wait for the shelf and the Live row, which take entry focus when they have something
        // (TV home overhaul plan, Phase 3).
        withTimeoutOrNull(ENTRY_FOCUS_WAIT_MS) {
            snapshotFlow { needsSignIn || (continueWatchingLoaded && liveRowLoaded) }.first { it }
        }
        if (needsSignIn) return@LaunchedEffect
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
                    snapshotFlow { continueWatchingItems }.first { items -> items.any { it.id == itemId } }
                }
            val index = items?.indexOfFirst { it.id == itemId } ?: -1
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
                        .first { entries -> entries.any { it.item.id == itemId } }
                }
            val index = entries?.indexOfFirst { it.item.id == itemId } ?: -1
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
                        .first { items -> items.any { it.id == itemId } }
                }
            val index = items?.indexOfFirst { it.id == itemId } ?: -1
            if (index >= 0) row.scrollToItem(index)
        }
    }
    val leaveTo: (String, () -> Unit) -> Unit = { key, navigate ->
        returnFocus.leaveFrom(key)
        navigate()
    }

    // Hold OK / Menu on a card (docs/plans/20261009_tv-recents-favorites-plan.md → A, "Home's
    // cards"): the list rows' menu. A removal takes the card out of its row at once, focus going
    // to the card that took its place (cardAfterChange), with the Undo bar; Menu while it shows
    // puts the card back, focus on it.
    var cardMenu by remember { mutableStateOf<HomeCardMenu?>(null) }
    val cardFocus = remember { FocusRequester() }
    var cardFocusRequest by remember { mutableStateOf<CardFocusRequest?>(null) }
    val shelfKeys: (HomeShelf) -> List<String> = { shelf ->
        when (shelf) {
            HomeShelf.CONTINUE_WATCHING -> continueWatchingItems.map { it.id }
            HomeShelf.CHANNELS -> liveRowEntries.map { it.item.id }
            HomeShelf.FAVORITE_CHANNELS -> favoriteChannelEntries.map { it.item.id }
            HomeShelf.FAVORITE_MOVIES -> favoriteMovies.map { it.id }
            HomeShelf.FAVORITE_SHOWS -> favoriteShows.map { it.id }
        }
    }
    val listStateOf: (HomeShelf) -> LazyListState = { shelf ->
        when (shelf) {
            HomeShelf.CONTINUE_WATCHING -> shelfListState
            HomeShelf.CHANNELS -> liveRowListState
            HomeShelf.FAVORITE_CHANNELS -> favoriteChannelsListState
            HomeShelf.FAVORITE_MOVIES -> favoriteMoviesListState
            HomeShelf.FAVORITE_SHOWS -> favoriteShowsListState
        }
    }
    val focusAfterChange: (HomeShelf, List<String>, String) -> Unit = { shelf, oldKeys, key ->
        cardFocusRequest = CardFocusRequest(cardAfterChange(shelf, oldKeys, key, HomeShelf.entries.associateWith(shelfKeys)))
    }
    // The card asked for, scrolled into its row when off screen; no card left: the rail. Never
    // nothing focused.
    LaunchedEffect(cardFocusRequest) {
        val request = cardFocusRequest ?: return@LaunchedEffect
        val card = request.card
        if (card == null) {
            rail?.entry?.requestFocusWithRetry()
        } else {
            val listState = listStateOf(card.shelf)
            val index = shelfKeys(card.shelf).indexOf(card.key)
            if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
            cardFocus.requestFocusWithRetry(fallback = rail?.entry)
        }
        cardFocusRequest = null
    }
    val cardFocusTarget: (HomeShelf, String) -> Modifier = { shelf, key ->
        if (cardFocusRequest?.card == HomeCard(shelf, key)) Modifier.focusRequester(cardFocus) else Modifier
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
                takeOut(continueWatchingItems, key, { it.id }, { continueWatchingItems }) {
                    continueWatchingItems =
                        it
                }
            }

            HomeShelf.CHANNELS -> {
                takeOut(liveRowEntries, key, { it.item.id }, { liveRowEntries }) { liveRowEntries = it }
            }

            HomeShelf.FAVORITE_CHANNELS -> {
                takeOut(favoriteChannelEntries, key, { it.item.id }, { favoriteChannelEntries }) {
                    favoriteChannelEntries =
                        it
                }
            }

            HomeShelf.FAVORITE_MOVIES -> {
                takeOut(favoriteMovies, key, { it.id }, { favoriteMovies }) { favoriteMovies = it }
            }

            HomeShelf.FAVORITE_SHOWS -> {
                takeOut(favoriteShows, key, { it.id }, { favoriteShows }) { favoriteShows = it }
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

    val reloadFavorites: suspend (MediaRepository, String) -> Unit = { repo, contentType ->
        when (contentType) {
            ContentType.LIVE_TV -> favoriteChannelEntries = loadFavoriteChannels(repo)
            ContentType.MOVIES -> favoriteMovies = loadFavorites(repo, contentType, supportedContentTypes)
            ContentType.TV_SHOWS -> favoriteShows = loadFavorites(repo, contentType, supportedContentTypes)
        }
    }
    // The shelf again after a watched mark; a card it no longer has hands focus on (an episode's
    // card turns into its show's "Up next" and stays).
    val reloadContinueWatching: suspend (MediaRepository, String?) -> Unit = { repo, focusedKey ->
        val oldKeys = shelfKeys(HomeShelf.CONTINUE_WATCHING)
        continueWatchingItems = repo.getContinueWatchingItems()
        if (focusedKey != null && focusedKey in oldKeys && focusedKey !in shelfKeys(HomeShelf.CONTINUE_WATCHING)) {
            focusAfterChange(HomeShelf.CONTINUE_WATCHING, oldKeys, focusedKey)
        }
    }

    // Opens the menu once the card's favourite and watched states are known.
    val openCardMenu: (HomeShelf, HomeCardItem) -> Unit = { shelf, ref ->
        mediaRepositoryRef?.let { repo ->
            coroutineScope.launchGuarded("Home.openCardMenu") {
                val isFavorite = shelf.isFavorites || repo.isFavoriteSuspend(ref.id, ref.contentType)
                val isWatched =
                    when {
                        ref.watchedItemId == null -> {
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
                            ),
                    )
            }
        }
    }
    cardMenu?.let { menu ->
        val repo = mediaRepositoryRef
        val ref = menu.ref
        FavoriteContextMenuDialog(
            target = menu.target,
            onConfirm = {
                when {
                    repo == null -> {}

                    menu.shelf.isFavorites -> {
                        removeCard(menu.shelf, ref.id, ref.name) {
                            repo.removeFavoriteSuspend(ref.id, ref.contentType)
                            suspend { repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType) }
                        }
                    }

                    menu.target.isFavorite -> {
                        // The card stays in its row; its favourite leaves the favourites row below.
                        coroutineScope.launchGuarded("Home.removeFavorite") {
                            repo.removeFavoriteSuspend(ref.id, ref.contentType)
                            reloadFavorites(repo, ref.contentType)
                        }
                        undoBar.show(parseDisplayTitle(ref.name).title.ifBlank { ref.name }) {
                            coroutineScope.launchGuarded("Home.undoRemoveFavorite") {
                                repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType)
                                reloadFavorites(repo, ref.contentType)
                            }
                        }
                    }

                    else -> {
                        coroutineScope.launchGuarded("Home.addFavorite") {
                            repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType)
                            reloadFavorites(repo, ref.contentType)
                        }
                    }
                }
            },
            onDismiss = { cardMenu = null },
            onToggleWatched =
                ref.watchedItemId?.let { watchedItemId ->
                    {
                        when {
                            repo == null -> {}

                            // A film watched leaves Continue Watching: a removal, with its Undo.
                            menu.shelf == HomeShelf.CONTINUE_WATCHING && ref.contentType == ContentType.MOVIES -> {
                                removeCard(menu.shelf, ref.id, ref.name) {
                                    repo.setWatched(watchedItemId, ref.contentType, true)
                                    suspend { repo.setWatched(watchedItemId, ref.contentType, false) }
                                }
                            }

                            else -> {
                                coroutineScope.launchGuarded("Home.toggleWatched") {
                                    repo.setWatched(watchedItemId, ref.contentType, menu.target.isWatched != true)
                                    reloadContinueWatching(repo, ref.id.takeIf { menu.shelf == HomeShelf.CONTINUE_WATCHING })
                                }
                            }
                        }
                    }
                },
            onRemoveFromRecent =
                if (menu.target.isInRecent && repo != null) {
                    {
                        removeCard(menu.shelf, ref.id, ref.name) {
                            val removal = repo.removeFromRecent(ref.id, ref.contentType)
                            suspend { removal?.let { repo.restoreRecent(it) } }
                        }
                    }
                } else {
                    null
                },
            list = if (menu.shelf.isFavorites) RowMenuList.FAVORITES else RowMenuList.RECENT,
        )
    }

    val scale = LocalUiScale.current

    // Menu undoes the last removal while its bar shows.
    Box(modifier = Modifier.fillMaxSize().undoOnMenuKey(undoBar)) {
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
                        Text(
                            text = stringResource(R.string.login_app_name),
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontSize =
                                        MaterialTheme.typography.headlineSmall.fontSize
                                            .scaled(scale),
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
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
                                    if (needsSignIn || firstRowFocus == null) {
                                        Modifier
                                    } else {
                                        Modifier.focusProperties { down = firstRowFocus }
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    ) {
                        // Shown with one source too, for its sync status; only a picker (and
                        // focusable) with two or more. The header's first focusable item hands Left
                        // to the navigation rail: the pill when it can pick, else Search the guide.
                        val canPick = providerName.isNotEmpty() && allProviders.size > 1
                        if (providerName.isNotEmpty()) {
                            val syncStatus = sourceSyncStatus(syncing, lastSyncedAtMs, lastSyncError)
                            val displayName =
                                if (appSettings.isDevMode && providerType.isNotEmpty()) {
                                    "$providerName ($providerType)"
                                } else {
                                    providerName
                                }
                            val switchProviderDescription =
                                stringResource(R.string.content_switch_provider_description_format, displayName)
                            var providerPillFocused by remember { mutableStateOf(false) }
                            val pillScale by animateFloatAsState(
                                targetValue = if (providerPillFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
                                animationSpec =
                                    tween(
                                        durationMillis = org.njarasoa.fijerena.core.ui.theme.CinemaAnimation.focusDurationMs,
                                    ),
                                label = "provider_pill_scale",
                            )
                            GlassPanel(
                                modifier =
                                    Modifier
                                        .weight(1f, fill = false)
                                        .scale(pillScale)
                                        .border(
                                            width = TvFocusTokens.focusBorderWidth,
                                            color =
                                                if (providerPillFocused) {
                                                    CinemaAccentLight
                                                } else {
                                                    androidx.compose.ui.graphics.Color.Transparent
                                                },
                                            shape = RoundedCornerShape(CinemaCornerRadius.large),
                                        ).then(
                                            if (canPick) {
                                                Modifier
                                                    .leftToRail()
                                                    .onFocusChanged { providerPillFocused = it.isFocused }
                                                    .clickable(role = Role.DropdownList) { showProviderPicker = true }
                                                    .semantics { contentDescription = switchProviderDescription }
                                            } else {
                                                Modifier
                                            },
                                        ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = Spacing.md,
                                            vertical = Spacing.xs,
                                        ),
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (providerPillFocused) CinemaTextPrimary else CinemaAccentLight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    SourceSyncStatusLine(
                                        status = syncStatus,
                                        lastSyncedAtMs = lastSyncedAtMs,
                                        modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.sm),
                                    )
                                    if (canPick) {
                                        Icon(
                                            imageVector = CinemaIcons.ArrowDropDown,
                                            contentDescription = null,
                                            tint = if (providerPillFocused) CinemaTextPrimary else CinemaAccentLight,
                                            modifier = Modifier.padding(start = Spacing.xs),
                                        )
                                    }
                                }
                            }
                        }
                        if (hasEpgData) {
                            CinemaIconButton(
                                onClick = { leaveTo(RETURN_EPG_BROWSER, onEpgBrowser) },
                                modifier =
                                    Modifier
                                        .then(if (canPick) Modifier else Modifier.leftToRail())
                                        .navReturnFocusTarget(returnFocus, RETURN_EPG_BROWSER),
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.MenuBook,
                                        contentDescription = stringResource(R.string.epg_browser_title),
                                        tint = CinemaTextPrimary,
                                    )
                                },
                            )
                        }
                    }
                }

                if (needsSignIn) {
                    JellyfinSignInPanel(
                        providerName = providerName,
                        onSignIn = { leaveTo(RETURN_SIGN_IN) { onSignInRequired(activeProviderId) } },
                        scale = scale,
                        signInButtonFocusRequester = returnFocus.requesterFor(RETURN_SIGN_IN),
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
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (continueWatchingItems.isNotEmpty()) {
                                TvContinueWatchingShelf(
                                    items = continueWatchingItems,
                                    firstItemFocus = shelfFirstFocus,
                                    onItemSelected = { item ->
                                        leaveTo(RETURN_CONTINUE_WATCHING_PREFIX + item.id) { onContinueWatchingSelected(item) }
                                    },
                                    onOpenActions = { item -> openCardMenu(HomeShelf.CONTINUE_WATCHING, item.toCardItem()) },
                                    listState = shelfListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_CONTINUE_WATCHING_PREFIX + item.id)
                                            .then(cardFocusTarget(HomeShelf.CONTINUE_WATCHING, item.id))
                                            .then(artOnFocus(item.thumbnailUrl))
                                    },
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
                                        leaveTo(RETURN_LIVE_ROW_PREFIX + entry.item.id) {
                                            onLiveChannelSelected(entry.item.id, zapListId(entry))
                                        }
                                    },
                                    firstItemFocus = liveRowFirstFocus,
                                    onOpenActions = { entry ->
                                        openCardMenu(
                                            HomeShelf.CHANNELS,
                                            HomeCardItem(entry.item.id, entry.item.name, entry.item.categoryId, ContentType.LIVE_TV),
                                        )
                                    },
                                    listState = liveRowListState,
                                    itemModifier = { entry ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_LIVE_ROW_PREFIX + entry.item.id)
                                            .then(cardFocusTarget(HomeShelf.CHANNELS, entry.item.id))
                                            .then(artOnFocus(entry.item.thumbnailUrl))
                                    },
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
                                        leaveTo(RETURN_FAVORITE_CHANNELS_PREFIX + entry.item.id) {
                                            onLiveChannelSelected(entry.item.id, zapListId(entry))
                                        }
                                    },
                                    firstItemFocus = favoriteChannelsFirstFocus,
                                    onOpenActions = { entry ->
                                        openCardMenu(
                                            HomeShelf.FAVORITE_CHANNELS,
                                            HomeCardItem(entry.item.id, entry.item.name, entry.item.categoryId, ContentType.LIVE_TV),
                                        )
                                    },
                                    listState = favoriteChannelsListState,
                                    itemModifier = { entry ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_CHANNELS_PREFIX + entry.item.id)
                                            .then(cardFocusTarget(HomeShelf.FAVORITE_CHANNELS, entry.item.id))
                                            .then(artOnFocus(entry.item.thumbnailUrl))
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }

                            if (favoriteMovies.isNotEmpty()) {
                                TvFavoritesRow(
                                    title = stringResource(R.string.home_favorite_movies),
                                    items = favoriteMovies,
                                    thumbnailType = ThumbnailContentType.MOVIE,
                                    onItemSelected = { item ->
                                        leaveTo(RETURN_FAVORITE_MOVIES_PREFIX + item.id) { onFavoriteSelected(item, ContentType.MOVIES) }
                                    },
                                    firstItemFocus = favoriteMoviesFirstFocus,
                                    onOpenActions = { item ->
                                        openCardMenu(
                                            HomeShelf.FAVORITE_MOVIES,
                                            HomeCardItem(item.id, item.name, item.categoryId, ContentType.MOVIES, watchedItemId = item.id),
                                        )
                                    },
                                    listState = favoriteMoviesListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_MOVIES_PREFIX + item.id)
                                            .then(cardFocusTarget(HomeShelf.FAVORITE_MOVIES, item.id))
                                            .then(artOnFocus(item.thumbnailUrl))
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }

                            if (favoriteShows.isNotEmpty()) {
                                TvFavoritesRow(
                                    title = stringResource(R.string.home_favorite_shows),
                                    items = favoriteShows,
                                    thumbnailType = ThumbnailContentType.TV_SHOW,
                                    onItemSelected = { item ->
                                        leaveTo(RETURN_FAVORITE_SHOWS_PREFIX + item.id) { onFavoriteSelected(item, ContentType.TV_SHOWS) }
                                    },
                                    firstItemFocus = favoriteShowsFirstFocus,
                                    onOpenActions = { item ->
                                        openCardMenu(
                                            HomeShelf.FAVORITE_SHOWS,
                                            HomeCardItem(item.id, item.name, item.categoryId, ContentType.TV_SHOWS),
                                        )
                                    },
                                    listState = favoriteShowsListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_SHOWS_PREFIX + item.id)
                                            .then(cardFocusTarget(HomeShelf.FAVORITE_SHOWS, item.id))
                                            .then(artOnFocus(item.thumbnailUrl))
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }
                        }
                    }
                }
            }

            TvUndoBar(undoBar, Modifier.align(Alignment.BottomCenter))

            if (showProviderPicker && allProviders.size > 1) {
                // A new dialog window starts with focus on the Close button *below* the list, so
                // D-pad Down had nowhere to go and Center just closed the dialog. Land on the
                // current provider's row instead (F-38).
                val pickerInitialFocus = remember { FocusRequester() }
                val pickerFocusId = (allProviders.firstOrNull { it.id == activeProviderId } ?: allProviders.first()).id
                CinemaAlertDialog(
                    initialFocus = pickerInitialFocus,
                    onDismissRequest = { showProviderPicker = false },
                    // The darkest surface behind the rows, so the option rows (resting container)
                    // stand out from it rather than dark on dark (TV UI audit #3).
                    containerColor = CinemaBackground,
                    titleContentColor = CinemaTextPrimary,
                    textContentColor = CinemaTextSecondary,
                    title = { androidx.compose.material3.Text(stringResource(R.string.content_switch_provider_title)) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            // The pill only says "Update failed"; the reason is here. It already
                            // carries the raw detail in developer mode (ProviderSyncRunner).
                            lastSyncError?.takeIf { !syncing }?.let { error ->
                                androidx.compose.material3.Text(
                                    text = stringResource(R.string.home_source_update_failed_detail, error),
                                    color = CinemaError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = Spacing.sm),
                                )
                            }
                            allProviders.forEach { provider ->
                                val isActive = provider.id == activeProviderId
                                val label =
                                    if (appSettings.isDevMode) {
                                        "${provider.name} (${provider.type})"
                                    } else {
                                        provider.name
                                    }
                                TvOptionRow(
                                    title = label,
                                    selected = isActive,
                                    activeLabel = stringResource(R.string.provider_active_label),
                                    onClick = {
                                        if (!isActive) {
                                            coroutineScope.launch {
                                                val providerRepo = ProviderRepository(context.applicationContext)
                                                providerRepo.pickProvider(provider.id)
                                                showProviderPicker = false
                                                refreshTrigger++
                                                onProviderChanged()
                                            }
                                        } else {
                                            showProviderPicker = false
                                        }
                                    },
                                    modifier =
                                        if (provider.id ==
                                            pickerFocusId
                                        ) {
                                            Modifier.focusRequester(pickerInitialFocus)
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                        }
                    },
                    // A standard dialog button, as the TV Guide's programme panel's Close.
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = { showProviderPicker = false },
                            colors =
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = CinemaSurfaceVariant,
                                    contentColor = CinemaTextPrimary,
                                ),
                        ) { androidx.compose.material3.Text(stringResource(R.string.common_close), color = CinemaTextPrimary) }
                    },
                )
            }
        }
    }
}

// Keys for the controls that navigate away from Home — see rememberNavReturnFocus.
private const val RETURN_EPG_BROWSER = "epgBrowser"
private const val RETURN_SIGN_IN = "signIn"
private const val RETURN_CONTINUE_WATCHING_PREFIX = "cw:"
private const val RETURN_LIVE_ROW_PREFIX = "live:"
private const val RETURN_FAVORITE_CHANNELS_PREFIX = "favLive:"
private const val RETURN_FAVORITE_MOVIES_PREFIX = "favMovie:"
private const val RETURN_FAVORITE_SHOWS_PREFIX = "favShow:"

/** What a card's menu acts on. */
private class HomeCardItem(
    val id: String,
    val name: String,
    val categoryId: String,
    val contentType: String,
    /** What Mark as watched marks: the film, or the episode a show's card resumes; null: no such action. */
    val watchedItemId: String? = null,
)

/** A Continue Watching card: Mark as watched on a film and on an episode to resume, not on an "Up next" one. */
private fun ContinueWatchingItem.toCardItem(): HomeCardItem =
    HomeCardItem(
        id = id,
        name = name,
        categoryId = categoryId,
        contentType = contentType,
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
    repo: MediaRepository,
    contentType: String,
    supportedContentTypes: Set<String>,
): List<MediaItem> = if (contentType in supportedContentTypes) repo.getFavoritesForContentTypeSuspend(contentType) else emptyList()

/** The Channels row for the active profile: last watched, then Recent. */
private suspend fun loadLiveRow(repo: MediaRepository): List<LiveRowEntry> =
    mergeLiveRow(
        lastItemId = repo.getLastItemId(ContentType.LIVE_TV),
        recent = repo.getRecentItemsSuspend(ContentType.LIVE_TV),
    )

/** The list a Live card zaps through once opened: Favorites from Favorite channels, Recent from Channels. */
private fun zapListId(entry: LiveRowEntry): String =
    if (entry.fromFavorites) CategoryViewModel.FAVORITES_CATEGORY_ID else CategoryViewModel.RECENT_CATEGORY_ID

/** The Favorite channels row for the active profile. */
private suspend fun loadFavoriteChannels(repo: MediaRepository): List<LiveRowEntry> =
    favoriteChannelsRow(repo.getFavoritesForContentTypeSuspend(ContentType.LIVE_TV))

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

/** In place of the library while this profile has no login for the active Jellyfin server. */
@Composable
private fun JellyfinSignInPanel(
    providerName: String,
    onSignIn: () -> Unit,
    scale: Float,
    signInButtonFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.profile_jellyfin_sign_in_prompt, providerName),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize =
                        MaterialTheme.typography.headlineSmall.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
        CinemaPrimaryButton(
            onClick = onSignIn,
            text = stringResource(R.string.profile_jellyfin_sign_in_button),
            // Home's only focusable item besides the header: Left goes to the navigation rail.
            modifier = (signInButtonFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).leftToRail(),
        )
    }
}
