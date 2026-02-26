package org.njarasoa.fijerena.core.network.xtream.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        XtreamCategoryEntity::class,
        XtreamStreamEntity::class,
        XtreamSeriesEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class XtreamDatabase : RoomDatabase() {
    abstract fun categoryDao(): XtreamCategoryDao
    abstract fun streamDao(): XtreamStreamDao
    abstract fun seriesDao(): XtreamSeriesDao

    companion object {
        @Volatile
        private var INSTANCE: XtreamDatabase? = null

        fun getInstance(context: Context): XtreamDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    XtreamDatabase::class.java,
                    "xtream_v2.db"
                ).fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
