package org.njarasoa.fijerena.core.navigation

import kotlinx.serialization.Serializable

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
     * "Who's watching?" — choose which profile this device uses. TV opens it on every launch when
     * there are two or more profiles; both apps open it from the home header's avatar. See
     * docs/plans/archive/20260929_live-sync-plan.md → User profiles.
     */
    @Serializable
    data object ProfilePicker : Screen

    /**
     * A profile's page (mobile; TV opens it inside Settings): name, colour, its own settings, its
     * content filters for the source in use, Switch to this profile, Delete. See
     * docs/plans/20261003_sources-guide-profiles-plan.md → P9.
     */
    @Serializable
    data class ProfileEdit(
        val profileId: String,
    ) : Screen

    /**
     * Content type selection screen destination.
     * Allows users to choose between Live TV, Movies, or TV Shows.
     */
    @Serializable
    data object ContentTypeSelection : Screen

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
     * @param showPreviewPane TV only: true (the default) makes this entry the preview-pane split
     * layout (video + list) alone, opened on [initialStreamId] from search or a guide — Back
     * leaves it for the screen that pushed it. False is the Live TV browse entry Home pushes: the
     * classic categories-left/streams-right layout also used by Movies/TV Shows, with the preview
     * (and full screen) as a layer over it — open on entry when [initialStreamId] is set (Home
     * passes the last channel), opened by OK on a channel, closed by Back, so Back from the
     * preview lands on browse instead of exiting Live TV outright. Ignored for Movies/TV Shows
     * (always classic layout) and by mobile, whose docked mini-player is the same kind of layer.
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
     * @param initialQuery Search term to run on arrival, e.g. a title tapped in a
     *   "more like this" row. Null when the user opened search themselves.
     */
    @Serializable
    data class Search(
        val contentType: String,
        val initialQuery: String? = null,
    ) : Screen

    /**
     * EPG (Electronic Program Guide) screen destination.
     * Shows TV guide with time grid for Live TV channels.
     *
     * @param categoryId The category ID to show EPG for (a real category, or the virtual
     *   `recent` / `favorites` lists)
     * @param focusChannelId The channel whose row the guide opens on (the one playing when opened
     *   from the player); null opens on the first channel with a programme on now
     */
    @Serializable
    data class EpgGuide(
        val categoryId: String,
        val categoryName: String,
        val focusChannelId: String? = null,
    ) : Screen

    /**
     * EPG Browser ("Search the guide") screen destination.
     * Allows searching programme titles in the locally-cached XMLTV file.
     *
     * @param categoryId When opened from a TV Guide: the guide's channel set, which the browser
     *   offers as an "In <category> only" filter (on by default). Null searches every channel.
     */
    @Serializable
    data class EpgBrowser(
        val categoryId: String? = null,
        val categoryName: String? = null,
    ) : Screen

    /**
     * EPG Management screen destination.
     * Manage a single provider's XMLTV EPG sources (add, edit, delete, refresh).
     * EPG sources belong to one provider and are never shared between providers.
     */
    @Serializable
    data class EpgManagement(
        val providerId: Long,
    ) : Screen

    /** Settings → Live sync: linking this device to a sync account, pairing, devices. */
    @Serializable
    data object SyncSettings : Screen

    /** Settings → Diagnostics (developer mode only): recorded crashes and process exit reasons. */
    @Serializable
    data object Diagnostics : Screen

    /** Start destination in crash-loop safe mode (`SafeMode.isActive`), in place of home. */
    @Serializable
    data object SafeMode : Screen

    /** Start destination when providers.db was written by a newer build (`ProvidersDbGuard.isBlocked`). */
    @Serializable
    data object NewerData : Screen

    /**
     * Player screen destination with stream parameters.
     *
     * @param streamId The Xtream stream ID to play (or episode ID for TV shows as string converted to int hash)
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
