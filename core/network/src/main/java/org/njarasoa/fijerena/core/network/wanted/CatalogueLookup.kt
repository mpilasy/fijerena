package org.njarasoa.fijerena.core.network.wanted

import org.njarasoa.fijerena.core.network.normalizeTmdbId
import org.njarasoa.fijerena.core.network.tmdb.TitleMatcher
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles

/** A film or show found in one source's stored catalogue. */
data class CatalogueHit(
    val providerId: Long,
    val itemId: String,
    val name: String,
    val categoryId: String,
    val tmdbId: String?,
)

/**
 * Finds a film or show in the stored catalogues of some Xtream sources, as the profile in use sees
 * them: excluded categories are left out, Parental controls exceptions are kept. By TMDB id first;
 * when no source has the id, by title and year ([TitleMatcher]), among the rows without a TMDB id
 * only, so a row with another id (a remake with the same name) never matches. Sources
 * are searched in the order given. Blocking: call it on `Dispatchers.IO`. See
 * the wanted titles plan → Matching.
 */
class CatalogueLookup(
    db: XtreamDatabase,
) {
    private val streams = db.streamDao()
    private val series = db.seriesDao()

    fun find(
        providerIds: List<Long>,
        contentType: String,
        tmdbId: String?,
        titles: List<String>,
        year: Int?,
    ): List<CatalogueHit> {
        val byId = if (tmdbId.isNullOrBlank()) emptyList() else providerIds.flatMap { byTmdbId(it, contentType, tmdbId) }
        return byId.ifEmpty { providerIds.flatMap { byTitle(it, contentType, titles, year) } }
    }

    /**
     * [related] with each [TmdbOnlyItem] card that one of [providerIds] has turned into that
     * source's card. A source's related rows match titles in that source only, by name, so a card
     * can say "no source" for a title another source has.
     */
    fun inSources(
        related: RelatedTitles,
        providerIds: List<Long>,
        contentType: String,
    ): RelatedTitles {
        fun List<MediaItem>.resolved() =
            map { item ->
                val title = TmdbOnlyItem.titleOf(item, contentType) ?: return@map item
                val hit =
                    find(providerIds, contentType, title.tmdbId, listOfNotNull(title.title, title.originalTitle), title.year)
                        .firstOrNull() ?: return@map item
                item.copy(id = hit.itemId, categoryId = hit.categoryId, providerId = hit.providerId, providerData = emptyMap())
            }.distinctBy { it.providerId to it.id }
        return related.copy(collection = related.collection.resolved(), moreLikeThis = related.moreLikeThis.resolved())
    }

    private fun byTmdbId(
        providerId: Long,
        contentType: String,
        tmdbId: String,
    ): List<CatalogueHit> =
        when (contentType) {
            ContentType.MOVIES -> streams.getByTmdbId(providerId, XtreamStreamEntity.TYPE_VOD, tmdbId, NO_ID).map(::hit)
            ContentType.TV_SHOWS -> series.getByTmdbId(providerId, tmdbId, NO_ID).map(::hit)
            else -> emptyList()
        }

    private fun byTitle(
        providerId: Long,
        contentType: String,
        titles: List<String>,
        year: Int?,
    ): List<CatalogueHit> =
        titles
            .distinct()
            .flatMap { title ->
                val query = ftsQuery(title) ?: return@flatMap emptyList()

                fun same(
                    name: String,
                    releaseDate: String?,
                    tmdbId: String?,
                ) = tmdbId.normalizeTmdbId() == null &&
                    TitleMatcher.matches(name, releaseDate?.take(YEAR_LENGTH)?.toIntOrNull(), title, year)
                when (contentType) {
                    ContentType.MOVIES -> {
                        streams
                            .searchByFts(providerId, XtreamStreamEntity.TYPE_VOD, query, includeExcluded = false)
                            .filter { same(it.name, it.releaseDate, it.tmdbId) }
                            .map(::hit)
                    }

                    ContentType.TV_SHOWS -> {
                        series
                            .searchByFts(providerId, query, includeExcluded = false)
                            .filter { same(it.name, it.releaseDate, it.tmdbId) }
                            .map(::hit)
                    }

                    else -> {
                        emptyList()
                    }
                }
            }.distinctBy { it.itemId }

    private fun hit(e: XtreamStreamEntity) = CatalogueHit(e.providerId, e.streamId.toString(), e.name, e.categoryId, e.tmdbId)

    private fun hit(e: XtreamSeriesEntity) = CatalogueHit(e.providerId, e.seriesId.toString(), e.name, e.categoryId, e.tmdbId)

    companion object {
        private const val NO_ID = -1
        private const val YEAR_LENGTH = 4

        /**
         * Every word of [title] as an FTS prefix term, or null when none is left. Built from the
         * normalised title (letters and digits only), so no FTS operator can reach the query.
         */
        fun ftsQuery(title: String): String? =
            TitleMatcher
                .normalize(title)
                .textWithoutYear
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { "$it*" }
                .ifBlank { null }
    }
}
