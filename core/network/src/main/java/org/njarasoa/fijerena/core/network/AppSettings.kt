package org.njarasoa.fijerena.core.network
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.njarasoa.fijerena.core.network.provider.ParentalRules
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.tmdb.TmdbKeyStore

class AppSettings(
    private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            "app_settings",
            Context.MODE_PRIVATE,
        )
    }

    companion object {
        private const val KEY_DEV_MODE = "dev_mode"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"

        /**
         * Retired: the source each profile last picked, from when a device had one source at a
         * time. No longer written nor synced; a value an older build sends is ignored. Read only
         * by the one-time upgrade to per-profile sources ([seedProfileSourcesOnce]).
         */
        private const val KEY_LAST_PROVIDER = "last_provider"
        private const val KEY_PROFILE_SOURCES = "profile_sources"
        private const val KEY_PROFILE_SOURCES_SEEDED = "profile_sources_seeded_v1"
        private const val KEY_PROFILE_SOURCE_RECORDS_QUEUED = "profile_source_records_queued_v1"
        private const val KEY_AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"

        /** A child profile ([isChildProfile]); public for the sync applier, which re-applies the filters. */
        const val KEY_CHILD_PROFILE = "child_profile"
        private const val KEY_RECENT_ROW_SIZE = "recent_row_size"
        private const val KEY_PER_SOURCE_PLAYBACK_MOVED = "per_source_playback_moved_v1"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_FAILURES = "pin_failures"
        private const val KEY_PIN_WAIT_UNTIL = "pin_wait_until"
        private const val KEY_CHILD_LOCK_FAILURES = "child_lock_failures"
        private const val KEY_CHILD_LOCK_WAIT_UNTIL = "child_lock_wait_until"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_FAVORITES_MAX_SIZE = "favorites_max_size"
        private const val KEY_CACHE_EXPIRY_HOURS = "cache_expiry_hours"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_THEME_ID = "theme_id"
        private const val KEY_UI_STYLE_ID = "ui_style_id"
        private const val KEY_UI_THEME_ID = "ui_theme_id"
        private const val KEY_POSTER_BACKDROP = "poster_backdrop"
        private const val KEY_EPG_URL = "epg_url"
        private const val KEY_EPG_TIMEZONE_OFFSET = "epg_timezone_offset"
        private const val KEY_EPG_AUTO_REFRESH = "epg_auto_refresh"
        private const val KEY_EPG_REFRESH_INTERVAL = "epg_refresh_interval"
        private const val KEY_CONTENT_AUTO_REFRESH = "content_auto_refresh"
        private const val KEY_CONTENT_REFRESH_TIME = "content_refresh_time"
        private const val KEY_CELLULAR_LIVE_MULTIPLIER = "cellular_live_multiplier"
        private const val KEY_CELLULAR_VOD_MULTIPLIER = "cellular_vod_multiplier"
        private const val KEY_HAS_PROVIDER_CACHE = "has_provider_cache"
        private const val KEY_FAVORITE_CATEGORY_ROWS_PURGED = "favorite_category_rows_purged_v1"
        private const val KEY_AUTO_XMLTV_SOURCES_CLEANED = "auto_xmltv_sources_cleaned_v1"
        private const val KEY_EPG_REFRESH_INTERVAL_COPIED = "epg_refresh_interval_copied_v1"
        private const val KEY_WATCH_DELAY_SECONDS = "watch_delay_seconds"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_EPG_SEARCH_HISTORY = "epg_search_history"
        private const val KEY_LAST_TAB = "last_tab"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_SHARE_NOW_PLAYING = "share_now_playing"
        private const val KEY_LAST_SHRINK_AT_MS = "last_shrink_at_ms"
        private const val KEY_ORPHAN_SWEEP_PENDING = "orphan_sweep_pending"
        private const val KEY_LAST_SHRINK_DURATION_MS = "last_shrink_duration_ms"
        private const val KEY_LAST_SHRINK_ROWS_REMOVED = "last_shrink_rows_removed"
        private const val KEY_LAST_SHRINK_BYTES_RECLAIMED = "last_shrink_bytes_reclaimed"
        private const val MAX_SEARCH_HISTORY = 20
        const val DEFAULT_WATCH_HISTORY_SIZE = 25
        const val DEFAULT_WATCH_DELAY_SECONDS = 10
        const val MIN_WATCH_DELAY_SECONDS = 5
        const val MAX_WATCH_DELAY_SECONDS = 120
        const val DEFAULT_FAVORITES_MAX_SIZE = 100
        const val DEFAULT_CACHE_EXPIRY_HOURS = 24
        const val DEFAULT_UI_SCALE = 0.8f
        const val DEFAULT_EPG_URL = ""
        const val DEFAULT_EPG_REFRESH_INTERVAL = 24
        const val DEFAULT_CONTENT_REFRESH_TIME = "04:00"
        const val DEFAULT_CELLULAR_MULTIPLIER = 1.0f
        const val DEFAULT_THEME_ID = "deep_night"
        const val DEFAULT_UI_STYLE_ID = "modern"

        /**
         * The user's TMDB key: synced like a setting, but stored in [TmdbKeyStore]'s encrypted file,
         * not in these prefs. Sync records are end-to-end encrypted, so the server never sees it.
         */
        const val KEY_TMDB_API_KEY = "tmdb_api_key"

        /** Settings kept the same on every device by live sync; those in [PER_PROFILE_SETTING_KEYS] are per profile. */
        val SYNCED_SETTING_KEYS =
            listOf(
                KEY_THEME_ID,
                KEY_UI_STYLE_ID,
                KEY_UI_THEME_ID,
                KEY_POSTER_BACKDROP,
                KEY_DEV_MODE,
                KEY_AUTOPLAY_NEXT_EPISODE,
                KEY_TMDB_API_KEY,
                KEY_PIN_HASH,
                KEY_CHILD_PROFILE,
            ) + ParentalList.entries.map { it.key }
        val PER_PROFILE_SETTING_KEYS =
            setOf(
                KEY_THEME_ID,
                KEY_UI_STYLE_ID,
                KEY_UI_THEME_ID,
                KEY_POSTER_BACKDROP,
                KEY_DEV_MODE,
                KEY_AUTOPLAY_NEXT_EPISODE,
                KEY_PIN_HASH,
                KEY_CHILD_PROFILE,
            )

        /**
         * A profile's sources sync one record per source — `profile_source:<providerKey>`, true or
         * false — not as one list: last writer wins per record, so two devices turning on different
         * sources before they sync both keep them, a source a device added while unlinked survives
         * it joining a group, and turning one off still beats an older "on". A whole-list
         * `profile_sources` record from a build before this is ignored.
         */
        const val PROFILE_SOURCE_SETTING_PREFIX = "profile_source:"

        fun profileSourceSettingKey(providerKey: String) = PROFILE_SOURCE_SETTING_PREFIX + providerKey

        /**
         * A live sync device's name as the group calls it, one shared record per device —
         * `device_name:<deviceId>`, the name or "" for the server's (the device's model, set when
         * it joined). End-to-end encrypted like every record, so the server never sees it.
         */
        const val DEVICE_NAME_SETTING_PREFIX = "device_name:"

        fun deviceNameSettingKey(deviceId: String) = DEVICE_NAME_SETTING_PREFIX + deviceId

        /** The whole-list setting key a build before per-source records synced. */
        const val LEGACY_PROFILE_SOURCES_SETTING = KEY_PROFILE_SOURCES

        private val sourceKeysSerializer = ListSerializer(String.serializer())

        /** The Parental controls lists' setting keys: shared, not per profile. */
        val PARENTAL_KEYS = ParentalList.entries.map { it.key }.toSet()
        const val MAX_DEVICE_NAME_LENGTH = 64
        const val MIN_CELLULAR_MULTIPLIER = 0.5f
        const val MAX_CELLULAR_MULTIPLIER = 3.0f
    }

    /**
     * The profile using this device. Per device and never synced: the TV and a phone are often in
     * different hands at the same time. See the live sync plan → User profiles.
     */
    var activeProfileId: String
        get() = prefs.getString(KEY_ACTIVE_PROFILE_ID, null) ?: ProfileEntity.DEFAULT_ID
        set(value) = prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, value) }

    /**
     * Whether live sync tells the group what this device is playing. Per device — not per profile
     * and never synced (not in [SYNCED_SETTING_KEYS]): turning it on for the kids' TV must not turn
     * it on everywhere. Off until turned on. See the live sync now playing plan.
     */
    var shareNowPlaying: Boolean
        get() = prefs.getBoolean(KEY_SHARE_NOW_PLAYING, false)
        set(value) = prefs.edit { putBoolean(KEY_SHARE_NOW_PLAYING, value) }

    /**
     * Developer mode of the active profile — each profile has its own, off until turned on. See
     * the profile scoped settings plan. Falls back to the install-wide flag
     * this replaced until [copyLegacyDevModeToProfiles] has run.
     */
    var isDevMode: Boolean
        get() = devMode(activeProfileId)
        set(value) = setDevMode(activeProfileId, value)

    /** [profileId]'s developer mode, whichever profile this device uses (its profile page edits it). */
    fun devMode(profileId: String): Boolean = prefs.getBoolean(devModeKey(profileId), prefs.getBoolean(KEY_DEV_MODE, false))

    fun setDevMode(
        profileId: String,
        enabled: Boolean,
    ) {
        prefs.edit { putBoolean(devModeKey(profileId), enabled) }
        SettingsSyncQueue.setting(context, KEY_DEV_MODE, profileId)
    }

    /**
     * [profileId] is a child's: every Xtream source shows it only what Parental controls let through
     * ([org.njarasoa.fijerena.core.network.provider.CategoryFilters.child]) and it is never
     * Expert. Per profile and synced; off until turned on. See
     * docs/plans/20261019_tv-household-ux-plan.md, Phase 1.
     */
    fun isChildProfile(profileId: String): Boolean = prefs.getBoolean(profileKey(KEY_CHILD_PROFILE, profileId), false)

    fun setChildProfile(
        profileId: String,
        child: Boolean,
    ) {
        prefs.edit { putBoolean(profileKey(KEY_CHILD_PROFILE, profileId), child) }
        SettingsSyncQueue.setting(context, KEY_CHILD_PROFILE, profileId)
    }

    /** Drops a deleted profile's child flag. */
    fun removeChildProfile(profileId: String) = prefs.edit { remove(profileKey(KEY_CHILD_PROFILE, profileId)) }

    /**
     * One-time upgrade: gives every profile in [profileIds] the install-wide developer-mode flag
     * (unless it already has its own), then drops that flag so profiles added later start off.
     */
    fun copyLegacyDevModeToProfiles(profileIds: List<String>) {
        if (!prefs.contains(KEY_DEV_MODE)) return
        val legacy = prefs.getBoolean(KEY_DEV_MODE, false)
        prefs.edit {
            profileIds.filterNot { prefs.contains(devModeKey(it)) }.forEach { putBoolean(devModeKey(it), legacy) }
            remove(KEY_DEV_MODE)
        }
    }

    /**
     * Whether the active profile's TV-show episodes roll on to the next one, after a short
     * countdown, when they end. Per profile and synced, like [isDevMode]; off until turned on.
     */
    var autoplayNextEpisode: Boolean
        get() = autoplayNextEpisode(activeProfileId)
        set(value) = setAutoplayNextEpisode(activeProfileId, value)

    /** [profileId]'s "Play next episode automatically", whichever profile this device uses. */
    fun autoplayNextEpisode(profileId: String): Boolean = prefs.getBoolean(profileKey(KEY_AUTOPLAY_NEXT_EPISODE, profileId), false)

    fun setAutoplayNextEpisode(
        profileId: String,
        enabled: Boolean,
    ) {
        prefs.edit { putBoolean(profileKey(KEY_AUTOPLAY_NEXT_EPISODE, profileId), enabled) }
        SettingsSyncQueue.setting(context, KEY_AUTOPLAY_NEXT_EPISODE, profileId)
    }

    /**
     * How many cards each Recent row shows, on this device: the rows merge every source, so one
     * number rather than one per source (D4). 1–100.
     */
    var recentRowSize: Int
        get() = prefs.getInt(KEY_RECENT_ROW_SIZE, DEFAULT_WATCH_HISTORY_SIZE)
        set(value) = prefs.edit { putInt(KEY_RECENT_ROW_SIZE, value.coerceIn(1, 100)) }

    /**
     * Once: the per-source Recent row size becomes [recentRowSize] (per device), the largest size
     * any source had ([sourceRecentSizes]). Written here only, not synced: every device works the
     * same answer out from the same sources.
     */
    fun movePerSourcePlaybackOnce(sourceRecentSizes: List<Int>) {
        if (prefs.getBoolean(KEY_PER_SOURCE_PLAYBACK_MOVED, false)) return
        prefs.edit {
            if (!prefs.contains(KEY_RECENT_ROW_SIZE)) {
                sourceRecentSizes.maxOrNull()?.let { putInt(KEY_RECENT_ROW_SIZE, it.coerceIn(1, 100)) }
            }
            putBoolean(KEY_PER_SOURCE_PLAYBACK_MOVED, true)
        }
    }

    /** The source [profileId] last picked under the retired [KEY_LAST_PROVIDER], if this device has it stored. */
    private fun lastProviderKey(profileId: String): String? = prefs.getString(profileKey(KEY_LAST_PROVIDER, profileId), null)

    /**
     * The `providerKey`s of the sources [profileId] has (multi-source plan, P1): per profile and
     * synced, so a profile has the same sources on every device. Null when nothing is stored — a
     * profile from before [seedProfileSourcesOnce], or one synced in from an older build. Keys of
     * sources this device doesn't have (deleted, or not synced here yet) are kept: the resolver
     * (`ProviderRepository.profileSources`) skips them. See
     * docs/plans/20261012_multi-source-search-history-plan.md → Phase 2.
     */
    fun profileSourceKeys(profileId: String): Set<String>? =
        decodeSourceKeys(prefs.getString(profileKey(KEY_PROFILE_SOURCES, profileId), null))

    fun setProfileSourceKeys(
        profileId: String,
        keys: Set<String>,
    ) {
        val before = profileSourceKeys(profileId)
        if (before == keys) return
        prefs.edit { putString(profileKey(KEY_PROFILE_SOURCES, profileId), encodeSourceKeys(keys)) }
        val changed = if (before == null) keys else (keys - before) + (before - keys)
        changed.forEach { SettingsSyncQueue.setting(context, profileSourceSettingKey(it), profileId) }
    }

    /** Turns [providerKey] on for [profileId], keeping the sources it already has. */
    fun addProfileSource(
        profileId: String,
        providerKey: String,
    ) = setProfileSourceKeys(profileId, profileSourceKeys(profileId).orEmpty() + providerKey)

    /**
     * The active profile and its developer mode ([isDevMode]), now and whenever either changes (a
     * profile switch, the switch on its page, or a value received from another device).
     */
    fun observeDevMode(): Flow<Pair<String, Boolean>> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(activeProfileId to isDevMode) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(activeProfileId to isDevMode)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()

    /** [profileSourceKeys], now and on every change (an edit here or a value received from another device). */
    fun observeProfileSourceKeys(profileId: String): Flow<Set<String>?> {
        val stored = profileKey(KEY_PROFILE_SOURCES, profileId)
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                    if (changed == stored) trySend(profileSourceKeys(profileId))
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(profileSourceKeys(profileId))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()
    }

    /**
     * One-time upgrade (multi-source plan, O2): each profile in [profileIds] gets only the source it
     * last picked ([lastProviderKey], when [existingKeys] still has it); one that never picked gets
     * [activeKey], the source this device is on. A profile that already has its sources — received
     * from another device before this ran — keeps them. Per device and not sent: every device works
     * it out the same from the synced last pick, and sending it could overwrite a choice made on a
     * device that upgraded first. A new link sends it with everything else (`LocalRecords`).
     */
    fun seedProfileSourcesOnce(
        profileIds: List<String>,
        existingKeys: Set<String>,
        activeKey: String?,
    ) {
        if (prefs.getBoolean(KEY_PROFILE_SOURCES_SEEDED, false)) return
        prefs.edit(commit = true) {
            profileIds.filterNot { prefs.contains(profileKey(KEY_PROFILE_SOURCES, it)) }.forEach { id ->
                val key = lastProviderKey(id)?.takeIf { it in existingKeys } ?: activeKey
                if (key != null) putString(profileKey(KEY_PROFILE_SOURCES, id), encodeSourceKeys(setOf(key)))
            }
            putBoolean(KEY_PROFILE_SOURCES_SEEDED, true)
        }
    }

    /**
     * One-time upgrade flag: whether this device has seeded the per-source `profile_source:<key>`
     * records (see `ProfileRepository.migrateLegacyProfileSettings`).
     */
    var profileSourceRecordsQueued: Boolean
        get() = prefs.getBoolean(KEY_PROFILE_SOURCE_RECORDS_QUEUED, false)
        set(value) = prefs.edit { putBoolean(KEY_PROFILE_SOURCE_RECORDS_QUEUED, value) }

    /** The name the group gave live sync device [deviceId]; null when it has none (the server's name then). */
    fun deviceName(deviceId: String): String? = prefs.getString(DEVICE_NAME_SETTING_PREFIX + deviceId, null)?.takeIf { it.isNotBlank() }

    /** Every device's name set here or received, by device id ("" for one put back to the server's). */
    fun deviceNames(): Map<String, String> =
        prefs.all.entries
            .filter { it.key.startsWith(DEVICE_NAME_SETTING_PREFIX) }
            .associate { it.key.removePrefix(DEVICE_NAME_SETTING_PREFIX) to (it.value as? String).orEmpty() }

    /** Names live sync device [deviceId] for the whole group; blank goes back to the server's name. */
    fun setDeviceName(
        deviceId: String,
        name: String,
    ) {
        prefs.edit { putString(DEVICE_NAME_SETTING_PREFIX + deviceId, name.trim().take(MAX_DEVICE_NAME_LENGTH)) }
        SettingsSyncQueue.setting(context, deviceNameSettingKey(deviceId))
    }

    /** [deviceNames] with blanks left out, now and on every change (a rename here or from another device). */
    fun observeDeviceNames(): Flow<Map<String, String>> =
        callbackFlow {
            val current = { deviceNames().filterValues { it.isNotBlank() } }
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                    if (changed?.startsWith(DEVICE_NAME_SETTING_PREFIX) == true) trySend(current())
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(current())
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()

    /** Drops a deleted profile's sources. */
    fun removeProfileSources(profileId: String) = prefs.edit { remove(profileKey(KEY_PROFILE_SOURCES, profileId)) }

    private fun encodeSourceKeys(keys: Set<String>): String = Json.encodeToString(sourceKeysSerializer, keys.sorted())

    /** Null for nothing stored, or for a value that isn't a JSON array of strings. */
    private fun decodeSourceKeys(raw: String?): Set<String>? =
        raw?.let {
            try {
                Json.decodeFromString(sourceKeysSerializer, it).toSet()
            } catch (_: IllegalArgumentException) {
                // cancellation-ok: non-suspend
                null
            }
        }

    /**
     * A setting received from another device (live sync): written straight to prefs, not through
     * the setters, which would queue it to be sent back. Keys other than [SYNCED_SETTING_KEYS] are
     * ignored — a newer app version may sync more, and an older one still sends the retired guide
     * auto-refresh keys. A value this version can't use (a blank theme, a non-boolean switch) is
     * dropped, so the local value stays: one bad record must not break every linked device (R-09).
     * An unknown theme id is kept, since `paletteById` already falls back to the default palette.
     */
    fun applyRemoteSetting(
        key: String,
        profileId: String,
        value: kotlinx.serialization.json.JsonPrimitive,
    ) {
        if (key == KEY_TMDB_API_KEY) {
            if (value.isString) TmdbKeyStore(context).applyRemote(value.content)
            return
        }
        if (key.startsWith(DEVICE_NAME_SETTING_PREFIX)) {
            if (value.isString) prefs.edit { putString(key, value.content.trim().take(MAX_DEVICE_NAME_LENGTH)) }
            return
        }
        if (key.startsWith(PROFILE_SOURCE_SETTING_PREFIX)) {
            val on = value.booleanOrNull ?: return
            val providerKey = key.removePrefix(PROFILE_SOURCE_SETTING_PREFIX).takeIf { it.isNotEmpty() } ?: return
            val current = profileSourceKeys(profileId)
            // Off for a profile with nothing stored changes nothing: an empty list would mean "no sources".
            if (current == null && !on) return
            val updated = if (on) current.orEmpty() + providerKey else current.orEmpty() - providerKey
            prefs.edit { putString(profileKey(KEY_PROFILE_SOURCES, profileId), encodeSourceKeys(updated)) }
            return
        }
        prefs.edit {
            when (key) {
                KEY_DEV_MODE -> {
                    value.booleanOrNull?.let { putBoolean(devModeKey(profileId), it) }
                }

                KEY_AUTOPLAY_NEXT_EPISODE, KEY_POSTER_BACKDROP -> {
                    if (profileId != SyncKind.SHARED) value.booleanOrNull?.let { putBoolean(profileKey(key, profileId), it) }
                }

                KEY_CHILD_PROFILE -> {
                    if (profileId != SyncKind.SHARED) value.booleanOrNull?.let { putBoolean(profileKey(key, profileId), it) }
                }

                // Blank: the PIN was removed.
                KEY_PIN_HASH -> {
                    if (profileId != SyncKind.SHARED && value.isString) putString(profileKey(key, profileId), value.content)
                }

                in PARENTAL_KEYS -> {
                    if (value.isString) putString(key, value.content)
                }

                // A device-wide theme an older build sends (profile id SHARED) has no profile to go to: dropped.
                KEY_THEME_ID, KEY_UI_STYLE_ID, KEY_UI_THEME_ID -> {
                    if (profileId != SyncKind.SHARED && value.isString && value.content.isNotBlank()) {
                        putString(profileKey(key, profileId), value.content)
                    }
                }
            }
        }
    }

    /** The value of a synced setting as it is sent — [profileId] matters only for [PER_PROFILE_SETTING_KEYS]. */
    fun syncedSetting(
        key: String,
        profileId: String,
    ): kotlinx.serialization.json.JsonPrimitive? {
        if (key == KEY_TMDB_API_KEY) {
            val store = TmdbKeyStore(context)
            return if (store.hasValue()) kotlinx.serialization.json.JsonPrimitive(store.key) else null
        }
        if (key.startsWith(DEVICE_NAME_SETTING_PREFIX)) {
            return prefs.getString(key, null)?.let { kotlinx.serialization.json.JsonPrimitive(it) }
        }
        if (key.startsWith(PROFILE_SOURCE_SETTING_PREFIX)) {
            val keys = profileSourceKeys(profileId) ?: return null
            return kotlinx.serialization.json.JsonPrimitive(key.removePrefix(PROFILE_SOURCE_SETTING_PREFIX) in keys)
        }
        val stored = if (key in PER_PROFILE_SETTING_KEYS) profileKey(key, profileId) else key
        if (!prefs.contains(stored)) return null
        return when (key) {
            KEY_DEV_MODE, KEY_AUTOPLAY_NEXT_EPISODE, KEY_CHILD_PROFILE, KEY_POSTER_BACKDROP -> {
                kotlinx.serialization.json.JsonPrimitive(
                    prefs.getBoolean(stored, false),
                )
            }

            KEY_THEME_ID, KEY_UI_STYLE_ID, KEY_UI_THEME_ID, KEY_PIN_HASH, in PARENTAL_KEYS -> {
                kotlinx.serialization.json.JsonPrimitive(prefs.getString(stored, null))
            }

            else -> {
                null
            }
        }
    }

    /** Drops a deleted profile's developer-mode flag. */
    fun removeDevMode(profileId: String) = prefs.edit { remove(devModeKey(profileId)) }

    /**
     * [profileId]'s PIN as stored by `ProfilePins` (`salt:hash`), or null without one. Per profile
     * and synced, so one PIN works on every device; a removed PIN is kept blank so the removal
     * syncs too.
     */
    fun pinHash(profileId: String): String? = prefs.getString(profileKey(KEY_PIN_HASH, profileId), null)?.takeIf { it.isNotBlank() }

    fun setPinHash(
        profileId: String,
        hash: String?,
    ) {
        prefs.edit { putString(profileKey(KEY_PIN_HASH, profileId), hash.orEmpty()) }
        SettingsSyncQueue.setting(context, KEY_PIN_HASH, profileId)
    }

    /** Wrong PINs in a row for [profileId] on this device (not synced). */
    fun pinFailures(profileId: String): Int = prefs.getInt(profileKey(KEY_PIN_FAILURES, profileId), 0)

    /** When [profileId]'s PIN may be tried again on this device, epoch ms; 0 when it may now. */
    fun pinWaitUntil(profileId: String): Long = prefs.getLong(profileKey(KEY_PIN_WAIT_UNTIL, profileId), 0L)

    fun setPinThrottle(
        profileId: String,
        failures: Int,
        waitUntil: Long,
    ) = prefs.edit {
        putInt(profileKey(KEY_PIN_FAILURES, profileId), failures)
        putLong(profileKey(KEY_PIN_WAIT_UNTIL, profileId), waitUntil)
    }

    /**
     * Wrong PINs in a row typed to leave a child profile on this device (not synced). The child lock
     * counts them apart from each profile's own count, so a child's guesses never start a wait on
     * the Expert's Settings PIN.
     */
    val childLockFailures: Int get() = prefs.getInt(KEY_CHILD_LOCK_FAILURES, 0)

    /** When a PIN to leave a child profile may be tried again on this device, epoch ms; 0 when it may now. */
    val childLockWaitUntil: Long get() = prefs.getLong(KEY_CHILD_LOCK_WAIT_UNTIL, 0L)

    fun setChildLockThrottle(
        failures: Int,
        waitUntil: Long,
    ) = prefs.edit {
        putInt(KEY_CHILD_LOCK_FAILURES, failures)
        putLong(KEY_CHILD_LOCK_WAIT_UNTIL, waitUntil)
    }

    /** Drops a deleted profile's PIN and its wrong-PIN count. */
    fun removePin(profileId: String) =
        prefs.edit {
            remove(profileKey(KEY_PIN_HASH, profileId))
            remove(profileKey(KEY_PIN_FAILURES, profileId))
            remove(profileKey(KEY_PIN_WAIT_UNTIL, profileId))
        }

    /** Drops a deleted profile's autoplay-next-episode choice. */
    fun removeAutoplayNextEpisode(profileId: String) = prefs.edit { remove(profileKey(KEY_AUTOPLAY_NEXT_EPISODE, profileId)) }

    private fun devModeKey(profileId: String) = profileKey(KEY_DEV_MODE, profileId)

    private fun profileKey(
        key: String,
        profileId: String,
    ) = "${key}_$profileId"

    var providerName: String
        get() = prefs.getString(KEY_PROVIDER_NAME, "My Provider") ?: "My Provider"
        set(value) = prefs.edit { putString(KEY_PROVIDER_NAME, value) }

    var favoritesMaxSize: Int
        get() = prefs.getInt(KEY_FAVORITES_MAX_SIZE, DEFAULT_FAVORITES_MAX_SIZE)
        set(value) {
            val clampedValue = value.coerceIn(10, 500)
            prefs.edit { putInt(KEY_FAVORITES_MAX_SIZE, clampedValue) }
        }

    /**
     * Default: 24 hours (1 day)
     * Range: 1-168 hours (1 hour to 7 days)
     */
    var cacheExpiryHours: Int
        get() = prefs.getInt(KEY_CACHE_EXPIRY_HOURS, DEFAULT_CACHE_EXPIRY_HOURS)
        set(value) {
            val clampedValue = value.coerceIn(1, 168)
            prefs.edit { putInt(KEY_CACHE_EXPIRY_HOURS, clampedValue) }
        }

    val cacheExpiryMs: Long
        get() = cacheExpiryHours * 60 * 60 * 1000L

    /**
     * TV's Text & grid size as picked: 0.4f (8K), 0.6f (4K), 0.8f (HD), 1.0f (SD), or null for
     * Auto — never picked, or Auto picked — where the TV app works it out from the screen.
     */
    var uiScaleChoice: Float?
        get() = if (prefs.contains(KEY_UI_SCALE)) prefs.getFloat(KEY_UI_SCALE, DEFAULT_UI_SCALE) else null
        set(value) {
            prefs.edit { if (value == null) remove(KEY_UI_SCALE) else putFloat(KEY_UI_SCALE, value.coerceIn(0.4f, 1.0f)) }
        }

    /**
     * The active profile's colour theme. Per profile and synced, like [isDevMode]; falls back to the
     * device-wide theme this replaced until [copyLegacyLookToProfiles] has run.
     */
    var themeId: String
        get() = themeId(activeProfileId)
        set(value) = setThemeId(activeProfileId, value)

    /** [profileId]'s colour theme in use: its packaged UI theme's, else its own. */
    fun themeId(profileId: String): String = look(profileId).themeId

    private fun storedThemeId(profileId: String): String =
        prefs.getString(profileKey(KEY_THEME_ID, profileId), null)
            ?: prefs.getString(KEY_THEME_ID, null)
            ?: DEFAULT_THEME_ID

    fun setThemeId(
        profileId: String,
        value: String,
    ) {
        prefs.edit { putString(profileKey(KEY_THEME_ID, profileId), value) }
        SettingsSyncQueue.setting(context, KEY_THEME_ID, profileId)
    }

    /**
     * The active profile's Look and Feel (shape/type/icon/grid/dialog character), independent of
     * [themeId]'s colour. Per profile and synced, like [themeId].
     */
    var uiStyleId: String
        get() = uiStyleId(activeProfileId)
        set(value) = setUiStyleId(activeProfileId, value)

    /** [profileId]'s Look and Feel in use: its packaged UI theme's, else its own. */
    fun uiStyleId(profileId: String): String = look(profileId).uiStyleId

    private fun storedUiStyleId(profileId: String): String =
        prefs.getString(profileKey(KEY_UI_STYLE_ID, profileId), null)
            ?: prefs.getString(KEY_UI_STYLE_ID, null)
            ?: DEFAULT_UI_STYLE_ID

    fun setUiStyleId(
        profileId: String,
        value: String,
    ) {
        prefs.edit { putString(profileKey(KEY_UI_STYLE_ID, profileId), value) }
        SettingsSyncQueue.setting(context, KEY_UI_STYLE_ID, profileId)
    }

    /**
     * The active profile's UI theme ([UiThemes.CUSTOM] or a packaged one's id). Per profile and
     * synced, like [themeId]. See docs/plans/20261021_ui-themes-plan.md.
     */
    var uiThemeId: String
        get() = uiThemeId(activeProfileId)
        set(value) = setUiThemeId(activeProfileId, value)

    fun uiThemeId(profileId: String): String = prefs.getString(profileKey(KEY_UI_THEME_ID, profileId), null) ?: UiThemes.CUSTOM

    /**
     * Picks [profileId]'s UI theme. A packaged one also writes its colour theme and Look and Feel as
     * the profile's own, so a build that does not know it shows the same look.
     */
    fun setUiThemeId(
        profileId: String,
        value: String,
    ) {
        prefs.edit { putString(profileKey(KEY_UI_THEME_ID, profileId), value) }
        SettingsSyncQueue.setting(context, KEY_UI_THEME_ID, profileId)
        UiThemes.packaged(value)?.let {
            setThemeId(profileId, it.themeId)
            setUiStyleId(profileId, it.uiStyleId)
        }
    }

    /**
     * Whether the focused title's poster shows behind the active profile's Home and lists (the
     * blurred wash, and the title art on TV Home). Per profile and synced; on until turned off.
     */
    val posterBackdrop: Boolean
        get() = posterBackdrop(activeProfileId)

    fun posterBackdrop(profileId: String): Boolean = prefs.getBoolean(profileKey(KEY_POSTER_BACKDROP, profileId), true)

    fun setPosterBackdrop(
        profileId: String,
        enabled: Boolean,
    ) {
        prefs.edit { putBoolean(profileKey(KEY_POSTER_BACKDROP, profileId), enabled) }
        SettingsSyncQueue.setting(context, KEY_POSTER_BACKDROP, profileId)
    }

    /** [profileId]'s look in use: its packaged UI theme's values, else its own. */
    fun look(profileId: String): ProfileLook =
        UiThemes.resolveLook(
            uiThemeId(profileId),
            ProfileLook(
                storedThemeId(profileId),
                storedUiStyleId(profileId),
                posterBackdrop(profileId),
            ),
        )

    /** A new profile starts with [fromProfileId]'s UI theme, colour theme, Look and Feel and poster background (the profile that made it). */
    fun copyLook(
        fromProfileId: String,
        toProfileId: String,
    ) {
        setThemeId(toProfileId, themeId(fromProfileId))
        setUiStyleId(toProfileId, uiStyleId(fromProfileId))
        setPosterBackdrop(toProfileId, posterBackdrop(fromProfileId))
        setUiThemeId(toProfileId, uiThemeId(fromProfileId))
    }

    /**
     * One-time upgrade: gives every profile in [profileIds] the device-wide colour theme and Look
     * and Feel (unless it already has its own), then drops them. Not sent: each device gives its
     * profiles its own look, which is what they saw there before; a later change syncs.
     */
    fun copyLegacyLookToProfiles(profileIds: List<String>) {
        val keys = listOf(KEY_THEME_ID, KEY_UI_STYLE_ID).filter { prefs.contains(it) }
        if (keys.isEmpty()) return
        prefs.edit {
            keys.forEach { key ->
                val legacy = prefs.getString(key, null)
                profileIds.filterNot { prefs.contains(profileKey(key, it)) }.forEach { putString(profileKey(key, it), legacy) }
                remove(key)
            }
        }
    }

    /** [activeProfileId], and again whenever it changes — here or by a switch live sync makes. */
    fun observeActiveProfileId(): Flow<String> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(activeProfileId) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(activeProfileId)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()

    fun observeLook(): Flow<ProfileLook> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(look(activeProfileId)) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(look(activeProfileId))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()

    /** Drops a deleted profile's colour theme and Look and Feel. */
    fun removeLook(profileId: String) =
        prefs.edit {
            remove(profileKey(KEY_THEME_ID, profileId))
            remove(profileKey(KEY_UI_STYLE_ID, profileId))
            remove(profileKey(KEY_UI_THEME_ID, profileId))
            remove(profileKey(KEY_POSTER_BACKDROP, profileId))
        }

    /**
     * Get or set application language (ISO 639-1 code).
     * Supported: "en", "fr", "mg"
     * Default: "en"
     */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }

    /**
     * The retired device-wide external XMLTV EPG URL, read only by the one-time migration to guide
     * sources. Empty string means none.
     */
    var epgUrl: String
        get() = prefs.getString(KEY_EPG_URL, DEFAULT_EPG_URL) ?: DEFAULT_EPG_URL
        set(value) = prefs.edit { putString(KEY_EPG_URL, value.trim()) }

    /**
     * The retired device-wide guide auto-refresh, read only: each guide source has its own
     * interval (`epg_source.refresh_interval_hours`), set on its row. Nothing writes, syncs or
     * exports these any more; they are the interval
     * [org.njarasoa.fijerena.core.network.xmltv.EpgRefreshSchedule.copyLegacyIntervalOnce] copies
     * once, and the one a source without an interval of its own uses.
     */
    val epgAutoRefreshEnabled: Boolean
        get() = prefs.getBoolean(KEY_EPG_AUTO_REFRESH, true)

    /** Hours, -1 = never. Retired, see [epgAutoRefreshEnabled]. */
    val epgRefreshInterval: Int
        get() = prefs.getInt(KEY_EPG_REFRESH_INTERVAL, DEFAULT_EPG_REFRESH_INTERVAL)

    /**
     * Enable or disable automatic background refresh of provider content (categories/streams).
     */
    var contentAutoRefreshEnabled: Boolean
        get() = prefs.getBoolean(KEY_CONTENT_AUTO_REFRESH, true)
        set(value) = prefs.edit { putBoolean(KEY_CONTENT_AUTO_REFRESH, value) }

    /**
     * Content refresh start time (HH:mm format).
     * Default: 04:00
     */
    var contentRefreshTime: String
        get() = prefs.getString(KEY_CONTENT_REFRESH_TIME, DEFAULT_CONTENT_REFRESH_TIME) ?: DEFAULT_CONTENT_REFRESH_TIME
        set(value) = prefs.edit { putString(KEY_CONTENT_REFRESH_TIME, value) }

    /**
     * Timezone offset override for XMLTV data, in hours (e.g., 8 for UTC+8, -5 for UTC-5).
     * When non-zero, replaces the timezone offset in XMLTV timestamps during parsing.
     * This fixes XMLTV sources that encode local times but mislabel them as UTC (+0000).
     * Default: 0 (use timezone from XMLTV data as-is).
     */
    var epgTimezoneOffsetHours: Int
        get() = prefs.getInt(KEY_EPG_TIMEZONE_OFFSET, 0)
        set(value) {
            val clamped = value.coerceIn(-12, 14)
            prefs.edit { putInt(KEY_EPG_TIMEZONE_OFFSET, clamped) }
        }

    /**
     * The cellular buffer multiplier for Live TV (0.5x - 3.0x) an older build let the user set.
     * No longer shown or applied (the player always uses 1.0x, UX overhaul A-W5); kept so settings
     * export / import of older files keeps its field.
     */
    var cellularLiveMultiplier: Float
        get() = prefs.getFloat(KEY_CELLULAR_LIVE_MULTIPLIER, DEFAULT_CELLULAR_MULTIPLIER)
        set(value) {
            val clamped = value.coerceIn(MIN_CELLULAR_MULTIPLIER, MAX_CELLULAR_MULTIPLIER)
            prefs.edit { putFloat(KEY_CELLULAR_LIVE_MULTIPLIER, clamped) }
        }

    /** As [cellularLiveMultiplier], for VOD: kept for export / import only, never applied. */
    var cellularVodMultiplier: Float
        get() = prefs.getFloat(KEY_CELLULAR_VOD_MULTIPLIER, DEFAULT_CELLULAR_MULTIPLIER)
        set(value) {
            val clamped = value.coerceIn(MIN_CELLULAR_MULTIPLIER, MAX_CELLULAR_MULTIPLIER)
            prefs.edit { putFloat(KEY_CELLULAR_VOD_MULTIPLIER, clamped) }
        }

    /**
     * Delay in seconds before a live channel is marked as "watched" (added to Last Watched).
     * Range: 5-120 seconds. Default: 30 seconds.
     */
    var watchDelaySeconds: Int
        get() = prefs.getInt(KEY_WATCH_DELAY_SECONDS, DEFAULT_WATCH_DELAY_SECONDS)
        set(value) {
            val clamped = value.coerceIn(MIN_WATCH_DELAY_SECONDS, MAX_WATCH_DELAY_SECONDS)
            prefs.edit { putInt(KEY_WATCH_DELAY_SECONDS, clamped) }
        }

    /**
     * Cached flag for whether at least one provider exists.
     * Used for fast startup — avoids Room DB query on cold start.
     * Must be updated whenever providers are added or removed.
     */
    var hasProviderCache: Boolean
        get() = prefs.getBoolean(KEY_HAS_PROVIDER_CACHE, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_PROVIDER_CACHE, value) }

    /**
     * A provider deletion started and hasn't finished its catalogue cleanup: the next app start
     * sweeps for orphaned catalogue rows. Per device, never synced. See
     * `ProviderRepository.sweepOrphanedCatalogData`.
     */

    /**
     * The entries of a Parental controls list (docs/plans/20261020_parental-controls-plan.md): the
     * same for the whole household, synced. Its [ParentalList.defaults] until
     * [seedParentalListsOnce] has stored them.
     */
    fun parentalList(list: ParentalList): List<String> =
        prefs.getString(list.key, null)?.let { stored ->
            try {
                Json.decodeFromString(sourceKeysSerializer, stored)
            } catch (_: Exception) {
                // cancellation-ok: not suspend code; a list that cannot be read gives the app's words
                null
            }
        } ?: list.defaults

    /** The admin changed [list]: stored and sent to the other devices. */
    fun setParentalList(
        list: ParentalList,
        entries: List<String>,
    ) {
        prefs.edit { putString(list.key, encodeParentalList(entries)) }
        SettingsSyncQueue.setting(context, list.key)
    }

    /** A child profile's rules, from the lists stored on this device. */
    fun parentalRules(): ParentalRules =
        ParentalRules(
            kidsCategories = parentalList(ParentalList.KIDS_CATEGORIES),
            kidsFilmCategories = parentalList(ParentalList.KIDS_FILM_CATEGORIES),
            adultCategories = parentalList(ParentalList.ADULT_CATEGORIES),
            hiddenTitles = parentalList(ParentalList.HIDDEN_TITLES),
            exceptions = parentalList(ParentalList.EXCEPTIONS),
        )

    /**
     * Once per list: stores the app's words, so a later app update does not change the list (P-D2).
     * Not sent: a list another device sends replaces it. True when a list was stored now.
     */
    fun seedParentalListsOnce(): Boolean {
        val missing = ParentalList.entries.filterNot { prefs.contains(it.key) }
        if (missing.isNotEmpty()) prefs.edit { missing.forEach { putString(it.key, encodeParentalList(it.defaults)) } }
        return missing.isNotEmpty()
    }

    private fun encodeParentalList(entries: List<String>): String =
        Json.encodeToString(sourceKeysSerializer, entries.map { it.trim() }.filter { it.isNotEmpty() }.distinct())

    var orphanSweepPending: Boolean
        get() = prefs.getBoolean(KEY_ORPHAN_SWEEP_PENDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ORPHAN_SWEEP_PENDING, value) }

    /** One-time flag: [FavoriteCategoryRowCleanup] has run on this install. Per device, never synced. */
    var favoriteCategoryRowsPurged: Boolean
        get() = prefs.getBoolean(KEY_FAVORITE_CATEGORY_ROWS_PURGED, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_FAVORITE_CATEGORY_ROWS_PURGED, value) }

    /** One-time flag: [org.njarasoa.fijerena.core.network.xtream.manager.AutoXmltvSources.cleanUpOnce] has run on this install. Per device, never synced. */
    var autoXmltvSourcesCleaned: Boolean
        get() = prefs.getBoolean(KEY_AUTO_XMLTV_SOURCES_CLEANED, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_AUTO_XMLTV_SOURCES_CLEANED, value) }

    /** One-time flag: [org.njarasoa.fijerena.core.network.xmltv.EpgRefreshSchedule.copyLegacyIntervalOnce] has run on this install. Per device, never synced. */
    var epgRefreshIntervalCopied: Boolean
        get() = prefs.getBoolean(KEY_EPG_REFRESH_INTERVAL_COPIED, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_EPG_REFRESH_INTERVAL_COPIED, value) }

    // Search and EPG search history belong to the active profile, like developer mode: stored
    // under `<key>_<profileId>`. Per device and never synced.

    /**
     * Get the active profile's search history as an ordered list (most recent first).
     */
    fun getSearchHistory(): List<String> = readHistory(KEY_SEARCH_HISTORY)

    /**
     * Add a search term to history. Deduplicates (case-insensitive) and caps at [MAX_SEARCH_HISTORY].
     */
    fun addSearchHistory(query: String) = addToHistory(KEY_SEARCH_HISTORY, query)

    fun removeSearchHistory(query: String) = removeFromHistory(KEY_SEARCH_HISTORY, query)

    fun clearSearchHistory() {
        prefs.edit { remove(historyKey(KEY_SEARCH_HISTORY, activeProfileId)) }
    }

    /**
     * Get the active profile's EPG search history as an ordered list (most recent first).
     */
    fun getEpgSearchHistory(): List<String> = readHistory(KEY_EPG_SEARCH_HISTORY)

    /**
     * Add a search term to EPG history. Deduplicates (case-insensitive) and caps at [MAX_SEARCH_HISTORY].
     */
    fun addEpgSearchHistory(query: String) = addToHistory(KEY_EPG_SEARCH_HISTORY, query)

    fun removeEpgSearchHistory(query: String) = removeFromHistory(KEY_EPG_SEARCH_HISTORY, query)

    fun clearEpgSearchHistory() {
        prefs.edit { remove(historyKey(KEY_EPG_SEARCH_HISTORY, activeProfileId)) }
    }

    /**
     * One-time upgrade: the install-wide search histories from before they were per profile go to
     * the default profile (unless it already has its own), so other profiles start empty.
     */
    fun moveLegacySearchHistoryToDefaultProfile() {
        val legacyKeys = listOf(KEY_SEARCH_HISTORY, KEY_EPG_SEARCH_HISTORY).filter { prefs.contains(it) }
        if (legacyKeys.isEmpty()) return
        prefs.edit {
            legacyKeys.forEach { legacy ->
                val target = historyKey(legacy, ProfileEntity.DEFAULT_ID)
                if (!prefs.contains(target)) putString(target, prefs.getString(legacy, null))
                remove(legacy)
            }
        }
    }

    /** Drops a deleted profile's search and EPG search history. */
    fun removeProfileSearchHistory(profileId: String) =
        prefs.edit {
            remove(historyKey(KEY_SEARCH_HISTORY, profileId))
            remove(historyKey(KEY_EPG_SEARCH_HISTORY, profileId))
        }

    /**
     * The section (a `ContentType`) the active profile last had open in the phone's bottom bar,
     * which the app opens on next time. Per device and never synced, like the search history.
     */
    var lastTab: String?
        get() = prefs.getString(profileKey(KEY_LAST_TAB, activeProfileId), null)
        set(value) = prefs.edit { putString(profileKey(KEY_LAST_TAB, activeProfileId), value) }

    /** Drops a deleted profile's last tab. */
    fun removeLastTab(profileId: String) = prefs.edit { remove(profileKey(KEY_LAST_TAB, profileId)) }

    private fun historyKey(
        key: String,
        profileId: String,
    ) = "${key}_$profileId"

    private fun readHistory(key: String): List<String> {
        val joined = prefs.getString(historyKey(key, activeProfileId), null) ?: return emptyList()
        return joined.split("\u001F").filter { it.isNotBlank() }
    }

    private fun addToHistory(
        key: String,
        query: String,
    ) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = readHistory(key).toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val capped = current.take(MAX_SEARCH_HISTORY)
        prefs.edit { putString(historyKey(key, activeProfileId), capped.joinToString("\u001F")) }
    }

    private fun removeFromHistory(
        key: String,
        query: String,
    ) {
        val current = readHistory(key).toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        prefs.edit { putString(historyKey(key, activeProfileId), current.joinToString("\u001F")) }
    }

    var lastShrinkAtMs: Long
        get() = prefs.getLong(KEY_LAST_SHRINK_AT_MS, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SHRINK_AT_MS, value) }

    var lastShrinkDurationMs: Long
        get() = prefs.getLong(KEY_LAST_SHRINK_DURATION_MS, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SHRINK_DURATION_MS, value) }

    var lastShrinkRowsRemoved: Long
        get() = prefs.getLong(KEY_LAST_SHRINK_ROWS_REMOVED, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SHRINK_ROWS_REMOVED, value) }

    var lastShrinkBytesReclaimed: Long
        get() = prefs.getLong(KEY_LAST_SHRINK_BYTES_RECLAIMED, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SHRINK_BYTES_RECLAIMED, value) }

    fun saveShrinkStats(
        durationMs: Long,
        rowsRemoved: Long,
        bytesReclaimed: Long,
    ) {
        prefs.edit {
            putLong(KEY_LAST_SHRINK_AT_MS, System.currentTimeMillis())
            putLong(KEY_LAST_SHRINK_DURATION_MS, durationMs)
            putLong(KEY_LAST_SHRINK_ROWS_REMOVED, rowsRemoved)
            putLong(KEY_LAST_SHRINK_BYTES_RECLAIMED, bytesReclaimed)
        }
    }
}
