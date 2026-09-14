package org.njarasoa.fijerena.feature.episode.fixtures

import org.njarasoa.fijerena.core.network.xtream.db.SeriesCompletedCount
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateDao
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity

/**
 * In-memory stand-in for [WatchStateDao], for the episode-selection Compose UI tests — no real
 * Room/SQLite instance needed. Mirrors core/network's own `FakeWatchStateDao` (JVM unit tests,
 * not visible to this module's androidTest source set), trimmed to what those tests actually
 * exercise.
 */
class FakeWatchStateDao : WatchStateDao {
    private val rows = LinkedHashMap<Triple<Long, String, String>, WatchStateEntity>()

    private fun key(
        providerId: Long,
        itemId: String,
        contentType: String,
    ) = Triple(providerId, itemId, contentType)

    /** Seeds a row directly, bypassing the upsert SQL — sets up state without exercising writes. */
    fun seed(entity: WatchStateEntity) {
        rows[key(entity.providerId, entity.itemId, entity.contentType)] = entity
    }

    override fun upsertProgress(
        providerId: Long,
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
        val k = key(providerId, itemId, contentType)
        val existing = rows[k]
        rows[k] =
            WatchStateEntity(
                providerId = providerId,
                itemId = itemId,
                contentType = contentType,
                itemName = itemName ?: existing?.itemName ?: "",
                categoryId = categoryId ?: existing?.categoryId ?: "",
                positionMs = positionMs,
                durationMs = durationMs,
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

    override fun upsertRecency(
        providerId: Long,
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
        val k = key(providerId, itemId, contentType)
        val existing = rows[k]
        rows[k] =
            existing?.copy(updatedAt = now, lastPlayedAt = now)
                ?: WatchStateEntity(
                    providerId = providerId,
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

    override suspend fun markWatched(
        providerId: Long,
        itemId: String,
        contentType: String,
        now: Long,
        seriesId: String?,
        episodeId: String?,
    ) {
        val k = key(providerId, itemId, contentType)
        val existing = rows[k]
        rows[k] =
            existing?.copy(isCompleted = true, updatedAt = now)
                ?: WatchStateEntity(
                    providerId = providerId,
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
        itemId: String,
        contentType: String,
        now: Long,
    ) {
        val k = key(providerId, itemId, contentType)
        val existing = rows[k] ?: return
        rows[k] = existing.copy(isCompleted = false, updatedAt = now)
    }

    override suspend fun clearRecentSeries(
        providerId: Long,
        seriesId: String,
        contentType: String,
        now: Long,
    ) {
        for ((k, existing) in rows.entries) {
            if (existing.providerId == providerId &&
                existing.contentType == contentType &&
                (existing.seriesId == seriesId || existing.itemId == seriesId)
            ) {
                rows[k] = existing.copy(lastPlayedAt = null, updatedAt = now)
            }
        }
    }

    override suspend fun getByContentType(
        providerId: Long,
        contentType: String,
    ): List<WatchStateEntity> = rows.values.filter { it.providerId == providerId && it.contentType == contentType }

    override suspend fun getRecent(
        providerId: Long,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity> =
        rows.values
            .filter { it.providerId == providerId && it.contentType == contentType && it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(limit)

    override suspend fun getRecentSeriesCollapsed(
        providerId: Long,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity> =
        rows.values
            .filter { it.providerId == providerId && it.contentType == contentType && it.lastPlayedAt != null }
            .groupBy { it.seriesId ?: it.itemId }
            .map { (_, group) -> group.sortedByDescending { it.lastPlayedAt }.first() }
            .sortedByDescending { it.lastPlayedAt }
            .take(limit)

    override suspend fun getSeriesCompletedCounts(
        providerId: Long,
        contentType: String,
    ): List<SeriesCompletedCount> =
        rows.values
            .filter { it.providerId == providerId && it.contentType == contentType && it.isCompleted && it.seriesId != null }
            .groupBy { it.seriesId!! }
            .map { (seriesId, group) -> SeriesCompletedCount(seriesId, group.map { it.episodeId ?: it.itemId }.distinct().size) }

    override suspend fun getItem(
        providerId: Long,
        itemId: String,
        contentType: String,
    ): WatchStateEntity? = rows[key(providerId, itemId, contentType)]

    override suspend fun getLatestSeriesTrackPrefs(
        providerId: Long,
        seriesId: String,
        contentType: String,
    ): WatchStateEntity? =
        rows.values
            .filter {
                it.providerId == providerId &&
                    it.seriesId == seriesId &&
                    it.contentType == contentType &&
                    (it.audioTrackIndex != null || it.subtitleTrackIndex != null)
            }.maxByOrNull { it.updatedAt }

    override suspend fun getAll(providerId: Long): List<WatchStateEntity> = rows.values.filter { it.providerId == providerId }

    override suspend fun deleteAll(providerId: Long) {
        rows.keys.filter { it.first == providerId }.forEach { rows.remove(it) }
    }

    override suspend fun restoreAll(entities: List<WatchStateEntity>) {
        entities.forEach { seed(it) }
    }
}
