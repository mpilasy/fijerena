package org.njarasoa.fijerena.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.navigation.navigateOnce
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.components.APP_LOADING_MIN_MS
import org.njarasoa.fijerena.core.ui.components.AppLoadingScreen
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.category.MobileCategoryListScreen
import org.njarasoa.fijerena.feature.contentselection.MobileContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.epg.MobileEpgGuideScreen
import org.njarasoa.fijerena.feature.epg.MobileEpgManagementScreen
import org.njarasoa.fijerena.feature.epgbrowser.MobileEpgBrowserScreen
import org.njarasoa.fijerena.feature.episode.MobileEpisodeSelectionScreen
import org.njarasoa.fijerena.feature.movie.MobileMovieDetailsScreen
import org.njarasoa.fijerena.feature.player.MobilePlayerScreen
import org.njarasoa.fijerena.feature.profile.ProfilePickerScreen
import org.njarasoa.fijerena.feature.provider.MobileAddProviderScreen
import org.njarasoa.fijerena.feature.provider.MobileProviderSelectionScreen
import org.njarasoa.fijerena.feature.safemode.MobileNewerDataScreen
import org.njarasoa.fijerena.feature.safemode.MobileSafeModeScreen
import org.njarasoa.fijerena.feature.search.MobileSearchScreen
import org.njarasoa.fijerena.feature.settings.MobileProfileEditScreen
import org.njarasoa.fijerena.feature.settings.MobileSettingsScreen
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@Composable
fun MobileNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    onThemeChanged: (String) -> Unit = {},
    onUiStyleChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current

    // The bottom bar (docs/plans/20261007_phone-home-overhaul-plan.md → Bottom navigation bar).
    // The section types the active source has, as Home reports them: null until it has, and while
    // a Jellyfin source waits for its sign-in (Home reports nothing then), so the bar shows Home alone.
    var supportedTypes by rememberSaveable { mutableStateOf<List<String>?>(null) }
    // The Live TV tab's dock as its screen reports it: stopped before the bar leaves the tab (the
    // engine is Activity-scoped), and the bar hides while the video takes the screen.
    var stopLiveDock by remember { mutableStateOf<(() -> Unit)?>(null) }
    var liveDockCoversScreen by remember { mutableStateOf(false) }

    // Back-Stack Rule 4's switches also drop every tab's saved back stack: a later tab tap would
    // otherwise restore a screen holding the previous source's (closed) repository.
    fun clearTabStacks() {
        supportedTypes = null
        navController.clearBackStack<Screen.LiveTvTab>()
        navController.clearBackStack<Screen.MoviesTab>()
        navController.clearBackStack<Screen.TvShowsTab>()
    }

    // Each tab keeps its place: leaving one saves its back stack, coming back restores it. Tapping
    // the tab you're on pops back to its root. The bar only shows on a tab's root, so the tab
    // being left is the current destination's.
    fun selectTab(tab: MobileTab) {
        val current = MobileTab.rootedAt(navController.currentDestination)
        if (tab == current) {
            navController.popBackStack(tab.route, inclusive = false)
        } else {
            if (current == MobileTab.LIVE_TV) stopLiveDock?.invoke()
            if (tab == MobileTab.HOME) {
                navController.popBackStack(Screen.ContentTypeSelection, inclusive = false, saveState = true)
            } else {
                navController.navigate(tab.route) {
                    popUpTo(Screen.ContentTypeSelection) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // Live sync moved this device off a profile or a provider another device deleted: every screen
    // may hold the old one's repository, so start over from home — what the profile picker does.
    LaunchedEffect(Unit) {
        org.njarasoa.fijerena.core.ui.di.AppContainer
            .getInstance(context)
            .externalSwitches
            .collect {
                // Before the graph exists (very first frames) there is nothing to rebuild.
                if (navController.currentBackStackEntry == null) return@collect
                navController.navigate(Screen.ContentTypeSelection) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
                clearTabStacks()
            }
    }
    val accountManager = remember { AccountManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // Async initialization: migrate legacy creds, determine start destination
    var hasProvider by remember { mutableStateOf<Boolean?>(null) }
    var hasAutoSkippedSingleContentType by rememberSaveable { mutableStateOf(false) }

    suspend fun initializeStartup() {
        val providerRepo = ProviderRepository(context.applicationContext)
        if (providerRepo.getProviderCount() == 0) {
            // Run one-time migration from AccountManager to Room
            val legacyCreds = accountManager.exportForMigration()
            if (legacyCreds != null) {
                val (url, username, password) = legacyCreds
                val name =
                    org.njarasoa.fijerena.core.network
                        .AppSettings(context.applicationContext)
                        .providerName
                providerRepo.addProvider(name, url, username, password)
            }
        }
        hasProvider = providerRepo.getProviderCount() > 0
    }

    LaunchedEffect(Unit) {
        // A providers.db from a newer build can't be opened: same skip, its own screen.
        if (SafeMode.isActive || ProvidersDbGuard.isBlocked) {
            // Crash-loop safe mode: none of initializeStartup()'s provider lookups, migration or
            // orphan sweep — any of them may be what kept crashing. The safe-mode screen is the
            // start destination; hasProvider only needs to stop being null.
            hasProvider = false
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

    // Provider lookup / credential migration still running.
    if (hasProvider == null || !minimumShownElapsed) {
        AppLoadingScreen(logoSize = MobileDimensions.epgProgramMinWidth)
        return
    }

    val startDestination =
        if (ProvidersDbGuard.isBlocked) {
            Screen.NewerData
        } else if (SafeMode.isActive) {
            Screen.SafeMode
        } else if (hasProvider == true) {
            Screen.ContentTypeSelection
        } else {
            Screen.Settings
        }

    // Auto-restore Xtream session if the active provider is Xtream
    LaunchedEffect(hasProvider, isAuthenticated) {
        if (hasProvider == true && isAuthenticated == null) {
            val providerRepo = ProviderRepository(context.applicationContext)
            val activeProvider = providerRepo.getActiveProvider()
            if (activeProvider != null && activeProvider.type == "XTREAM") {
                // The shared repository instance; AppContainer.getMediaRepository() handles connect().
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

    // The bar shows on Home and on a section tab's root, nowhere else.
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentTab = MobileTab.rootedAt(currentEntry?.destination)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        // Screens keep handling the system bars themselves; with the bar up, its height (which
        // takes in the navigation bar) is padded off and consumed below.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null && !(currentTab == MobileTab.LIVE_TV && liveDockCoversScreen)) {
                MobileBottomBar(tabs = visibleTabs(supportedTypes), selected = currentTab, onSelect = ::selectTab)
            }
        },
    ) { barPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(barPadding).consumeWindowInsets(barPadding),
            // Screen.Player defines its own vertical slide (below) — these NavHost-wide defaults
            // apply to the *other* screen in a Player transition too (the one that didn't define
            // its own transitions), so without the Player check here that screen independently
            // slid sideways while Player slid vertically: two perpendicular animations running at
            // once. Fading instead of sliding the non-Player side lets Player's own vertical
            // motion read as the transition, rather than fighting a lateral one.
            enterTransition = {
                if (isPlayerTransition(initialState, targetState)) {
                    fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(CinemaAnimation.navTransitionMs),
                    ) + fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
                }
            },
            exitTransition = {
                if (isPlayerTransition(initialState, targetState)) {
                    fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
                } else {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(CinemaAnimation.navTransitionMs),
                    ) + fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
                }
            },
            popEnterTransition = {
                if (isPlayerTransition(initialState, targetState)) {
                    fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(CinemaAnimation.navTransitionMs),
                    ) + fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
                }
            },
            popExitTransition = {
                if (isPlayerTransition(initialState, targetState)) {
                    fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
                } else {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(CinemaAnimation.navTransitionMs),
                    ) + fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
                }
            },
        ) {
            composable<Screen.ContentTypeSelection> {
                // A section opens as its tab, so Home's own shortcuts land where the bar would.
                val navigateToContentType: (String) -> Unit = { contentType ->
                    MobileTab.forContentType(contentType)?.let(::selectTab)
                }
                MobileContentTypeSelectionScreen(
                    onContentTypeSelected = navigateToContentType,
                    onCapabilitiesResolved = { types ->
                        supportedTypes = types.toList()
                        // Skip the picker tap entirely when the active provider only supports
                        // one content type — but only on the very first resolve per NavHost
                        // lifetime, so Back-navigation into this screen later still lands on a
                        // real, interactive Home (Settings/Search/EPG/provider-switch all live
                        // here and nowhere else).
                        if (!hasAutoSkippedSingleContentType) {
                            hasAutoSkippedSingleContentType = true
                            if (types.size == 1) {
                                navigateToContentType(types.first())
                            }
                        }
                    },
                    // Home's own source picker switched source in place: no screen of the old one
                    // is on the stack, but the tabs' saved ones are.
                    onProviderChanged = { clearTabStacks() },
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
                    onContinueWatchingSelected = { item ->
                        // Same dispatch as Screen.CategoryList's onStreamSelected below — a shelf
                        // card is just another resumable entry, and should route exactly like one.
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

            composable<Screen.EpgBrowser> { backStackEntry ->
                val browserScreen = backStackEntry.toRoute<Screen.EpgBrowser>()
                MobileEpgBrowserScreen(
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
                        // Land on the docked mini-player, not full-screen — same parity as every
                        // other Live TV entry point.
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
                CategoryListDestination(navController, backStackEntry.toRoute<Screen.CategoryList>())
            }

            // The section tabs' roots: the section's list as Home opened it before the bar. Live
            // TV's reports its dock, which the bar stops on leaving the tab and hides under.
            composable<Screen.LiveTvTab> {
                CategoryListDestination(navController, Screen.CategoryList(ContentType.LIVE_TV)) { stopDock, coversScreen ->
                    stopLiveDock = stopDock
                    liveDockCoversScreen = coversScreen
                }
            }

            composable<Screen.MoviesTab> {
                CategoryListDestination(navController, Screen.CategoryList(ContentType.MOVIES))
            }

            composable<Screen.TvShowsTab> {
                CategoryListDestination(navController, Screen.CategoryList(ContentType.TV_SHOWS))
            }

            // Player Screen — vertical slide, not the lateral push used by list/detail screens:
            // opening full-screen video reads as rising up, not stepping sideways into a list.
            composable<Screen.Player>(
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(CinemaAnimation.navTransitionMs, easing = CinemaAnimation.StandardEasing),
                    ) + fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(CinemaAnimation.navTransitionMs, easing = CinemaAnimation.StandardEasing),
                    ) + fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(CinemaAnimation.navTransitionMs, easing = CinemaAnimation.StandardEasing),
                    ) + fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(CinemaAnimation.navTransitionMs, easing = CinemaAnimation.StandardEasing),
                    ) + fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
                },
            ) { backStackEntry ->
                val playerScreen = backStackEntry.toRoute<Screen.Player>()
                MobilePlayerScreen(
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
                val addProviderScreen = backStackEntry.toRoute<Screen.AddProvider>()
                MobileAddProviderScreen(
                    editId = addProviderScreen.editId,
                    onBack = {
                        navController.navigateUp()
                    },
                    onSuccess = {
                        navController.navigateUp()
                    },
                    onGuideSources = { id ->
                        navController.navigateOnce(Screen.EpgManagement(providerId = id))
                    },
                )
            }

            composable<Screen.ProviderSelection> {
                MobileProviderSelectionScreen(
                    onProviderSelected = { provider ->
                        coroutineScope.launch {
                            // Activate the selected provider in Room
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

                            // Navigate to content selection for all provider types
                            navController.navigateOnce(Screen.ContentTypeSelection) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                            clearTabStacks()
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

            composable<Screen.ProfilePicker> {
                ProfilePickerScreen(
                    onProfileChosen = {
                        // Every screen below may hold the previous profile's repository; start
                        // over from home rather than returning to any of them.
                        navController.navigate(Screen.ContentTypeSelection) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                        clearTabStacks()
                    },
                )
            }

            composable<Screen.ProfileEdit> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.ProfileEdit>()
                // Settings' ProfilesViewModel (the page only opens from Settings): a save or delete
                // runs on in its scope after this page is popped.
                val settingsEntry = remember(backStackEntry) { navController.getBackStackEntry<Screen.Settings>() }
                MobileProfileEditScreen(
                    profileId = route.profileId,
                    viewModel = viewModel(settingsEntry, factory = SettingsViewModelFactory(context)),
                    onBack = { navController.navigateUp() },
                    onProfileSwitched = {
                        // As after the profile picker: every screen below may hold the previous
                        // profile's repository, so start over from home.
                        navController.navigate(Screen.ContentTypeSelection) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                        clearTabStacks()
                    },
                )
            }

            composable<Screen.Settings> {
                MobileSettingsScreen(
                    onBack = {
                        navController.navigateUp()
                    },
                    onThemeChanged = onThemeChanged,
                    onUiStyleChanged = onUiStyleChanged,
                    onManageProviders = {
                        navController.navigateOnce(Screen.ProviderSelection)
                    },
                    onDiagnostics = {
                        navController.navigateOnce(Screen.Diagnostics)
                    },
                    onDeviceInfo = {
                        navController.navigateOnce(Screen.DeviceInfo)
                    },
                    onLiveSync = {
                        navController.navigateOnce(Screen.SyncSettings)
                    },
                    onEditSource = { id ->
                        navController.navigateOnce(Screen.AddProvider(editId = id))
                    },
                    onEditProfile = { id ->
                        navController.navigateOnce(Screen.ProfileEdit(profileId = id))
                    },
                    onProviderChanged = {
                        coroutineScope.launch {
                            val providerRepo = ProviderRepository(context.applicationContext)
                            val activeProvider = providerRepo.getActiveProvider()

                            // Clear AppContainer caches for the new provider
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

                            navController.navigateOnce(Screen.ContentTypeSelection) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                            clearTabStacks()
                        }
                    },
                )
            }

            composable<Screen.SyncSettings> {
                org.njarasoa.fijerena.feature.settings
                    .MobileSyncSettingsScreen(onBack = { navController.navigateUp() })
            }

            composable<Screen.NewerData> {
                MobileNewerDataScreen()
            }

            composable<Screen.SafeMode> {
                MobileSafeModeScreen(onShowDiagnostics = { navController.navigateOnce(Screen.Diagnostics) })
            }

            composable<Screen.Diagnostics> {
                org.njarasoa.fijerena.feature.settings
                    .MobileDiagnosticsScreen(onBack = { navController.navigateUp() })
            }

            composable<Screen.DeviceInfo> {
                org.njarasoa.fijerena.feature.settings
                    .MobileDeviceInfoScreen(onBack = { navController.navigateUp() })
            }

            composable<Screen.Search> { backStackEntry ->
                val searchScreen = backStackEntry.toRoute<Screen.Search>()
                MobileSearchScreen(
                    contentType = searchScreen.contentType,
                    onStreamSelected = { itemId, itemName, categoryId, contentType ->
                        when (contentType) {
                            ContentType.TV_SHOWS -> {
                                navController.navigateOnce(
                                    Screen.EpisodeSelection(
                                        seriesId = itemId,
                                        seriesName = itemName,
                                        categoryId = categoryId,
                                    ),
                                )
                            }

                            ContentType.MOVIES -> {
                                navController.navigateOnce(
                                    Screen.MovieDetails(
                                        movieId = itemId,
                                        movieName = itemName,
                                        categoryId = categoryId,
                                    ),
                                )
                            }

                            else -> {
                                // Live TV: land on the docked mini-player, not full-screen.
                                navController.navigateOnce(
                                    Screen.CategoryList(
                                        contentType = contentType,
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
                MobileMovieDetailsScreen(
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
                MobileEpisodeSelectionScreen(
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

            composable<Screen.EpgManagement> { backStackEntry ->
                val epgScreen = backStackEntry.toRoute<Screen.EpgManagement>()
                MobileEpgManagementScreen(
                    providerId = epgScreen.providerId,
                    onBack = { navController.navigateUp() },
                )
            }

            composable<Screen.EpgGuide> { backStackEntry ->
                val epgScreen = backStackEntry.toRoute<Screen.EpgGuide>()
                MobileEpgGuideScreen(
                    categoryId = epgScreen.categoryId,
                    categoryName = epgScreen.categoryName,
                    focusChannelId = epgScreen.focusChannelId,
                    onProgramSelected = { _, channel ->
                        // Land on the docked mini-player, not full-screen.
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
        }
    }
}

/**
 * A section's category list: a [Screen.CategoryList] entry, or a bottom-bar tab's root
 * ([Screen.LiveTvTab]…) with [route] built from its section. [onDockChanged]: see
 * [MobileCategoryListScreen].
 */
@Composable
private fun CategoryListDestination(
    navController: NavHostController,
    route: Screen.CategoryList,
    onDockChanged: (stopDock: (() -> Unit)?, coversScreen: Boolean) -> Unit = { _, _ -> },
) {
    MobileCategoryListScreen(
        contentType = route.contentType,
        initialCategoryId = route.initialCategoryId,
        initialStreamId = route.initialStreamId,
        onStreamSelected = { itemId, itemName, categoryId, contentType, target ->
            when (target) {
                // Continue Watching: the card stands for the show, so open episode
                // selection with the last-watched episode's panel already up.
                is BrowseTarget.Series -> {
                    navController.navigateOnce(
                        Screen.EpisodeSelection(
                            seriesId = target.seriesId.raw,
                            seriesName = itemName,
                            categoryId = categoryId,
                            initialEpisodeId = target.resumeEpisodeId?.raw,
                        ),
                    )
                }

                // The card stands for one episode — play it, whether or not it can
                // name the show it belongs to.
                is BrowseTarget.Episode -> {
                    navController.navigateOnce(
                        Screen.Player(
                            streamId = target.episodeId.raw,
                            streamName = itemName,
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
                            movieName = itemName,
                            categoryId = categoryId,
                        ),
                    )
                }

                // Live TV: unreachable in practice for a genuine stream tap —
                // MobileCategoryListScreen docks it locally instead of calling this
                // callback (mirrors TV's LiveTvChannelList.onStreamPromote
                // interception). Kept for the "not resolvable from the current list"
                // case, same as TV.
                is BrowseTarget.Channel -> {
                    navController.navigateOnce(Screen.Player(target.streamId, itemName, categoryId, contentType))
                }

                // Browsed into by the list screen itself; it never reaches nav.
                is BrowseTarget.CategoryRef -> {
                    Unit
                }
            }
        },
        onSearchClick = {
            navController.navigateOnce(Screen.Search(route.contentType))
        },
        onEpgClick = { categoryId, categoryName ->
            navController.navigateOnce(
                Screen.EpgGuide(
                    categoryId = categoryId,
                    categoryName = categoryName,
                ),
            )
        },
        onBack = {
            navController.navigateUp()
        },
        onHome = { navController.popBackStack(Screen.ContentTypeSelection, inclusive = false) },
        onDockChanged = onDockChanged,
    )
}

/** Either side of this transition is [Screen.Player] — see the NavHost default transitions above. */
private fun isPlayerTransition(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
): Boolean = initialState.destination.hasRoute<Screen.Player>() || targetState.destination.hasRoute<Screen.Player>()
