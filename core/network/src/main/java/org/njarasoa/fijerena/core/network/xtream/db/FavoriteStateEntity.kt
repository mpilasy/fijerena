package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

/** Discriminates the two things a favourite can point at. See [FavoriteStateEntity.kind]. */
object FavoriteKind {
    const val STREAM = "STREAM"
    const val CATEGORY = "CATEGORY"
}

/**
 * Durable favourites, kept forever.
 *
 * Replaces the `favorites_v2` and `favorite_categories` SharedPreferences blobs, which were capped
 * at `providerSettings.favoritesMaxSize` and truncated on every write, silently evicting the oldest
 * entry once the cap was reached — see `docs/plans/favorites-durable-storage-plan.md`. Not
 * `xtream_`-prefixed, for the same reason as `watch_state`: `MediaRepository` backs SMB, Local and
 * Remote M3U through it too, not only Xtream.
 *
 * One table for both blobs rather than two: they asked the same question with different column
 * names, and [kind] keeps a single index serving both. For [FavoriteKind.CATEGORY], [itemId] is the
 * category id and [parentCategoryId] is null; for [FavoriteKind.STREAM], [itemId] is the stream id
 * and [parentCategoryId] is the category it was favourited from.
 *
 * [createdAt] carries the blob's ordering — newest first — which the favourites list relies on.
 */
@Entity(
    tableName = "favorite_state",
    primaryKeys = ["providerId", "itemId", "contentType", "kind"],
    indices = [Index(value = ["providerId", "kind", "contentType", "createdAt"])],
)
data class FavoriteStateEntity(
    val providerId: Long,
    val itemId: String,
    val contentType: String,
    val kind: String,
    val name: String,
    val parentCategoryId: String? = null,
    val createdAt: Long,
)
