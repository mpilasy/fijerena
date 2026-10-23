package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app's one search, TV and phone: the term typed and the choices around it, shared by every
 * Search screen, so Back to any of them shows the latest search, never one replaced since (each
 * screen runs it again when its own results are older). In memory; a new profile in use starts it
 * over ([forProfile]).
 */
object SearchSession {
    var query by mutableStateOf("")

    /** The content-type pill (null: All; [NOT_IN_SOURCES]: only TMDB's titles no source has). */
    var typeFilter by mutableStateOf<String?>(null)

    /** The source chosen in a multi-source profile (null: every source). */
    var source by mutableStateOf<Long?>(null)

    /** The decades and genres chosen in the filter (the search decade genre filter plan). */
    var facets by mutableStateOf(SearchFacetChoice())

    var tab by mutableStateOf(SearchTab.CATALOGUE)

    private var profileId: String? = null

    /** The type pill that shows only TMDB's titles no source has; not a content type. */
    const val NOT_IN_SOURCES = "NOT_IN_SOURCES"

    /** Called by each Search screen with the profile in use: another profile's search is dropped. */
    fun forProfile(id: String) {
        if (id == profileId) return
        profileId = id
        query = ""
        typeFilter = null
        source = null
        facets = SearchFacetChoice()
        tab = SearchTab.CATALOGUE
    }
}
