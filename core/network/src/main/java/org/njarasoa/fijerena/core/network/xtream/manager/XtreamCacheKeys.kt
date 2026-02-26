package org.njarasoa.fijerena.core.network.xtream.manager

object XtreamCacheKeys {
    // Cache expiry is now configurable via AppSettings (default: 24 hours)
    const val KEY_CATEGORIES_TIMESTAMP = "categories_timestamp"
    const val KEY_VOD_CATEGORIES_TIMESTAMP = "vod_categories_timestamp"
    const val KEY_SERIES_CATEGORIES_TIMESTAMP = "series_categories_timestamp"
    const val KEY_STREAMS_TIMESTAMP_PREFIX = "streams_timestamp_"

    // Legacy keys (kept for backwards compatibility but not used)
    const val KEY_LAST_CATEGORY_ID = "last_category_id"
    const val KEY_LAST_STREAM_ID = "last_stream_id"
    const val KEY_LAST_CONTENT_TYPE = "last_content_type"

    // Content-type specific last played tracking
    const val KEY_LAST_LIVE_CATEGORY = "last_live_category"
    const val KEY_LAST_LIVE_STREAM = "last_live_stream"
    const val KEY_LAST_MOVIES_CATEGORY = "last_movies_category"
    const val KEY_LAST_MOVIES_STREAM = "last_movies_stream"
    const val KEY_LAST_TVSHOWS_CATEGORY = "last_tvshows_category"
    const val KEY_LAST_TVSHOWS_STREAM = "last_tvshows_stream"

    // Watch history tracking
    const val KEY_WATCH_HISTORY = "watch_history"

    // Favorites tracking
    const val KEY_FAVORITES = "favorites"

    // EPG caching
    const val KEY_EPG_PREFIX = "epg_"
    const val KEY_EPG_TIMESTAMP_PREFIX = "epg_timestamp_"
    const val EPG_CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
}
