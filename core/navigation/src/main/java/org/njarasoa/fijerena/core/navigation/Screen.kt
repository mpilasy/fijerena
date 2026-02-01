package org.njarasoa.fijerena.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for Xtream IPTV app.
 *
 * Uses kotlinx.serialization for type-safe navigation with Navigation Compose.
 * Each screen is a @Serializable object/data class that can be passed directly
 * to NavController.navigate().
 *
 * Usage:
 * ```kotlin
 * navController.navigate(Screen.Login)
 * navController.navigate(Screen.CategoryList)
 * navController.navigate(Screen.Player(streamId = 12345))
 * ```
 */
sealed interface Screen {
    /**
     * Login screen destination.
     * Entry point for unauthenticated users.
     */
    @Serializable
    data object Login : Screen

    /**
     * Category list screen destination.
     * Shows available IPTV categories after successful login.
     */
    @Serializable
    data object CategoryList : Screen

    /**
     * Player screen destination with stream ID parameter.
     *
     * @param streamId The Xtream stream ID to play
     */
    @Serializable
    data class Player(val streamId: Int) : Screen
}
