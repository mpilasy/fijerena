package org.njarasoa.fijerena.navigation

import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.feature.category.CategoryGridScreen
import org.njarasoa.fijerena.feature.login.LoginScreenTv
import org.njarasoa.fijerena.feature.player.TvPlayerScreen

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
    // Check authentication status on startup
    val isAuthenticated by authViewModel.authResponse.collectAsState()

    // Auto-navigate to CategoryList if already authenticated
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated != null && authViewModel.isAuthenticated()) {
            navController.navigate(Screen.CategoryList) {
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
                        navController.navigate(Screen.CategoryList) {
                            popUpTo(Screen.Login) { inclusive = true }
                        }
                    }
                )
            }

            // Category List Screen
            composable<Screen.CategoryList> {
                CategoryGridScreen(
                    onStreamSelected = { streamId ->
                        navController.navigate(Screen.Player(streamId))
                    },
                    onLogout = {
                        authViewModel.clearAuthSession()
                        navController.navigate(Screen.Login) {
                            popUpTo(Screen.CategoryList) { inclusive = true }
                        }
                    }
                )
            }

            // Player Screen
            composable<Screen.Player> { backStackEntry ->
                val playerScreen = backStackEntry.toRoute<Screen.Player>()
                TvPlayerScreen(
                    streamId = playerScreen.streamId,
                    onBack = {
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}

