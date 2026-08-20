package org.njarasoa.fijerena.core.ui.model

import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet

/**
 * A pending favorite-toggle action from a long-press/context menu on a category or stream.
 */
sealed class FavoriteMenuTarget {
    data class Category(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val isFavorite: Boolean,
    ) : FavoriteMenuTarget()

    data class Stream(
        val itemId: String,
        val itemName: String,
        val categoryId: String,
        val contentType: String,
        val isFavorite: Boolean,
    ) : FavoriteMenuTarget()
}

/** Display name and current favorite state, for building a confirm/toggle dialog. */
fun FavoriteMenuTarget.nameAndFavoriteState(): Pair<String, Boolean> =
    when (this) {
        is FavoriteMenuTarget.Category -> categoryName to isFavorite
        is FavoriteMenuTarget.Stream -> itemName to isFavorite
    }

fun MediaItem.toFavoriteMenuTarget(
    contentType: String,
    favoriteIds: ImmutableStringSet,
): FavoriteMenuTarget.Stream =
    FavoriteMenuTarget.Stream(
        itemId = id,
        itemName = name,
        categoryId = categoryId,
        contentType = contentType,
        isFavorite = favoriteIds.contains(id),
    )

/**
 * Builds a [FavoriteMenuTarget] from a long-pressed [item]: a category reference
 * (e.g. from "Favorite Categories"/"Recent Categories") toggles the category's
 * favorite state, anything else toggles the stream's.
 */
fun MediaItem.toFavoriteMenuTarget(
    contentType: String,
    isFavorite: (itemId: String) -> Boolean,
    isFavoriteCategory: (categoryId: String) -> Boolean,
): FavoriteMenuTarget {
    val categoryRef = target as? BrowseTarget.CategoryRef
    return if (categoryRef != null) {
        FavoriteMenuTarget.Category(
            categoryId = categoryRef.categoryId,
            categoryName = name,
            contentType = contentType,
            isFavorite = isFavoriteCategory(categoryRef.categoryId),
        )
    } else {
        FavoriteMenuTarget.Stream(
            itemId = id,
            itemName = name,
            categoryId = categoryId,
            contentType = contentType,
            isFavorite = isFavorite(id),
        )
    }
}
