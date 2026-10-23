package org.njarasoa.fijerena.core.network.wanted

import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * A favourite whose item its source no longer lists. [copy] is the same film or show in the same
 * or another of the profile's sources (by TMDB id), which the user may move the favourite to;
 * always null for a live channel.
 */
data class GoneFavorite(
    val providerId: Long,
    val itemId: String,
    val contentType: String,
    val copy: CatalogueHit?,
)

/**
 * Finds a profile's favourite films, shows and channels that their Xtream source no longer lists,
 * and records the TMDB id of each film and show favourite that is still there. Never deletes or
 * moves a favourite (CONTRIBUTING.md rule 12). A source whose catalogue of a kind is empty (never
 * downloaded, or cleared) is skipped for that kind, so it never flags every favourite. Blocking:
 * call it on `Dispatchers.IO`. See the wanted titles plan → Favourites gone
 * from their source.
 */
object FavoriteCatalogueCheck {
    fun run(
        db: XtreamDatabase,
        profileId: String,
        providerIds: List<Long>,
    ): List<GoneFavorite> {
        val lookup = CatalogueLookup(db)
        return providerIds.flatMap { providerId ->
            // The same source first: its other copies are the closest (same language and naming).
            val copyOrder = listOf(providerId) + providerIds.filter { it != providerId }
            check(db, lookup, profileId, providerId, copyOrder)
        }
    }

    private fun check(
        db: XtreamDatabase,
        lookup: CatalogueLookup,
        profileId: String,
        providerId: Long,
        copyOrder: List<Long>,
    ): List<GoneFavorite> {
        val favorites = db.favoriteStateDao()
        val streams = db.streamDao()
        val series = db.seriesDao()
        val hasLive = streams.hasStreams(providerId, XtreamStreamEntity.TYPE_LIVE)
        val hasVod = streams.hasStreams(providerId, XtreamStreamEntity.TYPE_VOD)
        val hasSeries = series.hasSeries(providerId)

        fun copyOf(favorite: FavoriteStateEntity): CatalogueHit? =
            favorite.tmdbId?.let { lookup.find(copyOrder, favorite.contentType, it, emptyList(), null).firstOrNull() }

        fun recordTmdbId(
            favorite: FavoriteStateEntity,
            tmdbId: String?,
        ) {
            if (favorite.tmdbId == null && !tmdbId.isNullOrBlank()) {
                favorites.setTmdbId(providerId, profileId, favorite.itemId, favorite.contentType, favorite.kind, tmdbId)
            }
        }

        return favorites.getAllOfKind(providerId, profileId, FavoriteKind.STREAM).mapNotNull { favorite ->
            val id = favorite.itemId.toIntOrNull()
            when {
                id == null -> {
                    null
                }

                favorite.contentType == ContentType.LIVE_TV && hasLive -> {
                    if (streams.getStreamById(providerId, id) ==
                        null
                    ) {
                        GoneFavorite(providerId, favorite.itemId, ContentType.LIVE_TV, null)
                    } else {
                        null
                    }
                }

                favorite.contentType == ContentType.MOVIES && hasVod -> {
                    val row = streams.getStreamById(providerId, id)
                    row?.let { recordTmdbId(favorite, it.tmdbId) }
                    if (row == null) GoneFavorite(providerId, favorite.itemId, ContentType.MOVIES, copyOf(favorite)) else null
                }

                favorite.contentType == ContentType.TV_SHOWS && hasSeries -> {
                    val row = series.getSeriesById(providerId, id)
                    row?.let { recordTmdbId(favorite, it.tmdbId) }
                    if (row == null) GoneFavorite(providerId, favorite.itemId, ContentType.TV_SHOWS, copyOf(favorite)) else null
                }

                else -> {
                    null
                }
            }
        }
    }
}
