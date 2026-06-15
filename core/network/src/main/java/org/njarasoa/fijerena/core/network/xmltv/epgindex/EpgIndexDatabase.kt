package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

@Database(
    entities = [
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        EpgProgrammeFts::class,
        EpgIndexMetadata::class,
    ],
    version = 15,
    exportSchema = false,
)
abstract class EpgIndexDatabase : RoomDatabase() {
    abstract fun epgIndexDao(): EpgIndexDao

    companion object {
        private const val TAG = "EpgIndexDatabase"
        private const val DB_NAME = "epg_index.db"

        // FTS4 -> FTS5 migration is complex via raw SQL because of Room's internal validation.
        // We rely on fallbackToDestructiveMigration(true) for this jump to ensure a clean schema.

        @Volatile
        private var INSTANCE: EpgIndexDatabase? = null

        fun getInstance(context: Context): EpgIndexDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): EpgIndexDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    EpgIndexDatabase::class.java,
                    DB_NAME,
                ).openHelperFactory(RequerySQLiteOpenHelperFactory())
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration(true)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            try {
                                // Optimize for performance - run directly on the opened connection
                                db.execSQL("PRAGMA synchronous = NORMAL")
                                db.execSQL("PRAGMA cache_size = -64000") // 64MB cache
                                db.execSQL("PRAGMA page_size = 4096")
                                db.execSQL("PRAGMA temp_store = MEMORY")
                                // mmap_size and journal_size_limit echo their new value — Requery
                                // rejects execSQL for any statement that produces rows, so use query().
                                db.query("PRAGMA mmap_size = 268435456").close()
                                db.query("PRAGMA journal_size_limit = 10485760").close()

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
                    },
                ).build()

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
