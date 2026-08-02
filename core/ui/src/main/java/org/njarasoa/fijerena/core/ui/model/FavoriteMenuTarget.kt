package org.njarasoa.fijerena.core.ui.model

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
