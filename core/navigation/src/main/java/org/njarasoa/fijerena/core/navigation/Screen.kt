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
     * the live sync plan → User profiles.
     */
    @Serializable
    data object ProfilePicker : Screen

    /**
     * A profile's page (mobile; TV opens it inside Settings): name, colour, its own settings, its
     * content filters (on a source of its own), Switch to this profile, Delete. See
     * the sources guide profiles plan → P9.
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
     * leaves it for the screen that pushed it. False is Live TV browse (on TV, the Live TV
     * section's root, [LiveTvTab]): the classic categories-left/streams-right layout also used by
     * Movies/TV Shows, with the preview (and full screen) as a layer over it — open on entry when
     * [initialStreamId] is set (a fresh Live TV passes the last channel), opened by OK on a
     * channel, closed by Back, so Back from the preview lands on browse instead of exiting Live TV
     * outright. Ignored for Movies/TV Shows
     * (always classic layout) and by mobile, whose docked mini-player is the same kind of layer.
     * @param providerId The source the list was opened on (a Browse entry's, a category's from
     *   Search or a guide); 0 = a section's list, every Xtream source of the profile merged. Every
     *   other route below that takes one names a real source. See
     *   docs/plans/20261012_multi-source-search-history-plan.md → Phase 6.
     */
    @Serializable
    data class CategoryList(
        val contentType: String,
        val initialCategoryId: String? = null,
        val initialStreamId: String? = null,
        val showPreviewPane: Boolean = true,
        val providerId: Long = 0L,
    ) : Screen

    /**
     * The roots of the sections — the phone's bottom bar tabs, the TV's rail sections — each the
     * section's [CategoryList] with no category or channel picked
     * (the phone home overhaul plan → Bottom navigation bar,
     * the tv nav rail plan). Three destinations, not [CategoryList] with an
     * argument: Navigation saves a section's back stack under its root's destination, so one
     * destination for all three would restore Movies when Live TV is asked for.
     */
    @Serializable
    data object LiveTvTab : Screen

    @Serializable
    data object MoviesTab : Screen

    @Serializable
    data object TvShowsTab : Screen

    /** The phone's Home tab: the rows of every section (docs/plans/20261019_phone-home-tab-plan.md). */
    @Serializable
    data object HomeTab : Screen

    /**
     * Episode selection screen destination for TV shows.
     * Shows seasons and episodes for a selected series.
     *
     * @param initialEpisodeId When set (e.g. arriving from Continue Watching), open straight to
     * this episode's detail/resume panel instead of the season/episode list.
     * @param providerId The source the show is on.
     */
    @Serializable
    data class EpisodeSelection(
        val seriesId: String,
        val seriesName: String,
        val categoryId: String,
        val initialEpisodeId: String? = null,
        val providerId: Long,
    ) : Screen

    /**
     * A film or show as TMDB knows it, which no source of the profile may have: Search's "Not in
     * your sources" results and the Wanted list open it. It has no Play button; when a source has
     * the title, it opens that source's page instead. [contentType] is `MOVIES` or `TV_SHOWS`. See
     * the wanted titles plan → The details page.
     */
    @Serializable
    data class TmdbTitle(
        val contentType: String,
        val tmdbId: String,
        val title: String,
        val originalTitle: String? = null,
        val year: Int? = null,
        val posterPath: String? = null,
    ) : Screen

    /** @param providerId The source the film is on. */
    @Serializable
    data class MovieDetails(
        val movieId: String,
        val movieName: String,
        val categoryId: String,
        val providerId: Long,
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
        // Phone: a section's Search opens "ALL" with this section's filter chip selected.
        val initialTypeFilter: String? = null,
        // Phone: opened on its Guide tab, [initialQuery] run there (a Guide result's search, brought along).
        val initialGuideTab: Boolean = false,
        // Phone: the source chosen in the search brought along (a source's providerId), null for every source.
        val initialSource: Long? = null,
    ) : Screen

    /**
     * EPG (Electronic Program Guide) screen destination.
     * Shows TV guide with time grid for Live TV channels.
     *
     * @param categoryId The category ID to show EPG for (a real category, or the virtual
     *   `recent` / `favorites` lists)
     * @param focusChannelId The channel whose row the guide opens on (the one playing when opened
     *   from the player); null opens on the first channel with a programme on now
     * @param providerId The source whose channels the guide shows.
     */
    @Serializable
    data class EpgGuide(
        val categoryId: String,
        val categoryName: String,
        val focusChannelId: String? = null,
        val providerId: Long,
    ) : Screen

    /**
     * EPG Browser ("Search the guide") screen destination.
     * Allows searching programme titles in the locally-cached XMLTV file.
     *
     * @param categoryId When opened from a TV Guide: the guide's channel set, which the browser
     *   offers as an "In <category> only" filter (on by default). Null searches every channel.
     * @param providerId The source of that TV Guide, whose channels the filter keeps; 0 with no
     *   [categoryId]. The search itself covers every source of the profile.
     */
    @Serializable
    data class EpgBrowser(
        val categoryId: String? = null,
        val categoryName: String? = null,
        val providerId: Long = 0L,
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

    /** Settings → About → Device info (everyone): app, device, memory, storage, display, media, network and power. */
    @Serializable
    data object DeviceInfo : Screen

    /** Settings → About → Open-source licences: every library and font the app ships. */
    @Serializable
    data object Licenses : Screen

    /** Settings → About → Terms of use: the user alone is responsible for the sources they add. */
    @Serializable
    data object Terms : Screen

    /** One entry of [Licenses] with its licence texts, by its id. */
    @Serializable
    data class LicenseDetail(
        val entryId: String,
    ) : Screen

    /**
     * Settings › Parental controls on the phone (admin only): the word lists and Check a source. See
     * docs/plans/20261020_parental-controls-plan.md → The page.
     */
    @Serializable
    data object ParentalControls : Screen

    /** One list of [ParentalControls], by its `ParentalList.key`. */
    @Serializable
    data class ParentalListEdit(
        val listKey: String,
    ) : Screen

    /** [ParentalControls] › Check a source: the categories a child sees on source [providerId]. */
    @Serializable
    data class ParentalCheck(
        val providerId: Long,
    ) : Screen

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
     * @param catchupStartSec Catch-up: the archive window of live channel [streamId] to play, from
     *   this epoch second for [catchupDurationSec], starting [catchupOffsetSec] in (the programme's
     *   start); [programTitle] is the programme. Null plays [streamId] as it is. See
     *   the catchup plan.
     * @param providerId The source the stream plays from.
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
        val catchupStartSec: Long? = null,
        val catchupDurationSec: Long = 0L,
        val catchupOffsetSec: Long = 0L,
        val programTitle: String? = null,
        val providerId: Long,
    ) : Screen
}
