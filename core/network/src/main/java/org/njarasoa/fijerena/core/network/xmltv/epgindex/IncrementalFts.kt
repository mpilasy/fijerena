package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Where an incremental update of the guide search index stands (the pausable guide indexing plan).
 * [sourceIds] are the refreshed guide sources; [cursor] the last programme id done in [phase];
 * [cutoff] the end time below which the swap deletes a programme as ended; [boundary] the highest
 * programme id before the swap, so the new rows of [sourceIds] are those above it.
 */
internal data class FtsProgress(
    val phase: Phase,
    val sourceIds: List<Long>,
    val cursor: Long,
    val cutoff: Long,
    val boundary: Long,
) {
    enum class Phase {
        /** Deleting the index entries of the rows the swap will delete. The swap hasn't happened. */
        REMOVE,

        /** The swap is done: adding the index entries of the new rows. */
        ADD,

        /** Every entry is in: merging the index's segments. */
        MERGE,
    }
}

/**
 * The steps of an incremental index update, each one batch in one transaction with its progress
 * ([FtsProgress], kept in `epg_fts_progress`, a table Room doesn't manage). Committing the work
 * and the progress together means a batch is never done twice: deleting an FTS4 entry twice
 * would damage the index. The caller holds the indexer's write lock.
 */
internal object IncrementalFts {
    /** Programmes per batch: a second or two of work even on a slow TV. */
    const val BATCH_ROWS = 5_000

    /** One merge step: pages of work, segments merged at once ([SQLite FTS4 'merge']). */
    private const val MERGE_COMMAND = "merge=200,8"

    private const val TABLE = "epg_fts_progress"

    fun ensureTable(sdb: SupportSQLiteDatabase) {
        sdb.execSQL(
            "CREATE TABLE IF NOT EXISTS `$TABLE` (`id` INTEGER PRIMARY KEY CHECK (`id` = 1), `phase` TEXT NOT NULL, " +
                "`source_ids` TEXT NOT NULL, `cursor` INTEGER NOT NULL, `cutoff` INTEGER NOT NULL, `boundary` INTEGER NOT NULL)",
        )
    }

    fun read(sdb: SupportSQLiteDatabase): FtsProgress? {
        ensureTable(sdb)
        return sdb.query("SELECT phase, source_ids, cursor, cutoff, boundary FROM `$TABLE` WHERE id = 1").use { c ->
            if (!c.moveToFirst()) return null
            val phase = FtsProgress.Phase.entries.firstOrNull { it.name == c.getString(0) } ?: return null
            FtsProgress(
                phase = phase,
                sourceIds = c.getString(1).split(',').mapNotNull { it.toLongOrNull() },
                cursor = c.getLong(2),
                cutoff = c.getLong(3),
                boundary = c.getLong(4),
            )
        }
    }

    fun write(
        sdb: SupportSQLiteDatabase,
        progress: FtsProgress,
    ) {
        ensureTable(sdb)
        sdb.execSQL(
            "INSERT OR REPLACE INTO `$TABLE` (id, phase, source_ids, cursor, cutoff, boundary) VALUES (1, ?, ?, ?, ?, ?)",
            arrayOf<Any>(progress.phase.name, progress.sourceIds.joinToString(","), progress.cursor, progress.cutoff, progress.boundary),
        )
    }

    fun clear(sdb: SupportSQLiteDatabase) {
        ensureTable(sdb)
        sdb.execSQL("DELETE FROM `$TABLE`")
    }

    /**
     * Deletes the index entries of the next batch of rows the swap will delete: the live rows of
     * the refreshed sources and the ended ones. Returns the progress after it, or null when no
     * row is left (the caller then swaps).
     */
    fun removeBatch(
        sdb: SupportSQLiteDatabase,
        progress: FtsProgress,
    ): FtsProgress? {
        val filter = "(source_id IN (${placeholders(progress.sourceIds)}) OR end_epoch < ?)"
        val args: List<Any> = progress.sourceIds + progress.cutoff
        return inTransaction(sdb) {
            val last = lastIdOfBatch(sdb, filter, progress.cursor, args) ?: return@inTransaction null
            sdb.execSQL(
                "DELETE FROM epg_programme_fts WHERE docid IN " +
                    "(SELECT id FROM epg_programme WHERE id > ? AND id <= ? AND $filter)",
                (listOf<Any>(progress.cursor, last) + args).toTypedArray(),
            )
            progress.copy(cursor = last).also { write(sdb, it) }
        }
    }

    /**
     * Adds the index entries of the next batch of the refreshed sources' new rows. Returns the
     * progress after it, or null when every new row is in.
     */
    fun addBatch(
        sdb: SupportSQLiteDatabase,
        progress: FtsProgress,
    ): FtsProgress? {
        val filter = "source_id IN (${placeholders(progress.sourceIds)})"
        val args: List<Any> = progress.sourceIds
        return inTransaction(sdb) {
            val last = lastIdOfBatch(sdb, filter, progress.cursor, args) ?: return@inTransaction null
            sdb.execSQL(
                "INSERT INTO epg_programme_fts(docid, title, description) " +
                    "SELECT id, title, description FROM epg_programme WHERE id > ? AND id <= ? AND $filter",
                (listOf<Any>(progress.cursor, last) + args).toTypedArray(),
            )
            progress.copy(cursor = last).also { write(sdb, it) }
        }
    }

    /** One merge step of the index's segments. Returns whether it merged anything (then call it again). */
    fun mergeStep(sdb: SupportSQLiteDatabase): Boolean {
        val before = totalChanges(sdb)
        sdb.execSQL("INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('$MERGE_COMMAND')")
        // SQLite: a merge that did work raises total_changes() by 2 or more.
        return totalChanges(sdb) - before >= 2
    }

    // The id of the last row of the next batch after [cursor] that passes [filter]; null when none.
    private fun lastIdOfBatch(
        sdb: SupportSQLiteDatabase,
        filter: String,
        cursor: Long,
        args: List<Any>,
    ): Long? =
        sdb
            .query(
                "SELECT MAX(id) FROM (SELECT id FROM epg_programme WHERE id > ? AND $filter ORDER BY id LIMIT $BATCH_ROWS)",
                (listOf<Any>(cursor) + args).toTypedArray(),
            ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    private fun totalChanges(sdb: SupportSQLiteDatabase): Long =
        sdb.query("SELECT total_changes()").use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    private fun placeholders(ids: List<Long>): String = ids.joinToString(",") { "?" }

    private inline fun <T> inTransaction(
        sdb: SupportSQLiteDatabase,
        block: () -> T,
    ): T {
        sdb.beginTransaction()
        try {
            val result = block()
            sdb.setTransactionSuccessful()
            return result
        } finally {
            sdb.endTransaction()
        }
    }
}
