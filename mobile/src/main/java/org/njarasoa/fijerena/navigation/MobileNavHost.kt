package org.njarasoa.fijerena.navigation

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.APP_LOADING_MIN_MS
import org.njarasoa.fijerena.core.ui.components.AppLoadingScreen
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.category.MobileCategoryListScreen
import org.njarasoa.fijerena.feature.contentselection.ActiveSource
import org.njarasoa.fijerena.feature.contentselection.JellyfinSignInPanel
import org.njarasoa.fijerena.feature.contentselection.MobileSourceTopBar
import org.njarasoa.fijerena.feature.contentselection.loadActiveSource
import org.njarasoa.fijerena.feature.epg.MobileEpgGuideScreen
import org.njarasoa.fijerena.feature.epg.MobileEpgManagementScreen
import org.njarasoa.fijerena.feature.epgbrowser.MobileEpgBrowserScreen
import org.njarasoa.fijerena.feature.episode.MobileEpisodeSelectionScreen
import org.njarasoa.fijerena.feature.movie.MobileMovieDetailsScreen
import org.njarasoa.fijerena.feature.player.MobilePlayerScreen
import org.njarasoa.fijerena.feature.provider.MobileAddProviderScreen
import org.njarasoa.fijerena.feature.provider.MobileProviderSelectionScreen
import org.njarasoa.fijerena.feature.safemode.MobileNewerDataScreen
import org.njarasoa.fijerena.feature.safemode.MobileSafeModeScreen
import org.njarasoa.fijerena.feature.search.MobileSearchScreen
import org.njarasoa.fijerena.feature.settings.MobileProfileEditScreen
import org.njarasoa.fijerena.feature.settings.MobileSettingsScreen
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@Composable
fun MobileNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    onThemeChanged: (String) -> Unit = {},
    onUiStyleChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // No Home on the phone (docs/plans/archive/20261007_phone-home-overhaul-plan.md → Redesign: no Home on
    // the phone): the bottom bar's tabs are the active source's sections, read at startup, on each
    // visit to a tab's root and after every switch.
    var activeSource by remember { mutableStateOf<ActiveSource?>(null) }
    // The graph's start destination, decided once at startup.
    var openingTab by remember { mutableStateOf(MobileTab.LIVE_TV) }
    // Each Live TV dock as its screen reports it, by back-stack entry — the tab's root and any
    // CategoryList pushed deeper (a channel opened from Search or a guide): stopped before the bar
    // leaves the tab (the engine is Activity-scoped), and the bar hides while one takes the screen.
    // Keyed by entry so the screen leaving can't clear the report of the one arriving.
    val liveDocks = remember { mutableStateMapOf<String, Pair<(() -> Unit)?, Boolean>>() }

    fun stopLiveDocks() = liveDocks.values.forEach { (stop, _) -> stop?.invoke() }

    fun reportLiveDock(entryId: String): (stopDock: (() -> Unit)?, coversScreen: Boolean) -> Unit =
        { stopDock, coversScreen ->
            if (stopDock == null && !coversScreen) liveDocks.remove(entryId) else liveDocks[entryId] = stopDock to coversScreen
        }

    // Back-Stack Rule 4's switches also drop every tab's saved back stack: a later tab tap would
    // otherwise restore a screen holding the previous source's (closed) repository.
    fun clearTabStacks() {
        navController.clearBackStack<Screen.LiveTvTab>()
        navController.clearBackStack<Screen.MoviesTab>()
        navController.clearBackStack<Screen.TvShowsTab>()
    }

    // Each tab keeps its place: leaving one saves its back stack, coming back restores it. Tapping
    // the tab you're on pops back to its root. The bar only shows on a tab's root, so the tab
    // being left is the current destination's. Only one tab's stack is ever on the back stack, so
    // Back on a tab's root leaves the app.
    fun selectTab(tab: MobileTab) {
        val current = navController.currentTab()
        if (tab == current) {
            // From anywhere inside the tab: back to its start, the screens above dropped.
            navController.popBackStack(tab.route, inclusive = false)
        } else {
            stopLiveDocks()
            navController.navigate(tab.route) {
                popUpTo(navController.graph.id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // A Search result opens in its own section's tab: a film picked from a search started on Live TV
    // opens in Movies, with Movies highlighted and Back going to the Movies tab. The tab left is cut
    // back to its root first, so its saved place doesn't keep the search. Same section, or a section
    // the bar doesn't show (a single-section source): opened where it is, as before.
    fun openInSectionTab(
        contentType: String,
        screen: Screen,
    ) {
        val target = MobileTab.entries.firstOrNull { it.contentType == contentType }
        val current = navController.currentTab()
        val tabs = visibleTabs(activeSource?.sections)
        if (target == null || current == null || target == current || target !in tabs) {
            navController.navigateOnce(screen)
            return
        }
        navController.popBackStack(current.route, inclusive = false)
        selectTab(target)
        navController.navigate(screen)
    }

    // A tab root's top bar leaves it: the Live TV dock is stopped first, as the bar does.
    fun leaveTabRoot(go: () -> Unit) {
        stopLiveDocks()
        go()
    }

    // Starts over on [tab]'s root as the only screen, every tab's saved place dropped.
    fun restartOn(tab: MobileTab) {
        stopLiveDocks()
        clearTabStacks()
        navController.navigate(tab.route) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // Back-Stack Rule 4's switches (source, profile, live sync, Settings' source change): every
    // screen may hold the previous one's repository, so start over on the new profile's last tab,
    // or the first the source has.
    suspend fun startOver() {
        val source = loadActiveSource(context)
        activeSource = source
        restartOn(startTab(appSettings.lastTab, source?.sections))
    }

    // Each visit to a tab's root re-reads the source, as Home did: back from signing in to it, or
    // from renaming it.
    fun refreshSource() {
        coroutineScope.launch { activeSource = loadActiveSource(context, known = activeSource) }
    }

    // Remote Stop (live sync) leaves the screen that was playing for the Live TV tab's root — the
    // start tab when the source has no Live TV.
    fun openLiveTvRoot() {
        val types = activeSource?.sections
        val tab = if (types == null || ContentType.LIVE_TV in types) MobileTab.LIVE_TV else startTab(appSettings.lastTab, types)
        if (!navController.popBackStack(tab.route, inclusive = false)) {
            navController.clearBackStack(tab.route)
            navController.navigate(tab.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Live sync moved this device off a profile or a provider another device deleted: every screen
    // may hold the old one's repository, so start over — what the profile picker does.
    LaunchedEffect(Unit) {
        org.njarasoa.fijerena.core.ui.di.AppContainer
            .getInstance(context)
            .externalSwitches
            .collect {
                // Before the graph exists (very first frames) there is nothing to rebuild.
                if (navController.currentBackStackEntry == null) return@collect
                startOver()
            }
    }
    val accountManager = remember { AccountManager(context.applicationContext) }

    // Async initialization: migrate legacy creds, determine start destination
    var hasProvider by remember { mutableStateOf<Boolean?>(null) }

    suspend fun initializeStartup() {
        val providerRepo = ProviderRepository(context.applicationContext)
        if (providerRepo.getProviderCount() == 0) {
            // Run one-time migration from AccountManager to Room
            val legacyCreds = accountManager.exportForMigration()
            if (legacyCreds != null) {
                val (url, username, password) = legacyCreds
                val name = appSettings.providerName
                providerRepo.addProvider(name, url, username, password)
            }
        }
        val anyProvider = providerRepo.getProviderCount() > 0
        if (anyProvider) {
            // The tab the app opens on depends on the source's sections: read them before the
            // graph exists, rather than open on a tab the source doesn't have.
            val source = loadActiveSource(context)
            activeSource = source
            openingTab = startTab(appSettings.lastTab, source?.sections)
        }
        hasProvider = anyProvider
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
            openingTab.route
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

    // The bar shows on every screen inside a tab — its root and what's pushed on it (details,
    // episodes, Search, guides…) — so the current tab always leads back to its start and each tab
    // keeps its place; not on the player, Settings and its screens, or with a single section.
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentTab = currentEntry?.let { navController.currentTab() }
    val tabs = visibleTabs(activeSource?.sections)

    // The app opens next time on the tab used last. A tab the source doesn't have (opened while its
    // sections were unknown, before a Jellyfin sign-in) gives way to one it has.
    LaunchedEffect(currentTab, activeSource) {
        val types = activeSource?.sections ?: return@LaunchedEffect
        val tab = currentTab ?: return@LaunchedEffect
        if (tab.contentType in types) {
            appSettings.lastTab = tab.contentType
        } else {
            restartOn(startTab(appSettings.lastTab, types))
        }
    }

    // The top bar of [tab]'s root: the section's name over the source and its sync status; Search
    // the guide on Live TV, the section's Search, and the profile avatar (profiles and Settings).
    fun sourceTopBar(tab: MobileTab): @Composable ((() -> Unit)?) -> Unit =
        { onSearch ->
            MobileSourceTopBar(
                title = tab.label(),
                source = activeSource,
                onSourcePicked = { coroutineScope.launch { startOver() } },
                onSearch = onSearch,
                onSearchGuide =
                    if (tab == MobileTab.LIVE_TV) {
                        { leaveTabRoot { navController.navigateOnce(Screen.EpgBrowser()) } }
                    } else {
                        null
                    },
                // Every screen may hold the previous profile's repository: start over on its last
                // tab, as after the profile page's switch.
                onProfileChosen = { coroutineScope.launch { startOver() } },
                onSettings = { leaveTabRoot { navController.navigateOnce(Screen.Settings) } },
            )
        }

    // [tab]'s list under that top bar — or, while this profile has no login for the active
    // Jellyfin server, the sign-in panel.
    val tabRoot: @Composable (MobileTab, @Composable () -> Unit) -> Unit = { tab, section ->
        TabRoot(
            source = activeSource,
            topBar = sourceTopBar(tab),
            onEnter = ::refreshSource,
            onSignIn = { providerId ->
                // Plain navigate, not navigateOnce: this fires from the tab's first frames, often
                // while it is still entering after a switch — not RESUMED yet, so navigateOnce's
                // double-tap guard would drop it (and the prompt is only offered once per process).
                navController.navigate(Screen.AddProvider(editId = providerId)) { launchSingleTop = true }
            },
            section = section,
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        // Screens keep handling the system bars themselves; with the bar up, its height (which
        // takes in the navigation bar) is padded off and consumed below.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val entry = currentEntry
            val dockCovers = entry != null && liveDocks[entry.id]?.second == true
            if (currentTab != null && tabs.isNotEmpty() && !dockCovers && !hidesBottomBar(entry?.destination)) {
                MobileBottomBar(tabs = tabs, selected = currentTab, onSelect = ::selectTab)
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
                CategoryListDestination(
                    navController,
                    backStackEntry.toRoute<Screen.CategoryList>(),
                    onHome = ::openLiveTvRoot,
                    onDockChanged = reportLiveDock(backStackEntry.id),
                )
            }

            // The tabs' roots: the section's list under the source's top bar. Live TV's reports
            // its dock, which the bar stops on leaving the tab and hides under.
            composable<Screen.LiveTvTab> { backStackEntry ->
                tabRoot(MobileTab.LIVE_TV) {
                    CategoryListDestination(
                        navController,
                        Screen.CategoryList(ContentType.LIVE_TV),
                        onHome = ::openLiveTvRoot,
                        sourceTopBar = sourceTopBar(MobileTab.LIVE_TV),
                        onDockChanged = reportLiveDock(backStackEntry.id),
                    )
                }
            }

            composable<Screen.MoviesTab> {
                tabRoot(MobileTab.MOVIES) {
                    CategoryListDestination(
                        navController,
                        Screen.CategoryList(ContentType.MOVIES),
                        onHome = ::openLiveTvRoot,
                        sourceTopBar = sourceTopBar(MobileTab.MOVIES),
                    )
                }
            }

            composable<Screen.TvShowsTab> {
                tabRoot(MobileTab.TV_SHOWS) {
                    CategoryListDestination(
                        navController,
                        Screen.CategoryList(ContentType.TV_SHOWS),
                        onHome = ::openLiveTvRoot,
                        sourceTopBar = sourceTopBar(MobileTab.TV_SHOWS),
                    )
                }
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
                    onHome = ::openLiveTvRoot,
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

                            startOver()
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
                        // profile's repository, so start over on its last tab.
                        coroutineScope.launch { startOver() }
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

                            startOver()
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
                    initialTypeFilter = searchScreen.initialTypeFilter,
                    onStreamSelected = { itemId, itemName, categoryId, contentType ->
                        val screen =
                            when (contentType) {
                                ContentType.TV_SHOWS -> {
                                    Screen.EpisodeSelection(
                                        seriesId = itemId,
                                        seriesName = itemName,
                                        categoryId = categoryId,
                                    )
                                }

                                ContentType.MOVIES -> {
                                    Screen.MovieDetails(
                                        movieId = itemId,
                                        movieName = itemName,
                                        categoryId = categoryId,
                                    )
                                }

                                else -> {
                                    // Live TV: land on the docked mini-player, not full-screen.
                                    Screen.CategoryList(
                                        contentType = contentType,
                                        initialCategoryId = categoryId,
                                        initialStreamId = itemId,
                                    )
                                }
                            }
                        openInSectionTab(contentType, screen)
                    },
                    onCategorySelected = { categoryId, contentType ->
                        openInSectionTab(
                            contentType,
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
 * ([Screen.LiveTvTab]…) with [route] built from its section. [onHome], [sourceTopBar] and
 * [onDockChanged]: see [MobileCategoryListScreen].
 */
@Composable
private fun CategoryListDestination(
    navController: NavHostController,
    route: Screen.CategoryList,
    onHome: () -> Unit,
    sourceTopBar: (@Composable (onSearch: (() -> Unit)?) -> Unit)? = null,
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
            // Everything, with this section's chip selected (one tap widens to All).
            navController.navigateOnce(Screen.Search(SearchViewModel.CONTENT_TYPE_ALL, initialTypeFilter = route.contentType))
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
        onHome = onHome,
        onDockChanged = onDockChanged,
        sourceTopBar = sourceTopBar,
    )
}

/**
 * A bottom-bar tab's root: [section] — or, while this profile has no login for the active Jellyfin
 * server, a sign-in panel under [topBar], the sign-in screen opening by itself the first time per
 * process ([AppContainer.shouldPromptSignIn]). [onEnter] runs on each visit.
 */
@Composable
private fun TabRoot(
    source: ActiveSource?,
    topBar: @Composable ((() -> Unit)?) -> Unit,
    onEnter: () -> Unit,
    onSignIn: (providerId: Long) -> Unit,
    section: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { onEnter() }
    if (source?.needsSignIn == true) {
        val context = LocalContext.current
        val prompt = stringResource(R.string.profile_jellyfin_sign_in_prompt, source.name)
        LaunchedEffect(source.id) {
            if (AppContainer.getInstance(context.applicationContext).shouldPromptSignIn(source.id)) {
                Toast.makeText(context, prompt, Toast.LENGTH_LONG).show()
                onSignIn(source.id)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientBackdrop(modifier = Modifier.fillMaxSize())
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { topBar(null) },
            ) { paddingValues ->
                JellyfinSignInPanel(
                    providerName = source.name,
                    onSignIn = { onSignIn(source.id) },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    } else {
        section()
    }
}

/** Either side of this transition is [Screen.Player] — see the NavHost default transitions above. */
private fun isPlayerTransition(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
): Boolean = initialState.destination.hasRoute<Screen.Player>() || targetState.destination.hasRoute<Screen.Player>()
