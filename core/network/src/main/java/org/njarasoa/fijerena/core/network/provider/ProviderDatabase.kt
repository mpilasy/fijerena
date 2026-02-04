package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProviderEntity::class], version = 1, exportSchema = false)
abstract class ProviderDatabase : RoomDatabase() {

    abstract fun providerDao(): ProviderDao

    companion object {
        @Volatile
        private var INSTANCE: ProviderDatabase? = null

        fun getInstance(context: Context): ProviderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProviderDatabase::class.java,
                    "providers.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
