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
    ],
    version = 10,
    exportSchema = false,
)
abstract class XtreamDatabase : RoomDatabase() {
    abstract fun categoryDao(): XtreamCategoryDao

    abstract fun streamDao(): XtreamStreamDao

    abstract fun seriesDao(): XtreamSeriesDao

    abstract fun episodeDao(): XtreamEpisodeDao

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

        fun getInstance(context: Context): XtreamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        XtreamDatabase::class.java,
                        "xtream_v2.db",
                    ).addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
