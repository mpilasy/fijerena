package org.njarasoa.fijerena.core.ui.viewmodels

data class ParsedQuery(
    val positiveWords: List<String>,
    val negativeWords: List<String>,
)

object SearchUtils {
    fun getQueryWords(query: String): ParsedQuery {
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
     * Check if text matches all query words. Accepts pre-lowercased text for hot-loop callers
     * to avoid repeated case-folding across thousands of items.
     */
    fun matchesQuery(
        text: String,
        parsedQuery: ParsedQuery,
    ): Boolean {
        if (parsedQuery.positiveWords.isEmpty() && parsedQuery.negativeWords.isEmpty()) return true

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
        if (parsedQuery.positiveWords.isEmpty() && parsedQuery.negativeWords.isEmpty()) return true

        for (word in parsedQuery.positiveWords) {
            if (!textLower.contains(word)) return false
        }
        for (word in parsedQuery.negativeWords) {
            if (textLower.contains(word)) return false
        }
        return true
    }
}
