package org.njarasoa.fijerena.core.network.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Per-provider settings that can be customized independently for each provider.
 * Stored as JSON in ProviderEntity.providerSettings field.
 */
@Serializable
data class ProviderSettings(
    /** Maximum number of items in watch history for this provider (1-100) */
    val watchHistorySize: Int = 25,
    /** Maximum number of favorites for this provider (10-500) */
    val favoritesMaxSize: Int = 100,
    /** Whether to auto-resume playback from last position */
    val autoResumeEnabled: Boolean = true,
    /** Cache expiry time in hours (1-168, i.e., 1 hour to 1 week) */
    val cacheExpiryHours: Int = 24,
    /** Whether caching is enabled for this provider */
    val cachingEnabled: Boolean = true,
    /** Category filtering rules */
    val categoryFilters: CategoryFilters = CategoryFilters(),
    /** External XMLTV EPG URL for this provider (empty = use provider's native EPG) */
    val epgUrl: String = "",
    /** Stream output format for live streams: "m3u8" (HLS) or "ts" (MPEG-TS) */
    val streamOutputFormat: String = "m3u8",
    /** Playlist type for Xtream API: "m3u_plus" (extended M3U with EPG) or "simple" (basic M3U) */
    val playlistType: String = "m3u_plus",
) {
    /** Cache expiry time in milliseconds */
    val cacheExpiryMs: Long get() = cacheExpiryHours.toLong() * 60 * 60 * 1000

    companion object {
        /** Default settings instance */
        val DEFAULT = ProviderSettings()
    }
}

/**
 * Category filtering configuration.
 * Allows hiding or showing categories based on name matchers.
 */
@Serializable
data class CategoryFilters(
    /** Filter mode: EXCLUDE hides matching, INCLUDE shows only matching */
    val mode: FilterMode = FilterMode.EXCLUDE,
    /** Rules to match against category names (case-insensitive) */
    val rules: List<@Serializable(with = CategoryMatcherSerializer::class) CategoryMatcher> = emptyList(),
    /** Allowed Unicode scripts — empty means show all */
    val allowedScripts: Set<ScriptType> = emptySet(),
) {
    /**
     * Check if a category should be visible based on filter rules.
     * Both rule and script filters must pass (AND logic).
     * @param categoryName The name of the category to check
     * @return true if the category should be shown, false if it should be hidden
     */
    fun shouldShowCategory(categoryName: String): Boolean {
        // Rule filter
        if (rules.isNotEmpty()) {
            val matchesAnyRule = rules.any { it.matches(categoryName) }
            val passesRules =
                when (mode) {
                    FilterMode.EXCLUDE -> !matchesAnyRule
                    FilterMode.INCLUDE -> matchesAnyRule
                }
            if (!passesRules) return false
        }
        // Script filter
        if (allowedScripts.isNotEmpty()) {
            if (ScriptDetector.detectScript(categoryName) !in allowedScripts) return false
        }
        return true
    }
}

/**
 * Filter mode for category filtering.
 */
@Serializable
enum class FilterMode {
    /** Hide categories that match the rules */
    EXCLUDE,

    /** Show only categories that match the rules */
    INCLUDE,
}

/** How a [CategoryMatcher.value] is matched against a category name. */
@Serializable
enum class MatchType {
    STARTS_WITH,
    ENDS_WITH,
    CONTAINS,
    EXACT,
}

/**
 * A single category-name matching rule.
 *
 * Deserialized via [CategoryMatcherSerializer] (applied at the [CategoryFilters.rules] usage
 * site), which also accepts a legacy bare JSON string (the pre-migration `prefixes: List<String>`
 * shape), normalizing it to `MatchType.STARTS_WITH` — this is what previously-stored provider
 * settings look like before [org.njarasoa.fijerena.core.network.provider.ProviderRepository]
 * rewrites them.
 */
@Serializable
data class CategoryMatcher(
    val value: String,
    val matchType: MatchType = MatchType.STARTS_WITH,
) {
    fun matches(categoryName: String): Boolean =
        when (matchType) {
            MatchType.STARTS_WITH -> categoryName.startsWith(value, ignoreCase = true)
            MatchType.ENDS_WITH -> categoryName.endsWith(value, ignoreCase = true)
            MatchType.CONTAINS -> categoryName.contains(value, ignoreCase = true)
            MatchType.EXACT -> categoryName.equals(value, ignoreCase = true)
        }
}

object CategoryMatcherSerializer : JsonTransformingSerializer<CategoryMatcher>(CategoryMatcher.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) {
            buildJsonObject { put("value", element.content) }
        } else {
            element
        }
}
