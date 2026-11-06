package org.njarasoa.fijerena.core.network

import java.text.Collator

/**
 * The rules for a profile's tags on its favourites: free text, trimmed, 1 to [MAX_LENGTH]
 * characters, one tag per name without regard to case. See the favourite tags plan, FT-D4.
 */
object FavoriteTags {
    const val MAX_LENGTH = 30

    /** [name] trimmed and cut to [MAX_LENGTH]; null when nothing is left. */
    fun clean(name: String): String? =
        name
            .trim()
            .take(MAX_LENGTH)
            .trim()
            .ifEmpty { null }

    /**
     * [names] cleaned, one per name without regard to case (the first spelling wins), each in the
     * spelling of a tag in [existing] when one matches. Order is kept.
     */
    fun normalise(
        names: List<String>,
        existing: Collection<String> = emptyList(),
    ): List<String> {
        val known = existing.associateBy { it.lowercase() }
        val seen = HashSet<String>()
        return names.mapNotNull { raw ->
            val name = clean(raw) ?: return@mapNotNull null
            val key = name.lowercase()
            if (seen.add(key)) known[key] ?: name else null
        }
    }

    /** [names] with no two equal without regard to case, sorted in the device's locale. */
    fun sorted(names: Collection<String>): List<String> {
        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        return normalise(names.toList()).sortedWith(collator)
    }

    /** [tags] with [from] renamed to [to] (merging into a tag already called [to]); same list when [from] is not there. */
    fun rename(
        tags: List<String>,
        from: String,
        to: String,
    ): List<String> {
        if (tags.none { it.equals(from, ignoreCase = true) }) return tags
        val target = clean(to) ?: return remove(tags, from)
        return normalise(tags.map { if (it.equals(from, ignoreCase = true)) target else it }, existing = listOf(target))
    }

    /** [tags] without [name]. */
    fun remove(
        tags: List<String>,
        name: String,
    ): List<String> = tags.filterNot { it.equals(name, ignoreCase = true) }
}
