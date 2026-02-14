package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        EpgProgrammeFts::class,
        EpgIndexMetadata::class,
        EpgSourceEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class EpgIndexDatabase : RoomDatabase() {

    abstract fun epgIndexDao(): EpgIndexDao
    abstract fun epgSourceDao(): EpgSourceDao

    companion object {
        private const val TAG = "EpgIndexDatabase"
        private const val DB_NAME = "epg_index.db"

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
