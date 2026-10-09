package org.njarasoa.fijerena.feature.category.components

import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList

/**
 * A row removal or an Undo waiting for its list to come back (docs/plans/20261009_tv-recents-favorites-plan.md
 * → A, "Fix focus after a removal"): once the list on screen is no longer [from], focus goes to
 * [rowAfterChange] of [key] in it. The removed row's card is gone by then, and focus with it.
 */
internal class RowFocusAfterChange(
    val from: ImmutableMediaList?,
    val key: String,
    /** The list's row keys when the change was made, in order. */
    val oldKeys: List<String>,
)

/**
 * The row that takes focus once [key] was removed from (or put back in) a list that was [oldKeys]
 * and is now [newKeys]: [key] itself when it is there (an Undo, or a list that keeps the playing
 * channel), else the row that took its place — the next one still listed — else the previous one
 * when the last row went; null when the list is empty (its empty state or the panel's tabs take
 * focus).
 */
internal fun rowAfterChange(
    oldKeys: List<String>,
    key: String,
    newKeys: List<String>,
): String? {
    val listed = newKeys.toHashSet()
    val index = oldKeys.indexOf(key)
    val after = oldKeys.subList(index + 1, oldKeys.size)
    val before = oldKeys.subList(0, index.coerceAtLeast(0)).asReversed()
    return key.takeIf(listed::contains)
        ?: after.firstOrNull(listed::contains)
        ?: before.firstOrNull(listed::contains)
        ?: newKeys.firstOrNull()
}
