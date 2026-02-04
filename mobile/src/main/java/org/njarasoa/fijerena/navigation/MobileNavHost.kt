package org.njarasoa.fijerena.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.feature.login.LoginScreen
import org.njarasoa.fijerena.feature.player.MobilePlayerScreen
import org.njarasoa.fijerena.feature.contentselection.MobileContentTypeSelectionScreen
import org.njarasoa.fijerena.feature.category.MobileCategoryListScreen
import org.njarasoa.fijerena.feature.search.MobileSearchScreen
import org.njarasoa.fijerena.feature.settings.MobileSettingsScreen
import org.njarasoa.fijerena.feature.movie.MobileMovieDetailsScreen
import org.njarasoa.fijerena.feature.episode.MobileEpisodeSelectionScreen
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
    authViewModel: AuthViewModel = viewModel()
) {
    // Check authentication status on startup
    val authResponse by authViewModel.authResponse.collectAsState()

    // Determine initial destination based on auth status
    // LoginScreen will attempt to restore session automatically
    val startDestination = if (authViewModel.isAuthenticated()) {
        Screen.ContentTypeSelection
    } else {
        Screen.Login
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
            // Login Screen
            composable<Screen.Login> {
                LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.ContentTypeSelection) {
                            popUpTo(Screen.Login) { inclusive = true }
                        }
                    }
                )
            }

            // Content Type Selection Screen
            composable<Screen.ContentTypeSelection> {
                MobileContentTypeSelectionScreen(
                    onContentTypeSelected = { contentType ->
                        navController.navigate(Screen.CategoryList(contentType))
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings)
                    },
                    onLogout = {
                        authViewModel.clearAuthSession()
                        navController.navigate(Screen.Login) {
                            popUpTo(Screen.ContentTypeSelection) { inclusive = true }
                        }
                    }
                )
            }

            // Category List Screen
            composable<Screen.CategoryList> { backStackEntry ->
                val categoryListScreen = backStackEntry.toRoute<Screen.CategoryList>()
                MobileCategoryListScreen(
                    contentType = categoryListScreen.contentType,
                    onStreamSelected = { streamId, streamName, categoryId, contentType ->
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
                                navController.navigate(Screen.Player(streamId, streamName, categoryId, contentType))
                            }
                        }
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search(categoryListScreen.contentType))
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
                    onProviderChanged = {
                        // Navigate back to content type selection after provider change
                        navController.navigate(Screen.ContentTypeSelection) {
                            popUpTo(Screen.ContentTypeSelection) { inclusive = true }
                        }
                    },
                    onLogout = {
                        authViewModel.clearAuthSession()
                        navController.navigate(Screen.Login) {
                            popUpTo(Screen.ContentTypeSelection) { inclusive = true }
                        }
                    }
                )
            }

            // Search Screen
            composable<Screen.Search> { backStackEntry ->
                val searchScreen = backStackEntry.toRoute<Screen.Search>()
                MobileSearchScreen(
                    contentType = searchScreen.contentType,
                    onStreamSelected = { streamId, streamName, categoryId, contentType ->
                        // Navigate based on content type
                        when (searchScreen.contentType) {
                            "TV_SHOWS" -> navController.navigate(
                                Screen.EpisodeSelection(
                                    seriesId = streamId,
                                    seriesName = streamName,
                                    categoryId = categoryId
                                )
                            )
                            "MOVIES" -> navController.navigate(
                                Screen.MovieDetails(
                                    movieId = streamId,
                                    movieName = streamName,
                                    categoryId = categoryId
                                )
                            )
                            else -> navController.navigate(
                                Screen.Player(
                                    streamId = streamId,
                                    streamName = streamName,
                                    categoryId = categoryId,
                                    contentType = searchScreen.contentType
                                )
                            )
                        }
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
                    onPlayMovie = { movieId, movieName, extension ->
                        navController.navigate(
                            Screen.Player(
                                streamId = movieId,
                                streamName = movieName,
                                categoryId = movieDetailsScreen.categoryId,
                                contentType = "MOVIES",
                                episodeExtension = extension
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
                    onEpisodeSelected = { episodeId, episodeTitle, extension ->
                        navController.navigate(
                            Screen.Player(
                                streamId = episodeId.hashCode(),
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
        }
    }
}


