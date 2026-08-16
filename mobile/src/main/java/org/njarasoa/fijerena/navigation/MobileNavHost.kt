package org.njarasoa.fijerena.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.feature.category.MobileCategoryListScreen
import org.njarasoa.fijerena.feature.contentselection.MobileContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.epg.MobileEpgGuideScreen
import org.njarasoa.fijerena.feature.epg.MobileEpgManagementScreen
import org.njarasoa.fijerena.feature.epgbrowser.MobileEpgBrowserScreen
import org.njarasoa.fijerena.feature.episode.MobileEpisodeSelectionScreen
import org.njarasoa.fijerena.feature.movie.MobileMovieDetailsScreen
import org.njarasoa.fijerena.feature.player.MobilePlayerScreen
import org.njarasoa.fijerena.feature.provider.MobileAddProviderScreen
import org.njarasoa.fijerena.feature.provider.MobileProviderSelectionScreen
import org.njarasoa.fijerena.feature.search.MobileSearchScreen
import org.njarasoa.fijerena.feature.settings.MobileCellularBufferSettingsScreen
import org.njarasoa.fijerena.feature.settings.MobileSettingsScreen

/**
 * Mobile navigation host with Material3 transitions.
 *
 * Features:
 * - Type-safe navigation using kotlinx.serialization
 * - Smooth slide and fade transitions between screens
 * - Shared AuthViewModel for session management
 * - Automatic navigation to CategoryList after login
 * - Standard Material3 design
 *
 * Navigation Flow:
 * 1. Login screen (unauthenticated users)
 * 2. CategoryList screen (after successful login)
 * 3. Player screen (when stream is selected)
 *
 * @param navController Optional NavController (defaults to rememberNavController)
 * @param authViewModel Shared authentication ViewModel
 */
@Composable
fun MobileNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    onThemeChanged: (String) -> Unit = {},
    onUiStyleChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val accountManager = remember { AccountManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // Async initialization: migrate legacy creds, determine start destination
    var hasProvider by remember { mutableStateOf<Boolean?>(null) }
    var hasAutoSkippedSingleContentType by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
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

    val isAuthenticated by authViewModel.authResponse.collectAsStateWithLifecycle()

    // Show nothing until initialization completes
    if (hasProvider == null) return

    // Determine initial destination based on provider configuration
    val startDestination =
        if (hasProvider == true) {
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

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(CinemaAnimation.navTransitionMs),
                ) + fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(CinemaAnimation.navTransitionMs),
                ) + fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(CinemaAnimation.navTransitionMs),
                ) + fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(CinemaAnimation.navTransitionMs),
                ) + fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
            },
        ) {
            // Content Type Selection Screen
            composable<Screen.ContentTypeSelection> {
                val navigateToContentType: (String) -> Unit = { contentType ->
                    navController.navigate(Screen.CategoryList(contentType))
                }
                MobileContentTypeSelectionScreen(
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
                                navigateToContentType(supportedTypes.first())
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
                MobileEpgBrowserScreen(
                    onBack = { navController.navigateUp() },
                    onNavigateToPlayer = { streamId, _, categoryId ->
                        // Land on the docked mini-player, not full-screen — same parity as every
                        // other Live TV entry point.
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

            // Category List Screen
            composable<Screen.CategoryList> { backStackEntry ->
                val categoryListScreen = backStackEntry.toRoute<Screen.CategoryList>()
                MobileCategoryListScreen(
                    contentType = categoryListScreen.contentType,
                    initialCategoryId = categoryListScreen.initialCategoryId,
                    initialStreamId = categoryListScreen.initialStreamId,
                    onStreamSelected = { itemId, itemName, categoryId, contentType, providerData ->
                        when (categoryListScreen.contentType) {
                            ContentType.TV_SHOWS -> {
                                val episodeId = providerData["episodeId"]
                                if (providerData["resumeSeries"] == "true") {
                                    // Continue Watching: this card represents the show, not the
                                    // episode — open episode selection with the last-watched
                                    // episode's detail/resume panel already up.
                                    navController.navigate(
                                        Screen.EpisodeSelection(
                                            seriesId = itemId,
                                            seriesName = itemName,
                                            categoryId = categoryId,
                                            initialEpisodeId = episodeId,
                                        ),
                                    )
                                } else if (episodeId != null) {
                                    // Last-watched episode: go directly to player
                                    navController.navigate(
                                        Screen.Player(
                                            streamId = itemId,
                                            streamName = itemName,
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
                                            seriesName = itemName,
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
                                        movieName = itemName,
                                        categoryId = categoryId,
                                    ),
                                )
                            }
                            else -> {
                                // Live TV: unreachable in practice for a genuine stream tap —
                                // MobileCategoryListScreen docks it locally instead of calling
                                // this callback (mirrors TV's LiveTvChannelList.onStreamPromote
                                // interception). Kept as a fallback for the "not resolvable from
                                // the current list" case, same as TV.
                                navController.navigate(Screen.Player(itemId, itemName, categoryId, contentType))
                            }
                        }
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search(categoryListScreen.contentType))
                    },
                    onEpgClick = { categoryId, categoryName ->
                        navController.navigate(
                            Screen.EpgGuide(
                                categoryId = categoryId,
                                categoryName = categoryName,
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
                )
            }

            // Add/Edit Provider Screen
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
                )
            }

            // Provider Selection Screen
            composable<Screen.ProviderSelection> {
                MobileProviderSelectionScreen(
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

            // Settings Screen
            composable<Screen.Settings> {
                MobileSettingsScreen(
                    onBack = {
                        navController.navigateUp()
                    },
                    onThemeChanged = onThemeChanged,
                    onUiStyleChanged = onUiStyleChanged,
                    onManageProviders = {
                        navController.navigate(Screen.ProviderSelection)
                    },
                    onCellularBuffers = {
                        navController.navigate(Screen.CellularBufferSettings)
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

            // Cellular Buffer Settings Screen
            composable<Screen.CellularBufferSettings> {
                MobileCellularBufferSettingsScreen(
                    onBack = {
                        navController.navigateUp()
                    },
                )
            }

            // Search Screen
            composable<Screen.Search> { backStackEntry ->
                val searchScreen = backStackEntry.toRoute<Screen.Search>()
                MobileSearchScreen(
                    contentType = searchScreen.contentType,
                    onStreamSelected = { itemId, itemName, categoryId, contentType ->
                        // Navigate based on content type
                        when (contentType) {
                            ContentType.TV_SHOWS ->
                                navController.navigate(
                                    Screen.EpisodeSelection(
                                        seriesId = itemId,
                                        seriesName = itemName,
                                        categoryId = categoryId,
                                    ),
                                )
                            ContentType.MOVIES ->
                                navController.navigate(
                                    Screen.MovieDetails(
                                        movieId = itemId,
                                        movieName = itemName,
                                        categoryId = categoryId,
                                    ),
                                )
                            else ->
                                // Live TV: land on the docked mini-player, not full-screen.
                                navController.navigate(
                                    Screen.CategoryList(
                                        contentType = contentType,
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
                MobileMovieDetailsScreen(
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
                MobileEpisodeSelectionScreen(
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

            // EPG Management Screen (scoped to one provider)
            composable<Screen.EpgManagement> { backStackEntry ->
                val epgScreen = backStackEntry.toRoute<Screen.EpgManagement>()
                MobileEpgManagementScreen(
                    providerId = epgScreen.providerId,
                    onBack = { navController.navigateUp() },
                )
            }

            // EPG Guide Screen (Live TV)
            composable<Screen.EpgGuide> { backStackEntry ->
                val epgScreen = backStackEntry.toRoute<Screen.EpgGuide>()
                MobileEpgGuideScreen(
                    categoryId = epgScreen.categoryId,
                    categoryName = epgScreen.categoryName,
                    onProgramSelected = { _, channel ->
                        // Land on the docked mini-player, not full-screen.
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
        }
    }
}
