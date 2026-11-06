package org.njarasoa.fijerena.core.ui.model

import org.njarasoa.fijerena.core.network.FavoriteTags
import org.njarasoa.fijerena.core.ui.history.MergedLists

/** A favourite whose tags are being edited: the row's source, id, content type and name. */
data class FavoriteTagTarget(
    val providerId: Long,
    val itemId: String,
    val contentType: String,
    val name: String,
)

/**
 * The tag dialog's state, the same on TV and phone (favourite tags plan, FT-D7): a check row for
 * each of the profile's [tags], the [checked] ones on this favourite, and a new name that [add]
 * puts in the list, checked. A name equal to a tag without regard to case checks that tag.
 */
data class FavoriteTagsDraft(
    val tags: List<String>,
    val checked: Set<String>,
) {
    /** [tag] on or off. */
    fun toggle(tag: String): FavoriteTagsDraft = copy(checked = if (tag in checked) checked - tag else checked + tag)

    /** [name] added and checked; the same draft when it is blank. */
    fun add(name: String): FavoriteTagsDraft {
        val clean = FavoriteTags.clean(name) ?: return this
        val existing = tags.firstOrNull { it.equals(clean, ignoreCase = true) }
        return if (existing != null) copy(checked = checked + existing) else copy(tags = tags + clean, checked = checked + clean)
    }

    /** The checked tags, in the order of the list. */
    val result: List<String> get() = tags.filter { it in checked }

    companion object {
        /** The profile's [profileTags] and the favourite's [itemTags], which are checked. */
        fun of(
            profileTags: List<String>,
            itemTags: List<String>,
        ): FavoriteTagsDraft {
            val tags = FavoriteTags.normalise(profileTags + itemTags)
            val checked = FavoriteTags.normalise(itemTags, existing = tags).toSet()
            return FavoriteTagsDraft(tags, checked)
        }
    }
}

/** The tag dialog's start for [target]: the profile's tags, [target]'s checked. */
suspend fun MergedLists.favoriteTagsDraft(target: FavoriteTagTarget): FavoriteTagsDraft =
    FavoriteTagsDraft.of(profileFavoriteTags(), favoriteTags(target.providerId, target.itemId, target.contentType))

/** Saves [target]'s tags on its source. */
suspend fun MergedLists.saveFavoriteTags(
    target: FavoriteTagTarget,
    tags: List<String>,
): Boolean = setFavoriteTags(target.providerId, target.itemId, target.contentType, tags)
