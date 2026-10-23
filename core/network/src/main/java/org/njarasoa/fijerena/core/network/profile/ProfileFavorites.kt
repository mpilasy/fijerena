package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersStore
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync
import org.njarasoa.fijerena.core.player.domain.ContentType

/** A film, show or channel in a profile's favourites, or one that could be ([isFavorite]). */
data class ProfileFavorite(
    val providerId: Long,
    val itemId: String,
    val contentType: String,
    val name: String,
    val categoryId: String,
    val thumbnailUrl: String? = null,
    val isFavorite: Boolean = true,
) {
    val key: String get() = "$providerId:$contentType:$itemId"
}

/**
 * Any profile's favourites, read and written from another profile's Settings: a parent fills a
 * child's Home without switching to it (docs/plans/20261019_kids-home-plan.md, Phase 3). The rows
 * are `favorite_state`'s, written through the paths that record sync tombstones, so the child's
 * other devices follow.
 */
class ProfileFavorites(
    private val context: Context,
) {
    private val database = XtreamDatabase.getInstance(context)
    private val favoriteDao = database.favoriteStateDao()

    /** [profileId]'s Xtream sources, the ones its favourites and filters are on. */
    suspend fun sourceIds(profileId: String): List<Long> =
        ProviderRepository(context).profileSourcesList(profileId).filter { it.type == "XTREAM" }.map { it.id }

    /** [profileId]'s favourite films, shows and channels on its sources, newest first. */
    suspend fun list(profileId: String): List<ProfileFavorite> =
        withContext(Dispatchers.IO) {
            sourceIds(profileId)
                .flatMap { favoriteDao.getAllOfKind(it, profileId, FavoriteKind.STREAM) }
                .sortedByDescending { it.createdAt }
                .map { ProfileFavorite(it.providerId, it.itemId, it.contentType, it.name, it.parentCategoryId.orEmpty()) }
        }

    suspend fun add(
        profileId: String,
        item: ProfileFavorite,
    ) = withContext(Dispatchers.IO) {
        favoriteDao.upsertClearingTombstone(
            FavoriteStateEntity(
                providerId = item.providerId,
                profileId = profileId,
                itemId = item.itemId,
                contentType = item.contentType,
                kind = FavoriteKind.STREAM,
                name = item.name,
                parentCategoryId = item.categoryId,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(
        profileId: String,
        item: ProfileFavorite,
    ) = withContext(Dispatchers.IO) {
        favoriteDao.deleteRecordingTombstone(item.providerId, profileId, item.itemId, item.contentType, FavoriteKind.STREAM)
    }

    /**
     * Whether [profileId] may see a title named [name] in [categoryId] of [providerId]'s
     * [contentType]: its category passes the profile's own content filters (a child's hide adult
     * categories), and a child's title isn't named as adult content. A child's film or series that
     * a Parental controls exception allows shows also from a hidden category that is not adult, as
     * in the catalogue (XtreamCategoryExclusionSync). Built once per source and section, then asked
     * per title.
     */
    suspend fun visibility(
        profileId: String,
        providerId: Long,
        contentType: String,
    ): (categoryId: String, name: String) -> Boolean =
        withContext(Dispatchers.IO) {
            val filters = CategoryFiltersStore(context).get(providerId, profileId)
            val type =
                when (contentType) {
                    ContentType.LIVE_TV -> XtreamCategoryEntity.TYPE_LIVE
                    ContentType.MOVIES -> XtreamCategoryEntity.TYPE_VOD
                    else -> XtreamCategoryEntity.TYPE_SERIES
                }
            val films = contentType == ContentType.MOVIES
            val categories = database.categoryDao().getAllCategoriesIncludingExcluded(providerId, type)
            val shown =
                categories
                    .filter { filters.shouldShowCategory(it.categoryName, films = films) }
                    .mapTo(HashSet()) { it.categoryId }
            val child = filters.child
            val reached =
                if (child == null || type == XtreamCategoryEntity.TYPE_LIVE) {
                    emptySet()
                } else {
                    categories
                        .filter { XtreamCategoryExclusionSync.exceptionsReach(filters, child, it.categoryName, films) }
                        .mapTo(HashSet()) { it.categoryId }
                }
            return@withContext { categoryId, name ->
                (categoryId in shown && child?.hidesTitle(name) != true) ||
                    (categoryId in reached && child?.allowsTitle(name) == true)
            }
        }
}
