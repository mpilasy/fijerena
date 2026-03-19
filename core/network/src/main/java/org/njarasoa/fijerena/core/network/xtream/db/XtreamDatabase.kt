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
    ],
    version = 8,
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

        fun getInstance(context: Context): XtreamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        XtreamDatabase::class.java,
                        "xtream_v2.db",
                    ).addMigrations(MIGRATION_7_8)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
