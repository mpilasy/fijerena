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
     * Content type selection screen destination.
     * Allows users to choose between Live TV, Movies, or TV Shows.
     */
    @Serializable
    data object ContentTypeSelection : Screen

    /**
     * Category list screen destination.
     * Shows available categories for the selected content type.
     *
     * @param contentType The type of content to show categories for (LIVE_TV, MOVIES, TV_SHOWS)
     */
    @Serializable
    data class CategoryList(
        val contentType: String
    ) : Screen

    /**
     * Episode selection screen destination for TV shows.
     * Shows seasons and episodes for a selected series.
     *
     * @param seriesId The Xtream series ID
     * @param seriesName The display name of the series
     * @param categoryId The category ID this series belongs to
     */
    @Serializable
    data class EpisodeSelection(
        val seriesId: Int,
        val seriesName: String,
        val categoryId: String
    ) : Screen

    /**
     * Movie details screen destination for VOD movies.
     * Shows movie information and play button.
     *
     * @param movieId The Xtream movie ID
     * @param movieName The display name of the movie
     * @param categoryId The category ID this movie belongs to
     */
    @Serializable
    data class MovieDetails(
        val movieId: Int,
        val movieName: String,
        val categoryId: String
    ) : Screen

    /**
     * Player screen destination with stream parameters.
     *
     * @param streamId The Xtream stream ID to play (or episode ID for TV shows as string converted to int hash)
     * @param streamName The display name of the stream
     * @param categoryId The category ID this stream belongs to
     * @param contentType The type of content being played (LIVE_TV, MOVIES, TV_SHOWS)
     * @param episodeId Optional episode ID for TV shows (actual string ID from API)
     * @param episodeExtension Optional container extension for episode playback (e.g., "mp4", "mkv")
     */
    @Serializable
    data class Player(
        val streamId: Int,
        val streamName: String,
        val categoryId: String,
        val contentType: String,
        val episodeId: String? = null,
        val episodeExtension: String? = null
    ) : Screen
}
