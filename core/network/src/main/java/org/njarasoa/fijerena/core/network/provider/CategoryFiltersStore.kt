package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue

/**
 * Category filters per (provider, profile): each profile has its own complete set for every
 * provider. See docs/plans/20260930_profile-scoped-settings-plan.md.
 *
 * SharedPreferences, not Room: `MediaProviderFactory.create` and the Xtream content code read
 * filters synchronously, some of it on the main thread. One `category_filters` file, one key per
 * pair: `<providerId>_<profileId>` holding [CategoryFilters] JSON. No key means the fallback the
 * caller passes — the provider's own `categoryFilters` from before profiles had their own, which is
 * empty once [ProviderRepository.migrateCategoryFiltersToProfiles] has run.
 */
class CategoryFiltersStore(
    private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun get(
        providerId: Long,
        profileId: String,
        fallback: CategoryFilters = CategoryFilters(),
    ): CategoryFilters {
        val stored = prefs.getString(key(providerId, profileId), null) ?: return fallback
        return try {
            json.decodeFromString(CategoryFiltersSerializer, stored)
        } catch (_: Exception) {
            fallback
        }
    }

    fun has(
        providerId: Long,
        profileId: String,
    ): Boolean = prefs.contains(key(providerId, profileId))

    fun set(
        providerId: Long,
        profileId: String,
        filters: CategoryFilters,
    ) {
        prefs.edit { putString(key(providerId, profileId), json.encodeToString(CategoryFiltersSerializer, filters)) }
        SettingsSyncQueue.categoryFilters(context, providerId, profileId)
    }

    /** A new profile starts with [fromProfileId]'s filters on every provider. */
    fun copyProfile(
        fromProfileId: String,
        toProfileId: String,
    ) {
        val suffix = "_$fromProfileId"
        val copies =
            prefs.all
                .filterKeys { it.endsWith(suffix) }
                .mapKeys { (k, _) -> k.removeSuffix(suffix) + "_$toProfileId" }
        prefs.edit { copies.forEach { (k, v) -> putString(k, v as String) } }
        copies.keys.forEach { SettingsSyncQueue.categoryFilters(context, it.substringBefore('_').toLong(), toProfileId) }
    }

    /** A copied provider takes every profile's filters of the one it was copied from, and no others. */
    fun copyProvider(
        fromProviderId: Long,
        toProviderId: Long,
    ) {
        val prefix = "${fromProviderId}_"
        val all = prefs.all
        val copies =
            all
                .filterKeys { it.startsWith(prefix) }
                .mapKeys { (k, _) -> "${toProviderId}_" + k.removePrefix(prefix) }
        prefs.edit {
            all.keys.filter { it.startsWith("${toProviderId}_") }.forEach { remove(it) }
            copies.forEach { (k, v) -> putString(k, v as String) }
        }
        copies.keys.forEach { SettingsSyncQueue.categoryFilters(context, toProviderId, it.substringAfter('_')) }
    }

    fun removeProvider(providerId: Long) {
        val prefix = "${providerId}_"
        prefs.edit { prefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) } }
    }

    fun removeProfile(profileId: String) {
        val suffix = "_$profileId"
        prefs.edit { prefs.all.keys.filter { it.endsWith(suffix) }.forEach { remove(it) } }
    }

    private companion object {
        const val FILE_NAME = "category_filters"
        val json = Json { ignoreUnknownKeys = true }

        // Provider ids are numbers, so the first underscore always ends the provider part.
        fun key(
            providerId: Long,
            profileId: String,
        ) = "${providerId}_$profileId"
    }
}
