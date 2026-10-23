package org.njarasoa.fijerena.core.player.domain

data class MediaCategory(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val iconUrl: String? = null,
    val isVirtual: Boolean = false,
    /**
     * The source the category belongs to: Live TV, Movies and TV Shows list every source's categories
     * in one list (docs/plans/20261012_multi-source-search-history-plan.md → Phase 6). 0 on a virtual
     * category (Recent, Favourites…, which mix the sources) and on a category no list has placed yet.
     */
    val providerId: Long = 0L,
) {
    /**
     * The category's key across sources ([categoryKey]): the same id on two sources is two
     * categories. For list keys, focus keys and the selection. Computed once: list keys read it per row.
     */
    val key: String = categoryKey(id, providerId)
}

/** A category's key: its id alone when it has no source (a virtual one), else source and id. */
fun categoryKey(
    id: String,
    providerId: Long,
): String = if (providerId == 0L) id else "$providerId/$id"
