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
    val isAuthenticated by authViewModel.authResponse.collectAsState()

    // Auto-navigate to ContentTypeSelection if already authenticated
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
            startDestination = Screen.Login,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
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
                    onBack = {
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
                    authViewModel = authViewModel,
                    onStreamSelected = { streamId, streamName, categoryId, contentType ->
                        navController.navigate(Screen.Player(streamId, streamName, categoryId, contentType))
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
                    authViewModel = authViewModel,
                    onBack = {
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}

/**
 * Placeholder Content Type Selection screen for Mobile.
 * TODO: Implement full content type selection with touch UI.
 */
@Composable
fun MobileContentTypeSelectionScreen(
    onContentTypeSelected: (contentType: String) -> Unit,
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Text(
            text = "Content Type Selection (Mobile)\nTODO: Implement Live TV / Movies / TV Shows selection",
            modifier = Modifier.fillMaxSize()
        )
        // TODO: Implement content type selection UI
        // - Show three buttons: Live TV, Movies, TV Shows
        // - Add logout button
    }
}

/**
 * Placeholder Category List screen for Mobile.
 * TODO: Implement full category list with touch UI.
 */
@Composable
fun MobileCategoryListScreen(
    contentType: String,
    authViewModel: AuthViewModel,
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String, contentType: String) -> Unit,
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Text(
            text = "Category List Screen (Mobile)\nContent Type: $contentType\nTODO: Implement category grid with touch navigation",
            modifier = Modifier.fillMaxSize()
        )
        // TODO: Implement category list UI
        // - Use LazyVerticalGrid for categories
        // - Show category cards with images
        // - Implement pull-to-refresh
        // - Add search functionality
    }
}

/**
 * Placeholder Player screen for Mobile.
 * TODO: Implement full player with touch controls.
 */
@Composable
fun MobilePlayerScreen(
    streamId: Int,
    streamName: String,
    categoryId: String,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Text(
            text = "Player Screen (Mobile)\nStream: $streamName (ID: $streamId)\nCategory: $categoryId\nTODO: Implement ExoPlayer integration",
            modifier = Modifier.fillMaxSize()
        )
        // TODO: Implement player UI
        // - Integrate StreamingPlaybackService
        // - Show touch-based playback controls
        // - Handle orientation changes
        // - Add PiP (Picture-in-Picture) support
    }
}
