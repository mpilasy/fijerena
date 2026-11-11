package org.njarasoa.fijerena.core.network.sync

import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind

/**
 * The `kind` of a synced record. See the live sync plan → Record model.
 */
object SyncKind {
    const val WATCH = "watch"
    const val FAVORITE_STREAM = "favorite_stream"
    const val FAVORITE_CATEGORY = "favorite_category"

    /** "Clear watch history": one marker whose `deletedAt` drops every older watch row. */
    const val WATCH_CLEAR = "watch_clear"
    const val PROVIDER = "provider"
    const val PROFILE = "profile"

    /**
     * A profile's own Jellyfin login, keyed by the source's `providerKey`: older builds only. A
     * Jellyfin login now travels with the source ([PROVIDER]); one of these is never sent, and
     * one received becomes the source's login only while the source has none.
     */
    const val PROVIDER_LOGIN = "provider_login"

    /** A profile's category filters on one provider; keyed by the provider's `providerKey`. */
    const val CATEGORY_FILTERS = "category_filters"

    /** An EPG source; keyed by its `source_key`. */
    const val EPG_SOURCE = "epg_source"

    /** One setting; keyed by its `app_settings` key. Per profile for [AppSettings.PER_PROFILE_SETTING_KEYS], [SHARED] otherwise. */
    const val SETTING = "setting"

    /**
     * What a device is playing right now; keyed by its server device id. Volatile — see [VOLATILE]
     * and the live sync now playing plan.
     */
    const val NOW_PLAYING = "now_playing"

    /**
     * A command to one device (only "stop what you are playing"); keyed by the *target's* server
     * device id. Volatile: see [VOLATILE], [RemoteCommands] and the live sync now playing plan →
     * Remote Stop.
     */
    const val REMOTE_COMMAND = "remote_command"

    /**
     * A device asks the other devices for a profile's PIN; keyed by the *asking* device's server
     * id. Volatile — see [VOLATILE], [PinRequests] and the PIN on the phone plan.
     */
    const val PIN_REQUEST = "pin_request"

    /**
     * The answer to a [PIN_REQUEST]: unlocked or cancelled, never the PIN; keyed by the *asking*
     * device's server id. Volatile — see [VOLATILE] and [PinRequests].
     */
    const val PIN_ANSWER = "pin_answer"

    /**
     * A phone sends a film, an episode or a channel to one TV; keyed by the *target TV's* server
     * id. Volatile — see [VOLATILE], [PlayRequests] and the send to TV plan.
     */
    const val PLAY_REQUEST = "play_request"

    /** What the TV did with a [PLAY_REQUEST]; keyed by the *target TV's* server id. Volatile — see [VOLATILE] and [PlayRequests]. */
    const val PLAY_ANSWER = "play_answer"

    /**
     * Kinds held only in memory, on both ends: pushed from [VolatileRecords], applied to an
     * in-memory store — never a version row, a tombstone, or a step of the receiver's sync clocks.
     */
    val VOLATILE = setOf(NOW_PLAYING, REMOTE_COMMAND, PIN_REQUEST, PIN_ANSWER, PLAY_REQUEST, PLAY_ANSWER)

    /**
     * A film or show on a profile's Wanted list; keyed by its TMDB id, with the content type.
     * Not tied to a provider: its version and tombstone rows use [WANTED_PROVIDER_ID]. See the
     * wanted titles plan.
     */
    const val WANTED = "wanted"

    /** The `providerId` of [WANTED] rows in `xtream_v2.db`'s `sync_version` and `sync_tombstone`. */
    const val WANTED_PROVIDER_ID = 0L

    /**
     * A team or league a profile follows; keyed by its id at its source, with
     * `FollowedSportEntity.syncContentType` (`<kind>:<source>`) as the content type. Not tied to a
     * provider: its version and tombstone rows use [FOLLOWED_SPORT_PROVIDER_ID]. See the sports
     * teams plan.
     */
    const val FOLLOWED_SPORT = "followed_sport"

    /** The `providerId` of [FOLLOWED_SPORT] rows in `xtream_v2.db`'s `sync_version` and `sync_tombstone`. */
    const val FOLLOWED_SPORT_PROVIDER_ID = 0L

    /**
     * The kinds this build keeps (every kind but [VOLATILE]). Add each new kept kind here: an older
     * build skips a kind it doesn't know and moves past it, so a device whose last pass knew fewer
     * kinds pulls everything again once ([SyncEngine.syncNow]) and gets what it skipped.
     */
    val KEPT =
        setOf(
            WATCH,
            FAVORITE_STREAM,
            FAVORITE_CATEGORY,
            WATCH_CLEAR,
            PROVIDER,
            PROFILE,
            PROVIDER_LOGIN,
            CATEGORY_FILTERS,
            EPG_SOURCE,
            SETTING,
            WANTED,
            FOLLOWED_SPORT,
        )

    /** The profile slot of anything the whole household shares. */
    const val SHARED = "shared"

    fun forFavorite(favoriteKind: String): String = if (favoriteKind == FavoriteKind.CATEGORY) FAVORITE_CATEGORY else FAVORITE_STREAM

    /**
     * How long a deletion is kept locally — the server's planned tombstone horizon. A device
     * offline for longer does a full resync, so older deletions are never needed.
     */
    const val TOMBSTONE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000
}
