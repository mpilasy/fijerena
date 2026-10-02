package org.njarasoa.fijerena.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateDao

/**
 * Removes the stream favourites the "Favourite categories" list used to save by mistake: before
 * the fix, the star on one of its rows favourited the row itself, a stream favourite whose id is
 * `fav_cat_<categoryId>`, instead of the category.
 *
 * No real stream id can start with `fav_cat_`: Xtream ids are numbers, SMB's are `smb_file_…`,
 * Local's `local_file_…` / `local_saf_…` / `local_m3u_…`, Remote M3U's `rm3u_m3u_…`, and Jellyfin
 * keeps no rows here. Category favourites are never touched.
 *
 * Each row goes through [FavoriteStateDao.deleteRecordingTombstone], the delete an unfavourite
 * uses, so live sync removes it on the group's other devices too, older app versions included.
 */
object FavoriteCategoryRowCleanup {
    /** The id prefix of the list's rows. Fixed: it is what older versions wrote, whatever the UI uses now. */
    const val ROW_ID_PREFIX = "fav_cat_"

    /** Deletes every such row, on every provider and profile; returns the providers that had any. */
    fun purge(dao: FavoriteStateDao): Set<Long> {
        val rows = dao.getAllOfKindWithIdPrefix(FavoriteKind.STREAM, ROW_ID_PREFIX)
        rows.forEach { dao.deleteRecordingTombstone(it.providerId, it.profileId, it.itemId, it.contentType, FavoriteKind.STREAM) }
        return rows.mapTo(HashSet()) { it.providerId }
    }

    /**
     * [purge], once per install. The flag is set only after every row is gone, so an interrupted
     * run simply runs again; [onRemoved] then gets the providers whose in-memory favourites are stale.
     */
    suspend fun runOnce(
        settings: AppSettings,
        dao: FavoriteStateDao,
        onRemoved: suspend (Set<Long>) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            if (!settings.favoriteCategoryRowsPurged) {
                val providerIds = purge(dao)
                settings.favoriteCategoryRowsPurged = true
                if (providerIds.isNotEmpty()) onRemoved(providerIds)
            }
        }
    }
}
