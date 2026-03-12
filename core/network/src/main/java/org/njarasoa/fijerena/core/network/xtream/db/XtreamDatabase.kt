package org.njarasoa.fijerena.core.network.xtream.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        XtreamCategoryEntity::class,
        XtreamStreamEntity::class,
        XtreamSeriesEntity::class,
        XtreamEpisodeEntity::class,
        XtreamCategoryVectorEntity::class,
        XtreamStreamVectorEntity::class,
        XtreamSeriesVectorEntity::class,
        XtreamEpisodeVectorEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class XtreamDatabase : RoomDatabase() {
    abstract fun categoryDao(): XtreamCategoryDao
    abstract fun streamDao(): XtreamStreamDao
    abstract fun seriesDao(): XtreamSeriesDao
    abstract fun episodeDao(): XtreamEpisodeDao

    companion object {
        @Volatile
        private var INSTANCE: XtreamDatabase? = null

        fun getInstance(context: Context): XtreamDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    XtreamDatabase::class.java,
                    "xtream_v2.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                .build().also { INSTANCE = it }
            }
        }
    }
}
