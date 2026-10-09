package org.njarasoa.fijerena.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.navigation.navigateOnce
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.components.APP_LOADING_MIN_MS
import org.njarasoa.fijerena.core.ui.components.AppLoadingScreen
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.feature.category.TvCategoryGridScreen
import org.njarasoa.fijerena.feature.contentselection.ContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.epg.TvEpgGuideScreen
import org.njarasoa.fijerena.feature.epg.TvEpgManagementScreen
import org.njarasoa.fijerena.feature.epgbrowser.TvEpgBrowserScreen
import org.njarasoa.fijerena.feature.episode.EpisodeSelectionScreen
import org.njarasoa.fijerena.feature.movie.MovieDetailsScreen
import org.njarasoa.fijerena.feature.player.TvPlayerScreen
import org.njarasoa.fijerena.feature.profile.ProfilePickerScreen
import org.njarasoa.fijerena.feature.provider.TvAddProviderScreen
import org.njarasoa.fijerena.feature.provider.TvProviderSelectionScreen
import org.njarasoa.fijerena.feature.safemode.NewerDataScreen
import org.njarasoa.fijerena.feature.safemode.SafeModeScreen
import org.njarasoa.fijerena.feature.search.SearchScreen
import org.njarasoa.fijerena.feature.settings.SettingsScreen
import org.njarasoa.fijerena.ui.components.rail.HideTvNavRail
import org.njarasoa.fijerena.ui.components.rail.LocalTvNavRail
import org.njarasoa.fijerena.ui.components.rail.RailItem
import org.njarasoa.fijerena.ui.components.rail.TvNavRailState

/**
 * TV-optimized navigation host with D-pad focus management.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    onThemeChanged: (String) -> Unit = {},
    onUiStyleChanged: (String) -> Unit = {},
    onUiScaleChanged: (Float) -> Unit = {},
) {
    val context = LocalContext.current

    // Profile, source and live-sync switches start over from Home: every section's saved place may
    // hold the previous one's repository, so it goes too.
    fun clearSectionStacks() = TvSection.entries.forEach { navController.clearBackStack(it.route) }

    // Live sync moved this device off a profile or a provider another device deleted: every screen
    // may hold the old one's repository, so start over from home — what the profile picker does.
    LaunchedEffect(Unit) {
        org.njarasoa.fijerena.core.ui.di.AppContainer
            .getInstance(context)
            .externalSwitches
            .collect {
                // Before the graph exists (very first frames) there is nothing to rebuild.
                if (navController.currentBackStackEntry == null) return@collect
                clearSectionStacks()
                navController.navigate(Screen.ContentTypeSelection) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
    }
    val accountManager = remember { AccountManager(context.applicationContext) }
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var hasProvider by remember { mutableStateOf<Boolean?>(null) }
    var initializationComplete by remember { mutableStateOf(false) }
    // "Who's watching?" on every launch once a second profile exists — the TV is shared, so the
    // last person's profile is a poor guess (docs/plans/archive/20260929_live-sync-plan.md → User profiles).
    var pickProfileAtLaunch by remember { mutableStateOf(false) }
    var hasAutoSkippedSingleContentType by rememberSaveable { mutableStateOf(false) }

    // The navigation rail (docs/plans/20261008_tv-nav-rail-plan.md): drawn over the NavHost, given to
    // every screen as LocalTvNavRail.
    val rail = remember { TvNavRailState() }
    // A fresh Live TV opens its preview on this channel (see openSection): read once by the
    // section's first composition. Lost with the process, which only means browse instead.
    var liveTvOpenOn by remember { mutableStateOf<String?>(null) }

    // Settings and its screens on top aren't a section's place: dropped before a pick, not saved.
    fun popSettingsTree() {
        while (navController.currentDestination?.isSettingsTree() == true &&
            navController.previousBackStackEntry != null &&
            navController.popBackStack()
        ) {
            Unit
        }
    }

    // Leaving Live TV from under the TV Guide its full screen opened: its preview layer, saved open,
    // stays closed when the section comes back (TvCategoryGridScreen's closeSavedLivePreview).
    // Nothing to do with Live TV's browse on top: the layer is closed while the rail shows.
    fun closeSavedLivePreview() {
        if (navController.currentDestination?.hasRoute<Screen.LiveTvTab>() == true) return
        try {
            navController.getBackStackEntry(Screen.LiveTvTab).savedStateHandle[CLOSE_SAVED_LIVE_PREVIEW] = true
        } catch (_: IllegalArgumentException) {
            // Live TV isn't on the back stack.
        }
    }

    // Each section keeps its place: the section's stack above Home is saved under its root, and the
    // section picked comes back where it was left, or opens fresh. Home stays the start and is never
    // popped. What Home opened outside the sections (Search, a title) isn't a place: not saved.
    fun showSection(section: TvSection) {
        popSettingsTree()
        closeSavedLivePreview()
        val saveSection = navController.currentSection() != null
        navController.navigate(section.route) {
            popUpTo(Screen.ContentTypeSelection) { saveState = saveSection }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Live TV opened fresh is the browse screen with the preview as a layer over it, open on the
    // last channel (LT7) — so Back from the preview lands on a real browse screen, same as Movies
    // and TV Shows. A restored Live TV ignores it: coming back never starts video on its own.
    // No last channel, no preview: it would render an empty pane with no way to reach the
    // categories, stranding a first-run user; OK on a channel in browse opens it anyway.
    fun openSection(section: TvSection) {
        if (section == TvSection.LIVE_TV) {
            coroutineScope.launch {
                liveTvOpenOn = AppContainer.getInstance(context).getMediaRepository().getLastItemId(ContentType.LIVE_TV)
                showSection(section)
            }
        } else {
            showSection(section)
        }
    }

    // The rail's picks (docs/plans/20261008_tv-nav-rail-plan.md → Phase 0 findings → Navigation rules).
    fun selectRailItem(item: RailItem) {
        val section = TvSection.of(item)
        when {
            item == RailItem.PROFILE -> {
                navController.navigateOnce(Screen.ProfilePicker)
            }

            item == RailItem.HOME -> {
                popSettingsTree()
                closeSavedLivePreview()
                navController.popBackStack(
                    Screen.ContentTypeSelection,
                    inclusive = false,
                    saveState = navController.currentSection() != null,
                )
            }

            // The current section's Search; everything from Home, Settings and the rest.
            item == RailItem.SEARCH -> {
                if (rail.current != RailItem.SEARCH) {
                    val contentType = rail.current?.let(TvSection::of)?.contentType ?: SearchViewModel.CONTENT_TYPE_ALL
                    navController.navigateOnce(Screen.Search(contentType))
                }
            }

            // On top of where you are, or back to its start from one of its screens.
            item == RailItem.SETTINGS -> {
                if (!navController.popBackStack(Screen.Settings, inclusive = false)) {
                    navController.navigate(Screen.Settings) { launchSingleTop = true }
                }
            }

            // The section you are in: back to its start.
            section != null && section == navController.currentSection() -> {
                closeSavedLivePreview()
                navController.popBackStack(section.route, inclusive = false)
            }

            section != null -> {
                openSection(section)
            }
        }
    }

    // Async initialization — use cached provider flag for instant start destination,
    // then verify with Room DB in background
    suspend fun initializeStartup() {
        val providerRepo = ProviderRepository(context.applicationContext)
        val cachedHasProvider = appSettings.hasProviderCache

        if (cachedHasProvider) {
            // Fast path: trust cache for immediate UI, verify with DB
            val providerCount = providerRepo.getProviderCount()
            hasProvider = providerCount > 0
            appSettings.hasProviderCache = providerCount > 0
        } else {
            // Cold start or no providers — check DB
            // Migrate legacy AccountManager credentials to Room if needed
            if (providerRepo.getProviderCount() == 0) {
                val legacyCreds = accountManager.exportForMigration()
                if (legacyCreds != null) {
                    val (url, username, password) = legacyCreds
                    val name = appSettings.providerName
                    providerRepo.addProvider(name, url, username, password)
                }
            }

            val providerCount = providerRepo.getProviderCount()
            hasProvider = providerCount > 0
            appSettings.hasProviderCache = providerCount > 0
        }
        pickProfileAtLaunch = ProfileRepository(context.applicationContext).count() > 1
        initializationComplete = true
    }

    LaunchedEffect(Unit) {
        // A providers.db from a newer build can't be opened: same skip, its own screen.
        if (SafeMode.isActive || ProvidersDbGuard.isBlocked) {
            // Crash-loop safe mode: none of initializeStartup()'s provider lookups, migration or
            // orphan sweep — any of them may be what kept crashing. The safe-mode screen is the
            // start destination.
            initializationComplete = true
        } else {
            initializeStartup()
        }
    }

    val isAuthenticated by authViewModel.authResponse.collectAsStateWithLifecycle()

    // Floor on the loading screen's time so its animation is actually seen — see
    // APP_LOADING_MIN_MS for why nothing in front of it can move.
    var minimumShownElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(APP_LOADING_MIN_MS)
        minimumShownElapsed = true
    }

    val startDestination =
        remember(initializationComplete, hasProvider) {
            if (!initializationComplete) {
                null
            } else if (ProvidersDbGuard.isBlocked) {
                Screen.NewerData
            } else if (SafeMode.isActive) {
                Screen.SafeMode
            } else if (hasProvider == true && pickProfileAtLaunch) {
                Screen.ProfilePicker
            } else if (hasProvider == true) {
                Screen.ContentTypeSelection
            } else {
                Screen.Settings
            }
        }

    // Auto-restore Xtream session if the active provider is Xtream
    LaunchedEffect(initializationComplete, hasProvider, isAuthenticated) {
        if (initializationComplete && hasProvider == true && isAuthenticated == null) {
            val providerRepo = ProviderRepository(context.applicationContext)
            val activeProvider = providerRepo.getActiveProvider()
            if (activeProvider != null && activeProvider.type == "XTREAM") {
                // Use AppContainer to get the shared repository instance.
                // AppContainer.getMediaRepository() now handles connect() internally.
                val repo =
                    org.njarasoa.fijerena.core.ui.di.AppContainer
                        .getInstance(context)
                        .getMediaRepository(activeProvider.id)

                if (repo.isConnected()) {
                    // Update AuthViewModel for UI consistency
                    val authResponse = accountManager.getAuthResponse()
                    val credentials = accountManager.getCredentials()
                    if (authResponse != null && credentials != null) {
                        authViewModel.setAuthSession(authResponse, credentials.url)
                    }
                }
            }
        }
    }

    // A category list — a section's browse, or one pushed from Search, a guide or a title — with
    // the routes its rows and header open.
    @Composable
    fun CategoryGrid(
        contentType: String,
        initialCategoryId: String? = null,
        initialStreamId: String? = null,
        showPreviewPane: Boolean = true,
        closeSavedLivePreview: Boolean = false,
    ) {
        TvCategoryGridScreen(
            contentType = contentType,
            initialCategoryId = initialCategoryId,
            initialStreamId = initialStreamId,
            showPreviewPane = showPreviewPane,
            closeSavedLivePreview = closeSavedLivePreview,
            onStreamSelected = { itemId, streamName, categoryId, target ->
                when (target) {
                    // Continue Watching: the card represents the show, not the
                    // episode — open episode selection with the last-watched
                    // episode's detail/resume panel already up.
                    is BrowseTarget.Series -> {
                        navController.navigateOnce(
                            Screen.EpisodeSelection(
                                seriesId = target.seriesId.raw,
                                seriesName = streamName,
                                categoryId = categoryId,
                                initialEpisodeId = target.resumeEpisodeId?.raw,
                            ),
                        )
                    }

                    // The card represents one episode — play it, whether or not it
                    // can name the show it belongs to.
                    is BrowseTarget.Episode -> {
                        navController.navigateOnce(
                            Screen.Player(
                                streamId = target.episodeId.raw,
                                streamName = streamName,
                                categoryId = categoryId,
                                contentType = ContentType.TV_SHOWS,
                                episodeId = target.episodeId.raw,
                                episodeExtension = target.extension,
                                seriesId = target.seriesId?.raw,
                                seriesName = target.seriesName,
                            ),
                        )
                    }

                    is BrowseTarget.Movie -> {
                        navController.navigateOnce(
                            Screen.MovieDetails(
                                movieId = target.movieId,
                                movieName = streamName,
                                categoryId = categoryId,
                            ),
                        )
                    }

                    // Live TV: land on the preview pane, not full-screen. Browse opens
                    // its preview as a layer itself (TvCategoryGridScreen, LT7); this
                    // is only the channel panel's fallback for a row it cannot resolve.
                    is BrowseTarget.Channel -> {
                        navController.navigateOnce(
                            Screen.CategoryList(
                                contentType = contentType,
                                initialCategoryId = categoryId,
                                initialStreamId = target.streamId,
                            ),
                        )
                    }

                    // Browsed into by the list screen itself; it never reaches nav.
                    is BrowseTarget.CategoryRef -> {
                        Unit
                    }
                }
            },
            onSearchClick = {
                navController.navigateOnce(Screen.Search(contentType))
            },
            onEpgClick = { categoryId, categoryName, focusChannelId ->
                // The TV Guide for a category (the header button), or for the playing
                // channel's list from the player's Guide button, on that channel's row.
                navController.navigateOnce(
                    Screen.EpgGuide(
                        categoryId = categoryId,
                        categoryName = categoryName,
                        focusChannelId = focusChannelId,
                    ),
                )
            },
            onBack = {
                // A single pop always lands on whatever pushed this entry — Home under a
                // section's browse, or the search/EPG screen underneath a
                // search/EPG-originated preview. Live TV browse's own preview layer
                // closes back to browse before this is reached (LT7).
                navController.popBackStack()
            },
            onHome = { navController.popBackStack(Screen.ContentTypeSelection, inclusive = false) },
        )
    }

    // A section's start: its browse, with the preview a layer over Live TV's (showPreviewPane
    // false). Back here goes to the rail instead of Home (the Google TV pattern); Back in the rail
    // goes Home, keeping the section's place. The preview layer's own Back comes first while it is
    // open (its BackHandler is registered later), as do the screen's dialogs.
    @Composable
    fun SectionBrowse(
        section: TvSection,
        entry: NavBackStackEntry,
    ) {
        val openOn = remember { liveTvOpenOn.takeIf { section == TvSection.LIVE_TV }?.also { liveTvOpenOn = null } }
        val closeSavedPreview = remember { entry.savedStateHandle.remove<Boolean>(CLOSE_SAVED_LIVE_PREVIEW) == true }
        BackHandler(enabled = rail.visible) {
            if (rail.expanded || !rail.focusRail()) {
                navController.popBackStack(Screen.ContentTypeSelection, inclusive = false, saveState = true)
            }
        }
        CategoryGrid(
            contentType = section.contentType,
            initialStreamId = openOn,
            showPreviewPane = false,
            closeSavedLivePreview = closeSavedPreview,
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape,
    ) {
        if (initializationComplete && startDestination != null && minimumShownElapsed) {
            CompositionLocalProvider(LocalTvNavRail provides rail) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        // Declared, not inherited: with none of these set, navigation-compose applies its
                        // own default of fadeIn/fadeOut over 700ms, during which both the outgoing and
                        // incoming screen are composed and drawn at once. That is a long time to hold two
                        // full screens live on a Shield, and Live TV pushes two entries back to back so it
                        // paid for it twice. Fade rather than mobile's slide: a slide has to lay out both
                        // screens off-axis, and on a 10-foot D-pad UI it reads as drift rather than
                        // direction.
                        enterTransition = { fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs)) },
                        exitTransition = { fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs)) },
                        popExitTransition = { fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs)) },
                    ) {
                        composable<Screen.ContentTypeSelection> {
                            // Prevent back button from exiting the app on the root screen
                            BackHandler {}
                            // A tile opens its section where it was left — the rail's place for it too.
                            val navigateToContentType: (org.njarasoa.fijerena.core.navigation.ContentType) -> Unit = { contentType ->
                                TvSection.of(contentType.name)?.let(::openSection)
                            }
                            ContentTypeSelectionScreen(
                                onContentTypeSelected = navigateToContentType,
                                onCapabilitiesResolved = { supportedTypes ->
                                    // Skip the picker tap entirely when the active provider only supports
                                    // one content type — but only on the very first resolve per NavHost
                                    // lifetime, so Back-navigation into this screen later still lands on a
                                    // real, interactive Home (Settings/Search/EPG/provider-switch all live
                                    // here and nowhere else).
                                    if (!hasAutoSkippedSingleContentType) {
                                        hasAutoSkippedSingleContentType = true
                                        if (supportedTypes.size == 1) {
                                            org.njarasoa.fijerena.core.navigation.ContentType
                                                .fromString(supportedTypes.first())
                                                ?.let(navigateToContentType)
                                        }
                                    }
                                },
                                onSettings = {
                                    navController.navigateOnce(Screen.Settings)
                                },
                                onChooseProfile = {
                                    navController.navigateOnce(Screen.ProfilePicker)
                                },
                                onSignInRequired = { providerId ->
                                    // Plain navigate, not navigateOnce: this fires from home's load, often
                                    // while home is still entering after a profile switch — not RESUMED yet,
                                    // so navigateOnce's double-tap guard would drop it (and the prompt is
                                    // only offered once per process).
                                    navController.navigate(Screen.AddProvider(editId = providerId)) { launchSingleTop = true }
                                },
                                onSearch = {
                                    navController.navigateOnce(Screen.Search("ALL"))
                                },
                                onEpgBrowser = {
                                    navController.navigateOnce(Screen.EpgBrowser())
                                },
                                // A Live row channel opens its preview, zapping through the list the card
                                // came from (TV home overhaul plan, Phase 4); Back pops back to Home.
                                onLiveChannelSelected = { streamId, listId ->
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = ContentType.LIVE_TV,
                                            initialCategoryId = listId,
                                            initialStreamId = streamId,
                                        ),
                                    )
                                },
                                // Same routes as the lists' onStreamSelected (item.id is the movie or series id).
                                onFavoriteSelected = { item, favoriteType ->
                                    if (favoriteType == ContentType.TV_SHOWS) {
                                        navController.navigateOnce(
                                            Screen.EpisodeSelection(
                                                seriesId = item.id,
                                                seriesName = item.name,
                                                categoryId = item.categoryId,
                                            ),
                                        )
                                    } else {
                                        navController.navigateOnce(
                                            Screen.MovieDetails(movieId = item.id, movieName = item.name, categoryId = item.categoryId),
                                        )
                                    }
                                },
                                onContinueWatchingSelected = { item ->
                                    // Same dispatch as CategoryList's Recent row (below) — a shelf card is
                                    // just another resumable entry, and should route exactly like one.
                                    when (val target = item.target) {
                                        is BrowseTarget.Series -> {
                                            navController.navigateOnce(
                                                Screen.EpisodeSelection(
                                                    seriesId = target.seriesId.raw,
                                                    seriesName = item.name,
                                                    categoryId = item.categoryId,
                                                    initialEpisodeId = target.resumeEpisodeId?.raw,
                                                ),
                                            )
                                        }

                                        is BrowseTarget.Episode -> {
                                            navController.navigateOnce(
                                                Screen.Player(
                                                    streamId = target.episodeId.raw,
                                                    streamName = item.name,
                                                    categoryId = item.categoryId,
                                                    contentType = ContentType.TV_SHOWS,
                                                    episodeId = target.episodeId.raw,
                                                    episodeExtension = target.extension,
                                                    seriesId = target.seriesId?.raw,
                                                    seriesName = target.seriesName,
                                                ),
                                            )
                                        }

                                        is BrowseTarget.Movie -> {
                                            navController.navigateOnce(
                                                Screen.MovieDetails(
                                                    movieId = target.movieId,
                                                    movieName = item.name,
                                                    categoryId = item.categoryId,
                                                ),
                                            )
                                        }

                                        is BrowseTarget.Channel, is BrowseTarget.CategoryRef -> {
                                            Unit
                                        }
                                    }
                                },
                            )
                        }

                        composable<Screen.SyncSettings> {
                            org.njarasoa.fijerena.feature.settings
                                .SyncSettingsScreen()
                        }

                        composable<Screen.NewerData> {
                            HideTvNavRail()
                            NewerDataScreen()
                        }
                        composable<Screen.SafeMode> {
                            HideTvNavRail()
                            SafeModeScreen(onShowDiagnostics = { navController.navigateOnce(Screen.Diagnostics) })
                        }
                        composable<Screen.Diagnostics> {
                            org.njarasoa.fijerena.feature.settings
                                .DiagnosticsScreen()
                        }
                        composable<Screen.DeviceInfo> {
                            org.njarasoa.fijerena.feature.settings
                                .DeviceInfoScreen()
                        }

                        composable<Screen.EpgBrowser> { backStackEntry ->
                            val browserScreen = backStackEntry.toRoute<Screen.EpgBrowser>()
                            TvEpgBrowserScreen(
                                categoryId = browserScreen.categoryId,
                                categoryName = browserScreen.categoryName,
                                onBack = { navController.navigateUp() },
                                onGuideSources = { id ->
                                    navController.navigateOnce(Screen.EpgManagement(providerId = id))
                                },
                                // Its TV Guide opens on Recent (D7); Back from the grid returns here.
                                onTvGuide = { categoryId, categoryName ->
                                    navController.navigateOnce(Screen.EpgGuide(categoryId = categoryId, categoryName = categoryName))
                                },
                                onNavigateToPlayer = { streamId, _, categoryId ->
                                    // Land on the preview pane, not full-screen — see LiveTvSplitLayout.
                                    // Pushing (not popUpTo) a new CategoryList entry means Back from the
                                    // preview pops back here for free via normal nav-stack semantics.
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = ContentType.LIVE_TV,
                                            initialCategoryId = categoryId,
                                            initialStreamId = streamId,
                                        ),
                                    )
                                },
                            )
                        }

                        composable<Screen.CategoryList> { backStackEntry ->
                            val categoryListScreen = backStackEntry.toRoute<Screen.CategoryList>()
                            CategoryGrid(
                                contentType = categoryListScreen.contentType,
                                initialCategoryId = categoryListScreen.initialCategoryId,
                                initialStreamId = categoryListScreen.initialStreamId,
                                showPreviewPane = categoryListScreen.showPreviewPane,
                            )
                        }

                        // The sections' roots: their browse screen, saved with the section's stack.
                        composable<Screen.LiveTvTab> { backStackEntry ->
                            SectionBrowse(TvSection.LIVE_TV, backStackEntry)
                        }
                        composable<Screen.MoviesTab> { backStackEntry ->
                            SectionBrowse(TvSection.MOVIES, backStackEntry)
                        }
                        composable<Screen.TvShowsTab> { backStackEntry ->
                            SectionBrowse(TvSection.TV_SHOWS, backStackEntry)
                        }

                        composable<Screen.Search> { backStackEntry ->
                            val searchScreen = backStackEntry.toRoute<Screen.Search>()
                            SearchScreen(
                                contentType = searchScreen.contentType,
                                onStreamSelected = { itemId, streamName, categoryId, streamContentType ->
                                    when (streamContentType) {
                                        ContentType.TV_SHOWS -> {
                                            navController.navigateOnce(
                                                Screen.EpisodeSelection(
                                                    seriesId = itemId,
                                                    seriesName = streamName,
                                                    categoryId = categoryId,
                                                ),
                                            )
                                        }

                                        ContentType.MOVIES -> {
                                            navController.navigateOnce(
                                                Screen.MovieDetails(
                                                    movieId = itemId,
                                                    movieName = streamName,
                                                    categoryId = categoryId,
                                                ),
                                            )
                                        }

                                        else -> {
                                            // Live TV: land on the preview pane, not full-screen. Pushing
                                            // (not popUpTo) means Back from the preview pops back to these
                                            // search results for free via normal nav-stack semantics.
                                            navController.navigateOnce(
                                                Screen.CategoryList(
                                                    contentType = streamContentType,
                                                    initialCategoryId = categoryId,
                                                    initialStreamId = itemId,
                                                ),
                                            )
                                        }
                                    }
                                },
                                onCategorySelected = { categoryId, contentType ->
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = contentType,
                                            initialCategoryId = categoryId,
                                        ),
                                    )
                                },
                                onBack = { navController.navigateUp() },
                            )
                        }

                        composable<Screen.MovieDetails> { backStackEntry ->
                            val movieDetailsScreen = backStackEntry.toRoute<Screen.MovieDetails>()
                            MovieDetailsScreen(
                                movieId = movieDetailsScreen.movieId,
                                movieName = movieDetailsScreen.movieName,
                                categoryId = movieDetailsScreen.categoryId,
                                onPlayMovie = { movieId, movieName, extension, startFromBeginning ->
                                    navController.navigateOnce(
                                        Screen.Player(
                                            streamId = movieId,
                                            streamName = movieName,
                                            categoryId = movieDetailsScreen.categoryId,
                                            contentType = ContentType.MOVIES,
                                            episodeExtension = extension,
                                            startFromBeginning = startFromBeginning,
                                        ),
                                    )
                                },
                                onCategorySelected = { categoryId ->
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = ContentType.MOVIES,
                                            initialCategoryId = categoryId,
                                        ),
                                    )
                                },
                                onBack = {
                                    navController.navigateUp()
                                },
                                onRelatedTitleSelected = { related ->
                                    navController.navigateOnce(
                                        Screen.MovieDetails(
                                            movieId = related.id,
                                            movieName = related.name,
                                            categoryId = related.categoryId,
                                        ),
                                    )
                                },
                            )
                        }

                        composable<Screen.EpisodeSelection> { backStackEntry ->
                            val episodeSelectionScreen = backStackEntry.toRoute<Screen.EpisodeSelection>()
                            EpisodeSelectionScreen(
                                seriesId = episodeSelectionScreen.seriesId,
                                seriesName = episodeSelectionScreen.seriesName,
                                categoryId = episodeSelectionScreen.categoryId,
                                initialEpisodeId = episodeSelectionScreen.initialEpisodeId,
                                onEpisodeSelected = { episodeId, episodeTitle, extension, startFromBeginning ->
                                    navController.navigateOnce(
                                        Screen.Player(
                                            streamId = episodeId,
                                            streamName = episodeTitle,
                                            categoryId = episodeSelectionScreen.categoryId,
                                            contentType = ContentType.TV_SHOWS,
                                            episodeId = episodeId,
                                            episodeExtension = extension,
                                            seriesId = episodeSelectionScreen.seriesId,
                                            seriesName = episodeSelectionScreen.seriesName,
                                            startFromBeginning = startFromBeginning,
                                        ),
                                    )
                                },
                                onCategorySelected = { categoryId ->
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = ContentType.TV_SHOWS,
                                            initialCategoryId = categoryId,
                                        ),
                                    )
                                },
                                onBack = {
                                    navController.navigateUp()
                                },
                                onRelatedTitleSelected = { related ->
                                    navController.navigateOnce(
                                        Screen.EpisodeSelection(
                                            seriesId = related.id,
                                            seriesName = related.name,
                                            categoryId = related.categoryId,
                                        ),
                                    )
                                },
                            )
                        }

                        composable<Screen.EpgGuide> { backStackEntry ->
                            val epgScreen = backStackEntry.toRoute<Screen.EpgGuide>()
                            TvEpgGuideScreen(
                                categoryId = epgScreen.categoryId,
                                categoryName = epgScreen.categoryName,
                                focusChannelId = epgScreen.focusChannelId,
                                onProgramSelected = { _, channel ->
                                    // Land on the preview pane, not full-screen. Pushing (not popUpTo)
                                    // means Back from the preview pops back to the EPG guide for free.
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = ContentType.LIVE_TV,
                                            initialCategoryId = channel.categoryId,
                                            initialStreamId = channel.id,
                                        ),
                                    )
                                },
                                onChannelSelected = { streamId, _, categoryId ->
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = ContentType.LIVE_TV,
                                            initialCategoryId = categoryId,
                                            initialStreamId = streamId,
                                        ),
                                    )
                                },
                                // One search (G-9): "Search the guide", filtered to this guide's channels.
                                onSearch = {
                                    navController.navigateOnce(
                                        Screen.EpgBrowser(categoryId = epgScreen.categoryId, categoryName = epgScreen.categoryName),
                                    )
                                },
                                onBack = {
                                    navController.navigateUp()
                                },
                            )
                        }

                        composable<Screen.Player> { backStackEntry ->
                            HideTvNavRail()
                            val playerScreen = backStackEntry.toRoute<Screen.Player>()
                            TvPlayerScreen(
                                streamId = playerScreen.streamId,
                                streamName = playerScreen.streamName,
                                categoryId = playerScreen.categoryId,
                                contentType = playerScreen.contentType,
                                episodeId = playerScreen.episodeId,
                                episodeExtension = playerScreen.episodeExtension,
                                seriesId = playerScreen.seriesId,
                                seriesName = playerScreen.seriesName,
                                startFromBeginning = playerScreen.startFromBeginning,
                                onBack = {
                                    navController.navigateUp()
                                },
                                onHome = { navController.popBackStack(Screen.ContentTypeSelection, inclusive = false) },
                            )
                        }

                        composable<Screen.AddProvider> { backStackEntry ->
                            HideTvNavRail()
                            val addProviderScreen = backStackEntry.toRoute<Screen.AddProvider>()
                            TvAddProviderScreen(
                                editId = addProviderScreen.editId,
                                onBack = {
                                    navController.navigateUp()
                                },
                                onSuccess = {
                                    appSettings.hasProviderCache = true
                                    navController.navigateUp()
                                },
                                onGuideSources = { id ->
                                    navController.navigateOnce(Screen.EpgManagement(providerId = id))
                                },
                            )
                        }

                        composable<Screen.ProviderSelection> {
                            TvProviderSelectionScreen(
                                onProviderSelected = { provider ->
                                    coroutineScope.launch {
                                        val providerRepo = ProviderRepository(context.applicationContext)
                                        providerRepo.pickProvider(provider.id)

                                        // Clear AppContainer caches to force a fresh repository for the new provider
                                        val container =
                                            org.njarasoa.fijerena.core.ui.di.AppContainer
                                                .getInstance(context)
                                        container.clearAllCaches()

                                        // For Xtream providers, restore session to update AuthViewModel
                                        if (provider.type == "XTREAM") {
                                            val repo = container.getMediaRepository(provider.id)
                                            if (repo.isConnected()) {
                                                val authResponse = accountManager.getAuthResponse()
                                                val credentials = accountManager.getCredentials()
                                                if (authResponse != null && credentials != null) {
                                                    authViewModel.setAuthSession(authResponse, credentials.url)
                                                }
                                            }
                                        }

                                        // From the root, like a profile switch: every screen below holds
                                        // a repository clearAllCaches() just closed, so Back used to walk
                                        // into the old provider's Home, where writes went nowhere. See
                                        // docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-18.
                                        clearSectionStacks()
                                        navController.navigateOnce(Screen.ContentTypeSelection) {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                        }
                                    }
                                },
                                onAddProvider = {
                                    navController.navigateOnce(Screen.AddProvider())
                                },
                                onEditProvider = { id ->
                                    navController.navigateOnce(Screen.AddProvider(editId = id))
                                },
                                onManageEpg = { id ->
                                    navController.navigateOnce(Screen.EpgManagement(providerId = id))
                                },
                                onBack = {
                                    navController.navigateUp()
                                },
                            )
                        }

                        // EPG Management Screen (scoped to one provider)
                        composable<Screen.EpgManagement> { backStackEntry ->
                            val epgScreen = backStackEntry.toRoute<Screen.EpgManagement>()
                            TvEpgManagementScreen(
                                providerId = epgScreen.providerId,
                                onBack = { navController.navigateUp() },
                            )
                        }

                        composable<Screen.ProfilePicker> {
                            HideTvNavRail()
                            ProfilePickerScreen(
                                onProfileChosen = {
                                    // Every screen below may hold the previous profile's repository;
                                    // start over from home rather than returning to any of them.
                                    clearSectionStacks()
                                    navController.navigate(Screen.ContentTypeSelection) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                },
                            )
                        }

                        composable<Screen.Settings> {
                            // Prevent back from exiting if Settings is the start destination (no provider)
                            BackHandler(enabled = navController.previousBackStackEntry == null) {}
                            SettingsScreen(
                                onBack = {
                                    navController.navigateUp()
                                },
                                onThemeChanged = onThemeChanged,
                                onUiStyleChanged = onUiStyleChanged,
                                onUiScaleChanged = onUiScaleChanged,
                                onManageProviders = {
                                    navController.navigateOnce(Screen.ProviderSelection)
                                },
                                onLiveSync = {
                                    navController.navigateOnce(Screen.SyncSettings)
                                },
                                onDiagnostics = {
                                    navController.navigateOnce(Screen.Diagnostics)
                                },
                                onDeviceInfo = {
                                    navController.navigateOnce(Screen.DeviceInfo)
                                },
                                onProfileSwitched = {
                                    // As after the profile picker: every screen below may hold the previous
                                    // profile's repository, so start over from home.
                                    clearSectionStacks()
                                    navController.navigate(Screen.ContentTypeSelection) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                },
                                onProviderChanged = {
                                    coroutineScope.launch {
                                        val providerRepo = ProviderRepository(context.applicationContext)
                                        val activeProvider = providerRepo.getActiveProvider()

                                        val container =
                                            org.njarasoa.fijerena.core.ui.di.AppContainer
                                                .getInstance(context)
                                        container.clearAllCaches()

                                        if (activeProvider != null && activeProvider.type == "XTREAM") {
                                            val repo = container.getMediaRepository(activeProvider.id)
                                            if (repo.isConnected()) {
                                                val authResponse = accountManager.getAuthResponse()
                                                val credentials = accountManager.getCredentials()
                                                if (authResponse != null && credentials != null) {
                                                    authViewModel.setAuthSession(authResponse, credentials.url)
                                                }
                                            }
                                        }

                                        // From the root — see the provider-selection switch above.
                                        clearSectionStacks()
                                        navController.navigateOnce(Screen.ContentTypeSelection) {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    // After the NavHost, so it draws over the screen and its scrim covers it.
                    TvSectionRail(rail = rail, navController = navController, onSelect = ::selectRailItem)
                }
            }
        } else {
            // Provider lookup / credential migration / session restore — used to be a blank
            // window for however long that took.
            AppLoadingScreen()
        }
    }
}

/** Set on Live TV's root entry when a rail pick leaves it: see closeSavedLivePreview in [TvNavHost]. */
private const val CLOSE_SAVED_LIVE_PREVIEW = "closeSavedLivePreview"
