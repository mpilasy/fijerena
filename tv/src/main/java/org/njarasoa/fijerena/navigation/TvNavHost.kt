package org.njarasoa.fijerena.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.navigation.isExpertOnly
import org.njarasoa.fijerena.core.navigation.isPinGuarded
import org.njarasoa.fijerena.core.navigation.navigateOnce
import org.njarasoa.fijerena.core.navigation.navigateToSearch
import org.njarasoa.fijerena.core.navigation.opensHomeAfterSave
import org.njarasoa.fijerena.core.navigation.startsOnAddSource
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.CatchupWindow
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceOr
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.catchup.catchupRoute
import org.njarasoa.fijerena.core.ui.catchup.guideChannelRoute
import org.njarasoa.fijerena.core.ui.components.AppLoadingScreen
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.PinSession
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.ProfileSections
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.wanted.LocalOpenWanted
import org.njarasoa.fijerena.core.ui.wanted.route
import org.njarasoa.fijerena.core.ui.wanted.toTmdbTitle
import org.njarasoa.fijerena.feature.category.TvCategoryGridScreen
import org.njarasoa.fijerena.feature.contentselection.ContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.epg.TvEpgGuideScreen
import org.njarasoa.fijerena.feature.epg.TvEpgManagementScreen
import org.njarasoa.fijerena.feature.epgbrowser.TvEpgBrowserScreen
import org.njarasoa.fijerena.feature.episode.EpisodeSelectionScreen
import org.njarasoa.fijerena.feature.movie.MovieDetailsScreen
import org.njarasoa.fijerena.feature.person.PersonScreen
import org.njarasoa.fijerena.feature.player.TvPlayerScreen
import org.njarasoa.fijerena.feature.profile.ProfilePickerScreen
import org.njarasoa.fijerena.feature.provider.TvAddProviderScreen
import org.njarasoa.fijerena.feature.provider.TvProviderSelectionScreen
import org.njarasoa.fijerena.feature.safemode.NewerDataScreen
import org.njarasoa.fijerena.feature.safemode.SafeModeScreen
import org.njarasoa.fijerena.feature.search.SearchScreen
import org.njarasoa.fijerena.feature.settings.SettingsScreen
import org.njarasoa.fijerena.feature.wanted.TmdbTitleScreen
import org.njarasoa.fijerena.ui.components.LocalTvClock
import org.njarasoa.fijerena.ui.components.TvClockOverlay
import org.njarasoa.fijerena.ui.components.TvClockState
import org.njarasoa.fijerena.ui.components.TvPinDialog
import org.njarasoa.fijerena.ui.components.rail.HideTvNavRail
import org.njarasoa.fijerena.ui.components.rail.LocalTvNavRail
import org.njarasoa.fijerena.ui.components.rail.RailItem
import org.njarasoa.fijerena.ui.components.rail.TvNavRailState

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNavHost(
    navController: NavHostController = rememberNavController(),
    onUiScaleChanged: (Float?) -> Unit = {},
) {
    val context = LocalContext.current
    // Which screen a dead D-pad happened on, for Diagnostics (DeadKeyDetector).
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            org.njarasoa.fijerena.ui.DeadKeyDetector.screen =
                entry.destination.route
                    ?.substringAfterLast('.')
                    ?.substringBefore('/')
                    ?.substringBefore('?') ?: "?"
        }
    }

    // Profile, source and live-sync switches start over from Home: every section's saved place may
    // hold the previous one's repository, so it goes too.

    fun clearSectionStacks() = TvSection.entries.forEach { navController.clearBackStack(it.route) }

    // Live sync deleted a source of the profile in use on another device: every screen may hold
    // its repository, so start over from home — what the profile picker does.
    fun startOverFromHome() {
        // Before the graph exists (very first frames) there is nothing to rebuild.
        if (navController.currentBackStackEntry == null) return
        clearSectionStacks()
        navController.navigate(Screen.ContentTypeSelection) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
    // The profile in use was deleted on another device: whatever was on screen or playing goes,
    // and the profile picker asks who is watching.
    LaunchedEffect(Unit) {
        org.njarasoa.fijerena.core.ui.di.AppContainer
            .getInstance(context)
            .externalSwitches
            .collect {
                if (navController.currentBackStackEntry == null) return@collect
                clearSectionStacks()
                navController.navigate(Screen.ProfilePicker) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
    }
    // A deleted source waits for the player to close: what plays keeps playing.
    var sourceDeleted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        org.njarasoa.fijerena.core.ui.di.AppContainer
            .getInstance(context)
            .sourceDeletions
            .collect { sourceDeleted = true }
    }
    val playing =
        navController
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.hasRoute<Screen.Player>() == true
    LaunchedEffect(sourceDeleted, playing) {
        if (sourceDeleted && !playing) {
            sourceDeleted = false
            startOverFromHome()
        }
    }
    val accountManager = remember { AccountManager(context.applicationContext) }
    val appSettings = remember { AppSettings(context.applicationContext) }
    // The profile in use has Expert mode: only then does it add sources (Home's Add Source, the gate below).
    val expert by remember { appSettings.observeDevMode().map { it.second } }
        .collectAsStateWithLifecycle(initialValue = appSettings.isDevMode)
    // The clock over every screen, for every profile, when this device's Show Clock is on.
    val clock = remember { TvClockState() }
    val showClock by remember { appSettings.observeShowClock() }.collectAsStateWithLifecycle(initialValue = appSettings.showClock)
    LaunchedEffect(showClock) { clock.off = !showClock }
    val coroutineScope = rememberCoroutineScope()

    var hasProvider by remember { mutableStateOf<Boolean?>(null) }
    var initializationComplete by remember { mutableStateOf(false) }
    // "Who's watching?" on every launch once a second profile exists — the TV is shared, so the
    // last person's profile is a poor guess (the live sync plan → User profiles).
    var pickProfileAtLaunch by remember { mutableStateOf(false) }
    // No source at all and an Expert in use: Add Source over Settings at start (E1).
    var addSourceAtStart by remember { mutableStateOf(false) }
    var hasAutoSkippedSingleContentType by rememberSaveable { mutableStateOf(false) }

    // The navigation rail (the tv nav rail plan): drawn over the NavHost, given to
    // every screen as LocalTvNavRail.
    val rail = remember { TvNavRailState() }
    // A fresh Live TV opens its preview on this channel (see openSection), on its source: read once
    // by the section's first composition. Lost with the process, which only means browse instead.
    var liveTvOpenOn by remember { mutableStateOf<MediaItem?>(null) }

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

    // A section entered again comes back on Recent (the tv browse tabs plan), when it comes back on
    // its browse: one that comes back on a title keeps the list under it, so Back lands on the row.
    fun openBrowseOnRecent(section: TvSection) {
        val entry = navController.currentBackStackEntry ?: return
        if (entry.destination.hasRoute(section.route::class)) entry.savedStateHandle[OPEN_ON_RECENT] = true
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
        openBrowseOnRecent(section)
    }

    // Live TV opened fresh is the browse screen with the preview as a layer over it, open on the
    // last channel (LT7) — so Back from the preview lands on a real browse screen, same as Movies
    // and TV Shows. A restored Live TV ignores it: coming back never starts video on its own.
    // No last channel, no preview: it would render an empty pane with no way to reach the
    // categories, stranding a first-run user; OK on a channel in browse opens it anyway.
    fun openSection(section: TvSection) {
        if (section == TvSection.LIVE_TV) {
            coroutineScope.launch {
                // The newest of the profile's sources' last channels (multi-source plan, Phase 5).
                liveTvOpenOn = MergedLists.forSections(context)?.lastWatchedChannel()
                showSection(section)
            }
        } else {
            showSection(section)
        }
    }

    // The rail's picks (the tv nav rail plan → Phase 0 findings → Navigation rules).
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
                    // The only Search: an older one, and what it opened, goes first.
                    navController.navigateToSearch(Screen.Search(contentType))
                }
            }

            // On top of where you are, or back to its start from one of its screens.
            item == RailItem.SETTINGS -> {
                if (!navController.popBackStack(Screen.Settings, inclusive = false)) {
                    navController.navigate(Screen.Settings) { launchSingleTop = true }
                }
            }

            // From Settings (or Search) opened over a section, that section's item goes back to
            // where you were in it — the film, not the section's start.
            section != null && section == navController.currentSection() && rail.current != item -> {
                if (navController.currentDestination?.isSettingsTree() == true) popSettingsTree() else navController.popBackStack()
            }

            // The section you are in: back to its start, on Recent. Already there, nothing changes.
            section != null && section == navController.currentSection() -> {
                closeSavedLivePreview()
                if (navController.popBackStack(section.route, inclusive = false)) openBrowseOnRecent(section)
            }

            section != null -> {
                openSection(section)
            }
        }
    }

    // A Browse entry (O1): a Jellyfin or M3U source's own sections, on it alone — pushed over where
    // you are, as Search is, so Back returns there. With several sections, picked in a dialog.
    var browseChoice by remember { mutableStateOf<Pair<BrowseSource, List<String>>?>(null) }

    fun openBrowseSection(
        source: BrowseSource,
        contentType: String,
    ) {
        popSettingsTree()
        navController.navigateOnce(
            Screen.CategoryList(contentType = contentType, showPreviewPane = false, providerId = source.providerId),
        )
    }

    fun browseSource(source: BrowseSource) {
        coroutineScope.launch {
            when (val opening = ProfileSections.opening(context, source.providerId)) {
                // As Home's sign-in prompt: plain navigate, the rail is leaving focus.
                // The sign-in is the source's form, an Expert's only (PR-12): anyone else asks an admin.
                ProfileSections.Opening.SignIn -> {
                    if (expert) {
                        navController.navigate(Screen.AddProvider(editId = source.providerId)) { launchSingleTop = true }
                    } else {
                        android.widget.Toast
                            .makeText(context, R.string.browse_sign_in_ask_admin, android.widget.Toast.LENGTH_LONG)
                            .show()
                    }
                }

                is ProfileSections.Opening.Sections -> {
                    when (opening.contentTypes.size) {
                        0 -> Unit
                        1 -> openBrowseSection(source, opening.contentTypes.first())
                        else -> browseChoice = source to opening.contentTypes
                    }
                }
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
        if (hasProvider != true) {
            // A new install's first profile becomes Expert in the background startup work, maybe
            // after this point: the start needs it now (the call does nothing when there is one).
            ProfileRepository(context.applicationContext).ensureAnExpert()
            addSourceAtStart = startsOnAddSource(hasSource = false, expert = appSettings.isDevMode)
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
            } else if (addSourceAtStart) {
                // Add Source opens over it (the effect after the NavHost).
                Screen.Settings
            } else {
                // Not Expert: Home says to ask a grown-up for a source.
                Screen.ContentTypeSelection
            }
        }

    // The first source saved: Home, from the root (E1). No screen may keep a repository from before
    // it: clearAllCaches(), then start over like a profile switch (every screen below holds a
    // repository clearAllCaches() just closed). See the rock solid stability resilience plan → F-18.
    fun startOverAfterFirstSource() {
        coroutineScope.launch {
            org.njarasoa.fijerena.core.ui.di.AppContainer
                .getInstance(context)
                .clearAllCaches()
            clearSectionStacks()
            navController.navigateOnce(Screen.ContentTypeSelection) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // No session restore here: each Xtream source of the profile restores its own when it is first
    // used (AppContainer.getMediaRepository / getStoredDataRepository), as there is no source in use.

    // A category list — a section's browse, or one pushed from Search, a guide or a title — with
    // the routes its rows and header open.
    @Composable
    fun CategoryGrid(
        contentType: String,
        initialCategoryId: String? = null,
        initialStreamId: String? = null,
        showPreviewPane: Boolean = true,
        closeSavedLivePreview: Boolean = false,
        openOnRecent: Boolean = false,
        // The source the list was opened on (0: a section's, every Xtream source merged).
        providerId: Long = 0L,
        // initialStreamId's source when it isn't the list's (0 = the list's).
        initialStreamSource: Long = 0L,
    ) {
        TvCategoryGridScreen(
            contentType = contentType,
            initialCategoryId = initialCategoryId,
            initialStreamId = initialStreamId,
            initialStreamSource = initialStreamSource,
            showPreviewPane = showPreviewPane,
            closeSavedLivePreview = closeSavedLivePreview,
            openOnRecent = openOnRecent,
            providerId = providerId,
            onStreamSelected = { itemId, streamName, categoryId, target, rowSource ->
                // A merged Recent / Favourites row opens on its own source (multi-source plan, Phase 4).
                val source = rowSource.takeIf { it != 0L } ?: providerId
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
                                providerId = source,
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
                                providerId = source,
                            ),
                        )
                    }

                    is BrowseTarget.Movie -> {
                        navController.navigateOnce(
                            Screen.MovieDetails(
                                movieId = target.movieId,
                                movieName = streamName,
                                categoryId = categoryId,
                                providerId = source,
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
                                providerId = source,
                            ),
                        )
                    }

                    // Browsed into by the list screen itself, unless it is another source's
                    // category (Recent Categories across sources): that source's list, on it.
                    is BrowseTarget.CategoryRef -> {
                        navController.navigateOnce(
                            Screen.CategoryList(
                                contentType = contentType,
                                initialCategoryId = target.categoryId,
                                showPreviewPane = false,
                                providerId = source,
                            ),
                        )
                    }
                }
            },
            onEpgClick = { categoryId, categoryName, focusChannelId, source ->
                // The TV Guide for a category (the header button), or for the playing
                // channel's list from the player's Guide button, on that channel's row — on the
                // category's (or channel's) own source; 0: the list's.
                navController.navigateOnce(
                    Screen.EpgGuide(
                        categoryId = categoryId,
                        categoryName = categoryName,
                        focusChannelId = focusChannelId,
                        providerId = source.takeIf { it != 0L } ?: providerId,
                    ),
                )
            },
            // Start over (catch-up): the programme on air from its start, full screen, on the
            // channel's own source. Back returns to the Live TV preview.
            onStartOver = { program, channel ->
                navController.navigateOnce(catchupRoute(program, channel, channel.providerId.takeIf { it != 0L } ?: providerId))
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
        val openOnRecent = remember { entry.savedStateHandle.remove<Boolean>(OPEN_ON_RECENT) == true }
        BackHandler(enabled = rail.visible) {
            if (rail.expanded || !rail.focusRail()) {
                navController.popBackStack(Screen.ContentTypeSelection, inclusive = false, saveState = true)
            }
        }
        CategoryGrid(
            contentType = section.contentType,
            initialStreamId = openOn?.id,
            initialStreamSource = openOn?.providerId ?: 0L,
            showPreviewPane = false,
            closeSavedLivePreview = closeSavedPreview,
            openOnRecent = openOnRecent,
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape,
    ) {
        if (initializationComplete && startDestination != null) {
            CompositionLocalProvider(
                LocalTvNavRail provides rail,
                LocalTvClock provides clock,
                LocalOpenWanted provides { navController.navigateOnce(it) },
            ) {
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
                                onWantedSelected = { navController.navigateOnce(it) },
                                onCapabilitiesResolved = { mergedTypes ->
                                    // Skip the picker tap entirely when the profile has one section,
                                    // but only on the very first resolve per NavHost lifetime, so
                                    // Back-navigation into this screen later still lands on a real,
                                    // interactive Home.
                                    if (!hasAutoSkippedSingleContentType) {
                                        hasAutoSkippedSingleContentType = true
                                        if (mergedTypes.size == 1) {
                                            org.njarasoa.fijerena.core.navigation.ContentType
                                                .fromString(mergedTypes.first())
                                                ?.let(navigateToContentType)
                                        }
                                    }
                                },
                                onBrowse = ::browseSource,
                                onAddSource = if (expert) ({ navController.navigateOnce(Screen.AddProvider()) }) else null,
                                onEpgBrowser = {
                                    navController.navigateOnce(Screen.EpgBrowser())
                                },
                                // A Live row channel opens its preview, zapping through the list the card
                                // came from (TV home overhaul plan, Phase 4); Back pops back to Home.
                                onLiveChannelSelected = { streamId, listId, source ->
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = ContentType.LIVE_TV,
                                            initialCategoryId = listId,
                                            initialStreamId = streamId,
                                            providerId = source,
                                        ),
                                    )
                                },
                                // Same routes as the lists' onStreamSelected (item.id is the movie or series id).
                                // A chart's TMDB-only card (no source has it) opens its TMDB page.
                                onFavoriteSelected = { item, favoriteType ->
                                    val tmdbOnly = TmdbOnlyItem.titleOf(item, favoriteType)
                                    if (tmdbOnly != null) {
                                        navController.navigateOnce(tmdbOnly.route())
                                    } else if (favoriteType == ContentType.TV_SHOWS) {
                                        navController.navigateOnce(
                                            Screen.EpisodeSelection(
                                                seriesId = item.id,
                                                seriesName = item.name,
                                                categoryId = item.categoryId,
                                                providerId = item.providerId,
                                            ),
                                        )
                                    } else {
                                        navController.navigateOnce(
                                            Screen.MovieDetails(
                                                movieId = item.id,
                                                movieName = item.name,
                                                categoryId = item.categoryId,
                                                providerId = item.providerId,
                                            ),
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
                                                    providerId = item.providerId,
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
                                                    providerId = item.providerId,
                                                ),
                                            )
                                        }

                                        is BrowseTarget.Movie -> {
                                            navController.navigateOnce(
                                                Screen.MovieDetails(
                                                    movieId = target.movieId,
                                                    movieName = item.name,
                                                    categoryId = item.categoryId,
                                                    providerId = item.providerId,
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

                        composable<Screen.Licenses> {
                            org.njarasoa.fijerena.feature.settings.LicensesScreen(
                                onOpen = { id -> navController.navigateOnce(Screen.LicenseDetail(id)) },
                            )
                        }

                        composable<Screen.Terms> {
                            org.njarasoa.fijerena.feature.settings
                                .TermsScreen()
                        }

                        composable<Screen.LicenseDetail> { backStackEntry ->
                            org.njarasoa.fijerena.feature.settings.LicenseDetailScreen(
                                entryId = backStackEntry.toRoute<Screen.LicenseDetail>().entryId,
                            )
                        }

                        composable<Screen.EpgBrowser> { backStackEntry ->
                            val browserScreen = backStackEntry.toRoute<Screen.EpgBrowser>()
                            TvEpgBrowserScreen(
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
                                    // Land on the preview pane, not full-screen — see LiveTvSplitLayout.
                                    // Pushing (not popUpTo) a new CategoryList entry means Back from the
                                    // preview pops back here for free via normal nav-stack semantics.
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
                            val categoryListScreen = backStackEntry.toRoute<Screen.CategoryList>()
                            CategoryGrid(
                                contentType = categoryListScreen.contentType,
                                initialCategoryId = categoryListScreen.initialCategoryId,
                                initialStreamId = categoryListScreen.initialStreamId,
                                showPreviewPane = categoryListScreen.showPreviewPane,
                                providerId = categoryListScreen.providerId,
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

                        composable<Screen.TmdbTitle> { backStackEntry ->
                            val route = backStackEntry.toRoute<Screen.TmdbTitle>()
                            TmdbTitleScreen(
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

                        composable<Screen.Person> { backStackEntry ->
                            val route = backStackEntry.toRoute<Screen.Person>()
                            PersonScreen(
                                tmdbId = route.tmdbId,
                                name = route.name,
                                crew = route.crew,
                                // As Home's chart cards: a TMDB-only card opens its TMDB page, else the title's source page.
                                onTitleSelected = { item, contentType ->
                                    val tmdbOnly = TmdbOnlyItem.titleOf(item, contentType)
                                    when {
                                        tmdbOnly != null -> {
                                            navController.navigateOnce(tmdbOnly.route())
                                        }

                                        contentType == ContentType.TV_SHOWS -> {
                                            navController.navigateOnce(
                                                Screen.EpisodeSelection(
                                                    seriesId = item.id,
                                                    seriesName = item.name,
                                                    categoryId = item.categoryId,
                                                    providerId = item.providerId,
                                                ),
                                            )
                                        }

                                        else -> {
                                            navController.navigateOnce(
                                                Screen.MovieDetails(
                                                    movieId = item.id,
                                                    movieName = item.name,
                                                    categoryId = item.categoryId,
                                                    providerId = item.providerId,
                                                ),
                                            )
                                        }
                                    }
                                },
                                onBack = { navController.navigateUp() },
                            )
                        }

                        composable<Screen.Search> { backStackEntry ->
                            val searchScreen = backStackEntry.toRoute<Screen.Search>()
                            SearchScreen(
                                contentType = searchScreen.contentType,
                                onTmdbTitleSelected = { navController.navigateOnce(it.route()) },
                                onSearchEverything =
                                    { query: String ->
                                        navController.navigateToSearch(
                                            Screen.Search(SearchViewModel.CONTENT_TYPE_ALL, initialQuery = query),
                                        )
                                    }.takeIf { searchScreen.contentType != SearchViewModel.CONTENT_TYPE_ALL },
                                onStreamSelected = { itemId, streamName, categoryId, streamContentType, providerId ->
                                    when (streamContentType) {
                                        ContentType.TV_SHOWS -> {
                                            navController.navigateOnce(
                                                Screen.EpisodeSelection(
                                                    seriesId = itemId,
                                                    seriesName = streamName,
                                                    categoryId = categoryId,
                                                    providerId = providerId,
                                                ),
                                            )
                                        }

                                        ContentType.MOVIES -> {
                                            navController.navigateOnce(
                                                Screen.MovieDetails(
                                                    movieId = itemId,
                                                    movieName = streamName,
                                                    categoryId = categoryId,
                                                    providerId = providerId,
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
                                                    providerId = providerId,
                                                ),
                                            )
                                        }
                                    }
                                },
                                onCategorySelected = { categoryId, contentType, providerId ->
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = contentType,
                                            initialCategoryId = categoryId,
                                            providerId = providerId,
                                        ),
                                    )
                                },
                                // As Search the guide: the preview pane on the airing's channel; Back
                                // pops back to the Guide tab.
                                onGuideResult = { matched ->
                                    navController.navigateOnce(
                                        Screen.CategoryList(
                                            contentType = ContentType.LIVE_TV,
                                            initialCategoryId = matched.categoryId,
                                            initialStreamId = matched.streamId.toString(),
                                            providerId = matched.providerId,
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
                            EpisodeSelectionScreen(
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

                        composable<Screen.EpgGuide> { backStackEntry ->
                            val epgScreen = backStackEntry.toRoute<Screen.EpgGuide>()
                            TvEpgGuideScreen(
                                categoryId = epgScreen.categoryId,
                                categoryName = epgScreen.categoryName,
                                focusChannelId = epgScreen.focusChannelId,
                                providerId = epgScreen.providerId,
                                // Each row on its own source: a Recent or Favourites guide lists every source's.
                                onProgramSelected = { _, channel ->
                                    // Land on the preview pane, not full-screen. Pushing (not popUpTo)
                                    // means Back from the preview pops back to the EPG guide for free.
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
                                catchup =
                                    playerScreen.catchupStartSec?.let {
                                        CatchupWindow(it, playerScreen.catchupDurationSec, playerScreen.catchupOffsetSec)
                                    },
                                programTitle = playerScreen.programTitle,
                                providerId = playerScreen.providerId,
                                // Catch-up's Watch live: the channel's preview in place of the player,
                                // so Back returns where the catch-up was opened from.
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
                                onHome = { navController.popBackStack(Screen.ContentTypeSelection, inclusive = false) },
                            )
                        }

                        composable<Screen.AddProvider> { backStackEntry ->
                            HideTvNavRail()
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
                            TvAddProviderScreen(
                                editId = addProviderScreen.editId,
                                onBack = {
                                    navController.navigateUp()
                                },
                                onSuccess = {
                                    appSettings.hasProviderCache = true
                                    if (homeAfterSave) startOverAfterFirstSource() else navController.navigateUp()
                                },
                                onGuideSources = { id ->
                                    navController.navigateOnce(Screen.EpgManagement(providerId = id))
                                },
                            )
                        }

                        composable<Screen.ProviderSelection> {
                            TvProviderSelectionScreen(
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

                        composable<Screen.EpgManagement> { backStackEntry ->
                            val epgScreen = backStackEntry.toRoute<Screen.EpgManagement>()
                            TvEpgManagementScreen(
                                providerId = epgScreen.providerId,
                                onBack = { navController.navigateUp() },
                            )
                        }

                        composable<Screen.ProfilePicker> {
                            HideTvNavRail()
                            // The launch picker is the first screen: Back there doesn't leave the
                            // app (household UX plan, 2026-10-19). From the rail, Back returns.
                            BackHandler(enabled = navController.previousBackStackEntry == null) {}
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
                                onUiScaleChanged = onUiScaleChanged,
                                onManageProviders = {
                                    navController.navigateOnce(Screen.ProviderSelection)
                                },
                                onLiveSync = {
                                    navController.navigateOnce(Screen.SyncSettings)
                                },
                                onGuideSources = {
                                    navController.navigateOnce(Screen.EpgManagement())
                                },
                                onDiagnostics = {
                                    navController.navigateOnce(Screen.Diagnostics)
                                },
                                onDeviceInfo = {
                                    navController.navigateOnce(Screen.DeviceInfo)
                                },
                                onLicenses = {
                                    navController.navigateOnce(Screen.Licenses)
                                },
                                onTerms = {
                                    navController.navigateOnce(Screen.Terms)
                                },
                                onProfileSwitched = {
                                    // As after the profile picker: every screen below may hold the previous
                                    // profile's repository, so start over from home.
                                    clearSectionStacks()
                                    navController.navigate(Screen.ContentTypeSelection) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                },
                                // Settings opened with no source and has one on its return (a restore,
                                // live sync): Home, as after the first save.
                                onProviderChanged = ::startOverAfterFirstSource,
                            )
                        }
                    }
                    // E1: Add Source over Settings, once (a recreated Activity keeps its back stack).
                    var addSourceOpened by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (addSourceAtStart && !addSourceOpened) {
                            addSourceOpened = true
                            navController.navigate(Screen.AddProvider())
                        }
                    }
                    // Over the screen, under the rail's scrim.
                    TvClockOverlay(clock)
                    // After the NavHost, so it draws over the screen and its scrim covers it.
                    TvSectionRail(
                        rail = rail,
                        navController = navController,
                        onSelect = ::selectRailItem,
                        onBrowse = ::browseSource,
                    )
                    browseChoice?.let { (source, contentTypes) ->
                        BrowseSectionsDialog(
                            source = source,
                            contentTypes = contentTypes,
                            onPick = { contentType ->
                                browseChoice = null
                                openBrowseSection(source, contentType)
                            },
                            onDismiss = { browseChoice = null },
                        )
                    }
                }
            }
        } else {
            // Provider lookup / credential migration / session restore — used to be a blank
            // window for however long that took.
            AppLoadingScreen()
        }
    }

    // The screens Settings opens ask the PIN of the profile in use, once per session (locked again
    // after a while in the background or a switch of profile); Settings asks it before opening them,
    // this is the backstop. Picking a profile and watching never ask. Cancel goes back to Settings.
    if (startDestination != null) {
        val gateProfiles: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
        val inUse by gateProfiles.activeProfile.collectAsStateWithLifecycle()
        val entry = navController.currentBackStackEntryAsState().value
        val destination = entry?.destination
        // The household's shared setup (sources, guide sources, live sync, Parental controls) is
        // the Expert's only, whatever opened it: closed at once.
        val refused = !expert && entry.isExpertOnly()
        LaunchedEffect(entry, refused) { if (refused) navController.popBackStack() }
        // A child profile has no Settings: a restored back stack, a switch to a child or a stale
        // route closes it and goes Home. Safe mode's Diagnostics stays.
        val childInSettings =
            remember(entry, inUse?.id) {
                destination?.isSettingsTree() == true && !SafeMode.isActive && appSettings.isChildProfile(appSettings.activeProfileId)
            }
        LaunchedEffect(entry, childInSettings) {
            if (childInSettings && !navController.popBackStack(Screen.ContentTypeSelection, inclusive = false)) {
                navController.navigate(Screen.ContentTypeSelection) { popUpTo(navController.graph.id) { inclusive = true } }
            }
        }
        inUse?.let { profile ->
            LaunchedEffect(profile.id) { PinSession.onActiveProfile(profile.id) }
            if (!refused && !childInSettings && destination.isPinGuarded() && gateProfiles.needsPin(profile.id)) {
                // Nothing of Settings shows behind the prompt.
                Box(Modifier.fillMaxSize().background(CinemaTheme.colors.background))
                TvPinDialog(
                    state =
                        rememberPinPromptState(
                            gateProfiles.pins,
                            PinRequest.Check(profile.id, stringResource(R.string.pin_enter_for, profile.name)),
                        ) {},
                    onCancel = {
                        while (navController.currentDestination.isPinGuarded() && navController.popBackStack()) Unit
                    },
                )
            }
        }
    }
}

/** Set on Live TV's root entry when a rail pick leaves it: see closeSavedLivePreview in [TvNavHost]. */
private const val CLOSE_SAVED_LIVE_PREVIEW = "closeSavedLivePreview"

/** Set on a section's root entry when it is entered again on its browse: see openBrowseOnRecent in [TvNavHost]. */
private const val OPEN_ON_RECENT = "openOnRecent"
