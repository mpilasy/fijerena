package org.njarasoa.fijerena.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.feature.category.CategoryGridScreen
import org.njarasoa.fijerena.feature.contentselection.ContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.epg.EpgGuideScreen
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
    onThemeChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accountManager = remember { AccountManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // Migrate legacy AccountManager credentials to Room if needed, then check
    val hasProvider = remember {
        kotlinx.coroutines.runBlocking {
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
            providerRepo.getProviderCount() > 0
        }
    }
    val isAuthenticated by authViewModel.authResponse.collectAsState()

    // Determine initial destination based on provider configuration
    val startDestination = if (hasProvider) {
        Screen.ContentTypeSelection
    } else {
        Screen.Settings
    }

    // Auto-restore Xtream session if the active provider is Xtream
    LaunchedEffect(hasProvider, isAuthenticated) {
        if (hasProvider && isAuthenticated == null) {
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
            startDestination = startDestination
        ) {
            // Content Type Selection Screen
            composable<Screen.ContentTypeSelection> {
                ContentTypeSelectionScreen(
                    onContentTypeSelected = { contentType ->
                        navController.navigate(Screen.CategoryList(contentType.name)) {
                            popUpTo(Screen.ContentTypeSelection) { inclusive = false }
                        }
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings)
                    }
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
                    }
                )
            }

            // Category List Screen
            composable<Screen.CategoryList> { backStackEntry ->
                val categoryListScreen = backStackEntry.toRoute<Screen.CategoryList>()
                CategoryGridScreen(
                    contentType = categoryListScreen.contentType,
                    initialCategoryId = categoryListScreen.initialCategoryId,
                    onStreamSelected = { itemId, streamName, categoryId ->
                        when (categoryListScreen.contentType) {
                            "TV_SHOWS" -> {
                                // For TV shows, navigate to episode selection
                                navController.navigate(
                                    Screen.EpisodeSelection(
                                        seriesId = itemId,
                                        seriesName = streamName,
                                        categoryId = categoryId
                                    )
                                )
                            }
                            "MOVIES" -> {
                                // For movies, navigate to movie details
                                navController.navigate(
                                    Screen.MovieDetails(
                                        movieId = itemId,
                                        movieName = streamName,
                                        categoryId = categoryId
                                    )
                                )
                            }
                            else -> {
                                // For Live TV, go directly to player
                                navController.navigate(
                                    Screen.Player(
                                        streamId = itemId,
                                        streamName = streamName,
                                        categoryId = categoryId,
                                        contentType = categoryListScreen.contentType
                                    )
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
                                categoryName = categoryName
                            )
                        )
                    },
                    onBack = {
                        // Go back to content type selection to access Movies/TV Shows
                        navController.navigate(Screen.ContentTypeSelection) {
                            popUpTo(Screen.CategoryList("LIVE_TV")) { inclusive = true }
                        }
                    }
                )
            }

            // Search Screen
            composable<Screen.Search> { backStackEntry ->
                val searchScreen = backStackEntry.toRoute<Screen.Search>()
                SearchScreen(
                    contentType = searchScreen.contentType,
                    onStreamSelected = { itemId, streamName, categoryId ->
                        // Navigate based on content type
                        when (searchScreen.contentType) {
                            "TV_SHOWS" -> navController.navigate(
                                Screen.EpisodeSelection(
                                    seriesId = itemId,
                                    seriesName = streamName,
                                    categoryId = categoryId
                                )
                            )
                            "MOVIES" -> navController.navigate(
                                Screen.MovieDetails(
                                    movieId = itemId,
                                    movieName = streamName,
                                    categoryId = categoryId
                                )
                            )
                            else -> navController.navigate(
                                Screen.Player(
                                    streamId = itemId,
                                    streamName = streamName,
                                    categoryId = categoryId,
                                    contentType = searchScreen.contentType
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
                MovieDetailsScreen(
                    movieId = movieDetailsScreen.movieId,
                    movieName = movieDetailsScreen.movieName,
                    categoryId = movieDetailsScreen.categoryId,
                    onPlayMovie = { movieId, movieName, extension ->
                        navController.navigate(
                            Screen.Player(
                                streamId = movieId,
                                streamName = movieName,
                                categoryId = movieDetailsScreen.categoryId,
                                contentType = "MOVIES",
                                episodeExtension = extension // Pass extension for VOD playback
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
                EpisodeSelectionScreen(
                    seriesId = episodeSelectionScreen.seriesId,
                    seriesName = episodeSelectionScreen.seriesName,
                    categoryId = episodeSelectionScreen.categoryId,
                    onEpisodeSelected = { episodeId, episodeTitle, extension ->
                        navController.navigate(
                            Screen.Player(
                                streamId = episodeId,
                                streamName = episodeTitle,
                                categoryId = episodeSelectionScreen.categoryId,
                                contentType = "TV_SHOWS",
                                episodeId = episodeId,
                                episodeExtension = extension,
                                seriesId = episodeSelectionScreen.seriesId,
                                seriesName = episodeSelectionScreen.seriesName
                            )
                        )
                    },
                    onBack = {
                        navController.navigateUp()
                    }
                )
            }

            // EPG Guide Screen
            composable<Screen.EpgGuide> { backStackEntry ->
                val epgScreen = backStackEntry.toRoute<Screen.EpgGuide>()
                EpgGuideScreen(
                    categoryId = epgScreen.categoryId,
                    categoryName = epgScreen.categoryName,
                    onProgramSelected = { program, channel ->
                        // Navigate to player for the selected program
                        navController.navigate(
                            Screen.Player(
                                streamId = channel.id,
                                streamName = channel.name,
                                categoryId = channel.categoryId,
                                contentType = "LIVE_TV"
                            )
                        )
                    },
                    onChannelSelected = { streamId, streamName, categoryId ->
                        // Navigate to player for the selected channel
                        navController.navigate(
                            Screen.Player(
                                streamId = streamId,
                                streamName = streamName,
                                categoryId = categoryId,
                                contentType = "LIVE_TV"
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
                TvPlayerScreen(
                    streamId = playerScreen.streamId,
                    streamName = playerScreen.streamName,
                    categoryId = playerScreen.categoryId,
                    contentType = playerScreen.contentType,
                    episodeId = playerScreen.episodeId,
                    episodeExtension = playerScreen.episodeExtension,
                    seriesId = playerScreen.seriesId,
                    seriesName = playerScreen.seriesName,
                    onBack = {
                        navController.navigateUp()
                    }
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
                        navController.navigateUp()
                    }
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
                SettingsScreen(
                    onBack = {
                        navController.navigateUp()
                    },
                    onThemeChanged = onThemeChanged,
                    onManageProviders = {
                        navController.navigate(Screen.ProviderSelection)
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
        }
    }
}

