package org.njarasoa.fijerena.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.navigation.dropSearches
import org.njarasoa.fijerena.core.navigation.isExpertOnly
import org.njarasoa.fijerena.core.navigation.isOwnPageOfNonExpert
import org.njarasoa.fijerena.core.navigation.isPinGuarded
import org.njarasoa.fijerena.core.navigation.isSettingsArea
import org.njarasoa.fijerena.core.navigation.navigateOnce
import org.njarasoa.fijerena.core.navigation.navigateToSearch
import org.njarasoa.fijerena.core.navigation.opensHomeAfterSave
import org.njarasoa.fijerena.core.navigation.shownProfileId
import org.njarasoa.fijerena.core.navigation.startsOnAddSource
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.CatchupWindow
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.sourceOr
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.catchup.catchupRoute
import org.njarasoa.fijerena.core.ui.catchup.guideChannelRoute
import org.njarasoa.fijerena.core.ui.components.AppLoadingScreen
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.PinSession
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.ProfileSections
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.wanted.LocalOpenWanted
import org.njarasoa.fijerena.core.ui.wanted.route
import org.njarasoa.fijerena.core.ui.wanted.toTmdbTitle
import org.njarasoa.fijerena.feature.category.MobileCategoryListScreen
import org.njarasoa.fijerena.feature.contentselection.MobileSourceTopBar
import org.njarasoa.fijerena.feature.contentselection.MobileSourcesPanel
import org.njarasoa.fijerena.feature.epg.MobileEpgGuideScreen
import org.njarasoa.fijerena.feature.epg.MobileEpgManagementScreen
import org.njarasoa.fijerena.feature.epgbrowser.MobileEpgBrowserScreen
import org.njarasoa.fijerena.feature.episode.MobileEpisodeSelectionScreen
import org.njarasoa.fijerena.feature.home.MobileHomeScreen
import org.njarasoa.fijerena.feature.movie.MobileMovieDetailsScreen
import org.njarasoa.fijerena.feature.player.MobilePlayerScreen
import org.njarasoa.fijerena.feature.profile.MobileProfileMenu
import org.njarasoa.fijerena.feature.provider.MobileAddProviderScreen
import org.njarasoa.fijerena.feature.provider.MobileProviderSelectionScreen
import org.njarasoa.fijerena.feature.safemode.MobileNewerDataScreen
import org.njarasoa.fijerena.feature.safemode.MobileSafeModeScreen
import org.njarasoa.fijerena.feature.search.MobileSearchScreen
import org.njarasoa.fijerena.feature.settings.MobileProfileEditScreen
import org.njarasoa.fijerena.feature.settings.MobileSettingsScreen
import org.njarasoa.fijerena.feature.wanted.MobileTmdbTitleScreen
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.LocalMobileClock
import org.njarasoa.fijerena.ui.components.MobileClockOverlay
import org.njarasoa.fijerena.ui.components.MobileClockState
import org.njarasoa.fijerena.ui.components.PinDialog
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@Composable
fun MobileNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    // The profile in use has Expert mode: only then does it add sources (a tab's Add Source, the gate below).
    val expert by remember { appSettings.observeDevMode().map { it.second } }
        .collectAsStateWithLifecycle(initialValue = appSettings.isDevMode)
    // The clock over every screen, for every profile, when this device's Show Clock is on.
    val clock = remember { MobileClockState() }
    val showClock by remember { appSettings.observeShowClock() }.collectAsStateWithLifecycle(initialValue = appSettings.showClock)
    LaunchedEffect(showClock) { clock.off = !showClock }
    val coroutineScope = rememberCoroutineScope()

    // The bottom bar's tabs (docs/plans/20261019_phone-home-tab-plan.md): Home, then the profile's
    // merged sections (its Xtream sources', multi-source plan Phase 6), read at startup, on each visit
    // to a tab's root and after every switch; its other sources are Browse entries.
    var profileSections by remember { mutableStateOf<ProfileSections?>(null) }
    // A source's first catalogue sync is running: an empty list says the library is still loading,
    // not that the category is empty.
    val firstSyncs by ProviderSyncRunner.firstSyncs.collectAsStateWithLifecycle()
    val libraryLoading = profileSections?.xtreamIds.orEmpty().any { it in firstSyncs }
    // The graph's start destination, decided once at startup.
    var openingTab by remember { mutableStateOf(MobileTab.HOME) }
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
        navController.clearBackStack<Screen.HomeTab>()
        navController.clearBackStack<Screen.LiveTvTab>()
        navController.clearBackStack<Screen.MoviesTab>()
        navController.clearBackStack<Screen.TvShowsTab>()
        navController.clearBackStack<Screen.Settings>()
    }

    // Each tab keeps its place: leaving one saves its back stack, coming back restores it. Tapping
    // the tab you're on pops back to its root. The bar only shows on a tab's root, so the tab
    // being left is the current destination's. Only one tab's stack is ever on the back stack; Back
    // on a tab's root goes to Home, and on Home stays (never leaves the app; see below).
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
    // where it is, as before. A Guide tab result ([guide]) brings the search back on its Guide tab.
    fun openInSectionTab(
        contentType: String,
        screen: Screen,
        query: String,
        guide: Boolean = false,
        source: Long? = null,
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
        // The tab's own earlier Search (and what it opened) goes: only this one is ever there.
        navController.dropSearches()
        if (query.isNotBlank()) {
            navController.navigate(
                Screen.Search(
                    SearchViewModel.CONTENT_TYPE_ALL,
                    initialQuery = query,
                    initialTypeFilter = contentType,
                    initialGuideTab = guide,
                    initialSource = source,
                ),
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

    // The profile whose sources the tabs follow (below); a switch starts over on the new one.
    var sourcesProfileId by remember { mutableStateOf(appSettings.activeProfileId) }

    // Back-Stack Rule 4's switches (profile, live sync, Settings' first source): every screen may
    // hold the previous one's repository, so start over on the new profile's last tab, or the first
    // its sources have.
    suspend fun startOver() {
        val sections = ProfileSections.load(context)
        profileSections = sections
        sourcesProfileId = appSettings.activeProfileId
        restartOn(startTab(appSettings.lastTab, sections.merged))
    }

    // The first source saved on Add Source: Home, from the root (E1). No screen may keep a
    // repository from before it, as after Settings' first source.
    fun startOverAfterFirstSource() {
        coroutineScope.launch {
            org.njarasoa.fijerena.core.ui.di.AppContainer
                .getInstance(context)
                .clearAllCaches()
            profileSections = ProfileSections.load(context)
            sourcesProfileId = appSettings.activeProfileId
            restartOn(MobileTab.HOME)
        }
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

    // The profile in use was deleted on another device (live sync moved this one to another):
    // every screen goes, playback included, and the profile menu asks who is watching.
    var showProfileMenu by remember { mutableStateOf(false) }
    // Set when the menu asks who is watching: it then stays until a profile is picked.
    var mustChooseProfile by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        org.njarasoa.fijerena.core.ui.di.AppContainer
            .getInstance(context)
            .externalSwitches
            .collect {
                // Before the graph exists (very first frames) there is nothing to rebuild.
                if (navController.currentBackStackEntry == null) return@collect
                startOver()
                mustChooseProfile = true
                showProfileMenu = true
            }
    }
    // Another device deleted a source of the profile in use: the same, once nothing plays — the
    // player and the docked live channel keep playing until closed.
    var sourceDeleted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        org.njarasoa.fijerena.core.ui.di.AppContainer
            .getInstance(context)
            .sourceDeletions
            .collect { sourceDeleted = true }
    }
    val onPlayer =
        navController
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.hasRoute<Screen.Player>() == true
    val playing = onPlayer || liveDocks.values.any { (stop, _) -> stop != null }
    LaunchedEffect(sourceDeleted, playing) {
        if (sourceDeleted && !playing && navController.currentBackStackEntry != null) {
            sourceDeleted = false
            startOver()
        }
    }
    val accountManager = remember { AccountManager(context.applicationContext) }

    // Async initialization: migrate legacy creds, determine start destination
    var hasProvider by remember { mutableStateOf<Boolean?>(null) }
    // No source at all and an Expert in use: Add Source over Settings at start (E1).
    var addSourceAtStart by remember { mutableStateOf(false) }

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
        } else {
            // A new install's first profile becomes Expert in the background startup work, maybe
            // after this point: the start needs it now (the call does nothing when there is one).
            ProfileRepository(context.applicationContext).ensureAnExpert()
            addSourceAtStart = startsOnAddSource(hasSource = false, expert = appSettings.isDevMode)
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

    // The profile's sources as they change (one turned on or off, added or deleted): the tabs follow
    // at once, whatever screen is open. Not before startup has read them, nor in safe mode.
    LaunchedEffect(hasProvider, sourcesProfileId) {
        if (hasProvider == null || SafeMode.isActive || ProvidersDbGuard.isBlocked) return@LaunchedEffect
        ProviderRepository(context.applicationContext).profileSourceSet(sourcesProfileId).collect {
            profileSections = ProfileSections.load(context)
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
        } else if (addSourceAtStart) {
            // Add Source opens over it (the effect after the NavHost).
            Screen.Settings
        } else {
            // With no source, Home says to ask a grown-up for one (not Expert).
            openingTab.route
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
        val tab = currentTab?.takeIf { it != MobileTab.SETTINGS } ?: return@LaunchedEffect
        if (tab == MobileTab.HOME || tab.contentType in types) {
            appSettings.lastTab = tab.contentType
        } else {
            restartOn(startTab(appSettings.lastTab, types))
        }
    }

    // A Browse entry (O1) picked in the profile menu: that source's section, on it alone, pushed in
    // this tab — or its sign-in while the source has no login.
    fun browseSection(
        providerId: Long,
        contentType: String,
    ) = leaveTabRoot { navController.navigateOnce(Screen.CategoryList(contentType = contentType, providerId = providerId)) }

    // The sign-in is the source's form, an Expert's only (PR-12): anyone else asks an admin.
    fun browseSignIn(providerId: Long) =
        if (expert) {
            leaveTabRoot { navController.navigate(Screen.AddProvider(editId = providerId)) { launchSingleTop = true } }
        } else {
            Toast.makeText(context, R.string.browse_sign_in_ask_admin, Toast.LENGTH_LONG).show()
        }

    // The top bar of [tab]'s root: the section's name over the sources' sync summary; Search the
    // guide on Home and Live TV, and the section's Search (everything on Home). The profile avatar is in the dock — in the top bar
    // only while there is no dock (no section).
    fun sourceTopBar(tab: MobileTab): @Composable ((() -> Unit)?) -> Unit =
        { onSearch ->
            MobileSourceTopBar(
                title = tab.label(),
                sections = profileSections,
                // A section's Search: everything, with this section's chip chosen (one tap from
                // all of it) — Home's comes from its screen.
                onSearch =
                    onSearch ?: { leaveTabRoot { navController.navigateToSearch(sectionSearch(tab)) } }.takeIf { tab != MobileTab.HOME },
                onSearchGuide =
                    if (tab == MobileTab.LIVE_TV || (tab == MobileTab.HOME && ContentType.LIVE_TV in profileSections?.merged.orEmpty())) {
                        { leaveTabRoot { navController.navigateOnce(Screen.EpgBrowser()) } }
                    } else {
                        null
                    },
                // Every screen may hold the previous profile's repository: start over on its last
                // tab, as after the profile page's switch.
                onProfileChosen = { coroutineScope.launch { startOver() } },
                // A Browse entry (O1): that source's section, on it alone, pushed in this tab.
                onBrowseSection = ::browseSection,
                onBrowseSignIn = ::browseSignIn,
                showProfile = tabs.isEmpty(),
                onSettings = { selectTab(MobileTab.SETTINGS) },
            )
        }

    // [tab]'s list under that top bar — or, while the profile has no merged sections, the sources
    // panel: a Browse entry per source, or Add Source with none (for an Expert only).
    val tabRoot: @Composable (MobileTab, @Composable () -> Unit) -> Unit = { tab, section ->
        TabRoot(
            sections = profileSections,
            topBar = sourceTopBar(tab),
            onEnter = ::refreshSource,
            onBrowse = { source ->
                coroutineScope.launch {
                    when (val opening = ProfileSections.opening(context, source.providerId)) {
                        ProfileSections.Opening.SignIn -> {
                            if (expert) {
                                navController.navigate(Screen.AddProvider(editId = source.providerId)) { launchSingleTop = true }
                            } else {
                                Toast.makeText(context, R.string.browse_sign_in_ask_admin, Toast.LENGTH_LONG).show()
                            }
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
            onAddSource = if (expert) ({ navController.navigateOnce(Screen.AddProvider()) }) else null,
            section = section,
        )
    }

    // Search's screen, pushed ([Screen.Search]): a result opens in its section's tab.
    val search: @Composable (Screen.Search, (() -> Unit)?) -> Unit = { searchScreen, onBack ->
        var openingQuery by remember { mutableStateOf("") }
        var openingSource by remember { mutableStateOf<Long?>(null) }
        MobileSearchScreen(
            onTmdbTitleSelected = { navController.navigateOnce(it.route()) },
            onResultOpening = { query, source ->
                openingQuery = query
                openingSource = source
            },
            contentType = searchScreen.contentType,
            initialTypeFilter = searchScreen.initialTypeFilter,
            initialQuery = searchScreen.initialQuery,
            initialGuideTab = searchScreen.initialGuideTab,
            initialSource = searchScreen.initialSource,
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
                openInSectionTab(contentType, screen, openingQuery, source = openingSource)
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
                    source = openingSource,
                )
            },
            // A Guide tab result opens as Search the guide's does, the docked mini-player, in the
            // Live TV tab like a channel result; Back returns to the Guide tab.
            onGuideResult = { matched ->
                openInSectionTab(
                    ContentType.LIVE_TV,
                    Screen.CategoryList(
                        contentType = ContentType.LIVE_TV,
                        initialCategoryId = matched.categoryId,
                        initialStreamId = matched.streamId.toString(),
                        providerId = matched.providerId,
                    ),
                    openingQuery,
                    guide = true,
                )
            },
            onBack = onBack,
        )
    }
    // The bar floats over the screens (MobileDockStyle): content runs behind it, and each screen ends
    // its scrolling content with [LocalDockInset] of space so its last row can scroll clear of it.
    val entry = currentEntry
    val dockCovers = entry != null && liveDocks[entry.id]?.second == true
    val showBar = currentTab != null && tabs.isNotEmpty() && !dockCovers && !hidesBottomBar(entry?.destination)
    var barHeightPx by remember { mutableIntStateOf(0) }
    // The dock's profile slot: the profile in use, and its menu.
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val activeProfile by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
    val dockInset = if (showBar) with(LocalDensity.current) { barHeightPx.toDp() } else CinemaSpacing.none
    // Back never leaves the app (household UX plan, 2026-10-19): on a tab's start it goes to Home,
    // on Home it does nothing — a child pressing Back stays in. Composed before the NavHost, so the
    // NavHost's own Back (popping a screen) and a screen's (Live TV's dock) come first.
    BackHandler(enabled = currentTab != null && navController.previousBackStackEntry == null) {
        if (currentTab != MobileTab.HOME && MobileTab.HOME in tabs) selectTab(MobileTab.HOME)
    }
    Box(modifier = Modifier.fillMaxSize().background(CinemaTheme.colors.surface)) {
        CompositionLocalProvider(
            LocalDockInset provides dockInset,
            LocalOpenWanted provides { navController.navigateOnce(it) },
            LocalMobileClock provides clock,
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.consumeWindowInsets(PaddingValues(bottom = dockInset)),
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
                        onGuideSources = {
                            navController.navigateOnce(Screen.EpgManagement())
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

                // Home's root: every section's rows under the sources' top bar. A card opens inside
                // the Home tab, as the lists route it; Back returns to Home.
                composable<Screen.HomeTab> {
                    tabRoot(MobileTab.HOME) {
                        MobileHomeScreen(
                            onWanted = { navController.navigateOnce(it) },
                            supportedTypes = profileSections?.merged.orEmpty(),
                            sourceTopBar = sourceTopBar(MobileTab.HOME),
                            onSearch = { navController.navigateOnce(Screen.Search(SearchViewModel.CONTENT_TYPE_ALL)) },
                            onContinueWatching = { item ->
                                navController.navigateOnce(
                                    browseTargetRoute(item.target, item.name, item.categoryId, item.providerId, item.contentType),
                                )
                            },
                            onChannel = { streamId, listId, source ->
                                navController.navigateOnce(
                                    Screen.CategoryList(
                                        contentType = ContentType.LIVE_TV,
                                        initialCategoryId = listId,
                                        initialStreamId = streamId,
                                        providerId = source,
                                    ),
                                )
                            },
                            onFavorite = { item, contentType ->
                                val target =
                                    if (contentType == ContentType.TV_SHOWS) {
                                        BrowseTarget.Series(SeriesId(item.id))
                                    } else {
                                        BrowseTarget.Movie(item.id)
                                    }
                                navController.navigateOnce(
                                    browseTargetRoute(target, item.name, item.categoryId, item.providerId, contentType),
                                )
                            },
                            onCategory = { categoryId, contentType, source ->
                                navController.navigateOnce(
                                    Screen.CategoryList(contentType = contentType, initialCategoryId = categoryId, providerId = source),
                                )
                            },
                        )
                    }
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
                    // Read before the save: the first source opens Home once saved (E1).
                    var homeAfterSave by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        homeAfterSave =
                            opensHomeAfterSave(
                                addProviderScreen.editId,
                                ProviderRepository(context.applicationContext).getProviderCount(),
                            )
                    }
                    MobileAddProviderScreen(
                        editId = addProviderScreen.editId,
                        onBack = {
                            navController.navigateUp()
                        },
                        onSuccess = {
                            if (homeAfterSave) startOverAfterFirstSource() else navController.navigateUp()
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
                        // A route without admin is always the page of the profile in use; Admin → Profiles
                        // opens the one the route names, with its Admin rows (an Expert's only).
                        profileId =
                            if (route.admin) route.shownProfileId(appSettings.activeProfileId, expert) else appSettings.activeProfileId,
                        viewModel = viewModel(settingsEntry, factory = SettingsViewModelFactory(context)),
                        onBack = { navController.navigateUp() },
                        adminRows = route.admin && expert,
                        onParentalControls = { navController.navigateOnce(Screen.ParentalControls) },
                        onProfileSwitched = {
                            // As after the profile picker: every screen below may hold the previous
                            // profile's repository, so start over on its last tab.
                            coroutineScope.launch { startOver() }
                        },
                    )
                }

                // A tab root (the bar's Settings tab): no back arrow; Back goes to Home as on the others.
                composable<Screen.Settings> {
                    MobileSettingsScreen(
                        onBack = null,
                        onManageProviders = {
                            navController.navigateOnce(Screen.ProviderSelection)
                        },
                        onDiagnostics = {
                            navController.navigateOnce(Screen.Diagnostics)
                        },
                        onLicenses = { navController.navigateOnce(Screen.Licenses) },
                        onTerms = { navController.navigateOnce(Screen.Terms) },
                        onDeviceInfo = {
                            navController.navigateOnce(Screen.DeviceInfo)
                        },
                        onLiveSync = {
                            navController.navigateOnce(Screen.SyncSettings)
                        },
                        onGuideSources = {
                            navController.navigateOnce(Screen.EpgManagement())
                        },
                        onAdminProfile = { id ->
                            navController.navigateOnce(Screen.ProfileEdit(profileId = id, admin = true))
                        },
                        onParentalControls = { navController.navigateOnce(Screen.ParentalControls) },
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

                // Parental controls' pages share one view model, the main page's: a change made on a
                // list shows on the main page at once.
                composable<Screen.ParentalControls> {
                    org.njarasoa.fijerena.feature.settings.MobileParentalControlsScreen(
                        viewModel = viewModel(factory = SettingsViewModelFactory(context)),
                        onOpenList = { list -> navController.navigateOnce(Screen.ParentalListEdit(list.key)) },
                        onCheck = { id -> navController.navigateOnce(Screen.ParentalCheck(id)) },
                        // Settings, where the Profiles group (each profile's page) is: what is
                        // over it (this page, and a child's page that opened it) closes.
                        onProfiles = {
                            if (!navController.popBackStack<Screen.Settings>(inclusive = false)) navController.navigateUp()
                        },
                        onBack = { navController.navigateUp() },
                    )
                }
                composable<Screen.ParentalListEdit> { backStackEntry ->
                    val key = backStackEntry.toRoute<Screen.ParentalListEdit>().listKey
                    val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<Screen.ParentalControls>() }
                    ParentalList.entries.firstOrNull { it.key == key }?.let { list ->
                        org.njarasoa.fijerena.feature.settings.MobileParentalListScreen(
                            viewModel = viewModel(parentEntry, factory = SettingsViewModelFactory(context)),
                            list = list,
                            onBack = { navController.navigateUp() },
                        )
                    }
                }
                composable<Screen.ParentalCheck> { backStackEntry ->
                    val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<Screen.ParentalControls>() }
                    org.njarasoa.fijerena.feature.settings.MobileParentalCheckScreen(
                        viewModel = viewModel(parentEntry, factory = SettingsViewModelFactory(context)),
                        providerId = backStackEntry.toRoute<Screen.ParentalCheck>().providerId,
                        onBack = { navController.navigateUp() },
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
                composable<Screen.Licenses> {
                    org.njarasoa.fijerena.feature.settings.MobileLicensesScreen(
                        onOpen = { id -> navController.navigateOnce(Screen.LicenseDetail(id)) },
                        onBack = { navController.navigateUp() },
                    )
                }
                composable<Screen.Terms> {
                    org.njarasoa.fijerena.feature.settings
                        .MobileTermsScreen(onBack = { navController.navigateUp() })
                }
                composable<Screen.LicenseDetail> { backStackEntry ->
                    org.njarasoa.fijerena.feature.settings.MobileLicenseDetailScreen(
                        entryId = backStackEntry.toRoute<Screen.LicenseDetail>().entryId,
                        onBack = { navController.navigateUp() },
                    )
                }

                composable<Screen.Search> { backStackEntry ->
                    val searchScreen = backStackEntry.toRoute<Screen.Search>()
                    search(searchScreen) { navController.navigateUp() }
                }

                composable<Screen.TmdbTitle> { backStackEntry ->
                    val route = backStackEntry.toRoute<Screen.TmdbTitle>()
                    MobileTmdbTitleScreen(
                        title = remember(route) { route.toTmdbTitle() },
                        // A source has it: its page takes this one's place. navigate, not
                        // navigateOnce: this can fire while the page is still opening.
                        onOpenCopy = { copy ->
                            navController.navigate(copy.route(route.contentType)) { popUpTo<Screen.TmdbTitle> { inclusive = true } }
                        },
                        onBack = { navController.navigateUp() },
                        // As on a film or show page: a title no source has opens its TMDB page, else its source's page.
                        onRelatedTitleSelected = { related ->
                            val tmdbOnly = TmdbOnlyItem.titleOf(related, route.contentType)
                            when {
                                tmdbOnly != null -> {
                                    navController.navigateOnce(tmdbOnly.route())
                                }

                                route.contentType == ContentType.MOVIES -> {
                                    navController.navigateOnce(
                                        Screen.MovieDetails(
                                            movieId = related.id,
                                            movieName = related.name,
                                            categoryId = related.categoryId,
                                            providerId = related.providerId,
                                        ),
                                    )
                                }

                                else -> {
                                    navController.navigateOnce(
                                        Screen.EpisodeSelection(
                                            seriesId = related.id,
                                            seriesName = related.name,
                                            categoryId = related.categoryId,
                                            providerId = related.providerId,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }

                composable<Screen.MovieDetails> { backStackEntry ->
                    val movieDetailsScreen = backStackEntry.toRoute<Screen.MovieDetails>()
                    MobileMovieDetailsScreen(
                        movieId = movieDetailsScreen.movieId,
                        movieName = movieDetailsScreen.movieName,
                        categoryId = movieDetailsScreen.categoryId,
                        providerId = movieDetailsScreen.providerId,
                        onPlayMovie = { movieId, movieName, extension, startFromBeginning, playedCategoryId ->
                            navController.navigateOnce(
                                Screen.Player(
                                    streamId = movieId,
                                    streamName = movieName,
                                    categoryId = playedCategoryId,
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
                            // A TMDB title no source has opens its TMDB page (wishlist UX plan, Phase 4).
                            val tmdbOnly = TmdbOnlyItem.titleOf(related, ContentType.MOVIES)
                            if (tmdbOnly != null) {
                                navController.navigateOnce(tmdbOnly.route())
                            } else {
                                navController.navigateOnce(
                                    Screen.MovieDetails(
                                        movieId = related.id,
                                        movieName = related.name,
                                        categoryId = related.categoryId,
                                        providerId = related.sourceOr(movieDetailsScreen.providerId),
                                    ),
                                )
                            }
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
                        onEpisodeSelected = { episodeId, episodeTitle, extension, startFromBeginning, series ->
                            navController.navigateOnce(
                                Screen.Player(
                                    streamId = episodeId,
                                    streamName = episodeTitle,
                                    categoryId = series.categoryId,
                                    contentType = ContentType.TV_SHOWS,
                                    episodeId = episodeId,
                                    episodeExtension = extension,
                                    seriesId = series.seriesId,
                                    seriesName = series.seriesName,
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
                            // A TMDB title no source has opens its TMDB page (wishlist UX plan, Phase 4).
                            val tmdbOnly = TmdbOnlyItem.titleOf(related, ContentType.TV_SHOWS)
                            if (tmdbOnly != null) {
                                navController.navigateOnce(tmdbOnly.route())
                            } else {
                                navController.navigateOnce(
                                    Screen.EpisodeSelection(
                                        seriesId = related.id,
                                        seriesName = related.name,
                                        categoryId = related.categoryId,
                                        providerId = related.sourceOr(episodeSelectionScreen.providerId),
                                    ),
                                )
                            }
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
                        // Each row on its own source: a Recent or Favourites guide lists every source's.
                        onProgramSelected = { _, channel ->
                            // Land on the docked mini-player, not full-screen.
                            navController.navigateOnce(guideChannelRoute(channel, epgScreen.providerId))
                        },
                        // Catch-up: the programme from the channel's archive, full screen.
                        onWatchFromStart = { program, channel ->
                            navController.navigateOnce(catchupRoute(program, channel, channel.sourceOr(epgScreen.providerId)))
                        },
                        onChannelSelected = { channel ->
                            navController.navigateOnce(guideChannelRoute(channel, epgScreen.providerId))
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
        // After the NavHost, so it draws over the screen.
        MobileClockOverlay(clock)
        // E1: Add Source over Settings, once (a recreated Activity keeps its back stack).
        var addSourceOpened by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (addSourceAtStart && !addSourceOpened) {
                addSourceOpened = true
                navController.navigate(Screen.AddProvider())
            }
        }
        if (showBar && currentTab != null) {
            MobileBottomBar(
                tabs = tabs,
                selected = currentTab,
                // The profile slot opens its menu (switch profile, Settings, Browse entries).
                onSelect = { tab -> if (tab == MobileTab.SETTINGS) showProfileMenu = true else selectTab(tab) },
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeightPx = it.height },
                profile = activeProfile,
            )
        }
    }
    // The screens Settings opens ask the PIN of the profile in use, once per session (locked again
    // after a while in the background or a switch of profile); Settings asks it before opening them,
    // this is the backstop. Picking a profile and watching never ask. Cancel goes back to Settings.
    val gateEntry = navController.currentBackStackEntryAsState().value
    val gateDestination = gateEntry?.destination
    // The household's shared setup (sources, guide sources, live sync, Parental controls) is
    // the Expert's only, whatever opened it: closed at once.
    val refused = !expert && gateEntry.isExpertOnly()
    LaunchedEffect(gateEntry, refused) { if (refused) navController.popBackStack() }
    // A child profile has no Settings: Settings, or a screen it opens, reached anyway (a restored
    // back stack, a switch to a child while it is open) gives way to Home. Safe mode keeps its Logs.
    val childRefused =
        !SafeMode.isActive && activeProfile?.let { appSettings.isChildProfile(it.id) } == true && gateEntry.isSettingsArea()
    LaunchedEffect(gateEntry, childRefused) { if (childRefused) restartOn(MobileTab.HOME) }
    activeProfile?.let { profile ->
        LaunchedEffect(profile.id) { PinSession.onActiveProfile(profile.id) }
        if (!refused &&
            !childRefused &&
            !showProfileMenu &&
            gateDestination.isPinGuarded() &&
            !gateEntry.isOwnPageOfNonExpert(profile.id, expert) &&
            profilesViewModel.needsPin(profile.id)
        ) {
            // Nothing of Settings shows behind the prompt.
            Box(Modifier.fillMaxSize().background(CinemaTheme.colors.background))
            PinDialog(
                state =
                    rememberPinPromptState(
                        profilesViewModel.pins,
                        PinRequest.Check(profile.id, stringResource(R.string.pin_enter_for, profile.name)),
                    ) {},
                onCancel = {
                    while (navController.currentDestination.isPinGuarded() && navController.popBackStack()) Unit
                },
            )
        }
    }
    MobileProfileMenu(
        visible = showProfileMenu,
        viewModel = profilesViewModel,
        sections = profileSections,
        onDismiss = {
            showProfileMenu = false
            mustChooseProfile = false
        },
        // Every screen may hold the previous profile's repository: start over on its last tab.
        onProfileChosen = { coroutineScope.launch { startOver() } },
        mustChoose = mustChooseProfile,
        onSettings = { selectTab(MobileTab.SETTINGS) },
        onBrowseSection = ::browseSection,
        onBrowseSignIn = ::browseSignIn,
    )
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
            navController.navigateOnce(browseTargetRoute(target, itemName, categoryId, source, contentType))
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
 * Where a row or card opens, by what it stands for ([target]): a show's episodes (on the episode to
 * resume), an episode playing, a film's details, a channel, a category's list. On [source].
 */
private fun browseTargetRoute(
    target: BrowseTarget,
    itemName: String,
    categoryId: String,
    source: Long,
    contentType: String,
): Screen =
    when (target) {
        // Continue Watching: the card stands for the show, so open episode
        // selection with the last-watched episode's panel already up.
        is BrowseTarget.Series -> {
            Screen.EpisodeSelection(
                seriesId = target.seriesId.raw,
                seriesName = itemName,
                categoryId = categoryId,
                initialEpisodeId = target.resumeEpisodeId?.raw,
                providerId = source,
            )
        }

        // The card stands for one episode — play it, whether or not it can
        // name the show it belongs to.
        is BrowseTarget.Episode -> {
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
            )
        }

        is BrowseTarget.Movie -> {
            Screen.MovieDetails(
                movieId = target.movieId,
                movieName = itemName,
                categoryId = categoryId,
                providerId = source,
            )
        }

        // Live TV: unreachable in practice for a genuine stream tap —
        // MobileCategoryListScreen docks it locally instead of calling this
        // callback (mirrors TV's LiveTvChannelList.onStreamPromote
        // interception). Kept for the "not resolvable from the current list"
        // case, same as TV.
        is BrowseTarget.Channel -> {
            Screen.Player(target.streamId, itemName, categoryId, contentType, providerId = source)
        }

        // Browsed into by the list screen itself, unless it is another source's category
        // (Recent Categories across sources): that source's list, on it.
        is BrowseTarget.CategoryRef -> {
            Screen.CategoryList(contentType = contentType, initialCategoryId = target.categoryId, providerId = source)
        }
    }

/**
 * A bottom-bar tab's root: [section] — or, while the profile has no merged sections (no Xtream
 * source), the sources panel under [topBar]: a "Browse <source>" per source ([onBrowse], its first
 * section; the profile sheet lists them too), or Add Source with no source at all ([onAddSource];
 * null without Expert mode, which asks a grown-up instead).
 * [onEnter] runs on each visit.
 */
@Composable
private fun TabRoot(
    sections: ProfileSections?,
    topBar: @Composable ((() -> Unit)?) -> Unit,
    onEnter: () -> Unit,
    onBrowse: (BrowseSource) -> Unit,
    onAddSource: (() -> Unit)?,
    section: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { onEnter() }
    if (sections != null && sections.merged.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientBackdrop(modifier = Modifier.fillMaxSize())
            Scaffold(
                containerColor = CinemaTheme.colors.clear,
                topBar = { topBar(null) },
            ) { paddingValues ->
                MobileSourcesPanel(
                    message =
                        stringResource(
                            when {
                                !sections.hasNoSources -> R.string.home_only_browse_sources
                                onAddSource == null -> R.string.home_no_sources_ask
                                else -> R.string.home_no_sources
                            },
                        ),
                    actions =
                        if (sections.hasNoSources) {
                            listOfNotNull(onAddSource?.let { stringResource(R.string.provider_add_title) to it })
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

/** A section tab's Search: over everything, the section's own chip chosen. */
private fun sectionSearch(tab: MobileTab): Screen.Search =
    Screen.Search(SearchViewModel.CONTENT_TYPE_ALL, initialTypeFilter = tab.contentType)
