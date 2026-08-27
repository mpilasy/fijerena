package org.njarasoa.fijerena.core.network.fixtures

import org.njarasoa.fijerena.core.network.WatchedItem
import org.njarasoa.fijerena.core.network.xtream.db.SeriesCompletedCount
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateDao
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity

/**
 * In-memory stand-in for [WatchStateDao], reproducing the upsert/query semantics from
 * plans/watch-state-durable-storage-plan.md without a real Room/SQLite instance — this module's
 * unit tests run on plain JVM, with no Robolectric to back a real database.
 */
class FakeWatchStateDao : WatchStateDao {
    private val rows = LinkedHashMap<Triple<Long, String, String>, WatchStateEntity>()

    private fun key(
        providerId: Long,
        itemId: String,
        contentType: String,
    ) = Triple(providerId, itemId, contentType)

    /** Seeds a row directly, bypassing the upsert SQL — for tests that set up state, not exercise writes. */
    fun seed(entity: WatchStateEntity) {
        rows[key(entity.providerId, entity.itemId, entity.contentType)] = entity
    }

    fun all(): List<WatchStateEntity> = rows.values.toList()

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
                isCompleted = isCompleted,
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
            .map { (_, group) ->
                group.sortedWith(compareByDescending<WatchStateEntity> { it.lastPlayedAt }.thenByDescending { it.itemId }).first()
            }.sortedByDescending { it.lastPlayedAt }
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

    override suspend fun getAll(providerId: Long): List<WatchStateEntity> = rows.values.filter { it.providerId == providerId }

    override fun deleteAll(providerId: Long) {
        rows.keys.filter { it.first == providerId }.forEach { rows.remove(it) }
    }
}

/**
 * Inverse of `WatchStateEntity.toWatchedItem()` for test seeding: turns a fixture [WatchedItem]
 * into the row it would have produced. [at] stands in for `lastPlayedAt`/`updatedAt` — the fixture
 * itself carries `timestamp`, but tests seed several rows at once and want explicit control over
 * their relative recency rather than whatever `System.currentTimeMillis()` happened to return.
 */
fun WatchedItem.toWatchStateEntity(
    providerId: Long,
    at: Long,
): WatchStateEntity =
    WatchStateEntity(
        providerId = providerId,
        itemId = itemId,
        contentType = contentType,
        itemName = itemName,
        categoryId = categoryId,
        positionMs = playbackPosition,
        durationMs = duration,
        isCompleted = isCompleted,
        updatedAt = at,
        lastPlayedAt = at,
        seriesId = seriesId?.raw,
        episodeId = episodeId?.raw,
        seriesName = seriesName,
        episodeExtension = episodeExtension,
        audioTrackIndex = audioTrackIndex,
        subtitleTrackIndex = subtitleTrackIndex,
    )
