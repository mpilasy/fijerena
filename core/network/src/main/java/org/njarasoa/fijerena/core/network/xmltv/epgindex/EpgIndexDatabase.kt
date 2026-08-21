package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

@Database(
    entities = [
        EpgChannelEntity::class,
        EpgChannelStagingEntity::class,
        EpgProgrammeEntity::class,
        EpgProgrammeStagingEntity::class,
        EpgProgrammeFts::class,
        EpgIndexMetadata::class,
    ],
    version = 16,
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
                INSTANCE ?: run {
                    seedPageSize(context)
                    buildDatabase(context)
                }.also { INSTANCE = it }
            }

        /**
         * Creates the database file with a 4 KB page size before Room can put a table in it.
         *
         * Page size is frozen by the first `CREATE TABLE` and, on a WAL database, not even
         * `VACUUM` will change it afterwards — so the only opportunity is while the file does not
         * exist yet. Requery hardcodes 1024 (its `SQLiteGlobal.getDefaultPageSize()` measures the
         * filesystem block size and then returns a literal), and applies it per connection; that
         * application is a no-op here because this seeds a header first. Creating and dropping a
         * throwaway table is what forces the header to be written.
         *
         * Existing files are left alone: nothing here can migrate them, only a delete-and-rebuild.
         */
        private fun seedPageSize(context: Context) {
            val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
            if (dbFile.exists()) return
            try {
                dbFile.parentFile?.mkdirs()
                // Framework SQLite, deliberately, not Requery: Requery forces its hardcoded 1024
                // onto every connection it opens, so seeding through it produces the very file we
                // are trying to avoid. The framework honours the requested size — which is why
                // Room's other databases are already on 4 KB pages.
                android.database.sqlite.SQLiteDatabase
                    .openOrCreateDatabase(dbFile.path, null)
                    .use { raw ->
                        raw.execSQL("PRAGMA page_size = 4096")
                        raw.execSQL("CREATE TABLE _page_size_seed (x INTEGER)")
                        raw.execSQL("DROP TABLE _page_size_seed")
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Could not seed page size — index will use the SQLite default", e)
            }
        }

        private fun buildDatabase(context: Context): EpgIndexDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    EpgIndexDatabase::class.java,
                    DB_NAME,
                ).openHelperFactory(CreationPragmaFactory(RequerySQLiteOpenHelperFactory()))
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration(true)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            try {
                                // Optimize for performance - run directly on the opened connection
                                // page_size and auto_vacuum are not here: both are only settable
                                // before the first table exists, and this callback runs after Room
                                // has created the schema. See [CreationPragmaFactory].
                                db.execSQL("PRAGMA synchronous = NORMAL")
                                db.execSQL("PRAGMA cache_size = -64000") // 64MB cache
                                db.execSQL("PRAGMA temp_store = MEMORY")
                                // journal_size_limit echoes its new value — Requery rejects execSQL
                                // for any statement that produces rows, so it goes through
                                // execPragma, which steps the cursor so the statement actually runs.
                                db.execPragma("PRAGMA journal_size_limit = 10485760")
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

/**
 * Turns on incremental auto-vacuum before the schema exists.
 *
 * `auto_vacuum` can only be enabled on a database with no tables in it; afterwards it takes a full
 * `VACUUM` to change. Room's [RoomDatabase.Callback.onOpen] fires *after* it has created the
 * schema, so setting it there is too late — `onConfigure` runs before Room touches the file.
 *
 * `page_size` belongs here too and is deliberately absent: Requery's `SQLiteGlobal` computes the
 * filesystem block size, discards it, and returns a hardcoded 1024, which it applies per
 * connection before any callback of ours runs. Every Requery-backed database is therefore on 1 KB
 * pages, and no setting from here can change that — only `PRAGMA page_size` followed by a one-time
 * `VACUUM`, after which Requery's re-application is a no-op on the now-populated file.
 */
private class CreationPragmaFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val inner = configuration.callback
        val callback =
            object : SupportSQLiteOpenHelper.Callback(inner.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.execPragma("PRAGMA auto_vacuum = INCREMENTAL")
                    inner.onConfigure(db)
                }

                override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    inner.onUpgrade(db, oldVersion, newVersion)

                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    inner.onDowngrade(db, oldVersion, newVersion)

                override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)

                override fun onCorruption(db: SupportSQLiteDatabase) = inner.onCorruption(db)
            }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration
                .builder(configuration.context)
                .name(configuration.name)
                .callback(callback)
                .build(),
        )
    }
}
