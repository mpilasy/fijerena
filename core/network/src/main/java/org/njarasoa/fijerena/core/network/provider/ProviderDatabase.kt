package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProviderEntity::class], version = 3, exportSchema = false)
abstract class ProviderDatabase : RoomDatabase() {

    abstract fun providerDao(): ProviderDao

    companion object {
        @Volatile
        private var INSTANCE: ProviderDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE providers ADD COLUMN type TEXT NOT NULL DEFAULT 'XTREAM'")
                db.execSQL("ALTER TABLE providers ADD COLUMN config TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add providerSettings column for per-provider settings (JSON blob)
                db.execSQL("ALTER TABLE providers ADD COLUMN providerSettings TEXT NOT NULL DEFAULT '{}'")
            }
        }

        fun getInstance(context: Context): ProviderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProviderDatabase::class.java,
                    "providers.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { INSTANCE = it }
            }
        }
    }
}
