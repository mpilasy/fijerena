package org.njarasoa.fijerena.core.network

data class ParsedQuery(
    val positiveWords: List<String>,
    val negativeWords: List<String>,
) {
    val isEmpty: Boolean get() = positiveWords.isEmpty() && negativeWords.isEmpty()
}

object SearchUtils {
    // ⚡ Bolt: Performance Optimization
    // Pre-parse the query so we don't perform substring operations or prefix checks in the hot loop
    fun parseQuery(query: String): ParsedQuery {
        val words = query.lowercase().split(" ").filter { it.isNotBlank() }
        val positive = mutableListOf<String>()
        val negative = mutableListOf<String>()
        for (word in words) {
            if (word.startsWith("-") && word.length > 1) {
                negative.add(word.substring(1))
            } else {
                positive.add(word)
            }
        }
        return ParsedQuery(positive, negative)
    }

    /**
     * Check if text matches all query words using a raw string query.
     */
    fun matchesQuery(
        text: String,
        query: String,
    ): Boolean {
        val parsed = parseQuery(query)
        return matchesQuery(text, parsed)
    }

    /**
     * Check if text matches all query words. Accepts pre-lowercased text for hot-loop callers
     * to avoid repeated case-folding across thousands of items.
     */
    fun matchesQuery(
        text: String,
        parsedQuery: ParsedQuery,
    ): Boolean {
        if (parsedQuery.isEmpty) return true

        for (word in parsedQuery.positiveWords) {
            if (!text.contains(word, ignoreCase = true)) return false
        }

        for (word in parsedQuery.negativeWords) {
            if (text.contains(word, ignoreCase = true)) return false
        }

        return true
    }

    /** Fast path for callers that already have lowercased text (query words are already lowercase) */
    fun matchesQueryLower(
        textLower: String,
        parsedQuery: ParsedQuery,
    ): Boolean {
        if (parsedQuery.isEmpty) return true

        for (word in parsedQuery.positiveWords) {
            if (!textLower.contains(word)) return false
        }

        for (word in parsedQuery.negativeWords) {
            if (textLower.contains(word)) return false
        }

        return true
    }
}
