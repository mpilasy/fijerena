package org.njarasoa.fijerena.core.ui.viewmodels

object SearchUtils {
    fun getQueryWords(query: String): List<String> {
        return query.lowercase().split(" ").filter { it.isNotBlank() }
    }

    fun matchesQuery(text: String, queryWords: List<String>): Boolean {
        if (queryWords.isEmpty()) return true
        val lowerText = text.lowercase()
        return queryWords.all { lowerText.contains(it) }
    }
}
