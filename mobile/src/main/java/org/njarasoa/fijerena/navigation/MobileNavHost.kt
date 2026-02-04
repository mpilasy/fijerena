package org.njarasoa.fijerena.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.feature.provider.MobileAddProviderScreen
import org.njarasoa.fijerena.feature.provider.MobileProviderSelectionScreen
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
    authViewModel: AuthViewModel = viewModel(),
    onThemeChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accountManager = remember { AccountManager(context.applicationContext) }
    val repository = remember {
        XtreamRepository(accountManager, context.applicationContext)
    }

    // Check if provider is configured on startup
    val hasProvider = remember { accountManager.hasStoredCredentials() }
    val isAuthenticated by authViewModel.authResponse.collectAsState()

    // Determine initial destination based on provider configuration
    val startDestination = if (hasProvider) {
        Screen.ContentTypeSelection
    } else {
        Screen.Settings
    }

    // Auto-restore session if credentials are stored but not authenticated
    LaunchedEffect(hasProvider, isAuthenticated) {
        if (hasProvider && isAuthenticated == null) {
            when (val result = repository.restoreSession()) {
                is Result.Success -> {
                    val url = repository.getCurrentUrl() ?: ""
                    authViewModel.setAuthSession(result.data, url)
                }
                is Result.Error -> {
                    // Silently fail - user can configure provider in Settings
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
                    onLogout = {
                        authViewModel.clearAuthSession()
                        navController.navigate(Screen.Settings) {
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
                        // For now, navigate to content type selection
                        navController.navigate(Screen.ContentTypeSelection) {
                            popUpTo(Screen.ProviderSelection) { inclusive = true }
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
                    onProviderChanged = {
                        // Navigate back to content type selection after provider change
                        navController.navigate(Screen.ContentTypeSelection) {
                            popUpTo(Screen.ContentTypeSelection) { inclusive = true }
                        }
                    },
                    onLogout = {
                        authViewModel.clearAuthSession()
                        navController.navigate(Screen.Settings) {
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


