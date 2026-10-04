package org.njarasoa.fijerena.core.network.provider

import androidx.sqlite.db.SupportSQLiteDatabase
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.xtream.db.SyncClockEntity

/**
 * Queues changes to providers, profiles and EPG sources for live sync, from inside SQLite — the
 * `providers.db` counterpart of `XtreamSyncTriggers`; see there for the reasoning. An update is
 * queued only when a synced column actually changes, so sync statistics, activation and EPG
 * ingestion bookkeeping never are. Deleting an EPG source records its tombstone here too, since it
 * has several delete paths. Recreated on every open, like `XtreamSyncTriggers`; nothing fires while
 * [SyncClockEntity.applying] is set. See `docs/plans/archive/20260929_live-sync-plan.md` → Flow.
 */
internal object SettingsSyncTriggers {
    private const val WALL_MS = "CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER)"
    private const val NOT_APPLYING = "(SELECT `applying` FROM `sync_clock` WHERE `id` = ${SyncClockEntity.SINGLE_ROW}) = 0"
    private const val TICK = "UPDATE `sync_clock` SET `hlc` = MAX($WALL_MS, `hlc` + 1) WHERE `id` = ${SyncClockEntity.SINGLE_ROW};"
    private const val CLOCK = "(SELECT `hlc` FROM `sync_clock` WHERE `id` = ${SyncClockEntity.SINGLE_ROW})"

    // Delete-then-insert, never OR REPLACE — see XtreamSyncTriggers.queue.
    private fun queue(
        kind: String,
        itemKey: String,
    ) = "DELETE FROM `sync_version` WHERE `kind` = $kind AND `profileId` = '${SyncKind.SHARED}' AND `itemKey` = $itemKey; " +
        "INSERT INTO `sync_version` (`kind`, `profileId`, `itemKey`, `hlc`, `pending`) " +
        "VALUES ($kind, '${SyncKind.SHARED}', $itemKey, $CLOCK, 1);"

    private fun changed(vararg columns: String) = columns.joinToString(" OR ") { "OLD.`$it` IS NOT NEW.`$it`" }

    private fun createTrigger(
        db: SupportSQLiteDatabase,
        name: String,
        sql: String,
    ) {
        db.execSQL("DROP TRIGGER IF EXISTS `$name`")
        db.execSQL(sql)
    }

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (${SyncClockEntity.SINGLE_ROW}, 0, 0)")

        val provider = queue("'${SyncKind.PROVIDER}'", "NEW.`providerKey`")
        createTrigger(
            db,
            "sync_providers_insert",
            "CREATE TRIGGER `sync_providers_insert` AFTER INSERT ON `providers` " +
                "WHEN $NOT_APPLYING BEGIN $TICK $provider END",
        )
        createTrigger(
            db,
            "sync_providers_update",
            "CREATE TRIGGER `sync_providers_update` AFTER UPDATE ON `providers` " +
                "WHEN $NOT_APPLYING AND (${changed("name", "url", "username", "type", "config", "providerSettings")}) " +
                "BEGIN $TICK $provider END",
        )

        val profile = queue("'${SyncKind.PROFILE}'", "NEW.`id`")
        createTrigger(
            db,
            "sync_profiles_insert",
            "CREATE TRIGGER `sync_profiles_insert` AFTER INSERT ON `profiles` " +
                "WHEN $NOT_APPLYING BEGIN $TICK $profile END",
        )
        createTrigger(
            db,
            "sync_profiles_update",
            "CREATE TRIGGER `sync_profiles_update` AFTER UPDATE ON `profiles` " +
                "WHEN $NOT_APPLYING AND (${changed("name", "colorIndex")}) BEGIN $TICK $profile END",
        )

        val source = queue("'${SyncKind.EPG_SOURCE}'", "NEW.`source_key`")
        createTrigger(
            db,
            "sync_epg_source_insert",
            "CREATE TRIGGER `sync_epg_source_insert` AFTER INSERT ON `epg_source` " +
                "WHEN $NOT_APPLYING BEGIN $TICK $source END",
        )
        createTrigger(
            db,
            "sync_epg_source_update",
            "CREATE TRIGGER `sync_epg_source_update` AFTER UPDATE ON `epg_source` " +
                "WHEN $NOT_APPLYING AND (${changed("url", "label", "timezone_offset_hours", "enabled", "provider_id")}) " +
                "BEGIN $TICK $source END",
        )
        createTrigger(
            db,
            "sync_epg_source_delete",
            "CREATE TRIGGER `sync_epg_source_delete` AFTER DELETE ON `epg_source` " +
                "WHEN $NOT_APPLYING BEGIN " +
                "DELETE FROM `sync_tombstone` WHERE `kind` = '${SyncKind.EPG_SOURCE}' AND `itemKey` = OLD.`source_key`; " +
                "INSERT INTO `sync_tombstone` (`kind`, `itemKey`, `deletedAt`) " +
                "VALUES ('${SyncKind.EPG_SOURCE}', OLD.`source_key`, 0); END",
        )

        createTrigger(
            db,
            "sync_tombstone_insert",
            "CREATE TRIGGER `sync_tombstone_insert` AFTER INSERT ON `sync_tombstone` " +
                "WHEN $NOT_APPLYING BEGIN $TICK " +
                "UPDATE `sync_tombstone` SET `deletedAt` = $CLOCK WHERE NEW.`deletedAt` = 0 " +
                "AND `kind` = NEW.`kind` AND `itemKey` = NEW.`itemKey`; " +
                "${queue("NEW.`kind`", "NEW.`itemKey`")} END",
        )
    }
}
