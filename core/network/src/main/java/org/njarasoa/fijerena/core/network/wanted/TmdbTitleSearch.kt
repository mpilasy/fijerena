package org.njarasoa.fijerena.core.network.wanted

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Search's "Not in your sources" section: the films and shows TMDB finds for a query that none of
 * the profile's Xtream sources has (TMDB id, then title and year, the original title too: TMDB
 * answers in the app language, and the sources often name a title in another). A child profile,
 * or no usable TMDB key, gets nothing, and TMDB is never called then. See
 * the wanted titles plan → Search.
 */
class TmdbTitleSearch(
    private val context: Context,
    private val tmdb: TmdbApiService = TmdbApiService.getInstance(context),
) {
    /** Whether the profile in use may search TMDB at all. */
    fun isOffered(): Boolean {
        val settings = AppSettings(context)
        return tmdb.hasApiKey() && !settings.isChildProfile(settings.activeProfileId)
    }

    /**
     * At most [MAX_RESULTS] titles of [contentTypes] for [query], in TMDB's order. Throws when
     * TMDB fails; the caller shows nothing then (the raw error in developer mode).
     */
    suspend fun search(
        query: String,
        contentTypes: Set<String>,
    ): List<TmdbTitle> {
        val found =
            if (isOffered() && query.trim().length >= MIN_QUERY_LENGTH) {
                tmdb
                    .searchMulti(query.trim())
                    .results
                    .mapNotNull(TmdbTitle::of)
                    .filter { it.contentType in contentTypes }
            } else {
                emptyList()
            }
        return if (found.isEmpty()) found else withoutOwned(found)
    }

    private suspend fun withoutOwned(found: List<TmdbTitle>): List<TmdbTitle> {
        val sources =
            ProviderRepository(context)
                .profileSourcesList()
                .filter { it.type == XTREAM }
                .map { it.id }
        val lookup = CatalogueLookup(XtreamDatabase.getInstance(context))
        return withContext(Dispatchers.IO) {
            found
                .filter { lookup.find(sources, it.contentType, it.tmdbId, listOfNotNull(it.title, it.originalTitle), it.year).isEmpty() }
                .take(MAX_RESULTS)
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        const val MAX_RESULTS = 20
        private const val XTREAM = "XTREAM"
    }
}
