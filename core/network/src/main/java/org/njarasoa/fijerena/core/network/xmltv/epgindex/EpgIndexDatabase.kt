package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi

@Database(
    entities = [
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        EpgProgrammeFts::class,
        EpgIndexMetadata::class,
        EpgSourceEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class EpgIndexDatabase : RoomDatabase() {

    abstract fun epgIndexDao(): EpgIndexDao
    abstract fun epgSourceDao(): EpgSourceDao

    companion object {
        private const val TAG = "EpgIndexDatabase"
        private const val DB_NAME = "epg_index.db"

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE epg_source ADD COLUMN ingest_method TEXT NOT NULL DEFAULT 'DOWNLOADED'"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE epg_source ADD COLUMN last_ingestion_duration_ms INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE epg_source ADD COLUMN last_download_duration_ms INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // FTS4 -> FTS5 migration is complex via raw SQL because of Room's internal validation.
        // We rely on fallbackToDestructiveMigration(true) for this jump to ensure a clean schema.
        // The MIGRATION_10_11 is intentionally omitted to trigger the fallback.

        @Volatile
        private var INSTANCE: EpgIndexDatabase? = null

        fun getInstance(context: Context): EpgIndexDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): EpgIndexDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                EpgIndexDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(RequerySQLiteOpenHelperFactory())
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration(true)
                .addCallback(object : RoomDatabase.Callback() {
                    @OptIn(DelicateCoroutinesApi::class)
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                // Optimize for performance
                                db.execSQL("PRAGMA synchronous = NORMAL")
                                db.execSQL("PRAGMA cache_size = -8000") // 8MB cache
                                
                                val cursor = db.query("PRAGMA auto_vacuum")
                                val currentMode = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                                cursor.close()
                                if (currentMode != 2) { // 2 = INCREMENTAL
                                    db.execSQL("PRAGMA auto_vacuum = INCREMENTAL")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to run DB maintenance", e)
                            }
                        }
                    }
                })
                .build()
        }

        /**
         * Destroy the database instance and delete the file.
         * Used before re-indexing to start fresh.
         */
        suspend fun destroy(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
            val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
            dbFile.delete()
            // Also delete WAL and SHM files
            java.io.File(dbFile.path + "-wal").delete()
            java.io.File(dbFile.path + "-shm").delete()
        }
    }
}
