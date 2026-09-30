package org.njarasoa.fijerena.core.network.xtream.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.njarasoa.fijerena.core.network.sync.SyncKind

/**
 * Queues every local change to favourites, watch state and deletions for live sync, from inside
 * SQLite: triggers run in the writing transaction, so nothing is changed without being queued, and
 * they catch every write path — including indirect ones such as a TMDB group completion updating
 * sibling rows — without each call site having to remember. See
 * `docs/plans/20260929_live-sync-plan.md` → Flow.
 *
 * Each trigger advances [SyncClockEntity.hlc] and records the key in [SyncOutboxEntity] at that
 * value. A new [SyncTombstoneEntity] with `deletedAt = 0` is stamped with the clock too. Nothing
 * fires while [SyncClockEntity.applying] is set. Deleting rows fires nothing: a deletion is queued
 * through its tombstone, and rows removed with their provider or profile are covered by that
 * one's own tombstone.
 *
 * Installed on every open (`IF NOT EXISTS`), so fresh installs, migrations and destructive
 * rebuilds all end up with them. Room doesn't manage triggers.
 */
internal object XtreamSyncTriggers {
    // Wall clock in ms from SQLite itself; julianday keeps sub-second precision.
    private const val WALL_MS = "CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER)"
    private const val NOT_APPLYING = "(SELECT `applying` FROM `sync_clock` WHERE `id` = ${SyncClockEntity.SINGLE_ROW}) = 0"
    private const val TICK = "UPDATE `sync_clock` SET `hlc` = MAX($WALL_MS, `hlc` + 1) WHERE `id` = ${SyncClockEntity.SINGLE_ROW};"
    private const val CLOCK = "(SELECT `hlc` FROM `sync_clock` WHERE `id` = ${SyncClockEntity.SINGLE_ROW})"

    private fun queue(
        kind: String,
        row: String,
    ) = "INSERT OR REPLACE INTO `sync_outbox` (`providerId`, `profileId`, `kind`, `itemId`, `contentType`, `hlc`) " +
        "VALUES ($row.`providerId`, $row.`profileId`, $kind, $row.`itemId`, $row.`contentType`, $CLOCK);"

    private val favoriteKind =
        "CASE NEW.`kind` WHEN '${FavoriteKind.CATEGORY}' THEN '${SyncKind.FAVORITE_CATEGORY}' ELSE '${SyncKind.FAVORITE_STREAM}' END"

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (${SyncClockEntity.SINGLE_ROW}, 0, 0)",
        )
        for (event in listOf("INSERT", "UPDATE")) {
            val suffix = event.lowercase()
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `sync_watch_state_$suffix` AFTER $event ON `watch_state` " +
                    "WHEN $NOT_APPLYING BEGIN $TICK ${queue("'${SyncKind.WATCH}'", "NEW")} END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `sync_favorite_state_$suffix` AFTER $event ON `favorite_state` " +
                    "WHEN $NOT_APPLYING BEGIN $TICK ${queue(favoriteKind, "NEW")} END",
            )
        }
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `sync_tombstone_insert` AFTER INSERT ON `sync_tombstone` " +
                "WHEN $NOT_APPLYING BEGIN $TICK " +
                "UPDATE `sync_tombstone` SET `deletedAt` = $CLOCK WHERE NEW.`deletedAt` = 0 " +
                "AND `providerId` = NEW.`providerId` AND `profileId` = NEW.`profileId` AND `kind` = NEW.`kind` " +
                "AND `itemId` = NEW.`itemId` AND `contentType` = NEW.`contentType`; " +
                "${queue("NEW.`kind`", "NEW")} END",
        )
    }
}
