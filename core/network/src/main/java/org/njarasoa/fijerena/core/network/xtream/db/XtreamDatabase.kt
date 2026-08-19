package org.njarasoa.fijerena.core.network.xtream.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        XtreamCategoryEntity::class,
        XtreamStreamEntity::class,
        XtreamSeriesEntity::class,
        XtreamEpisodeEntity::class,
        XtreamStreamFts::class,
        XtreamSeriesFts::class,
        XtreamEpgCacheEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
abstract class XtreamDatabase : RoomDatabase() {
    abstract fun categoryDao(): XtreamCategoryDao

    abstract fun streamDao(): XtreamStreamDao

    abstract fun seriesDao(): XtreamSeriesDao

    abstract fun episodeDao(): XtreamEpisodeDao

    abstract fun epgCacheDao(): XtreamEpgCacheDao

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

        fun getInstance(context: Context): XtreamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        XtreamDatabase::class.java,
                        "xtream_v2.db",
                    ).addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
