package org.njarasoa.fijerena.core.network.xtream.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.njarasoa.fijerena.core.network.sync.SyncKind

/**
 * Queues every local change to favourites, watch state, the Wanted list, followed teams and leagues
 * and deletions for live sync, from inside SQLite: triggers run in the writing transaction, so
 * nothing changes without being queued, and they catch every write path (a TMDB group completion
 * that updates sibling rows too) with no call site to remember. See the live sync plan → Flow.
 *
 * Each trigger advances [SyncClockEntity.hlc] and records the key in [SyncVersionEntity] at that
 * value, pending. A new [SyncTombstoneEntity] with `deletedAt = 0` is stamped with the clock too.
 * Nothing fires while [SyncClockEntity.applying] is set. Deleting rows fires nothing: a deletion is
 * queued through its tombstone, and rows removed with their provider or profile are covered by
 * that one's own tombstone.
 *
 * Dropped and recreated on every open, so fresh installs, migrations and destructive rebuilds all
 * end up with them, and an app update that changes one replaces the old definition. Room doesn't
 * manage triggers.
 */
internal object XtreamSyncTriggers {
    // Wall clock in ms from SQLite itself; julianday keeps sub-second precision.
    private const val WALL_MS = "CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER)"
    private const val NOT_APPLYING = "(SELECT `applying` FROM `sync_clock` WHERE `id` = ${SyncClockEntity.SINGLE_ROW}) = 0"
    private const val TICK = "UPDATE `sync_clock` SET `hlc` = MAX($WALL_MS, `hlc` + 1) WHERE `id` = ${SyncClockEntity.SINGLE_ROW};"
    private const val CLOCK = "(SELECT `hlc` FROM `sync_clock` WHERE `id` = ${SyncClockEntity.SINGLE_ROW})"

    // Delete-then-insert, never INSERT OR REPLACE: inside a trigger SQLite applies the conflict
    // rule of the statement that fired it, and Room's @Insert/@Update run as OR ABORT — a REPLACE
    // here would abort the user's write the second time the same key is queued.
    private fun queue(
        kind: String,
        row: String,
    ) = "DELETE FROM `sync_version` WHERE `providerId` = $row.`providerId` AND `profileId` = $row.`profileId` " +
        "AND `kind` = $kind AND `itemId` = $row.`itemId` AND `contentType` = $row.`contentType`; " +
        "INSERT INTO `sync_version` (`providerId`, `profileId`, `kind`, `itemId`, `contentType`, `hlc`, `pending`) " +
        "VALUES ($row.`providerId`, $row.`profileId`, $kind, $row.`itemId`, $row.`contentType`, $CLOCK, 1);"

    // A Wanted row has no provider and keys itself by TMDB id: see SyncKind.WANTED.
    private val wanted =
        "DELETE FROM `sync_version` WHERE `providerId` = ${SyncKind.WANTED_PROVIDER_ID} AND `profileId` = NEW.`profileId` " +
            "AND `kind` = '${SyncKind.WANTED}' AND `itemId` = NEW.`tmdbId` AND `contentType` = NEW.`contentType`; " +
            "INSERT INTO `sync_version` (`providerId`, `profileId`, `kind`, `itemId`, `contentType`, `hlc`, `pending`) " +
            "VALUES (${SyncKind.WANTED_PROVIDER_ID}, NEW.`profileId`, '${SyncKind.WANTED}', NEW.`tmdbId`, NEW.`contentType`, $CLOCK, 1);"

    // A followed team or league has no provider either; its kind and source share the content
    // type slot: see SyncKind.FOLLOWED_SPORT and FollowedSportEntity.syncContentType.
    private const val FOLLOWED_CONTENT_TYPE = "NEW.`kind` || ':' || NEW.`source`"
    private val followedSport =
        "DELETE FROM `sync_version` WHERE `providerId` = ${SyncKind.FOLLOWED_SPORT_PROVIDER_ID} AND `profileId` = NEW.`profileId` " +
            "AND `kind` = '${SyncKind.FOLLOWED_SPORT}' AND `itemId` = NEW.`itemId` AND `contentType` = $FOLLOWED_CONTENT_TYPE; " +
            "INSERT INTO `sync_version` (`providerId`, `profileId`, `kind`, `itemId`, `contentType`, `hlc`, `pending`) " +
            "VALUES (${SyncKind.FOLLOWED_SPORT_PROVIDER_ID}, NEW.`profileId`, '${SyncKind.FOLLOWED_SPORT}', NEW.`itemId`, " +
            "$FOLLOWED_CONTENT_TYPE, $CLOCK, 1);"

    private val favoriteKind =
        "CASE NEW.`kind` WHEN '${FavoriteKind.CATEGORY}' THEN '${SyncKind.FAVORITE_CATEGORY}' ELSE '${SyncKind.FAVORITE_STREAM}' END"

    private fun createTrigger(
        db: SupportSQLiteDatabase,
        name: String,
        sql: String,
    ) {
        db.execSQL("DROP TRIGGER IF EXISTS `$name`")
        db.execSQL(sql)
    }

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (${SyncClockEntity.SINGLE_ROW}, 0, 0)",
        )
        for (event in listOf("INSERT", "UPDATE")) {
            val suffix = event.lowercase()
            createTrigger(
                db,
                "sync_watch_state_$suffix",
                "CREATE TRIGGER `sync_watch_state_$suffix` AFTER $event ON `watch_state` " +
                    "WHEN $NOT_APPLYING BEGIN $TICK ${queue("'${SyncKind.WATCH}'", "NEW")} END",
            )
            createTrigger(
                db,
                "sync_favorite_state_$suffix",
                "CREATE TRIGGER `sync_favorite_state_$suffix` AFTER $event ON `favorite_state` " +
                    "WHEN $NOT_APPLYING BEGIN $TICK ${queue(favoriteKind, "NEW")} END",
            )
            // An update is queued only when a synced column changes: the release data is each
            // device's own, refreshed from TMDB every week (wishlist UX plan, Phase 3).
            val wantedChanged =
                if (event == "UPDATE") {
                    " AND (" +
                        listOf("title", "originalTitle", "year", "posterPath", "addedAt", "notifiedAt", "seenAt", "askedAt")
                            .joinToString(" OR ") { "OLD.`$it` IS NOT NEW.`$it`" } + ")"
                } else {
                    ""
                }
            createTrigger(
                db,
                "sync_wanted_title_$suffix",
                "CREATE TRIGGER `sync_wanted_title_$suffix` AFTER $event ON `wanted_title` " +
                    "WHEN $NOT_APPLYING$wantedChanged BEGIN $TICK $wanted END",
            )
            createTrigger(
                db,
                "sync_followed_sport_$suffix",
                "CREATE TRIGGER `sync_followed_sport_$suffix` AFTER $event ON `followed_sport` " +
                    "WHEN $NOT_APPLYING BEGIN $TICK $followedSport END",
            )
        }
        createTrigger(
            db,
            "sync_tombstone_insert",
            "CREATE TRIGGER `sync_tombstone_insert` AFTER INSERT ON `sync_tombstone` " +
                "WHEN $NOT_APPLYING BEGIN $TICK " +
                "UPDATE `sync_tombstone` SET `deletedAt` = $CLOCK WHERE NEW.`deletedAt` = 0 " +
                "AND `providerId` = NEW.`providerId` AND `profileId` = NEW.`profileId` AND `kind` = NEW.`kind` " +
                "AND `itemId` = NEW.`itemId` AND `contentType` = NEW.`contentType`; " +
                "${queue("NEW.`kind`", "NEW")} END",
        )
    }
}
