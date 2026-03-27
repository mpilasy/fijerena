package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProviderEntity::class, EpgSourceEntity::class, EpgPipelineStatsEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class SettingsDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao

    abstract fun epgSourceDao(): EpgSourceDao

    abstract fun epgPipelineStatsDao(): EpgPipelineStatsDao

    companion object {
        private const val DB_NAME = "providers.db"

        @Volatile
        private var INSTANCE: SettingsDatabase? = null

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE providers ADD COLUMN type TEXT NOT NULL DEFAULT 'XTREAM'")
                    db.execSQL("ALTER TABLE providers ADD COLUMN config TEXT NOT NULL DEFAULT ''")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE providers ADD COLUMN providerSettings TEXT NOT NULL DEFAULT '{}'")
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
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
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `epg_pipeline_stats` (
                            `id` INTEGER PRIMARY KEY NOT NULL, 
                            `updated_at_ms` INTEGER NOT NULL, 
                            `duration_ms` INTEGER NOT NULL, 
                            `sources_processed` INTEGER NOT NULL, 
                            `errors` INTEGER NOT NULL, 
                            `total_channels` INTEGER NOT NULL, 
                            `total_programmes` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE providers ADD COLUMN lastSyncedAtMs INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE providers ADD COLUMN lastSyncDurationMs INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE providers ADD COLUMN lastSyncError TEXT")
                }
            }

        fun getInstance(context: Context): SettingsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        SettingsDatabase::class.java,
                        DB_NAME,
                    ).addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                    ).build()
                    .also { INSTANCE = it }
            }
    }
}
