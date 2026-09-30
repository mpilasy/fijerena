package org.njarasoa.fijerena.core.network.fixtures

import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateDao
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.SyncTombstoneEntity

/**
 * In-memory [FavoriteStateDao]. Keyed on the real primary key so `upsert` replaces the same way
 * Room's REPLACE does, and [getAll] returns newest-first like the indexed query it stands in for.
 */
class FakeFavoriteStateDao : FavoriteStateDao {
    private val rows = LinkedHashMap<Key, FavoriteStateEntity>()

    private data class Key(
        val providerId: Long,
        val profileId: String,
        val itemId: String,
        val contentType: String,
        val kind: String,
    )

    /** The `sync_tombstone` rows this DAO wrote, by their primary key. */
    val tombstones = LinkedHashMap<List<Any>, SyncTombstoneEntity>()

    private fun key(e: FavoriteStateEntity) = Key(e.providerId, e.profileId, e.itemId, e.contentType, e.kind)

    override fun getAll(
        providerId: Long,
        profileId: String,
    ): List<FavoriteStateEntity> =
        rows.values
            .filter { it.providerId == providerId && it.profileId == profileId }
            .sortedByDescending { it.createdAt }

    override fun getAllProfiles(providerId: Long): List<FavoriteStateEntity> = rows.values.filter { it.providerId == providerId }

    override fun upsert(entity: FavoriteStateEntity) {
        rows[key(entity)] = entity
    }

    override fun delete(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        kind: String,
    ) {
        rows.remove(Key(providerId, profileId, itemId, contentType, kind))
    }

    override fun deleteAllOfKind(
        providerId: Long,
        profileId: String,
        kind: String,
    ) {
        rows.values.removeAll { it.providerId == providerId && it.profileId == profileId && it.kind == kind }
    }

    override fun deleteProfile(profileId: String) {
        rows.values.removeAll { it.profileId == profileId }
    }

    override fun deleteAllProfiles(providerId: Long) {
        rows.values.removeAll { it.providerId == providerId }
    }

    override fun restoreAll(entities: List<FavoriteStateEntity>) {
        entities.forEach { upsert(it) }
    }

    override fun count(providerId: Long): Int = rows.values.count { it.providerId == providerId }

    override fun deleteOrphaned(validProviderIds: List<Long>): Int {
        val toRemove = rows.keys.filter { it.providerId !in validProviderIds }
        toRemove.forEach { rows.remove(it) }
        return toRemove.size
    }

    override fun get(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        kind: String,
    ): FavoriteStateEntity? = rows[Key(providerId, profileId, itemId, contentType, kind)]

    override fun getAllOfKind(
        providerId: Long,
        profileId: String,
        kind: String,
    ): List<FavoriteStateEntity> = rows.values.filter { it.providerId == providerId && it.profileId == profileId && it.kind == kind }

    override fun insertTombstone(tombstone: SyncTombstoneEntity) {
        tombstones[listOf(tombstone.providerId, tombstone.profileId, tombstone.kind, tombstone.itemId, tombstone.contentType)] = tombstone
    }

    override fun deleteTombstone(
        providerId: Long,
        profileId: String,
        kind: String,
        itemId: String,
        contentType: String,
    ) {
        tombstones.remove(listOf(providerId, profileId, kind, itemId, contentType))
    }
}
