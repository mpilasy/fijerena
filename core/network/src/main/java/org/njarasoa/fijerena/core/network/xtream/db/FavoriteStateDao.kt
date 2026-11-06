package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.njarasoa.fijerena.core.network.FavoriteTags
import org.njarasoa.fijerena.core.network.sync.SyncKind

/**
 * Favourites storage. Every read here is blocking rather than suspending on purpose: the caller is
 * `MediaRepository`, which serves favourites from an in-memory snapshot because Compose asks
 * `isFavorite()` synchronously during composition. The snapshot is filled from [getAll] inside
 * `setProvider()`, which runs on `Dispatchers.IO`.
 *
 * There is no cap and no trim query. That is the point of the table — see
 * the favorites durable storage plan.
 */
@Dao
interface FavoriteStateDao {
    /** Whole-provider read for one profile's snapshot. Newest first. */
    @Query("SELECT * FROM favorite_state WHERE providerId = :providerId AND profileId = :profileId ORDER BY createdAt DESC")
    fun getAll(
        providerId: Long,
        profileId: String,
    ): List<FavoriteStateEntity>

    /** Every profile's rows for this provider — copying a provider copies everyone's favourites. */
    @Query("SELECT * FROM favorite_state WHERE providerId = :providerId")
    fun getAllProfiles(providerId: Long): List<FavoriteStateEntity>

    /**
     * Insert-or-replace. Re-favouriting something already favourited refreshes `createdAt`, which
     * moves it to the front of the list.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: FavoriteStateEntity)

    @Query(
        "DELETE FROM favorite_state WHERE providerId = :providerId AND profileId = :profileId " +
            "AND itemId = :itemId AND contentType = :contentType AND kind = :kind",
    )
    fun delete(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        kind: String,
    )

    @Query(
        "SELECT * FROM favorite_state WHERE providerId = :providerId AND profileId = :profileId " +
            "AND itemId = :itemId AND contentType = :contentType AND kind = :kind",
    )
    fun get(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        kind: String,
    ): FavoriteStateEntity?

    /** Records a film's or show's TMDB id on its favourite (an update, so live sync sends it). */
    @Query(
        "UPDATE favorite_state SET tmdbId = :tmdbId WHERE providerId = :providerId AND profileId = :profileId " +
            "AND itemId = :itemId AND contentType = :contentType AND kind = :kind",
    )
    fun setTmdbId(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        kind: String,
        tmdbId: String,
    )

    /** Sets a stream favourite's tags (an update, so live sync sends it). [tags] is the JSON text. */
    @Query(
        "UPDATE favorite_state SET tags = :tags WHERE providerId = :providerId AND profileId = :profileId " +
            "AND itemId = :itemId AND contentType = :contentType AND kind = '${FavoriteKind.STREAM}'",
    )
    fun setTags(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        tags: String,
    )

    /** The profile's stream favourites that have a tag, on every provider: its tags are one list. */
    @Query(
        "SELECT * FROM favorite_state WHERE profileId = :profileId AND kind = '${FavoriteKind.STREAM}' " +
            "AND tags != '${FavoriteStateEntity.NO_TAGS}'",
    )
    fun getTagged(profileId: String): List<FavoriteStateEntity>

    /** Renames the profile's tag [from] to [to] on every favourite of every provider; see [FavoriteTags.rename]. */
    @Transaction
    fun renameTag(
        profileId: String,
        from: String,
        to: String,
    ) = rewriteTags(this, profileId) { FavoriteTags.rename(it, from, to) }

    /** Removes the profile's tag [name] from every favourite of every provider. */
    @Transaction
    fun deleteTag(
        profileId: String,
        name: String,
    ) = rewriteTags(this, profileId) { FavoriteTags.remove(it, name) }

    @Query("SELECT * FROM favorite_state WHERE providerId = :providerId AND profileId = :profileId AND kind = :kind")
    fun getAllOfKind(
        providerId: Long,
        profileId: String,
        kind: String,
    ): List<FavoriteStateEntity>

    /**
     * Every provider's and profile's rows of [kind] whose `itemId` starts with [prefix], compared
     * literally (not `LIKE`, where `_` is a wildcard). Backs `FavoriteCategoryRowCleanup`.
     */
    @Query("SELECT * FROM favorite_state WHERE kind = :kind AND substr(itemId, 1, length(:prefix)) = :prefix")
    fun getAllOfKindWithIdPrefix(
        kind: String,
        prefix: String,
    ): List<FavoriteStateEntity>

    // --- Live sync tombstones: see SyncTombstoneEntity. The production writers use these. ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTombstone(tombstone: SyncTombstoneEntity)

    @Query(
        "DELETE FROM sync_tombstone WHERE providerId = :providerId AND profileId = :profileId " +
            "AND kind = :kind AND itemId = :itemId AND contentType = :contentType",
    )
    fun deleteTombstone(
        providerId: Long,
        profileId: String,
        kind: String,
        itemId: String,
        contentType: String,
    )

    /**
     * [upsert], dropping any tombstone left by an earlier removal of the same favourite. A row
     * without tags keeps the tags of the row it replaces: re-favouriting keeps them (favourite tags
     * plan, FT-D3).
     */
    @Transaction
    fun upsertClearingTombstone(entity: FavoriteStateEntity) {
        val kept =
            if (entity.tags == FavoriteStateEntity.NO_TAGS) {
                get(entity.providerId, entity.profileId, entity.itemId, entity.contentType, entity.kind)?.tags
            } else {
                null
            }
        upsert(if (kept != null) entity.copy(tags = kept) else entity)
        deleteTombstone(entity.providerId, entity.profileId, SyncKind.forFavorite(entity.kind), entity.itemId, entity.contentType)
    }

    /**
     * [delete], recording the removal for live sync. [deletedAt] 0 (the default) means "now on the
     * sync clock", stamped by [XtreamSyncTriggers]; tests pass explicit values.
     */
    @Transaction
    fun deleteRecordingTombstone(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        kind: String,
        deletedAt: Long = 0,
    ) {
        delete(providerId, profileId, itemId, contentType, kind)
        insertTombstone(SyncTombstoneEntity(providerId, profileId, SyncKind.forFavorite(kind), itemId, contentType, deletedAt))
    }

    /** [deleteAllOfKind], recording each removed favourite for live sync; see [deleteRecordingTombstone] for [deletedAt]. */
    @Transaction
    fun deleteAllOfKindRecordingTombstones(
        providerId: Long,
        profileId: String,
        kind: String,
        deletedAt: Long = 0,
    ) {
        getAllOfKind(providerId, profileId, kind).forEach {
            insertTombstone(SyncTombstoneEntity(providerId, profileId, SyncKind.forFavorite(kind), it.itemId, it.contentType, deletedAt))
        }
        deleteAllOfKind(providerId, profileId, kind)
    }

    /** [restoreAll], dropping tombstones of the favourites it brings back. */
    @Transaction
    fun restoreAllClearingTombstones(entities: List<FavoriteStateEntity>) {
        restoreAll(entities)
        entities.forEach {
            deleteTombstone(it.providerId, it.profileId, SyncKind.forFavorite(it.kind), it.itemId, it.contentType)
        }
    }

    /** Backs "Clear All Favorites", which is scoped to streams — categories are left alone. */
    @Query("DELETE FROM favorite_state WHERE providerId = :providerId AND profileId = :profileId AND kind = :kind")
    fun deleteAllOfKind(
        providerId: Long,
        profileId: String,
        kind: String,
    )

    /** Profile deletion: that profile's rows across every provider. */
    @Query("DELETE FROM favorite_state WHERE profileId = :profileId")
    fun deleteProfile(profileId: String)

    /** Restore path: rewrites `providerId` before insert, so it takes whole rows. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun restoreAll(entities: List<FavoriteStateEntity>)

    @Query("SELECT COUNT(*) FROM favorite_state WHERE providerId = :providerId")
    fun count(providerId: Long): Int
}

/** Rewrites the tags of each tagged favourite of [profileId] that [change] changes. */
private fun rewriteTags(
    dao: FavoriteStateDao,
    profileId: String,
    change: (List<String>) -> List<String>,
) {
    dao.getTagged(profileId).forEach { row ->
        val tags = FavoriteStateEntity.decodeTags(row.tags)
        val changed = change(tags)
        if (changed != tags) {
            dao.setTags(row.providerId, profileId, row.itemId, row.contentType, FavoriteStateEntity.encodeTags(changed))
        }
    }
}
