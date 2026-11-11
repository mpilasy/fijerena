package org.njarasoa.fijerena.core.network.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The settings of one provider, stored as JSON in ProviderEntity.providerSettings. */
@Serializable
data class ProviderSettings(
    /** Retired: Recent rows are one size per device, AppSettings.recentRowSize (D4); read once to move it. */
    val watchHistorySize: Int = 25,
    /** Retired: favourites are no longer capped. Kept so stored settings keep decoding. */
    val favoritesMaxSize: Int = 100,
    /** Retired: films and episodes always resume. Kept so stored settings keep decoding. */
    val autoResumeEnabled: Boolean = true,
    /** Cache expiry time in hours (1-168). */
    val cacheExpiryHours: Int = 24,
    /** Retired: the source's short-guide cache is always on (Settings reorg, D5). Kept so stored settings keep decoding. */
    val cachingEnabled: Boolean = true,
    val categoryFilters:
        @Serializable(with = CategoryFiltersSerializer::class)
        CategoryFilters = CategoryFilters(),
    /** Retired: nothing reads it (guide sources replaced it). Kept so stored settings keep decoding. */
    val epgUrl: String = "",
    /** Stream output format for live streams: "m3u8" (HLS) or "ts" (MPEG-TS) */
    val streamOutputFormat: String = "m3u8",
    /** Not read anywhere. Kept so stored settings keep decoding. */
    val playlistType: String = "m3u_plus",
    /**
     * Xtream: whether the source's own guide (`xmltv.php`) is added as an automatic guide source
     * while the source has live channels. null: not decided, which means on. Detection sets it to
     * false when that guide comes back empty; the viewer sets it either way in Edit Source.
     */
    val providesGuide: Boolean? = null,
    /** The viewer set [providesGuide]: detection no longer changes it. */
    val providesGuideSetByUser: Boolean = false,
    /**
     * Xtream: usernames of the source's extra logins on the same panel, which playback shares with
     * the main one. Passwords live in `provider_creds_<id>`. Changed only through
     * [ProviderRepository]'s login functions, never by a settings save. See the shared logins plan.
     */
    val extraLogins: List<String> = emptyList(),
    /**
     * What this source's badge says instead of its name ("X4P"), at most
     * [SourceShortNames.MAX_LENGTH] characters. Blank: worked out from the name ([SourceShortNames]).
     */
    val shortName: String = "",
) {
    val cacheExpiryMs: Long get() = cacheExpiryHours.toLong() * 60 * 60 * 1000

    /** The effective "Provides a guide": the viewer's or detection's value, else on. */
    val providesGuideOn: Boolean get() = providesGuide != false

    companion object {
        val DEFAULT = ProviderSettings()
    }
}

/** Hides or shows categories by name matchers. */
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
    /**
     * A child profile's Parental controls rules: only the categories they show, whatever the rules
     * say. Null for other profiles. Never stored: [CategoryFiltersStore.get] sets it from the
     * profile, so the rules editor and sync carry only the rules (the parental controls plan).
     */
    @kotlinx.serialization.Transient
    val child: ParentalRules? = null,
) {
    /**
     * True when these filters hide no category: no rule, no script and not a child profile. A
     * child's rules alone hide every category that is not a kids' one.
     */
    val filtersNothing: Boolean get() = rules.isEmpty() && allowedScripts.isEmpty() && child == null

    /** Both the rule and the script filters must pass. */
    fun shouldShowCategory(
        categoryName: String,
        films: Boolean = false,
    ): Boolean {
        if (child != null && !child.showsCategory(categoryName, films)) return false
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
 * Deserialized via [CategoryMatcherSerializer] (on [CategoryFilters.rules]), which also accepts a
 * legacy bare JSON string (the old `prefixes: List<String>` shape) as `MatchType.STARTS_WITH`.
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
 * Maps the legacy `prefixes` key to `rules` before the normal decoder, so old stored settings
 * (`{"prefixes": ["Adult", ...]}`) are not dropped by `ignoreUnknownKeys`.
 * [CategoryMatcherSerializer] then normalises each bare-string entry.
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
