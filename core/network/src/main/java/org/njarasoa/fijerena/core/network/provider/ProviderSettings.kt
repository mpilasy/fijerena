package org.njarasoa.fijerena.core.network.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
    val autoResumeEnabled: Boolean = true,
    /** Cache expiry time in hours (1-168, i.e., 1 hour to 1 week) */
    val cacheExpiryHours: Int = 24,
    val cachingEnabled: Boolean = true,
    val categoryFilters:
        @Serializable(with = CategoryFiltersSerializer::class)
        CategoryFilters = CategoryFilters(),
    /** External XMLTV EPG URL for this provider (empty = use provider's native EPG) */
    val epgUrl: String = "",
    /** Stream output format for live streams: "m3u8" (HLS) or "ts" (MPEG-TS) */
    val streamOutputFormat: String = "m3u8",
    /** Playlist type for Xtream API: "m3u_plus" (extended M3U with EPG) or "simple" (basic M3U) */
    val playlistType: String = "m3u_plus",
    /**
     * Xtream: whether the source's own guide (`xmltv.php`) is added as an automatic guide source
     * while the source has live channels. null: not decided, which means on. Detection sets it to
     * false when that guide comes back empty; the viewer sets it either way in Edit Source.
     */
    val providesGuide: Boolean? = null,
    /** The viewer set [providesGuide]: detection no longer changes it. */
    val providesGuideSetByUser: Boolean = false,
) {
    val cacheExpiryMs: Long get() = cacheExpiryHours.toLong() * 60 * 60 * 1000

    /** The effective "Provides a guide": the viewer's or detection's value, else on. */
    val providesGuideOn: Boolean get() = providesGuide != false

    companion object {
        val DEFAULT = ProviderSettings()
    }
}

/**
 * Allows hiding or showing categories based on name matchers.
 */
@Serializable
data class CategoryFilters(
    /** Filter mode: EXCLUDE hides matching, INCLUDE shows only matching */
    val mode: FilterMode = FilterMode.EXCLUDE,
    /** Rules to match against category names (case-insensitive) */
    val rules: List<
        @Serializable(with = CategoryMatcherSerializer::class)
        CategoryMatcher,
    > = emptyList(),
    /** Allowed Unicode scripts — empty means show all */
    val allowedScripts: Set<ScriptType> = emptySet(),
) {
    /**
     * Both rule and script filters must pass (AND logic).
     */
    fun shouldShowCategory(categoryName: String): Boolean {
        if (rules.isNotEmpty()) {
            val matchesAnyRule = rules.any { it.matches(categoryName) }
            val passesRules =
                when (mode) {
                    FilterMode.EXCLUDE -> !matchesAnyRule
                    FilterMode.INCLUDE -> matchesAnyRule
                }
            if (!passesRules) return false
        }
        if (allowedScripts.isNotEmpty()) {
            if (ScriptDetector.detectScript(categoryName) !in allowedScripts) return false
        }
        return true
    }
}

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

/**
 * Appends [values] as new [CategoryMatcher]s with [matchType], skipping blanks and duplicates.
 * A duplicate is a case-insensitive value match with the same [matchType] — same text under a
 * different matchType is a distinct rule and is kept.
 */
fun List<CategoryMatcher>.withAddedRules(
    values: List<String>,
    matchType: MatchType,
): List<CategoryMatcher> {
    val result = toMutableList()
    val seen = result.mapTo(mutableSetOf()) { it.value.trim().lowercase() to it.matchType }
    for (raw in values) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) continue
        val key = trimmed.lowercase() to matchType
        if (seen.add(key)) result.add(CategoryMatcher(value = trimmed, matchType = matchType))
    }
    return result
}

object CategoryMatcherSerializer : JsonTransformingSerializer<CategoryMatcher>(CategoryMatcher.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) {
            buildJsonObject { put("value", element.content) }
        } else {
            element
        }
}

/**
 * Maps the legacy `prefixes` JSON key to the current `rules` key before delegating to the
 * normal decoder, so pre-migration stored settings (`{"prefixes": ["Adult", ...]}`) aren't
 * silently dropped by `ignoreUnknownKeys` and defaulted to an empty rule list.
 * [CategoryMatcherSerializer] then normalizes each individual bare-string entry.
 */
object CategoryFiltersSerializer : JsonTransformingSerializer<CategoryFilters>(CategoryFilters.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject || "rules" in element || "prefixes" !in element) return element
        return JsonObject(
            element.toMutableMap().apply {
                put("rules", remove("prefixes")!!)
            },
        )
    }
}
