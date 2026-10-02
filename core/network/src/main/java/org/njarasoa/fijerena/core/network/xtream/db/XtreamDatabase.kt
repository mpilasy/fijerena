package org.njarasoa.fijerena.core.network.xtream.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.execPragma

@Database(
    entities = [
        XtreamCategoryEntity::class,
        XtreamStreamEntity::class,
        XtreamSeriesEntity::class,
        XtreamEpisodeEntity::class,
        XtreamStreamFts::class,
        XtreamSeriesFts::class,
        XtreamEpgCacheEntity::class,
        WatchStateEntity::class,
        FavoriteStateEntity::class,
        SyncTombstoneEntity::class,
        SyncVersionEntity::class,
        SyncClockEntity::class,
    ],
    version = XtreamDatabase.DB_VERSION,
    exportSchema = true,
)
abstract class XtreamDatabase : RoomDatabase() {
    abstract fun categoryDao(): XtreamCategoryDao

    abstract fun streamDao(): XtreamStreamDao

    abstract fun seriesDao(): XtreamSeriesDao

    abstract fun episodeDao(): XtreamEpisodeDao

    abstract fun epgCacheDao(): XtreamEpgCacheDao

    abstract fun watchStateDao(): WatchStateDao

    abstract fun favoriteStateDao(): FavoriteStateDao

    abstract fun syncTombstoneDao(): SyncTombstoneDao

    abstract fun syncVersionDao(): SyncVersionDao

    companion object {
        @Volatile
        private var INSTANCE: XtreamDatabase? = null

        /** Migration 7→8: remove AI vector embedding tables. */
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `xtream_category_vectors`")
                    db.execSQL("DROP TABLE IF EXISTS `xtream_stream_vectors`")
                    db.execSQL("DROP TABLE IF EXISTS `xtream_series_vectors`")
                    db.execSQL("DROP TABLE IF EXISTS `xtream_episode_vectors`")
                }
            }

        /** Migration 8→9: add richer episode metadata columns. */
        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `xtream_episodes` ADD COLUMN `plot` TEXT")
                    db.execSQL("ALTER TABLE `xtream_episodes` ADD COLUMN `airDate` TEXT")
                    db.execSQL("ALTER TABLE `xtream_episodes` ADD COLUMN `durationSecs` INTEGER")
                    db.execSQL("ALTER TABLE `xtream_episodes` ADD COLUMN `bitrate` INTEGER")
                    db.execSQL("ALTER TABLE `xtream_episodes` ADD COLUMN `tmdbId` TEXT")
                }
            }

        /** Migration 9→10: add FTS search virtual tables for streams and series. */
        private val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_streams_fts` USING fts4(content=`xtream_streams`, tokenize=unicode61, `name`)")
                    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_series_fts`  USING fts4(content=`xtream_series`,  tokenize=unicode61, `name`)")
                    db.execSQL("INSERT INTO `xtream_streams_fts`(`xtream_streams_fts`) VALUES('rebuild')")
                    db.execSQL("INSERT INTO `xtream_series_fts`(`xtream_series_fts`)   VALUES('rebuild')")
                }
            }

        /** Migration 10→11: add `excluded` flag for category-filter exclusion. */
        private val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `xtream_categories` ADD COLUMN `excluded` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `xtream_streams` ADD COLUMN `excluded` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `xtream_series` ADD COLUMN `excluded` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_xtream_categories_providerId_type_excluded` ON `xtream_categories` (`providerId`, `type`, `excluded`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_xtream_streams_providerId_type_categoryId_excluded` ON `xtream_streams` (`providerId`, `type`, `categoryId`, `excluded`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_xtream_series_providerId_categoryId_excluded` ON `xtream_series` (`providerId`, `categoryId`, `excluded`)")
                }
            }

        /** Migration 11→12: persist TMDB-derived detail fields (content rating, tmdbId) so movie/series detail screens don't need TMDB/Xtream detail calls on a warm reopen, even after a process restart. */
        private val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `xtream_streams` ADD COLUMN `contentRating` TEXT")
                    db.execSQL("ALTER TABLE `xtream_streams` ADD COLUMN `tmdbId` TEXT")
                    db.execSQL("ALTER TABLE `xtream_streams` ADD COLUMN `containerExtension` TEXT")
                    db.execSQL("ALTER TABLE `xtream_streams` ADD COLUMN `detailFetchedAt` INTEGER")
                    db.execSQL("ALTER TABLE `xtream_series` ADD COLUMN `contentRating` TEXT")
                    db.execSQL("ALTER TABLE `xtream_series` ADD COLUMN `tmdbId` TEXT")
                    db.execSQL("ALTER TABLE `xtream_series` ADD COLUMN `detailFetchedAt` INTEGER")
                }
            }

        /** Migration 12→13: move the per-stream EPG payload cache out of SharedPreferences. */
        private val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `xtream_epg_cache` (" +
                            "`providerId` INTEGER NOT NULL, `streamId` INTEGER NOT NULL, " +
                            "`payload` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`providerId`, `streamId`))",
                    )
                }
            }

        /** Migration 13→14: remember when a TMDB episode synopsis was stored, so it can expire. */
        private val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `xtream_episodes` ADD COLUMN `plotFetchedAt` INTEGER")
                }
            }

        /**
         * Migration 14→15: durable watch state (position + completion), replacing the
         * `watch_history_v3` SharedPreferences blob that truncated on every write.
         * See `docs/plans/20260828_watch-state-durable-storage-plan.md`. Nothing reads or writes this table
         * yet — it ships dark until Phase 2.
         */
        /**
         * Migration 15→16: durable favourites, replacing the `favorites_v2` and
         * `favorite_categories` SharedPreferences blobs that truncated at
         * `providerSettings.favoritesMaxSize` on every write.
         * See `docs/plans/20260828_favorites-durable-storage-plan.md`.
         */
        private val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `favorite_state` (" +
                            "`providerId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `contentType` TEXT NOT NULL, " +
                            "`kind` TEXT NOT NULL, `name` TEXT NOT NULL, `parentCategoryId` TEXT, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`providerId`, `itemId`, `contentType`, `kind`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_favorite_state_providerId_kind_contentType_createdAt` " +
                            "ON `favorite_state` (`providerId`, `kind`, `contentType`, `createdAt`)",
                    )
                }
            }

        private val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `watch_state` (" +
                            "`providerId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `contentType` TEXT NOT NULL, " +
                            "`itemName` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, " +
                            "`durationMs` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                            "`lastPlayedAt` INTEGER, `seriesId` TEXT, `episodeId` TEXT, `seriesName` TEXT, " +
                            "`episodeExtension` TEXT, `audioTrackIndex` INTEGER, `subtitleTrackIndex` INTEGER, " +
                            "PRIMARY KEY(`providerId`, `itemId`, `contentType`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_watch_state_providerId_contentType_lastPlayedAt` " +
                            "ON `watch_state` (`providerId`, `contentType`, `lastPlayedAt`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_watch_state_providerId_seriesId` " +
                            "ON `watch_state` (`providerId`, `seriesId`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_xtream_streams_providerId_tmdbId` " +
                            "ON `xtream_streams` (`providerId`, `tmdbId`)",
                    )
                }
            }

        /**
         * Migration 16→17: add `posterPath` column to `xtream_streams` and `xtream_series` for TMDB poster caching.
         */
        private val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `xtream_streams` ADD COLUMN `posterPath` TEXT")
                    db.execSQL("ALTER TABLE `xtream_series` ADD COLUMN `posterPath` TEXT")
                }
            }

        /**
         * Migration 17→18: composite indices for series/episode sibling lookups by tmdbId
         * (`xtream_series`) and (season, episodeNum) (`xtream_episodes`) — see the entities'
         * kdoc. Names match Room's default `index_<table>_<col1>_<col2>...` convention exactly,
         * or Room's schema validation flags them as unexpected on the next open.
         */
        // internal, not private: exercised directly by XtreamDatabaseMigrationTest (androidTest) —
        // MigrationTestHelper needs the actual Migration object, not a re-implementation of it.
        internal val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_xtream_series_providerId_tmdbId` " +
                            "ON `xtream_series` (`providerId`, `tmdbId`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_xtream_episodes_providerId_season_episodeNum` " +
                            "ON `xtream_episodes` (`providerId`, `season`, `episodeNum`)",
                    )
                }
            }

        /**
         * Migration 18→19: `episodesFetchedAt` on `xtream_series` — the persisted freshness stamp
         * behind the 24h episode-list cache (see [XtreamSeriesEntity.episodesFetchedAt]).
         */
        private val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `xtream_series` ADD COLUMN `episodesFetchedAt` INTEGER")
                }
            }

        /**
         * Migration 19→20: `profileId` on `watch_state` and `favorite_state`, part of each
         * primary key. SQLite can't alter a primary key, so both tables are rebuilt: create the
         * new shape, copy every row across assigned to the Default profile, drop the old table,
         * rename. Indices are recreated with `profileId` after `providerId`.
         * See `docs/plans/20260929_live-sync-plan.md` → User profiles.
         *
         * Neither table is re-fetchable from any server, and this database falls back to
         * destructive migration — a schema mismatch here wipes them. Every CREATE statement below
         * must match Room's generated schema for the entities exactly.
         */
        // internal, not private: exercised directly by XtreamDatabaseMigrationTest (androidTest).
        internal val MIGRATION_19_20 =
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val defaultProfile = ProfileEntity.DEFAULT_ID
                    val watchColumns =
                        "`itemId`, `contentType`, `itemName`, `categoryId`, `positionMs`, `durationMs`, " +
                            "`isCompleted`, `updatedAt`, `lastPlayedAt`, `seriesId`, `episodeId`, `seriesName`, " +
                            "`episodeExtension`, `audioTrackIndex`, `subtitleTrackIndex`"
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `watch_state_new` (" +
                            "`providerId` INTEGER NOT NULL, `profileId` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                            "`contentType` TEXT NOT NULL, `itemName` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                            "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, `lastPlayedAt` INTEGER, `seriesId` TEXT, `episodeId` TEXT, " +
                            "`seriesName` TEXT, `episodeExtension` TEXT, `audioTrackIndex` INTEGER, " +
                            "`subtitleTrackIndex` INTEGER, " +
                            "PRIMARY KEY(`providerId`, `profileId`, `itemId`, `contentType`))",
                    )
                    db.execSQL(
                        "INSERT INTO `watch_state_new` (`providerId`, `profileId`, $watchColumns) " +
                            "SELECT `providerId`, ?, $watchColumns FROM `watch_state`",
                        arrayOf<Any>(defaultProfile),
                    )
                    db.execSQL("DROP TABLE `watch_state`")
                    db.execSQL("ALTER TABLE `watch_state_new` RENAME TO `watch_state`")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_watch_state_providerId_profileId_contentType_lastPlayedAt` " +
                            "ON `watch_state` (`providerId`, `profileId`, `contentType`, `lastPlayedAt`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_watch_state_providerId_profileId_seriesId` " +
                            "ON `watch_state` (`providerId`, `profileId`, `seriesId`)",
                    )

                    val favoriteColumns = "`itemId`, `contentType`, `kind`, `name`, `parentCategoryId`, `createdAt`"
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `favorite_state_new` (" +
                            "`providerId` INTEGER NOT NULL, `profileId` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                            "`contentType` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                            "`parentCategoryId` TEXT, `createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`providerId`, `profileId`, `itemId`, `contentType`, `kind`))",
                    )
                    db.execSQL(
                        "INSERT INTO `favorite_state_new` (`providerId`, `profileId`, $favoriteColumns) " +
                            "SELECT `providerId`, ?, $favoriteColumns FROM `favorite_state`",
                        arrayOf<Any>(defaultProfile),
                    )
                    db.execSQL("DROP TABLE `favorite_state`")
                    db.execSQL("ALTER TABLE `favorite_state_new` RENAME TO `favorite_state`")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_favorite_state_providerId_profileId_kind_contentType_createdAt` " +
                            "ON `favorite_state` (`providerId`, `profileId`, `kind`, `contentType`, `createdAt`)",
                    )
                }
            }

        /**
         * Migration 20→21, live sync phase 3: the `sync_tombstone` table for favourite and
         * watch-history deletions. See `docs/plans/20260929_live-sync-plan.md` → Deletions.
         */
        // internal, not private: exercised directly by XtreamDatabaseMigrationTest (androidTest).
        internal val MIGRATION_20_21 =
            object : Migration(20, 21) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `sync_tombstone` (`providerId` INTEGER NOT NULL, " +
                            "`profileId` TEXT NOT NULL, `kind` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                            "`contentType` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`providerId`, `profileId`, `kind`, `itemId`, `contentType`))",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_tombstone_deletedAt` ON `sync_tombstone` (`deletedAt`)")
                }
            }

        /**
         * Migration 21→22, live sync phase 4: `sync_outbox` (keys changed locally, waiting to be
         * sent) and `sync_clock` (this database's hybrid logical clock). The triggers that fill
         * them are installed on open — see [XtreamSyncTriggers].
         */
        // internal, not private: exercised directly by XtreamDatabaseMigrationTest (androidTest).
        internal val MIGRATION_21_22 =
            object : Migration(21, 22) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `sync_outbox` (`providerId` INTEGER NOT NULL, " +
                            "`profileId` TEXT NOT NULL, `kind` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                            "`contentType` TEXT NOT NULL, `hlc` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`providerId`, `profileId`, `kind`, `itemId`, `contentType`))",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_hlc` ON `sync_outbox` (`hlc`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `sync_clock` (`id` INTEGER NOT NULL, `hlc` INTEGER NOT NULL, " +
                            "`applying` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    )
                }
            }

        /**
         * Migration 22→23, live sync phase 5: `sync_outbox` becomes `sync_version` — entries are
         * kept after sending, as the version the merge compares against, with `pending` marking
         * the ones still to send. Existing entries were never sent, so they stay pending.
         */
        // internal, not private: exercised directly by XtreamDatabaseMigrationTest (androidTest).
        internal val MIGRATION_22_23 =
            object : Migration(22, 23) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `sync_version` (`providerId` INTEGER NOT NULL, " +
                            "`profileId` TEXT NOT NULL, `kind` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                            "`contentType` TEXT NOT NULL, `hlc` INTEGER NOT NULL, `pending` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`providerId`, `profileId`, `kind`, `itemId`, `contentType`))",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_version_pending_hlc` ON `sync_version` (`pending`, `hlc`)")
                    db.execSQL(
                        "INSERT INTO `sync_version` SELECT `providerId`, `profileId`, `kind`, `itemId`, `contentType`, `hlc`, 1 " +
                            "FROM `sync_outbox`",
                    )
                    db.execSQL("DROP TABLE `sync_outbox`")
                }
            }

        /**
         * Migration 23→24: streams and series follow their category's `excluded` flag at query
         * time, so a profile switch no longer rewrites the catalogue (see
         * docs/plans/20261001_fast-profile-switch-plan.md). Their own `excluded` columns stay,
         * unused; only the indexes on them go.
         */
        // internal, not private: exercised directly by XtreamDatabaseMigrationTest (androidTest).
        internal val MIGRATION_23_24 =
            object : Migration(23, 24) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP INDEX IF EXISTS `index_xtream_streams_providerId_type_categoryId_excluded`")
                    db.execSQL("DROP INDEX IF EXISTS `index_xtream_series_providerId_categoryId_excluded`")
                }
            }

        const val DB_VERSION = 24
        private const val DB_NAME = "xtream_v2.db"

        // Versions before MIGRATION_7_8 held nothing but rebuildable catalogue — the only jump
        // that may still drop tables. Every later version has user data (watch_state since v15,
        // favorite_state since v16).
        private val PRE_USER_DATA_VERSIONS = intArrayOf(1, 2, 3, 4, 5, 6)

        /**
         * A file written by a newer build (an older APK installed over a newer one) has no
         * migration path down. Room would either crash on open every launch or, with the blanket
         * destructive fallback this used to have, silently drop every table — watch history and
         * favourites included. Set the newer file aside instead (only the latest one is kept) and
         * start fresh; installing the newer build again and restoring the `.bak` set recovers it.
         * See docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-20.
         */
        private fun setAsideIfNewer(context: Context) {
            val file = context.getDatabasePath(DB_NAME)
            val fileVersion =
                if (file.exists()) {
                    try {
                        android.database.sqlite.SQLiteDatabase
                            .openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                            .use { it.version }
                    } catch (e: Exception) {
                        android.util.Log.w("XtreamDatabase", "Couldn't read the version of $DB_NAME", e)
                        0
                    }
                } else {
                    0
                }
            if (fileVersion > DB_VERSION) {
                val backupBase = "$DB_NAME.v$fileVersion.bak"
                file.parentFile?.listFiles { f -> f.name.startsWith("$DB_NAME.v") && f.name.contains(".bak") }?.forEach { it.delete() }
                listOf("", "-wal", "-shm").forEach { suffix ->
                    val part = java.io.File(file.path + suffix)
                    if (part.exists()) part.renameTo(java.io.File(file.parentFile, backupBase + suffix))
                }
                val notice = IllegalStateException("$DB_NAME is v$fileVersion, newer than this build's v$DB_VERSION: moved to $backupBase, starting empty")
                android.util.Log.e("XtreamDatabase", notice.message, notice)
                org.njarasoa.fijerena.core.player.diagnostics.CrashLog.record("database downgrade", notice)
            }
        }

        fun getInstance(context: Context): XtreamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    setAsideIfNewer(context.applicationContext)
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            XtreamDatabase::class.java,
                            DB_NAME,
                        ).addMigrations(
                            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                            MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                            MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                            MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
                        )
                        .fallbackToDestructiveMigrationFrom(dropAllTables = true, *PRE_USER_DATA_VERSIONS)
                        // Explicit rather than relying on JournalMode.AUTOMATIC's default: AUTOMATIC
                        // silently falls back to TRUNCATE (readers block on writes) on any device
                        // ActivityManager reports as low-RAM, which several of this app's actual
                        // Android TV targets plausibly are.
                        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                        .addCallback(
                            object : RoomDatabase.Callback() {
                                override fun onOpen(db: SupportSQLiteDatabase) {
                                    // NORMAL is safe under WAL (only FULL protects against an OS
                                    // crash, not an app crash, and this is WAL — see SQLite docs) and
                                    // avoids an fsync on every transaction. journal_size_limit caps
                                    // how large the WAL file is allowed to grow before SQLite
                                    // truncates it back down after a checkpoint, instead of the file
                                    // growing unbounded across this catalogue's frequent syncs.
                                    try {
                                        db.execSQL("PRAGMA synchronous = NORMAL")
                                        db.execPragma("PRAGMA journal_size_limit = 10485760") // 10MB
                                    } catch (e: Exception) {
                                        android.util.Log.w("XtreamDatabase", "Failed to run DB maintenance", e)
                                    }
                                    // Not in the try: without the triggers, changes would silently
                                    // never sync. A failure here must surface.
                                    XtreamSyncTriggers.install(db)
                                }
                            },
                        )
                        .build()
                        .also { INSTANCE = it }
                }
            }
    }
}
