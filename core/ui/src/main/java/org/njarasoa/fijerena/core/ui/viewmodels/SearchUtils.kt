package org.njarasoa.fijerena.core.ui.viewmodels

object SearchUtils {
    fun getQueryWords(query: String): List<String> {
        return query.lowercase().split(" ").filter { it.isNotBlank() }
    }

    /**
     * Check if text matches all query words. Accepts pre-lowercased text for hot-loop callers
     * to avoid repeated case-folding across thousands of items.
     */
    fun matchesQuery(text: String, queryWords: List<String>): Boolean {
        if (queryWords.isEmpty()) return true
        val textLower = text.lowercase()
        return queryWords.all { textLower.contains(it) }
    }

    /** Fast path for callers that already have lowercased text (query words are already lowercase) */
    fun matchesQueryLower(textLower: String, queryWords: List<String>): Boolean {
        if (queryWords.isEmpty()) return true
        return queryWords.all { textLower.contains(it) }
    }
}
