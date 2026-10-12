package org.njarasoa.fijerena.feature.episode.fixtures

import org.njarasoa.fijerena.core.network.xtream.db.RecentPlay
import org.njarasoa.fijerena.core.network.xtream.db.SeriesCompletedCount
import org.njarasoa.fijerena.core.network.xtream.db.SyncTombstoneEntity
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateDao
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity

/**
 * In-memory stand-in for [WatchStateDao], for the episode-selection Compose UI tests — no real
 * Room/SQLite instance needed. A copy of core/network's own `FakeWatchStateDao` (JVM unit tests,
 * not visible to this module's androidTest source set); keep the two in step when the DAO changes.
 */
class FakeWatchStateDao : WatchStateDao {
    private data class Key(
        val providerId: Long,
        val profileId: String,
        val itemId: String,
        val contentType: String,
    )

    private val rows = LinkedHashMap<Key, WatchStateEntity>()

    /** Seeds a row directly, bypassing the upsert SQL — for tests that set up state, not exercise writes. */
    fun seed(entity: WatchStateEntity) {
        rows[Key(entity.providerId, entity.profileId, entity.itemId, entity.contentType)] = entity
    }

    fun all(): List<WatchStateEntity> = rows.values.toList()

    override fun upsertProgress(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        itemName: String?,
        categoryId: String?,
        positionMs: Long,
        durationMs: Long,
        isCompleted: Boolean,
        now: Long,
        seriesId: String?,
        episodeId: String?,
        seriesName: String?,
        episodeExtension: String?,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
    ) {
        val k = Key(providerId, profileId, itemId, contentType)
        val existing = rows[k]
        rows[k] =
            WatchStateEntity(
                providerId = providerId,
                profileId = profileId,
                itemId = itemId,
                contentType = contentType,
                itemName = itemName ?: existing?.itemName ?: "",
                categoryId = categoryId ?: existing?.categoryId ?: "",
                positionMs = positionMs,
                durationMs = durationMs,
                // Sticky since Phase 6: can only raise isCompleted, never lower it.
                isCompleted = isCompleted || existing?.isCompleted == true,
                updatedAt = now,
                lastPlayedAt = now,
                seriesId = seriesId ?: existing?.seriesId,
                episodeId = episodeId ?: existing?.episodeId,
                seriesName = seriesName ?: existing?.seriesName,
                episodeExtension = episodeExtension ?: existing?.episodeExtension,
                audioTrackIndex = audioTrackIndex ?: existing?.audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex ?: existing?.subtitleTrackIndex,
            )
    }

    override suspend fun markWatched(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        now: Long,
        seriesId: String?,
        episodeId: String?,
    ) {
        val k = Key(providerId, profileId, itemId, contentType)
        val existing = rows[k]
        rows[k] =
            existing?.copy(isCompleted = true, updatedAt = now)
                ?: WatchStateEntity(
                    providerId = providerId,
                    profileId = profileId,
                    itemId = itemId,
                    contentType = contentType,
                    itemName = "",
                    categoryId = "",
                    positionMs = 0L,
                    durationMs = 0L,
                    isCompleted = true,
                    updatedAt = now,
                    lastPlayedAt = null,
                    seriesId = seriesId,
                    episodeId = episodeId,
                )
    }

    override suspend fun markUnwatched(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        now: Long,
    ) {
        val k = Key(providerId, profileId, itemId, contentType)
        val existing = rows[k] ?: return
        rows[k] = existing.copy(isCompleted = false, updatedAt = now)
    }

    override suspend fun clearRecentSeries(
        providerId: Long,
        profileId: String,
        seriesId: String,
        contentType: String,
        now: Long,
    ) {
        for ((k, existing) in rows.entries) {
            if (existing.providerId == providerId &&
                existing.profileId == profileId &&
                existing.contentType == contentType &&
                (existing.seriesId == seriesId || existing.itemId == seriesId)
            ) {
                rows[k] = existing.copy(lastPlayedAt = null, updatedAt = now)
            }
        }
    }

    override suspend fun getRecentSeriesPlays(
        providerId: Long,
        profileId: String,
        seriesId: String,
        contentType: String,
    ): List<RecentPlay> =
        rows.values
            .filter {
                it.providerId == providerId &&
                    it.profileId == profileId &&
                    it.contentType == contentType &&
                    (it.seriesId == seriesId || it.itemId == seriesId)
            }.mapNotNull { row -> row.lastPlayedAt?.let { RecentPlay(row.itemId, it) } }

    override suspend fun restoreLastPlayedAt(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        lastPlayedAt: Long,
        now: Long,
    ) {
        val k = Key(providerId, profileId, itemId, contentType)
        val existing = rows[k] ?: return
        if (existing.lastPlayedAt == null) rows[k] = existing.copy(lastPlayedAt = lastPlayedAt, updatedAt = now)
    }

    override suspend fun clearRecent(
        providerId: Long,
        profileId: String,
        contentType: String,
        now: Long,
    ) {
        for ((k, existing) in rows.entries) {
            if (existing.providerId == providerId &&
                existing.profileId == profileId &&
                existing.contentType == contentType &&
                existing.lastPlayedAt != null
            ) {
                rows[k] = existing.copy(lastPlayedAt = null, updatedAt = now)
            }
        }
    }

    override fun upsertRecency(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        itemName: String?,
        categoryId: String?,
        now: Long,
        seriesId: String?,
        episodeId: String?,
        seriesName: String?,
        episodeExtension: String?,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
    ) {
        val k = Key(providerId, profileId, itemId, contentType)
        val existing = rows[k]
        rows[k] =
            if (existing != null) {
                // Must not touch positionMs/durationMs/isCompleted — a start write must never
                // erase progress a progress write already stored.
                existing.copy(
                    updatedAt = now,
                    lastPlayedAt = now,
                    itemName = itemName ?: existing.itemName,
                    seriesId = seriesId ?: existing.seriesId,
                    episodeId = episodeId ?: existing.episodeId,
                    seriesName = seriesName ?: existing.seriesName,
                    episodeExtension = episodeExtension ?: existing.episodeExtension,
                    audioTrackIndex = audioTrackIndex ?: existing.audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex ?: existing.subtitleTrackIndex,
                )
            } else {
                WatchStateEntity(
                    providerId = providerId,
                    profileId = profileId,
                    itemId = itemId,
                    contentType = contentType,
                    itemName = itemName ?: "",
                    categoryId = categoryId ?: "",
                    positionMs = 0L,
                    durationMs = 0L,
                    isCompleted = false,
                    updatedAt = now,
                    lastPlayedAt = now,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    seriesName = seriesName,
                    episodeExtension = episodeExtension,
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex,
                )
            }
    }

    override suspend fun getByContentType(
        providerId: Long,
        profileId: String,
        contentType: String,
    ): List<WatchStateEntity> =
        rows.values.filter {
            it.providerId == providerId && it.profileId == profileId &&
                it.contentType == contentType
        }

    override suspend fun getRecent(
        providerId: Long,
        profileId: String,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity> =
        rows.values
            .filter { it.providerId == providerId && it.profileId == profileId && it.contentType == contentType && it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(limit)

    override suspend fun getRecentSeriesCollapsed(
        providerId: Long,
        profileId: String,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity> =
        rows.values
            .filter { it.providerId == providerId && it.profileId == profileId && it.contentType == contentType && it.lastPlayedAt != null }
            .groupBy { it.seriesId ?: it.itemId }
            .map { (_, group) ->
                group.sortedWith(compareByDescending<WatchStateEntity> { it.lastPlayedAt }.thenByDescending { it.itemId }).first()
            }.sortedByDescending { it.lastPlayedAt }
            .take(limit)

    override suspend fun getSeriesCompletedCounts(
        providerId: Long,
        profileId: String,
        contentType: String,
    ): List<SeriesCompletedCount> =
        rows.values
            .filter {
                it.providerId == providerId && it.profileId == profileId && it.contentType == contentType && it.isCompleted &&
                    it.seriesId != null
            }.groupBy { it.seriesId!! }
            .map { (seriesId, group) -> SeriesCompletedCount(seriesId, group.map { it.episodeId ?: it.itemId }.distinct().size) }

    override suspend fun getItem(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
    ): WatchStateEntity? = rows[Key(providerId, profileId, itemId, contentType)]

    override suspend fun getLatestSeriesTrackPrefs(
        providerId: Long,
        profileId: String,
        seriesId: String,
        contentType: String,
    ): WatchStateEntity? =
        rows.values
            .filter {
                it.providerId == providerId && it.profileId == profileId &&
                    it.seriesId == seriesId &&
                    it.contentType == contentType &&
                    (it.audioTrackIndex != null || it.subtitleTrackIndex != null)
            }.maxByOrNull { it.updatedAt }

    override suspend fun getResumable(
        providerId: Long,
        profileId: String,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity> =
        rows.values
            .filter {
                it.providerId == providerId && it.profileId == profileId &&
                    it.contentType == contentType &&
                    it.lastPlayedAt != null &&
                    !it.isCompleted &&
                    it.durationMs > 0 &&
                    (it.positionMs * 100.0 / it.durationMs) in 2.0..95.0
            }.sortedByDescending { it.lastPlayedAt }
            .take(limit)

    override suspend fun getResumableSeriesCollapsed(
        providerId: Long,
        profileId: String,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity> =
        rows.values
            .filter {
                it.providerId == providerId && it.profileId == profileId &&
                    it.contentType == contentType &&
                    it.lastPlayedAt != null &&
                    !it.isCompleted &&
                    it.durationMs > 0 &&
                    (it.positionMs * 100.0 / it.durationMs) in 2.0..95.0
            }.groupBy { it.seriesId ?: it.itemId }
            .map { (_, group) ->
                group.sortedWith(compareByDescending<WatchStateEntity> { it.lastPlayedAt }.thenByDescending { it.itemId }).first()
            }.sortedByDescending { it.lastPlayedAt }
            .take(limit)

    override suspend fun getAll(
        providerId: Long,
        profileId: String,
    ): List<WatchStateEntity> = rows.values.filter { it.providerId == providerId && it.profileId == profileId }

    override suspend fun getAllProfiles(providerId: Long): List<WatchStateEntity> = rows.values.filter { it.providerId == providerId }

    override suspend fun deleteAll(
        providerId: Long,
        profileId: String,
    ) {
        rows.keys.filter { it.providerId == providerId && it.profileId == profileId }.forEach { rows.remove(it) }
    }

    override suspend fun deleteProfile(profileId: String) {
        rows.keys.filter { it.profileId == profileId }.forEach { rows.remove(it) }
    }

    override suspend fun deleteAllProfiles(providerId: Long) {
        rows.keys.filter { it.providerId == providerId }.forEach { rows.remove(it) }
    }

    override suspend fun restoreAll(entities: List<WatchStateEntity>) {
        entities.forEach { seed(it) }
    }

    /** The `sync_tombstone` rows this DAO wrote. */
    val tombstones = mutableListOf<SyncTombstoneEntity>()

    override suspend fun insertTombstone(tombstone: SyncTombstoneEntity) {
        tombstones.removeAll { it.providerId == tombstone.providerId && it.profileId == tombstone.profileId && it.kind == tombstone.kind }
        tombstones.add(tombstone)
    }
}
