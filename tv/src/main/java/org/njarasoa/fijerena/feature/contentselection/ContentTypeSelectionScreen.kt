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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.home.LiveRowEntry
import org.njarasoa.fijerena.core.ui.home.mergeLiveRow
import org.njarasoa.fijerena.core.ui.home.sourceSyncStatus
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
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.category.components.MinimalBringIntoView
import org.njarasoa.fijerena.feature.contentselection.components.HomeClock
import org.njarasoa.fijerena.feature.contentselection.components.SourceSyncStatusLine
import org.njarasoa.fijerena.feature.contentselection.components.TvContinueWatchingShelf
import org.njarasoa.fijerena.feature.contentselection.components.TvFavoritesRow
import org.njarasoa.fijerena.feature.contentselection.components.TvLiveRow
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.navigation.ContentType as NavContentType
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

@Composable
fun ContentTypeSelectionScreen(
    onContentTypeSelected: (NavContentType) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit = {},
    onEpgBrowser: () -> Unit = {},
    onProviderChanged: () -> Unit = {},
    onCapabilitiesResolved: (Set<String>) -> Unit = {},
    onContinueWatchingSelected: (ContinueWatchingItem) -> Unit = {},
    // A channel from the Live row, and the list it zaps through (CategoryViewModel's virtual
    // "favorites" or "recent" category id).
    onLiveChannelSelected: (streamId: String, contextCategoryId: String) -> Unit = { _, _ -> },
    // A favourite movie (ContentType.MOVIES) or show (ContentType.TV_SHOWS) from its Home row.
    onFavoriteSelected: (item: MediaItem, contentType: String) -> Unit = { _, _ -> },
    onChooseProfile: () -> Unit = {},
    onSignInRequired: (providerId: Long) -> Unit = {},
) {
    val context = LocalContext.current
    val signInResources = LocalResources.current
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val activeProfile by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
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
    var liveTvCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moviesCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tvShowsCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }

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
    var liveRowLoaded by remember { mutableStateOf(false) }
    var liveNowPlaying by remember { mutableStateOf<Map<String, EpgProgram>>(emptyMap()) }

    // Favourite movies and shows rows (Phase 5), loaded with the Live row.
    var favoriteMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteShows by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

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
        // Reset counts so stale values don't linger during provider switch
        liveTvCounts = null
        moviesCounts = null
        tvShowsCounts = null
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

    LaunchedEffect(mediaProviderRef) {
        val mp = mediaProviderRef ?: return@LaunchedEffect
        // getCategories() already excludes filtered-out categories at the DB layer, so its size
        // IS the visible count — the total (for "X of Y") needs the unfiltered count separately.
        val xtream = mp as? org.njarasoa.fijerena.core.network.XtreamMediaProvider
        withContext(Dispatchers.IO) {
            if (ContentType.LIVE_TV in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.LIVE_TV).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.LIVE_TV) ?: cats.size
                    liveTvCounts = Pair(cats.size, total)
                }
            }
            if (ContentType.MOVIES in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.MOVIES).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.MOVIES) ?: cats.size
                    moviesCounts = Pair(cats.size, total)
                }
            }
            if (ContentType.TV_SHOWS in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.TV_SHOWS).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.TV_SHOWS) ?: cats.size
                    tvShowsCounts = Pair(cats.size, total)
                }
            }
        }
    }

    // "Jump Back In" shelf — reload whenever the repository changes (provider switch) and again
    // on every ON_RESUME, so returning from playback immediately reflects updated progress.
    LaunchedEffect(mediaRepositoryRef) {
        val repo = mediaRepositoryRef ?: return@LaunchedEffect
        continueWatchingItems = repo.getContinueWatchingItems()
        continueWatchingLoaded = true
    }
    LaunchedEffect(mediaRepositoryRef, supportedContentTypes) {
        val repo = mediaRepositoryRef ?: return@LaunchedEffect
        liveRowEntries = if (ContentType.LIVE_TV in supportedContentTypes) loadLiveRow(repo) else emptyList()
        favoriteMovies = loadFavorites(repo, ContentType.MOVIES, supportedContentTypes)
        favoriteShows = loadFavorites(repo, ContentType.TV_SHOWS, supportedContentTypes)
        liveRowLoaded = true
    }
    LaunchedEffect(mediaRepositoryRef, liveRowEntries) {
        val repo = mediaRepositoryRef ?: return@LaunchedEffect
        if (liveRowEntries.isEmpty()) return@LaunchedEffect
        val items = liveRowEntries.map { it.item }
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
                        coroutineScope.launch { liveRowEntries = loadLiveRow(repo) }
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

    // The hero cards' focus (UX overhaul plan Part II Phase 4). Home opens on the first card with
    // content, not the "Switch Source" chip (F-H-1). Down from any header button goes back to the
    // card focused last, default the leftmost: the buttons sit top-right, so the geometric search
    // picked the rightmost card and Up/Down were not reversible (F-H-2, R8). A Live TV card with
    // no channels is dimmed and cannot take focus (F-H-3).
    val heroCardFocus =
        remember {
            mapOf(RETURN_LIVE_TV to FocusRequester(), RETURN_MOVIES to FocusRequester(), RETURN_TV_SHOWS to FocusRequester())
        }
    var lastHeroCard by rememberSaveable { mutableStateOf<String?>(null) }
    val liveTvEmpty = liveTvCounts?.first == 0
    val heroCards = focusableHeroCards(supportedContentTypes, liveTvCounts)
    val headerDownCard = lastHeroCard?.takeIf { it in heroCards } ?: heroCards.firstOrNull()
    val heroFallbackFocus = heroCards.firstOrNull()?.let(heroCardFocus::getValue)
    // The first card of each row: where focus enters a row it has not been in yet (HomeRow).
    val shelfFirstFocus = remember { FocusRequester() }
    val liveRowFirstFocus = remember { FocusRequester() }
    val liveRowListState = rememberLazyListState()
    val favoriteMoviesFirstFocus = remember { FocusRequester() }
    val favoriteMoviesListState = rememberLazyListState()
    val favoriteShowsFirstFocus = remember { FocusRequester() }
    val favoriteShowsListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        // Back hands focus to the control that was left (NavReturnFocusEffect below).
        if (returnFocus.isReturn) return@LaunchedEffect
        // Wait for the Live TV count, so an empty Live TV card is not the one focused, and for the
        // shelf, which takes entry focus when it has something (TV home overhaul plan, Phase 3).
        withTimeoutOrNull(ENTRY_FOCUS_WAIT_MS) {
            snapshotFlow {
                needsSignIn ||
                    ((ContentType.LIVE_TV !in supportedContentTypes || liveTvCounts != null) && continueWatchingLoaded && liveRowLoaded)
            }.first { it }
        }
        if (needsSignIn) return@LaunchedEffect
        val firstTile =
            focusableHeroCards(supportedContentTypes, liveTvCounts)
                .firstOrNull()
                ?.let(heroCardFocus::getValue)
        when {
            continueWatchingItems.isNotEmpty() -> shelfFirstFocus.requestFocusWithRetry(fallback = firstTile)
            liveRowEntries.isNotEmpty() -> liveRowFirstFocus.requestFocusWithRetry(fallback = firstTile)
            favoriteMovies.isNotEmpty() -> favoriteMoviesFirstFocus.requestFocusWithRetry(fallback = firstTile)
            favoriteShows.isNotEmpty() -> favoriteShowsFirstFocus.requestFocusWithRetry(fallback = firstTile)
            else -> firstTile?.requestFocusWithRetry()
        }
    }
    NavReturnFocusEffect(returnFocus, fallback = heroFallbackFocus) { key ->
        if (key.startsWith(RETURN_CONTINUE_WATCHING_PREFIX)) {
            val itemId = key.removePrefix(RETURN_CONTINUE_WATCHING_PREFIX)
            val items =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { continueWatchingItems }.first { items -> items.any { it.id == itemId } }
                }
            val index = items?.indexOfFirst { it.id == itemId } ?: -1
            if (index >= 0) shelfListState.scrollToItem(index)
        }
        if (key.startsWith(RETURN_LIVE_ROW_PREFIX)) {
            val itemId = key.removePrefix(RETURN_LIVE_ROW_PREFIX)
            val entries =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { liveRowEntries }.first { entries -> entries.any { it.item.id == itemId } }
                }
            val index = entries?.indexOfFirst { it.item.id == itemId } ?: -1
            if (index >= 0) liveRowListState.scrollToItem(index)
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

    val scale = LocalUiScale.current

    Box(modifier = Modifier.fillMaxSize()) {
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
                                    if (needsSignIn || headerDownCard == null) {
                                        Modifier
                                    } else {
                                        Modifier.focusProperties { down = heroCardFocus.getValue(headerDownCard) }
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    ) {
                        // Shown with one source too, for its sync status; only a picker (and
                        // focusable) with two or more.
                        if (providerName.isNotEmpty()) {
                            val canPick = allProviders.size > 1
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
                                modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_EPG_BROWSER),
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.MenuBook,
                                        contentDescription = stringResource(R.string.epg_browser_title),
                                        tint = CinemaTextPrimary,
                                    )
                                },
                            )
                        }
                        CinemaIconButton(
                            onClick = { leaveTo(RETURN_SEARCH, onSearch) },
                            modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_SEARCH),
                            icon = {
                                Icon(
                                    imageVector = CinemaIcons.Search,
                                    contentDescription = stringResource(R.string.content_search_all_description),
                                    tint = CinemaTextPrimary,
                                )
                            },
                        )
                        // Always shown, even with one profile, so profiles are discoverable.
                        activeProfile?.let { profile ->
                            val switchLabel = stringResource(R.string.profile_switch_description, profile.name)
                            CinemaIconButton(
                                onClick = { leaveTo(RETURN_PROFILE, onChooseProfile) },
                                modifier =
                                    Modifier
                                        .semantics { contentDescription = switchLabel }
                                        .navReturnFocusTarget(returnFocus, RETURN_PROFILE),
                                icon = {
                                    ProfileAvatar(
                                        name = profile.name,
                                        colorIndex = profile.colorIndex,
                                        size = TvDimensions.iconMedium,
                                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                                    )
                                },
                            )
                        }
                        CinemaIconButton(
                            onClick = { leaveTo(RETURN_SETTINGS, onSettings) },
                            modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_SETTINGS),
                            icon = {
                                Icon(
                                    imageVector = CinemaIcons.Settings,
                                    contentDescription = stringResource(R.string.settings_title),
                                    tint = CinemaTextPrimary,
                                )
                            },
                        )
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
                    // Section tiles, then the shelf (TV home overhaul plan, Phase 2). Scrollable: on a
                    // lower-density TV the shelf can still run past the bottom edge. Scrolled only as
                    // far as the focused card needs: Android TV's default pulls it a third of the way
                    // down, which at a large Text & grid size slid the tiles under the header on open.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
                                verticalAlignment = Alignment.CenterVertically,
                                // Up from a row comes back to the tile focused last, not the one
                                // geometrically above the card.
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRestorer(headerDownCard?.let(heroCardFocus::getValue) ?: FocusRequester.Default)
                                        .focusGroup(),
                            ) {
                                val isDevMode = appSettings.isDevMode
                                var cardIndex = 1
                                val heroCardModifier: (String) -> Modifier = { key ->
                                    Modifier
                                        .focusRequester(heroCardFocus.getValue(key))
                                        .onFocusChanged { if (it.hasFocus) lastHeroCard = key }
                                        // Left on the first focusable tile and Right on the last stay
                                        // put: with Live TV dimmed, the search left from Movies fell
                                        // through to the shelf below.
                                        .focusProperties {
                                            if (key == heroCards.firstOrNull()) left = FocusRequester.Cancel
                                            if (key == heroCards.lastOrNull()) right = FocusRequester.Cancel
                                        }
                                }
                                if (ContentType.LIVE_TV in supportedContentTypes) {
                                    SectionTile(
                                        title = stringResource(R.string.provider_live_tv_label),
                                        icon = CinemaIcons.LiveTv,
                                        categoryCounts = liveTvCounts,
                                        showCount = isDevMode,
                                        showLivePulse = !liveTvEmpty,
                                        emptyLabel = if (liveTvEmpty) stringResource(R.string.content_type_live_tv_no_channels) else null,
                                        gradientColors = listOf(CinemaOrange, CinemaOrangeDark),
                                        onClick = { leaveTo(RETURN_LIVE_TV) { onContentTypeSelected(NavContentType.LIVE_TV) } },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .then(heroCardModifier(RETURN_LIVE_TV))
                                                .staggeredEntrance(cardIndex++)
                                                .navReturnFocusTarget(returnFocus, RETURN_LIVE_TV),
                                    )
                                }

                                if (ContentType.MOVIES in supportedContentTypes) {
                                    SectionTile(
                                        title = stringResource(R.string.provider_movies_label),
                                        icon = CinemaIcons.Movie,
                                        categoryCounts = moviesCounts,
                                        showCount = isDevMode,
                                        gradientColors = listOf(CinemaAccent, CinemaAccentDark),
                                        onClick = { leaveTo(RETURN_MOVIES) { onContentTypeSelected(NavContentType.MOVIES) } },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .then(heroCardModifier(RETURN_MOVIES))
                                                .staggeredEntrance(cardIndex++)
                                                .navReturnFocusTarget(returnFocus, RETURN_MOVIES),
                                    )
                                }

                                if (ContentType.TV_SHOWS in supportedContentTypes) {
                                    SectionTile(
                                        title = stringResource(R.string.provider_tv_shows_label),
                                        icon = CinemaIcons.Tv,
                                        categoryCounts = tvShowsCounts,
                                        showCount = isDevMode,
                                        gradientColors = listOf(CinemaAccentLight, CinemaAccent),
                                        onClick = { leaveTo(RETURN_TV_SHOWS) { onContentTypeSelected(NavContentType.TV_SHOWS) } },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .then(heroCardModifier(RETURN_TV_SHOWS))
                                                .staggeredEntrance(cardIndex++)
                                                .navReturnFocusTarget(returnFocus, RETURN_TV_SHOWS),
                                    )
                                }
                            }

                            if (continueWatchingItems.isNotEmpty()) {
                                TvContinueWatchingShelf(
                                    items = continueWatchingItems,
                                    firstItemFocus = shelfFirstFocus,
                                    onItemSelected = { item ->
                                        leaveTo(RETURN_CONTINUE_WATCHING_PREFIX + item.id) { onContinueWatchingSelected(item) }
                                    },
                                    listState = shelfListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_CONTINUE_WATCHING_PREFIX + item.id)
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
                                    entries = liveRowEntries,
                                    nowPlaying = liveNowPlaying,
                                    onEntrySelected = { entry ->
                                        val listId =
                                            if (entry.fromFavorites) {
                                                CategoryViewModel.FAVORITES_CATEGORY_ID
                                            } else {
                                                CategoryViewModel.RECENT_CATEGORY_ID
                                            }
                                        leaveTo(RETURN_LIVE_ROW_PREFIX + entry.item.id) { onLiveChannelSelected(entry.item.id, listId) }
                                    },
                                    firstItemFocus = liveRowFirstFocus,
                                    listState = liveRowListState,
                                    itemModifier = { entry ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_LIVE_ROW_PREFIX + entry.item.id)
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
                                    listState = favoriteMoviesListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_MOVIES_PREFIX + item.id)
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
                                    listState = favoriteShowsListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_SHOWS_PREFIX + item.id)
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
private const val RETURN_LIVE_TV = "liveTv"
private const val RETURN_MOVIES = "movies"
private const val RETURN_TV_SHOWS = "tvShows"
private const val RETURN_EPG_BROWSER = "epgBrowser"
private const val RETURN_SEARCH = "search"
private const val RETURN_PROFILE = "profile"
private const val RETURN_SETTINGS = "settings"
private const val RETURN_SIGN_IN = "signIn"
private const val RETURN_CONTINUE_WATCHING_PREFIX = "cw:"
private const val RETURN_LIVE_ROW_PREFIX = "live:"
private const val RETURN_FAVORITE_MOVIES_PREFIX = "favMovie:"
private const val RETURN_FAVORITE_SHOWS_PREFIX = "favShow:"

/** The active profile's favourites of [contentType], none when the source doesn't have that type. */
private suspend fun loadFavorites(
    repo: MediaRepository,
    contentType: String,
    supportedContentTypes: Set<String>,
): List<MediaItem> = if (contentType in supportedContentTypes) repo.getFavoritesForContentTypeSuspend(contentType) else emptyList()

/** The Live row's channels for the active profile: last watched, favourites, recent. */
private suspend fun loadLiveRow(repo: MediaRepository): List<LiveRowEntry> =
    mergeLiveRow(
        lastItemId = repo.getLastItemId(ContentType.LIVE_TV),
        recent = repo.getRecentItemsSuspend(ContentType.LIVE_TV),
        favorites = repo.getFavoritesForContentTypeSuspend(ContentType.LIVE_TV),
    )

/** How long Back waits for the Continue Watching shelf to reload before giving up on its card. */
private const val RETURN_SHELF_WAIT_MS = 2_000L

/** How long a fresh open waits for the Live TV count before focusing the first card anyway. */
private const val ENTRY_FOCUS_WAIT_MS = 2_000L

/** The hero cards that can take focus, left to right: Live TV only when it has channels (or is still loading). */
private fun focusableHeroCards(
    supportedContentTypes: Set<String>,
    liveTvCounts: Pair<Int, Int>?,
): List<String> =
    buildList {
        if (ContentType.LIVE_TV in supportedContentTypes && liveTvCounts?.first != 0) add(RETURN_LIVE_TV)
        if (ContentType.MOVIES in supportedContentTypes) add(RETURN_MOVIES)
        if (ContentType.TV_SHOWS in supportedContentTypes) add(RETURN_TV_SHOWS)
    }

/**
 * A section tile on Home: Live TV, Movies or TV Shows (TV home overhaul plan, Phase 2). The category
 * count is developer information, shown only in developer mode.
 */
@Composable
private fun SectionTile(
    title: String,
    icon: ImageVector,
    categoryCounts: Pair<Int, Int>?,
    showCount: Boolean = false,
    showLivePulse: Boolean = false,
    gradientColors: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Non-null: nothing to open (Live TV with no channels). The tile is dimmed, shows this in
    // place of the count and cannot take focus.
    emptyLabel: String? = null,
) {
    val scale = LocalUiScale.current
    val shape = RoundedCornerShape(CinemaCornerRadius.large)
    Card(
        onClick = onClick,
        modifier =
            modifier
                .then(if (emptyLabel != null) Modifier.focusProperties { canFocus = false }.alpha(CinemaAlpha.textFaint) else Modifier)
                .height(TvDimensions.homeSectionTileHeight.scaled(scale)),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaSurface,
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleSubtle,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        shape = CardDefaults.shape(shape = shape),
        border =
            CardDefaults.border(
                border = Border(border = BorderStroke(TvFocusTokens.borderThin, CinemaGlassBorder), shape = shape),
                focusedBorder = Border(border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaTextPrimary), shape = shape),
            ),
        glow = CardDefaults.glow(glow = TvFocusTokens.restingGlow, focusedGlow = TvFocusTokens.focusedGlow),
    ) {
        val brush = remember(gradientColors) { Brush.horizontalGradient(colors = gradientColors) }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(brush = brush, shape = shape)
                    .padding(horizontal = Spacing.md.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CinemaTextPrimary,
                modifier = Modifier.size(TvDimensions.homeSectionTileIconSize.scaled(scale)),
            )
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize =
                            MaterialTheme.typography.titleLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.sm.scaled(scale)),
            )
            if (showLivePulse) {
                val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(CinemaAnimation.shimmerDurationMs),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "live_pulse_alpha",
                )
                // pulseAlpha is read inside graphicsLayer's lambda, never in the composable
                // body. Read from the body it invalidates *composition* every animation frame —
                // and since this runs forever, Home recomposed at 60fps with nothing on screen
                // changing: 242 recompositions per 4 idle seconds, measured on a Shield, versus
                // 0 on every other screen. In the lambda the read is deferred to draw.
                // White, after the title (TV UI audit #2): the live red on the tile's orange
                // gradient, tucked against the icon, could not be seen.
                Box(
                    modifier =
                        Modifier
                            .padding(start = Spacing.sm.scaled(scale))
                            .size(TvDimensions.liveDotSize.scaled(scale))
                            .graphicsLayer { alpha = pulseAlpha }
                            .background(CinemaTextPrimary, shape = CircleShape),
                )
            }
            val countText =
                when {
                    emptyLabel != null -> {
                        emptyLabel
                    }

                    !showCount || categoryCounts == null -> {
                        null
                    }

                    categoryCounts.first < categoryCounts.second -> {
                        stringResource(R.string.category_filtered_of_total_format, categoryCounts.first, categoryCounts.second)
                    }

                    else -> {
                        stringResource(R.string.category_count_format, categoryCounts.first)
                    }
                }
            // The rest of the tile, the chip at its end: cut with "…" when the tile is too narrow
            // for it (a large Text & grid size squeezed it to "No").
            if (countText != null) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textLow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = Spacing.sm)
                            .wrapContentWidth(Alignment.End)
                            .background(
                                CinemaTextPrimary.copy(alpha = CinemaAlpha.heroChipBackground),
                                shape = RoundedCornerShape(CinemaCornerRadius.small),
                            ).padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
        }
    }
}

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
            modifier = signInButtonFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
        )
    }
}
