package org.njarasoa.fijerena.core.network.sync

import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind

/**
 * The `kind` of a synced record — see `docs/plans/20260929_live-sync-plan.md` → Record model.
 * Kinds are added as their write paths are queued for sync.
 */
object SyncKind {
    const val WATCH = "watch"
    const val FAVORITE_STREAM = "favorite_stream"
    const val FAVORITE_CATEGORY = "favorite_category"

    /** "Clear watch history": one marker whose `deletedAt` drops every older watch row. */
    const val WATCH_CLEAR = "watch_clear"
    const val PROVIDER = "provider"
    const val PROFILE = "profile"

    /** A Jellyfin login, per provider and profile; keyed by the provider's `providerKey`. */
    const val PROVIDER_LOGIN = "provider_login"

    /** A profile's category filters on one provider; keyed by the provider's `providerKey`. */
    const val CATEGORY_FILTERS = "category_filters"

    /** An EPG source; keyed by its `source_key`. */
    const val EPG_SOURCE = "epg_source"

    /** One setting; keyed by its `app_settings` key. Per profile for dev mode, [SHARED] otherwise. */
    const val SETTING = "setting"

    /** The profile slot of anything the whole household shares. */
    const val SHARED = "shared"

    fun forFavorite(favoriteKind: String): String =
        if (favoriteKind == FavoriteKind.CATEGORY) FAVORITE_CATEGORY else FAVORITE_STREAM

    /**
     * How long a deletion is kept locally — the server's planned tombstone horizon. A device
     * offline for longer does a full resync, so older deletions are never needed.
     */
    const val TOMBSTONE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000
}
