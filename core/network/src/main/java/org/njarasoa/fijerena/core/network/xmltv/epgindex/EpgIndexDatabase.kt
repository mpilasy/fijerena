package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        EpgProgrammeFts::class,
        EpgIndexMetadata::class,
        EpgSourceEntity::class
    ],
    version = 8,
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
                .addMigrations(MIGRATION_7_8)
                .fallbackToDestructiveMigration(true)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        val cursor = db.query("PRAGMA auto_vacuum")
                        val currentMode = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                        cursor.close()
                        if (currentMode == 2) return
                        Log.d(TAG, "Enabling incremental auto_vacuum (current mode=$currentMode)")
                        db.execSQL("PRAGMA auto_vacuum = INCREMENTAL")
                        db.execSQL("VACUUM")
                        Log.d(TAG, "One-time VACUUM complete, auto_vacuum now INCREMENTAL")
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
