package org.njarasoa.fijerena.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.components.APP_LOADING_MIN_MS
import org.njarasoa.fijerena.core.ui.components.AppLoadingScreen
import org.njarasoa.fijerena.feature.category.TvCategoryGridScreen
import org.njarasoa.fijerena.feature.contentselection.ContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.epg.TvEpgGuideScreen
import org.njarasoa.fijerena.feature.epg.TvEpgManagementScreen
import org.njarasoa.fijerena.feature.epgbrowser.TvEpgBrowserScreen
import org.njarasoa.fijerena.feature.episode.EpisodeSelectionScreen
import org.njarasoa.fijerena.feature.movie.MovieDetailsScreen
import org.njarasoa.fijerena.feature.player.TvPlayerScreen
import org.njarasoa.fijerena.feature.provider.TvAddProviderScreen
import org.njarasoa.fijerena.feature.provider.TvProviderSelectionScreen
import org.njarasoa.fijerena.feature.search.SearchScreen
import org.njarasoa.fijerena.feature.settings.EditProviderScreen
import org.njarasoa.fijerena.feature.settings.SettingsScreen

/**
 * TV-optimized navigation host with D-pad focus management.
 *
 * Features:
 * - Type-safe navigation using kotlinx.serialization
 * - D-pad friendly focus restoration between destinations
 * - Shared AuthViewModel for session management
 * - Automatic navigation to CategoryList after login
 * - androidx.tv.material3 components throughout
 *
 * Navigation Flow:
 * 1. Login screen (unauthenticated users)
 * 2. CategoryList screen (after successful login)
 * 3. Player screen (when stream is selected)
 *
 * @param navController Optional NavController (defaults to rememberNavController)
 * @param authViewModel Shared authentication ViewModel
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
    val accountManager = remember { AccountManager(context.applicationContext) }
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // Use mutable states for asynchronous data loading
    var hasProvider by remember { mutableStateOf<Boolean?>(null) }
    var initializationComplete by remember { mutableStateOf(false) }
    var hasAutoSkippedSingleContentType by rememberSaveable { mutableStateOf(false) }

    // Async initialization — use cached provider flag for instant start destination,
    // then verify with Room DB in background
    LaunchedEffect(Unit) {
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
        initializationComplete = true
    }

    val isAuthenticated by authViewModel.authResponse.collectAsStateWithLifecycle()

    // Floor on the loading screen's time so its animation is actually seen — see
    // APP_LOADING_MIN_MS for why nothing in front of it can move.
    var minimumShownElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(APP_LOADING_MIN_MS)
        minimumShownElapsed = true
    }

    // Determine initial destination based on provider configuration
    val startDestination =
        remember(initializationComplete, hasProvider) {
            if (!initializationComplete) {
                null
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
                val repo = org.njarasoa.fijerena.core.ui.di.AppContainer
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape,
    ) {
        if (initializationComplete && startDestination != null && minimumShownElapsed) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                // Content Type Selection Screen
                composable<Screen.ContentTypeSelection> {
                    // Prevent back button from exiting the app on the root screen
                    BackHandler {}
                    val navigateToContentType: (org.njarasoa.fijerena.core.navigation.ContentType) -> Unit = { contentType ->
                        if (contentType.name == ContentType.LIVE_TV) {
                            // Live TV never lands full-screen or bare — silently push the
                            // classic categories/streams browse screen first (so Back from the
                            // preview below lands on a real screen, same as Movies/TV Shows),
                            // then push the preview on top of it.
                            navController.navigate(
                                Screen.CategoryList(contentType.name, showPreviewPane = false),
                            ) {
                                popUpTo(Screen.ContentTypeSelection) { inclusive = false }
                            }
                            navController.navigate(
                                Screen.CategoryList(contentType.name, showPreviewPane = true),
                            )
                        } else {
                            navController.navigate(Screen.CategoryList(contentType.name)) {
                                popUpTo(Screen.ContentTypeSelection) { inclusive = false }
                            }
                        }
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
                            navController.navigate(Screen.Settings)
                        },
                        onSearch = {
                            navController.navigate(Screen.Search("ALL"))
                        },
                        onEpgBrowser = {
                            navController.navigate(Screen.EpgBrowser)
                        },
                    )
                }

                // EPG Browser Screen
                composable<Screen.EpgBrowser> {
                    TvEpgBrowserScreen(
                        onBack = { navController.navigateUp() },
                        onNavigateToPlayer = { streamId, _, categoryId ->
                            // Land on the preview pane, not full-screen — see LiveTvSplitLayout.
                            // Pushing (not popUpTo) a new CategoryList entry means Back from the
                            // preview pops back here for free via normal nav-stack semantics.
                            navController.navigate(
                                Screen.CategoryList(
                                    contentType = ContentType.LIVE_TV,
                                    initialCategoryId = categoryId,
                                    initialStreamId = streamId,
                                ),
                            )
                        },
                    )
                }

                // Edit Provider Screen
                composable<Screen.EditProvider> {
                    EditProviderScreen(
                        onBack = {
                            navController.navigateUp()
                        },
                        onSuccess = {
                            // After successful update, navigate back to category list
                            navController.navigateUp()
                        },
                    )
                }

                // Category List Screen
                composable<Screen.CategoryList> { backStackEntry ->
                    val categoryListScreen = backStackEntry.toRoute<Screen.CategoryList>()
                    TvCategoryGridScreen(
                        contentType = categoryListScreen.contentType,
                        initialCategoryId = categoryListScreen.initialCategoryId,
                        initialStreamId = categoryListScreen.initialStreamId,
                        showPreviewPane = categoryListScreen.showPreviewPane,
                        onStreamSelected = { itemId, streamName, categoryId, providerData ->
                            when (categoryListScreen.contentType) {
                                ContentType.TV_SHOWS -> {
                                    val episodeId = providerData["episodeId"]
                                    if (providerData["resumeSeries"] == "true") {
                                        // Continue Watching: this card represents the show, not
                                        // the episode — open episode selection with the
                                        // last-watched episode's detail/resume panel already up.
                                        navController.navigate(
                                            Screen.EpisodeSelection(
                                                seriesId = itemId,
                                                seriesName = streamName,
                                                categoryId = categoryId,
                                                initialEpisodeId = episodeId,
                                            ),
                                        )
                                    } else if (episodeId != null) {
                                        // Last-watched episode: go directly to player
                                        navController.navigate(
                                            Screen.Player(
                                                streamId = itemId,
                                                streamName = streamName,
                                                categoryId = categoryId,
                                                contentType = ContentType.TV_SHOWS,
                                                episodeId = episodeId,
                                                episodeExtension = providerData["episodeExtension"],
                                                seriesId = providerData["seriesId"],
                                                seriesName = providerData["seriesName"],
                                            ),
                                        )
                                    } else {
                                        // Regular series: navigate to episode selection
                                        navController.navigate(
                                            Screen.EpisodeSelection(
                                                seriesId = itemId,
                                                seriesName = streamName,
                                                categoryId = categoryId,
                                            ),
                                        )
                                    }
                                }
                                ContentType.MOVIES -> {
                                    // For movies, navigate to movie details
                                    navController.navigate(
                                        Screen.MovieDetails(
                                            movieId = itemId,
                                            movieName = streamName,
                                            categoryId = categoryId,
                                        ),
                                    )
                                }
                                else -> {
                                    // Live TV: land on the preview pane, not full-screen. Reachable
                                    // from the classic browse screen too (showPreviewPane=false,
                                    // e.g. the one silently pushed under the main-menu preview) —
                                    // same rule applies there as everywhere else.
                                    navController.navigate(
                                        Screen.CategoryList(
                                            contentType = categoryListScreen.contentType,
                                            initialCategoryId = categoryId,
                                            initialStreamId = itemId,
                                        ),
                                    )
                                }
                            }
                        },
                        onSearchClick = {
                            navController.navigate(Screen.Search(categoryListScreen.contentType))
                        },
                        onEpgClick = { categoryId, categoryName ->
                            // Navigate to EPG Guide for the selected category
                            navController.navigate(
                                Screen.EpgGuide(
                                    categoryId = categoryId,
                                    categoryName = categoryName,
                                ),
                            )
                        },
                        onBack = {
                            // A single pop always lands on whatever pushed this entry — the
                            // content-type screen normally, or the silently-pushed bare browse
                            // screen underneath the Live TV main-menu preview, or the search/EPG
                            // screen underneath a search/EPG-originated preview.
                            navController.popBackStack()
                        },
                    )
                }

                // Search Screen
                composable<Screen.Search> { backStackEntry ->
                    val searchScreen = backStackEntry.toRoute<Screen.Search>()
                    SearchScreen(
                        contentType = searchScreen.contentType,
                        onStreamSelected = { itemId, streamName, categoryId, streamContentType ->
                            // Navigate based on content type
                            when (streamContentType) {
                                ContentType.TV_SHOWS ->
                                    navController.navigate(
                                        Screen.EpisodeSelection(
                                            seriesId = itemId,
                                            seriesName = streamName,
                                            categoryId = categoryId,
                                        ),
                                    )
                                ContentType.MOVIES ->
                                    navController.navigate(
                                        Screen.MovieDetails(
                                            movieId = itemId,
                                            movieName = streamName,
                                            categoryId = categoryId,
                                        ),
                                    )
                                else ->
                                    // Live TV: land on the preview pane, not full-screen. Pushing
                                    // (not popUpTo) means Back from the preview pops back to these
                                    // search results for free via normal nav-stack semantics.
                                    navController.navigate(
                                        Screen.CategoryList(
                                            contentType = streamContentType,
                                            initialCategoryId = categoryId,
                                            initialStreamId = itemId,
                                        ),
                                    )
                            }
                        },
                        onCategorySelected = { categoryId, contentType ->
                            navController.navigate(
                                Screen.CategoryList(
                                    contentType = contentType,
                                    initialCategoryId = categoryId,
                                ),
                            )
                        },
                        onBack = { navController.navigateUp() },
                    )
                }

                // Movie Details Screen (for VOD Movies)
                composable<Screen.MovieDetails> { backStackEntry ->
                    val movieDetailsScreen = backStackEntry.toRoute<Screen.MovieDetails>()
                    MovieDetailsScreen(
                        movieId = movieDetailsScreen.movieId,
                        movieName = movieDetailsScreen.movieName,
                        categoryId = movieDetailsScreen.categoryId,
                        onPlayMovie = { movieId, movieName, extension, startFromBeginning ->
                            navController.navigate(
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
                            navController.navigate(
                                Screen.CategoryList(
                                    contentType = ContentType.MOVIES,
                                    initialCategoryId = categoryId,
                                ),
                            )
                        },
                        onBack = {
                            navController.navigateUp()
                        },
                    )
                }

                // Episode Selection Screen (for TV Shows)
                composable<Screen.EpisodeSelection> { backStackEntry ->
                    val episodeSelectionScreen = backStackEntry.toRoute<Screen.EpisodeSelection>()
                    EpisodeSelectionScreen(
                        seriesId = episodeSelectionScreen.seriesId,
                        seriesName = episodeSelectionScreen.seriesName,
                        categoryId = episodeSelectionScreen.categoryId,
                        initialEpisodeId = episodeSelectionScreen.initialEpisodeId,
                        onEpisodeSelected = { episodeId, episodeTitle, extension, startFromBeginning ->
                            navController.navigate(
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
                            navController.navigate(
                                Screen.CategoryList(
                                    contentType = ContentType.TV_SHOWS,
                                    initialCategoryId = categoryId,
                                ),
                            )
                        },
                        onBack = {
                            navController.navigateUp()
                        },
                    )
                }

                // EPG Guide Screen
                composable<Screen.EpgGuide> { backStackEntry ->
                    val epgScreen = backStackEntry.toRoute<Screen.EpgGuide>()
                    TvEpgGuideScreen(
                        categoryId = epgScreen.categoryId,
                        categoryName = epgScreen.categoryName,
                        onProgramSelected = { _, channel ->
                            // Land on the preview pane, not full-screen. Pushing (not popUpTo)
                            // means Back from the preview pops back to the EPG guide for free.
                            navController.navigate(
                                Screen.CategoryList(
                                    contentType = ContentType.LIVE_TV,
                                    initialCategoryId = channel.categoryId,
                                    initialStreamId = channel.id,
                                ),
                            )
                        },
                        onChannelSelected = { streamId, _, categoryId ->
                            navController.navigate(
                                Screen.CategoryList(
                                    contentType = ContentType.LIVE_TV,
                                    initialCategoryId = categoryId,
                                    initialStreamId = streamId,
                                ),
                            )
                        },
                        onBack = {
                            navController.navigateUp()
                        },
                    )
                }

                // Player Screen
                composable<Screen.Player> { backStackEntry ->
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
                    )
                }

                // Add/Edit Provider Screen
                composable<Screen.AddProvider> { backStackEntry ->
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
                    )
                }

                // Provider Selection Screen
                composable<Screen.ProviderSelection> {
                    TvProviderSelectionScreen(
                        onProviderSelected = { provider ->
                            coroutineScope.launch {
                                // Activate the selected provider in Room
                                val providerRepo = ProviderRepository(context.applicationContext)
                                providerRepo.setActiveProvider(provider.id)

                                // Clear AppContainer caches to force a fresh repository for the new provider
                                val container = org.njarasoa.fijerena.core.ui.di.AppContainer.getInstance(context)
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
                                navController.navigate(Screen.ContentTypeSelection) {
                                    popUpTo(Screen.ProviderSelection) { inclusive = true }
                                }
                            }
                        },
                        onAddProvider = {
                            navController.navigate(Screen.AddProvider())
                        },
                        onEditProvider = { id ->
                            navController.navigate(Screen.AddProvider(editId = id))
                        },
                        onManageEpg = { id ->
                            navController.navigate(Screen.EpgManagement(providerId = id))
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

                // Settings Screen
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
                            navController.navigate(Screen.ProviderSelection)
                        },
                        onProviderChanged = {
                            coroutineScope.launch {
                                val providerRepo = ProviderRepository(context.applicationContext)
                                val activeProvider = providerRepo.getActiveProvider()

                                // Clear AppContainer caches for the new provider
                                val container = org.njarasoa.fijerena.core.ui.di.AppContainer.getInstance(context)
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

                                navController.navigate(Screen.ContentTypeSelection) {
                                    popUpTo(Screen.Settings) { inclusive = false }
                                }
                            }
                        },
                    )
                }
            }
        } else {
            // Provider lookup / credential migration / session restore — used to be a blank
            // window for however long that took.
            AppLoadingScreen()
        }
    }
}
