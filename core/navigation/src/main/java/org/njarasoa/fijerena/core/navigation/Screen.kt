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
     * Provider selection screen destination.
     * Shown when multiple providers are configured.
     */
    @Serializable
    data object ProviderSelection : Screen

    /**
     * Add/edit provider screen destination.
     *
     * @param editId If > 0, edit the provider with this ID instead of creating new
     */
    @Serializable
    data class AddProvider(
        val editId: Long = -1L,
    ) : Screen

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
     * Edit provider URL screen destination.
     * Allows users to change the provider URL without re-entering credentials.
     */
    @Serializable
    data object EditProvider : Screen

    /**
     * Settings screen destination.
     * Allows users to configure app settings like dev mode, watch history size, etc.
     */
    @Serializable
    data object Settings : Screen

    /**
     * Category list screen destination.
     * Shows available categories for the selected content type.
     *
     * @param contentType The type of content to show categories for (LIVE_TV, MOVIES, TV_SHOWS)
     * @param initialStreamId For Live TV: seed the preview pane (TV) or docked mini-player
     * (mobile) with this stream immediately on entry (e.g. arriving from EPG/catalog search or
     * the per-category EPG guide) instead of waiting for D-pad focus to settle (TV) or a tap
     * (mobile). Null means no specific stream was picked to get here.
     * @param showPreviewPane TV only: whether this entry shows the preview-pane split layout
     * (video + list) or the classic categories-left/streams-right layout also used by Movies/TV
     * Shows. False is used for a silently-pushed entry underneath a preview entry, so Back from
     * the preview lands on a real "browse" screen instead of exiting Live TV outright. Ignored
     * for Movies/TV Shows (always classic layout) and by mobile, whose docked mini-player is an
     * overlay on the same list screen rather than a replacement destination, so it has no
     * equivalent "Back exits entirely" problem to solve.
     */
    @Serializable
    data class CategoryList(
        val contentType: String,
        val initialCategoryId: String? = null,
        val initialStreamId: String? = null,
        val showPreviewPane: Boolean = true,
    ) : Screen

    /**
     * Episode selection screen destination for TV shows.
     * Shows seasons and episodes for a selected series.
     *
     * @param seriesId The Xtream series ID
     * @param seriesName The display name of the series
     * @param categoryId The category ID this series belongs to
     * @param initialEpisodeId When set (e.g. arriving from Continue Watching), open straight to
     * this episode's detail/resume panel instead of the season/episode list.
     */
    @Serializable
    data class EpisodeSelection(
        val seriesId: String,
        val seriesName: String,
        val categoryId: String,
        val initialEpisodeId: String? = null,
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
        val movieId: String,
        val movieName: String,
        val categoryId: String,
    ) : Screen

    /**
     * Search screen destination.
     * Allows users to search across all categories for a specific content type.
     *
     * @param contentType The type of content to search (LIVE_TV, MOVIES, TV_SHOWS)
     */
    @Serializable
    data class Search(
        val contentType: String,
    ) : Screen

    /**
     * EPG (Electronic Program Guide) screen destination.
     * Shows TV guide with time grid for Live TV channels.
     *
     * @param categoryId The category ID to show EPG for
     * @param categoryName The display name of the category
     */
    @Serializable
    data class EpgGuide(
        val categoryId: String,
        val categoryName: String,
    ) : Screen

    /**
     * EPG Browser screen destination.
     * Allows searching programme titles in the locally-cached XMLTV file.
     */
    @Serializable
    data object EpgBrowser : Screen

    /**
     * EPG Management screen destination.
     * Manage multiple XMLTV EPG sources (add, edit, delete, refresh).
     */
    @Serializable
    data object EpgManagement : Screen

    /**
     * Cellular Buffer Settings screen destination (developer mode only).
     * Configure cellular buffer multipliers for Live TV and VOD.
     */
    @Serializable
    data object CellularBufferSettings : Screen

    /**
     * Player screen destination with stream parameters.
     *
     * @param streamId The Xtream stream ID to play (or episode ID for TV shows as string converted to int hash)
     * @param streamName The display name of the stream
     * @param categoryId The category ID this stream belongs to
     * @param contentType The type of content being played (LIVE_TV, MOVIES, TV_SHOWS)
     * @param episodeId Optional episode ID for TV shows (actual string ID from API)
     * @param episodeExtension Optional container extension for episode playback (e.g., "mp4", "mkv")
     * @param seriesId Optional series ID for TV shows (used for watch history tracking)
     * @param seriesName Optional series name for TV shows (used for watch history tracking)
     */
    @Serializable
    data class Player(
        val streamId: String,
        val streamName: String,
        val categoryId: String,
        val contentType: String,
        val episodeId: String? = null,
        val episodeExtension: String? = null,
        val seriesId: String? = null,
        val seriesName: String? = null,
        val startFromBeginning: Boolean = false,
    ) : Screen
}
