package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        EpgProgrammeFts::class,
        EpgIndexMetadata::class,
        EpgSourceEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class EpgIndexDatabase : RoomDatabase() {

    abstract fun epgIndexDao(): EpgIndexDao
    abstract fun epgSourceDao(): EpgSourceDao

    companion object {
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
