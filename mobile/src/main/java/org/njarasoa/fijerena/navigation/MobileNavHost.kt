package org.njarasoa.fijerena.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.njarasoa.fijerena.core.data.AuthViewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.feature.provider.MobileAddProviderScreen
import org.njarasoa.fijerena.feature.provider.MobileProviderSelectionScreen
import org.njarasoa.fijerena.feature.player.MobilePlayerScreen
import org.njarasoa.fijerena.feature.contentselection.MobileContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.category.MobileCategoryListScreen
import org.njarasoa.fijerena.feature.epg.MobileEpgGuideScreen
import org.njarasoa.fijerena.feature.epg.MobileEpgManagementScreen
import org.njarasoa.fijerena.feature.epgbrowser.MobileEpgBrowserScreen
import org.njarasoa.fijerena.feature.search.MobileSearchScreen
import org.njarasoa.fijerena.feature.settings.MobileSettingsScreen
import org.njarasoa.fijerena.feature.movie.MobileMovieDetailsScreen
import org.njarasoa.fijerena.feature.episode.MobileEpisodeSelectionScreen
import org.njarasoa.fijerena.feature.settings.MobileCellularBufferSettingsScreen
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation

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
    onThemeChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accountManager = remember { AccountManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // Async initialization: migrate legacy creds, determine start destination
    var hasProvider by remember { mutableStateOf<Boolean?>(null) }
    var lastContentType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val providerRepo = ProviderRepository(context.applicationContext)
        if (providerRepo.getProviderCount() == 0) {
            // Run one-time migration from AccountManager to Room
            val legacyCreds = accountManager.exportForMigration()
            if (legacyCreds != null) {
                val (url, username, password) = legacyCreds
                val name = org.njarasoa.fijerena.core.network.AppSettings(context.applicationContext).providerName
                providerRepo.addProvider(name, url, username, password)
            }
        }
        val hasProviderResult = providerRepo.getProviderCount() > 0
        hasProvider = hasProviderResult

        if (hasProviderResult) {
            val activeProvider = providerRepo.getActiveProvider()
            if (activeProvider != null) {
                val prefs = context.applicationContext.getSharedPreferences(
                    "media_cache_${activeProvider.id}",
                    android.content.Context.MODE_PRIVATE
                )
                lastContentType = prefs.getString("last_content_type", null)
            }
        }
    }

    val isAuthenticated by authViewModel.authResponse.collectAsStateWithLifecycle()

    // Show nothing until initialization completes
    if (hasProvider == null) return

    // Determine initial destination based on provider configuration
    val startDestination = if (hasProvider == true) {
        Screen.ContentTypeSelection
    } else {
        Screen.Settings
    }

    // Auto-navigate to last content type (and category) on startup
    LaunchedEffect(lastContentType) {
        val ct = lastContentType ?: return@LaunchedEffect
        val providerRepo = ProviderRepository(context.applicationContext)
        val activeProvider = providerRepo.getActiveProvider()
        val lastCategoryId = if (activeProvider != null) {
            val prefs = context.applicationContext.getSharedPreferences(
                "media_cache_${activeProvider.id}",
                android.content.Context.MODE_PRIVATE
            )
            val key = when (ct) {
                org.njarasoa.fijerena.core.player.domain.ContentType.LIVE_TV -> "last_live_category"
                org.njarasoa.fijerena.core.player.domain.ContentType.MOVIES -> "last_movies_category"
                org.njarasoa.fijerena.core.player.domain.ContentType.TV_SHOWS -> "last_tvshows_category"
                else -> null
            }
            key?.let { prefs.getString(it, null) }
        } else null
        navController.navigate(Screen.CategoryList(ct, lastCategoryId)) {
            popUpTo(Screen.ContentTypeSelection) { inclusive = false }
        }
    }

    // Auto-restore Xtream session if the active provider is Xtream
    LaunchedEffect(hasProvider, isAuthenticated) {
        if (hasProvider == true && isAuthenticated == null) {
            val providerRepo = ProviderRepository(context.applicationContext)
            val activeProvider = providerRepo.getActiveProvider()
            if (activeProvider != null && activeProvider.type == "XTREAM") {
                val repository = XtreamRepository(
                    accountManager, context.applicationContext, activeProvider.id
                )
                when (val result = repository.restoreSession()) {
                    is Result.Success -> {
                        val url = repository.getCurrentUrl() ?: ""
                        authViewModel.setAuthSession(result.data, url)
                    }
                    is Result.Error -> {
                        // Silently fail - factories will handle connection
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
                    animationSpec = tween(CinemaAnimation.navTransitionMs)
                ) + fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(CinemaAnimation.navTransitionMs)
                ) + fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(CinemaAnimation.navTransitionMs)
                ) + fadeIn(animationSpec = tween(CinemaAnimation.navTransitionMs))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(CinemaAnimation.navTransitionMs)
                ) + fadeOut(animationSpec = tween(CinemaAnimation.navTransitionMs))
            }
        ) {
            // Content Type Selection Screen
            composable<Screen.ContentTypeSelection> {
                MobileContentTypeSelectionScreen(
                    onContentTypeSelected = { contentType ->
                        navController.navigate(Screen.CategoryList(contentType))
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings)
                    },
                    onSearch = {
                        navController.navigate(Screen.Search("ALL"))
                    },
                    onEpgBrowser = {
                        navController.navigate(Screen.EpgBrowser)
                    }
                )
            }

            // EPG Browser Screen
            composable<Screen.EpgBrowser> {
                MobileEpgBrowserScreen(
                    onBack = { navController.navigateUp() },
                    onNavigateToPlayer = { streamId, streamName, categoryId ->
                        navController.navigate(Screen.Player(streamId, streamName, categoryId, ContentType.LIVE_TV))
                    }
                )
            }

            // Category List Screen
            composable<Screen.CategoryList> { backStackEntry ->
                val categoryListScreen = backStackEntry.toRoute<Screen.CategoryList>()
                MobileCategoryListScreen(
                    contentType = categoryListScreen.contentType,
                    initialCategoryId = categoryListScreen.initialCategoryId,
                    onStreamSelected = { itemId, itemName, categoryId, contentType ->
                        when (categoryListScreen.contentType) {
                            ContentType.TV_SHOWS -> {
                                // For TV shows, navigate to episode selection
                                navController.navigate(
                                    Screen.EpisodeSelection(
                                        seriesId = itemId,
                                        seriesName = itemName,
                                        categoryId = categoryId
                                    )
                                )
                            }
                            ContentType.MOVIES -> {
                                // For movies, navigate to movie details
                                navController.navigate(
                                    Screen.MovieDetails(
                                        movieId = itemId,
                                        movieName = itemName,
                                        categoryId = categoryId
                                    )
                                )
                            }
                            else -> {
                                // For Live TV, go directly to player
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
                                categoryName = categoryName
                            )
                        )
                    },
                    onBack = {
                        navController.navigateUp()
                    }
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
                    }
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
                    }
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

                            // For Xtream providers, restore session for backward compatibility
                            if (provider.type == "XTREAM") {
                                val xtreamRepo = XtreamRepository(
                                    accountManager, context.applicationContext, provider.id
                                )
                                when (val result = xtreamRepo.restoreSession()) {
                                    is Result.Success -> {
                                        val url = xtreamRepo.getCurrentUrl() ?: ""
                                        authViewModel.setAuthSession(result.data, url)
                                    }
                                    is Result.Error -> { /* factories will handle connection */ }
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
                    onBack = {
                        navController.navigateUp()
                    }
                )
            }

            // Settings Screen
            composable<Screen.Settings> {
                MobileSettingsScreen(
                    onBack = {
                        navController.navigateUp()
                    },
                    onThemeChanged = onThemeChanged,
                    onManageProviders = {
                        navController.navigate(Screen.ProviderSelection)
                    },
                    onManageEpg = {
                        navController.navigate(Screen.EpgManagement)
                    },
                    onCellularBuffers = {
                        navController.navigate(Screen.CellularBufferSettings)
                    },
                    onProviderChanged = {
                        coroutineScope.launch {
                            val providerRepo = ProviderRepository(context.applicationContext)
                            val activeProvider = providerRepo.getActiveProvider()

                            if (activeProvider != null && activeProvider.type == "XTREAM") {
                                val xtreamRepo = XtreamRepository(
                                    accountManager, context.applicationContext, activeProvider.id
                                )
                                when (val result = xtreamRepo.restoreSession()) {
                                    is Result.Success -> {
                                        val url = xtreamRepo.getCurrentUrl() ?: ""
                                        authViewModel.setAuthSession(result.data, url)
                                    }
                                    is Result.Error -> { /* factories will handle connection */ }
                                }
                            }

                            navController.navigate(Screen.ContentTypeSelection) {
                                popUpTo(Screen.Settings) { inclusive = false }
                            }
                        }
                    }
                )
            }

            // Cellular Buffer Settings Screen
            composable<Screen.CellularBufferSettings> {
                MobileCellularBufferSettingsScreen(
                    onBack = {
                        navController.navigateUp()
                    }
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
                            ContentType.TV_SHOWS -> navController.navigate(
                                Screen.EpisodeSelection(
                                    seriesId = itemId,
                                    seriesName = itemName,
                                    categoryId = categoryId
                                )
                            )
                            ContentType.MOVIES -> navController.navigate(
                                Screen.MovieDetails(
                                    movieId = itemId,
                                    movieName = itemName,
                                    categoryId = categoryId
                                )
                            )
                            else -> navController.navigate(
                                Screen.Player(
                                    streamId = itemId,
                                    streamName = itemName,
                                    categoryId = categoryId,
                                    contentType = contentType
                                )
                            )
                        }
                    },
                    onCategorySelected = { categoryId, contentType ->
                        navController.navigate(
                            Screen.CategoryList(
                                contentType = contentType,
                                initialCategoryId = categoryId
                            )
                        )
                    },
                    onBack = { navController.navigateUp() }
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
                                startFromBeginning = startFromBeginning
                            )
                        )
                    },
                    onBack = {
                        navController.navigateUp()
                    }
                )
            }

            // Episode Selection Screen (for TV Shows)
            composable<Screen.EpisodeSelection> { backStackEntry ->
                val episodeSelectionScreen = backStackEntry.toRoute<Screen.EpisodeSelection>()
                MobileEpisodeSelectionScreen(
                    seriesId = episodeSelectionScreen.seriesId,
                    seriesName = episodeSelectionScreen.seriesName,
                    categoryId = episodeSelectionScreen.categoryId,
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
                                startFromBeginning = startFromBeginning
                            )
                        )
                    },
                    onBack = {
                        navController.navigateUp()
                    }
                )
            }

            // EPG Management Screen
            composable<Screen.EpgManagement> {
                MobileEpgManagementScreen(
                    onBack = { navController.navigateUp() }
                )
            }

            // EPG Guide Screen (Live TV)
            composable<Screen.EpgGuide> { backStackEntry ->
                val epgScreen = backStackEntry.toRoute<Screen.EpgGuide>()
                MobileEpgGuideScreen(
                    categoryId = epgScreen.categoryId,
                    categoryName = epgScreen.categoryName,
                    onProgramSelected = { program, channel ->
                        navController.navigate(
                            Screen.Player(
                                streamId = channel.id,
                                streamName = channel.name,
                                categoryId = channel.categoryId,
                                contentType = ContentType.LIVE_TV
                            )
                        )
                    },
                    onChannelSelected = { streamId, streamName, categoryId ->
                        navController.navigate(
                            Screen.Player(
                                streamId = streamId,
                                streamName = streamName,
                                categoryId = categoryId,
                                contentType = ContentType.LIVE_TV
                            )
                        )
                    },
                    onBack = {
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}


