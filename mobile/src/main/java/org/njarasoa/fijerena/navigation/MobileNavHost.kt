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
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.navigation.navigateOnce
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.CatchupWindow
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.catchup.catchupRoute
import org.njarasoa.fijerena.core.ui.components.AppLoadingScreen
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.ProfileSections
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.category.MobileCategoryListScreen
import org.njarasoa.fijerena.feature.contentselection.MobileSourceTopBar
import org.njarasoa.fijerena.feature.contentselection.MobileSourcesPanel
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
    onThemeChanged: (String) -> Unit = {},
    onUiStyleChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // No Home on the phone (docs/plans/archive/20261007_phone-home-overhaul-plan.md → Redesign: no Home on
    // the phone): the bottom bar's tabs are the profile's merged sections (its Xtream sources',
    // multi-source plan Phase 6), read at startup, on each visit to a tab's root and after every
    // switch; its other sources are Browse entries.
    var profileSections by remember { mutableStateOf<ProfileSections?>(null) }
    // A source's first catalogue sync is running: an empty list says the library is still loading,
    // not that the category is empty.
    val firstSyncs by ProviderSyncRunner.firstSyncs.collectAsStateWithLifecycle()
    val libraryLoading = profileSections?.xtreamIds.orEmpty().any { it in firstSyncs }
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
        navController.clearBackStack<Screen.SearchTab>()
        navController.clearBackStack<Screen.Settings>()
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
    // opens in Movies, with Movies highlighted. The search comes along — pushed again in that tab
    // with the same query and the result's section chip — so Back returns to the results, then to
    // the tab's start. The tab left is cut back to its root, so its saved place doesn't keep the
    // search. Same section, or a section the bar doesn't show (a single-section source): opened
    // where it is, as before.
    fun openInSectionTab(
        contentType: String,
        screen: Screen,
        query: String,
    ) {
        val target = MobileTab.entries.firstOrNull { it.contentType == contentType }
        val current = navController.currentTab()
        val tabs = visibleTabs(profileSections?.merged)
        if (target == null || current == null || target == current || target !in tabs) {
            navController.navigateOnce(screen)
            return
        }
        navController.popBackStack(current.route, inclusive = false)
        selectTab(target)
        if (query.isNotBlank()) {
            navController.navigate(
                Screen.Search(SearchViewModel.CONTENT_TYPE_ALL, initialQuery = query, initialTypeFilter = contentType),
            )
        }
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

    // Back-Stack Rule 4's switches (profile, live sync, Settings' first source): every screen may
    // hold the previous one's repository, so start over on the new profile's last tab, or the first
    // its sources have.
    suspend fun startOver() {
        val sections = ProfileSections.load(context)
        profileSections = sections
        restartOn(startTab(appSettings.lastTab, sections.merged))
    }

    // Each visit to a tab's root re-reads the profile's sources: a source turned on or off, added,
    // signed in to or renamed meanwhile.
    fun refreshSource() {
        coroutineScope.launch { profileSections = ProfileSections.load(context) }
    }

    // Remote Stop (live sync) leaves the screen that was playing for the Live TV tab's root — the
    // start tab when the source has no Live TV.
    fun openLiveTvRoot() {
        val types = profileSections?.merged
        val tab = if (types == null || ContentType.LIVE_TV in types) MobileTab.LIVE_TV else startTab(appSettings.lastTab, types)
        if (!navController.popBackStack(tab.route, inclusive = false)) {
            navController.clearBackStack(tab.route)
            navController.navigate(tab.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Live sync moved this device off a profile, or deleted a source on another device: every
    // screen may hold the old one's repository, so start over — what the profile picker does.
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
            // The tab the app opens on depends on the profile's sections: read them before the
            // graph exists, rather than open on a tab its sources don't have.
            val sections = ProfileSections.load(context)
            profileSections = sections
            openingTab = startTab(appSettings.lastTab, sections.merged)
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

    // Provider lookup / credential migration still running.
    if (hasProvider == null) {
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

    // No session restore here: each Xtream source of the profile restores its own when it is first
    // used (AppContainer), as there is no source in use.

    // The bar shows on every screen inside a tab — its root and what's pushed on it (details,
    // episodes, Search, guides…) — so the current tab always leads back to its start and each tab
    // keeps its place; not on the player, Settings and its screens, or with a single section.
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentTab = currentEntry?.let { navController.currentTab() }
    val tabs = visibleTabs(profileSections?.merged)

    // The app opens next time on the tab used last. A tab the profile's sources don't have (one
    // turned off meanwhile) gives way to one they have; with none, the tab roots show the sources
    // panel instead.
    LaunchedEffect(currentTab, profileSections) {
        val types = profileSections?.merged?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        // Settings is a tab but not a section: never the tab the app opens on.
        val tab = currentTab?.takeIf { it.isSection } ?: return@LaunchedEffect
        if (tab.contentType in types) {
            appSettings.lastTab = tab.contentType
        } else {
            restartOn(startTab(appSettings.lastTab, types))
        }
    }

    // The top bar of [tab]'s root: the section's name over the sources' sync summary; Search the
    // guide on Live TV, the section's Search, and the profile avatar (profiles; Settings is a tab).
    fun sourceTopBar(tab: MobileTab): @Composable ((() -> Unit)?) -> Unit =
        { onSearch ->
            MobileSourceTopBar(
                title = tab.label(),
                sections = profileSections,
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
                // A Browse entry (O1): that source's section, on it alone, pushed in this tab.
                onBrowseSection = { providerId, contentType ->
                    leaveTabRoot { navController.navigateOnce(Screen.CategoryList(contentType = contentType, providerId = providerId)) }
                },
                onBrowseSignIn = { providerId ->
                    leaveTabRoot { navController.navigate(Screen.AddProvider(editId = providerId)) { launchSingleTop = true } }
                },
            )
        }

    // [tab]'s list under that top bar — or, while the profile has no merged sections, the sources
    // panel: a Browse entry per source, or Add Source with none.
    val tabRoot: @Composable (MobileTab, @Composable () -> Unit) -> Unit = { tab, section ->
        TabRoot(
            sections = profileSections,
            topBar = sourceTopBar(tab),
            onEnter = ::refreshSource,
            onBrowse = { source ->
                coroutineScope.launch {
                    when (val opening = ProfileSections.opening(context, source.providerId)) {
                        ProfileSections.Opening.SignIn -> {
                            navController.navigate(Screen.AddProvider(editId = source.providerId)) { launchSingleTop = true }
                        }

                        is ProfileSections.Opening.Sections -> {
                            opening.contentTypes.firstOrNull()?.let { contentType ->
                                leaveTabRoot {
                                    navController.navigateOnce(
                                        Screen.CategoryList(contentType = contentType, providerId = source.providerId),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            onAddSource = { navController.navigateOnce(Screen.AddProvider()) },
            section = section,
        )
    }

    // Search's screen, pushed ([Screen.Search]) or as the Search tab's root: a result opens in its
    // section's tab.
    val search: @Composable (Screen.Search, (() -> Unit)?) -> Unit = { searchScreen, onBack ->
        var openingQuery by remember { mutableStateOf("") }
        MobileSearchScreen(
            onResultOpening = { openingQuery = it },
            contentType = searchScreen.contentType,
            initialTypeFilter = searchScreen.initialTypeFilter,
            initialQuery = searchScreen.initialQuery,
            onStreamSelected = { itemId, itemName, categoryId, contentType, providerId ->
                val screen =
                    when (contentType) {
                        ContentType.TV_SHOWS -> {
                            Screen.EpisodeSelection(
                                seriesId = itemId,
                                seriesName = itemName,
                                categoryId = categoryId,
                                providerId = providerId,
                            )
                        }

                        ContentType.MOVIES -> {
                            Screen.MovieDetails(
                                movieId = itemId,
                                movieName = itemName,
                                categoryId = categoryId,
                                providerId = providerId,
                            )
                        }

                        else -> {
                            // Live TV: land on the docked mini-player, not full-screen.
                            Screen.CategoryList(
                                contentType = contentType,
                                initialCategoryId = categoryId,
                                initialStreamId = itemId,
                                providerId = providerId,
                            )
                        }
                    }
                openInSectionTab(contentType, screen, openingQuery)
            },
            onCategorySelected = { categoryId, contentType, providerId ->
                openInSectionTab(
                    contentType,
                    Screen.CategoryList(
                        contentType = contentType,
                        initialCategoryId = categoryId,
                        providerId = providerId,
                    ),
                    openingQuery,
                )
            },
            onBack = onBack,
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
                    providerId = browserScreen.providerId,
                    onBack = { navController.navigateUp() },
                    onGuideSources = { id ->
                        navController.navigateOnce(Screen.EpgManagement(providerId = id))
                    },
                    // Its TV Guide opens on Recent (D7); Back from the grid returns here.
                    onTvGuide = { categoryId, categoryName, providerId ->
                        navController.navigateOnce(
                            Screen.EpgGuide(categoryId = categoryId, categoryName = categoryName, providerId = providerId),
                        )
                    },
                    onNavigateToPlayer = { matched ->
                        // Land on the docked mini-player, not full-screen — same parity as every
                        // other Live TV entry point.
                        navController.navigateOnce(
                            Screen.CategoryList(
                                contentType = ContentType.LIVE_TV,
                                initialCategoryId = matched.categoryId,
                                initialStreamId = matched.streamId.toString(),
                                providerId = matched.providerId,
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
                        libraryLoading = libraryLoading,
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
                        libraryLoading = libraryLoading,
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
                        libraryLoading = libraryLoading,
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
                    catchup =
                        playerScreen.catchupStartSec?.let {
                            CatchupWindow(it, playerScreen.catchupDurationSec, playerScreen.catchupOffsetSec)
                        },
                    programTitle = playerScreen.programTitle,
                    providerId = playerScreen.providerId,
                    // Catch-up's Watch live: the channel in the docked mini-player in place of the
                    // player, so Back returns where the catch-up was opened from.
                    onWatchLive = {
                        navController.navigateOnce(
                            Screen.CategoryList(
                                contentType = ContentType.LIVE_TV,
                                initialCategoryId = playerScreen.categoryId,
                                initialStreamId = playerScreen.streamId,
                                providerId = playerScreen.providerId,
                            ),
                        ) { popUpTo<Screen.Player> { inclusive = true } }
                    },
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

            // A tab root (the bar's Settings tab): no back arrow, Back leaves the app as on the others.
            composable<Screen.Settings> {
                MobileSettingsScreen(
                    onBack = null,
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
                            // The first source was added: no screen may keep a repository from before it.
                            org.njarasoa.fijerena.core.ui.di.AppContainer
                                .getInstance(context)
                                .clearAllCaches()
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
                search(searchScreen) { navController.navigateUp() }
            }

            // The Search tab's root: Search over everything, no Back arrow (Back leaves the app,
            // as on every tab's root).
            composable<Screen.SearchTab> {
                search(Screen.Search(SearchViewModel.CONTENT_TYPE_ALL), null)
            }

            composable<Screen.MovieDetails> { backStackEntry ->
                val movieDetailsScreen = backStackEntry.toRoute<Screen.MovieDetails>()
                MobileMovieDetailsScreen(
                    movieId = movieDetailsScreen.movieId,
                    movieName = movieDetailsScreen.movieName,
                    categoryId = movieDetailsScreen.categoryId,
                    providerId = movieDetailsScreen.providerId,
                    onPlayMovie = { movieId, movieName, extension, startFromBeginning ->
                        navController.navigateOnce(
                            Screen.Player(
                                streamId = movieId,
                                streamName = movieName,
                                categoryId = movieDetailsScreen.categoryId,
                                contentType = ContentType.MOVIES,
                                episodeExtension = extension,
                                startFromBeginning = startFromBeginning,
                                providerId = movieDetailsScreen.providerId,
                            ),
                        )
                    },
                    onCategorySelected = { categoryId ->
                        navController.navigateOnce(
                            Screen.CategoryList(
                                contentType = ContentType.MOVIES,
                                initialCategoryId = categoryId,
                                providerId = movieDetailsScreen.providerId,
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
                                providerId = movieDetailsScreen.providerId,
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
                    providerId = episodeSelectionScreen.providerId,
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
                                providerId = episodeSelectionScreen.providerId,
                            ),
                        )
                    },
                    onCategorySelected = { categoryId ->
                        navController.navigateOnce(
                            Screen.CategoryList(
                                contentType = ContentType.TV_SHOWS,
                                initialCategoryId = categoryId,
                                providerId = episodeSelectionScreen.providerId,
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
                                providerId = episodeSelectionScreen.providerId,
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
                    providerId = epgScreen.providerId,
                    onProgramSelected = { _, channel ->
                        // Land on the docked mini-player, not full-screen.
                        navController.navigateOnce(
                            Screen.CategoryList(
                                contentType = ContentType.LIVE_TV,
                                initialCategoryId = channel.categoryId,
                                initialStreamId = channel.id,
                                providerId = epgScreen.providerId,
                            ),
                        )
                    },
                    // Catch-up: the programme from the channel's archive, full screen.
                    onWatchFromStart = { program, channel ->
                        navController.navigateOnce(catchupRoute(program, channel, epgScreen.providerId))
                    },
                    onChannelSelected = { streamId, _, categoryId ->
                        navController.navigateOnce(
                            Screen.CategoryList(
                                contentType = ContentType.LIVE_TV,
                                initialCategoryId = categoryId,
                                initialStreamId = streamId,
                                providerId = epgScreen.providerId,
                            ),
                        )
                    },
                    // One search (G-9): "Search the guide", filtered to this guide's channels.
                    onSearch = {
                        navController.navigateOnce(
                            Screen.EpgBrowser(
                                categoryId = epgScreen.categoryId,
                                categoryName = epgScreen.categoryName,
                                providerId = epgScreen.providerId,
                            ),
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
    libraryLoading: Boolean = false,
) {
    MobileCategoryListScreen(
        contentType = route.contentType,
        initialCategoryId = route.initialCategoryId,
        initialStreamId = route.initialStreamId,
        providerId = route.providerId,
        onStreamSelected = { itemId, itemName, categoryId, contentType, target, rowSource ->
            // A merged Recent / Favourites row opens on its own source (multi-source plan, Phase 4).
            val source = rowSource.takeIf { it != 0L } ?: route.providerId
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
                            providerId = source,
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
                            providerId = source,
                        ),
                    )
                }

                is BrowseTarget.Movie -> {
                    navController.navigateOnce(
                        Screen.MovieDetails(
                            movieId = target.movieId,
                            movieName = itemName,
                            categoryId = categoryId,
                            providerId = source,
                        ),
                    )
                }

                // Live TV: unreachable in practice for a genuine stream tap —
                // MobileCategoryListScreen docks it locally instead of calling this
                // callback (mirrors TV's LiveTvChannelList.onStreamPromote
                // interception). Kept for the "not resolvable from the current list"
                // case, same as TV.
                is BrowseTarget.Channel -> {
                    navController.navigateOnce(
                        Screen.Player(target.streamId, itemName, categoryId, contentType, providerId = source),
                    )
                }

                // Browsed into by the list screen itself, unless it is another source's category
                // (Recent Categories across sources): that source's list, on it.
                is BrowseTarget.CategoryRef -> {
                    navController.navigateOnce(
                        Screen.CategoryList(contentType = contentType, initialCategoryId = target.categoryId, providerId = source),
                    )
                }
            }
        },
        onSearchClick = {
            // Everything, with this section's chip selected (one tap widens to All).
            navController.navigateOnce(Screen.Search(SearchViewModel.CONTENT_TYPE_ALL, initialTypeFilter = route.contentType))
        },
        // The category's own source (the list holds every source's, multi-source plan Phase 6).
        onEpgClick = { categoryId, categoryName, source ->
            navController.navigateOnce(
                Screen.EpgGuide(
                    categoryId = categoryId,
                    categoryName = categoryName,
                    providerId = source.takeIf { it != 0L } ?: route.providerId,
                ),
            )
        },
        // Start over (catch-up): the programme on air from its start, full screen, on the channel's source.
        onStartOver = { program, channel ->
            navController.navigateOnce(catchupRoute(program, channel, channel.providerId.takeIf { it != 0L } ?: route.providerId))
        },
        onBack = {
            navController.navigateUp()
        },
        onHome = onHome,
        onDockChanged = onDockChanged,
        sourceTopBar = sourceTopBar,
        libraryLoading = libraryLoading,
    )
}

/**
 * A bottom-bar tab's root: [section] — or, while the profile has no merged sections (no Xtream
 * source), the sources panel under [topBar]: a "Browse <source>" per source ([onBrowse], its first
 * section; the profile sheet lists them too), or Add Source with no source at all ([onAddSource]).
 * [onEnter] runs on each visit.
 */
@Composable
private fun TabRoot(
    sections: ProfileSections?,
    topBar: @Composable ((() -> Unit)?) -> Unit,
    onEnter: () -> Unit,
    onBrowse: (BrowseSource) -> Unit,
    onAddSource: () -> Unit,
    section: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { onEnter() }
    if (sections != null && sections.merged.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientBackdrop(modifier = Modifier.fillMaxSize())
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { topBar(null) },
            ) { paddingValues ->
                MobileSourcesPanel(
                    message =
                        stringResource(if (sections.hasNoSources) R.string.home_no_sources else R.string.home_only_browse_sources),
                    actions =
                        if (sections.hasNoSources) {
                            listOf(stringResource(R.string.provider_add_title) to onAddSource)
                        } else {
                            sections.browse.map { source -> stringResource(R.string.browse_source, source.name) to { onBrowse(source) } }
                        },
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
