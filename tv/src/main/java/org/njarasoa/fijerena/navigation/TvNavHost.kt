package org.njarasoa.fijerena.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.navigation.ContentType
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.feature.category.CategoryGridScreen
import org.njarasoa.fijerena.feature.contentselection.ContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.episode.EpisodeSelectionScreen
import org.njarasoa.fijerena.feature.login.LoginScreenTv
import org.njarasoa.fijerena.feature.movie.MovieDetailsScreen
import org.njarasoa.fijerena.feature.player.TvPlayerScreen
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
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }

    // Check authentication status on startup
    val isAuthenticated by authViewModel.authResponse.collectAsState()

    // Auto-navigate to content type selection if already authenticated
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated != null && authViewModel.isAuthenticated()) {
            navController.navigate(Screen.ContentTypeSelection) {
                popUpTo(Screen.Login) { inclusive = true }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Login
        ) {
            // Login Screen
            composable<Screen.Login> {
                LoginScreenTv(
                    authViewModel = authViewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.CategoryList("LIVE_TV")) {
                            popUpTo(Screen.Login) { inclusive = true }
                        }
                    }
                )
            }

            // Content Type Selection Screen
            composable<Screen.ContentTypeSelection> {
                ContentTypeSelectionScreen(
                    onContentTypeSelected = { contentType ->
                        navController.navigate(Screen.CategoryList(contentType.name)) {
                            popUpTo(Screen.ContentTypeSelection) { inclusive = false }
                        }
                    },
                    onLogout = {
                        authViewModel.clearAuthSession()
                        navController.navigate(Screen.Login) {
                            popUpTo(Screen.ContentTypeSelection) { inclusive = true }
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
                    onStreamSelected = { streamId, streamName, categoryId ->
                        when (categoryListScreen.contentType) {
                            "TV_SHOWS" -> {
                                // For TV shows, navigate to episode selection
                                navController.navigate(
                                    Screen.EpisodeSelection(
                                        seriesId = streamId,
                                        seriesName = streamName,
                                        categoryId = categoryId
                                    )
                                )
                            }
                            "MOVIES" -> {
                                // For movies, navigate to movie details
                                navController.navigate(
                                    Screen.MovieDetails(
                                        movieId = streamId,
                                        movieName = streamName,
                                        categoryId = categoryId
                                    )
                                )
                            }
                            else -> {
                                // For Live TV, go directly to player
                                navController.navigate(
                                    Screen.Player(
                                        streamId = streamId,
                                        streamName = streamName,
                                        categoryId = categoryId,
                                        contentType = categoryListScreen.contentType
                                    )
                                )
                            }
                        }
                    },
                    onBack = {
                        // Go back to content type selection to access Movies/TV Shows
                        navController.navigate(Screen.ContentTypeSelection) {
                            popUpTo(Screen.CategoryList("LIVE_TV")) { inclusive = true }
                        }
                    },
                    onLogout = {
                        authViewModel.clearAuthSession()
                        navController.navigate(Screen.Login) {
                            popUpTo(Screen.Login) { inclusive = true }
                        }
                    }
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
                                streamId = episodeId.hashCode(), // Use hash for navigation ID
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

            // Settings Screen
            composable<Screen.Settings> {
                SettingsScreen(
                    onBack = {
                        navController.navigateUp()
                    },
                    onProviderChanged = {
                        // Clear navigation stack and go back to login
                        navController.navigate(Screen.Login) {
                            popUpTo(Screen.Login) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

