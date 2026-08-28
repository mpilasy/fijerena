package org.njarasoa.fijerena.core.network.fixtures

import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateDao
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity

/**
 * In-memory [FavoriteStateDao]. Keyed on the real primary key so `upsert` replaces the same way
 * Room's REPLACE does, and [getAll] returns newest-first like the indexed query it stands in for.
 */
class FakeFavoriteStateDao : FavoriteStateDao {
    private val rows = LinkedHashMap<Key, FavoriteStateEntity>()

    private data class Key(
        val providerId: Long,
        val itemId: String,
        val contentType: String,
        val kind: String,
    )

    private fun key(e: FavoriteStateEntity) = Key(e.providerId, e.itemId, e.contentType, e.kind)

    override fun getAll(providerId: Long): List<FavoriteStateEntity> =
        rows.values
            .filter { it.providerId == providerId }
            .sortedByDescending { it.createdAt }

    override fun upsert(entity: FavoriteStateEntity) {
        rows[key(entity)] = entity
    }

    override fun delete(
        providerId: Long,
        itemId: String,
        contentType: String,
        kind: String,
    ) {
        rows.remove(Key(providerId, itemId, contentType, kind))
    }

    override fun deleteAllOfKind(
        providerId: Long,
        kind: String,
    ) {
        rows.values.removeAll { it.providerId == providerId && it.kind == kind }
    }

    override fun deleteAll(providerId: Long) {
        rows.values.removeAll { it.providerId == providerId }
    }

    override fun restoreAll(entities: List<FavoriteStateEntity>) {
        entities.forEach { upsert(it) }
    }

    override fun count(providerId: Long): Int = rows.values.count { it.providerId == providerId }
}
