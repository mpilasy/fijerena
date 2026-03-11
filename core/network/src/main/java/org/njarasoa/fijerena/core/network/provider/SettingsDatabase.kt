package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProviderEntity::class, EpgSourceEntity::class],
    version = 4,
    exportSchema = false
)
abstract class SettingsDatabase : RoomDatabase() {

    abstract fun providerDao(): ProviderDao
    abstract fun epgSourceDao(): EpgSourceDao

    companion object {
        private const val DB_NAME = "providers.db"

        @Volatile
        private var INSTANCE: SettingsDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE providers ADD COLUMN type TEXT NOT NULL DEFAULT 'XTREAM'")
                db.execSQL("ALTER TABLE providers ADD COLUMN config TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE providers ADD COLUMN providerSettings TEXT NOT NULL DEFAULT '{}'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `epg_source` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `url` TEXT NOT NULL, 
                        `label` TEXT NOT NULL, 
                        `timezone_offset_hours` INTEGER NOT NULL, 
                        `added_at_ms` INTEGER NOT NULL, 
                        `last_ingested_at_ms` INTEGER NOT NULL, 
                        `last_error` TEXT, 
                        `enabled` INTEGER NOT NULL DEFAULT 1, 
                        `last_channels` INTEGER NOT NULL DEFAULT 0, 
                        `last_programmes` INTEGER NOT NULL DEFAULT 0, 
                        `last_download_bytes` INTEGER NOT NULL DEFAULT 0, 
                        `ingest_method` TEXT NOT NULL DEFAULT 'DOWNLOADED', 
                        `last_ingestion_duration_ms` INTEGER NOT NULL DEFAULT 0, 
                        `last_download_duration_ms` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): SettingsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SettingsDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { INSTANCE = it }
            }
        }
    }
}
