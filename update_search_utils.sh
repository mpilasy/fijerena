cat << 'INNER_EOF' > ./core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/SearchUtils.kt
package org.njarasoa.fijerena.core.ui.viewmodels

object SearchUtils {
    data class ParsedQuery(
        val positiveWords: List<String>,
        val negativeWords: List<String>
    )

    fun getQueryWords(query: String): List<String> = query.lowercase().split(" ").filter { it.isNotBlank() }

    fun parseQuery(query: String): ParsedQuery {
        val words = getQueryWords(query)
        val positiveWords = mutableListOf<String>()
        val negativeWords = mutableListOf<String>()
        for (word in words) {
            if (word.startsWith("-") && word.length > 1) {
                negativeWords.add(word.substring(1))
            } else {
                positiveWords.add(word)
            }
        }
        return ParsedQuery(positiveWords, negativeWords)
    }

    /**
     * Check if text matches all query words. Accepts pre-lowercased text for hot-loop callers
     * to avoid repeated case-folding across thousands of items.
     */
    fun matchesQuery(
        text: String,
        parsedQuery: ParsedQuery,
    ): Boolean {
        for (word in parsedQuery.positiveWords) {
            if (!text.contains(word, ignoreCase = true)) return false
        }
        for (word in parsedQuery.negativeWords) {
            if (text.contains(word, ignoreCase = true)) return false
        }
        return true
    }

    @Deprecated("Use parseQuery and matchesQuery(String, ParsedQuery) instead")
    fun matchesQuery(
        text: String,
        queryWords: List<String>,
    ): Boolean {
        if (queryWords.isEmpty()) return true
        return queryWords.all { word ->
            if (word.startsWith("-") && word.length > 1) {
                !text.contains(word.substring(1), ignoreCase = true)
            } else {
                text.contains(word, ignoreCase = true)
            }
        }
    }

    /** Fast path for callers that already have lowercased text (query words are already lowercase) */
    fun matchesQueryLower(
        textLower: String,
        parsedQuery: ParsedQuery,
    ): Boolean {
        for (word in parsedQuery.positiveWords) {
            if (!textLower.contains(word)) return false
        }
        for (word in parsedQuery.negativeWords) {
            if (textLower.contains(word)) return false
        }
        return true
    }

    @Deprecated("Use parseQuery and matchesQueryLower(String, ParsedQuery) instead")
    fun matchesQueryLower(
        textLower: String,
        queryWords: List<String>,
    ): Boolean {
        if (queryWords.isEmpty()) return true
        return queryWords.all { word ->
            if (word.startsWith("-") && word.length > 1) {
                !textLower.contains(word.substring(1))
            } else {
                textLower.contains(word)
            }
        }
    }
}
INNER_EOF
