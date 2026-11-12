package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
    version = 18,
    exportSchema = false,
)
abstract class EpgIndexDatabase : RoomDatabase() {
    abstract fun epgIndexDao(): EpgIndexDao

    companion object {
        private const val TAG = "EpgIndexDatabase"
        internal const val DB_NAME = "epg_index.db"

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
         * The first `CREATE TABLE` fixes the page size, and on a WAL database not even `VACUUM`
         * changes it later, so the only chance is before the file exists. Requery applies a
         * hardcoded 1024 per connection; that is a no-op here because this writes the header first,
         * with a throwaway table.
         *
         * Existing files are left alone: nothing here can migrate them, only a delete-and-rebuild.
         */
        private fun seedPageSize(context: Context) {
            val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
            if (dbFile.exists()) return
            try {
                dbFile.parentFile?.mkdirs()
                // Framework SQLite on purpose, not Requery: Requery forces its 1024 onto every
                // connection. The framework honours the requested size, as Room's other databases
                // on 4 KB pages show.
                android.database.sqlite.SQLiteDatabase
                    .openOrCreateDatabase(dbFile.path, null)
                    .use { raw ->
                        raw.execSQL("PRAGMA page_size = 4096")
                        raw.execSQL("CREATE TABLE _page_size_seed (x INTEGER)")
                        raw.execSQL("DROP TABLE _page_size_seed")
                    }
            } catch (e: Exception) {
                // cancellation-ok: non-suspend
                Log.w(TAG, "Could not seed page size — index will use the SQLite default", e)
            }
        }

        /**
         * Drops the four `epg_programme` indexes that no query used (43 MB on the TV emulator), and
         * keeps the guide: without this, the destructive fallback would delete it and every source
         * would download again. The bulk-ingest indexes are created again in case the app stopped
         * in the middle of an ingest that had dropped them, so the file matches the schema. See
         * the disk footprint plan → Phase 3.
         */
        internal val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    listOf(
                        "idx_programme_start",
                        "idx_programme_time_range",
                        "idx_programme_channel",
                        "idx_programme_channel_source",
                    ).forEach { db.execSQL("DROP INDEX IF EXISTS `$it`") }
                    EpgIndexer.BULK_DROP_INDEX_DDL.forEach { db.execSQL(it) }
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
                .addMigrations(MIGRATION_17_18)
                .fallbackToDestructiveMigration(true)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            try {
                                // page_size and auto_vacuum are not here: both are only settable
                                // before the first table exists, and this callback runs after Room
                                // has created the schema. See [CreationPragmaFactory].
                                db.execSQL("PRAGMA synchronous = NORMAL")
                                // Baseline of each pooled connection for its lifetime (not a temporary
                                // bump like EpgIndexer's): 64 MB × several connections reserved
                                // 200 MB+ of native RAM on 1-2 GB Android TV devices.
                                db.execSQL("PRAGMA cache_size = -8000") // 8MB cache

                                // journal_size_limit echoes its new value — Requery rejects execSQL
                                // for any statement that produces rows, so it goes through
                                // execPragma, which steps the cursor so the statement actually runs.
                                db.execPragma("PRAGMA journal_size_limit = 10485760")
                            } catch (e: Exception) {
                                // cancellation-ok: non-suspend
                                Log.w(TAG, "Failed to run DB maintenance", e)
                            }
                        }
                    },
                ).build()

        /**
         * Closes the database and deletes the file, before a new index.
         *
         * The deletes run inside the same `synchronized(this)` block as close()/INSTANCE=null: with
         * the lock released between them, a racing [getInstance] could build a new database at this
         * path and then lose its file to the deletes.
         */
        suspend fun destroy(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
                dbFile.delete()
                // Also delete WAL and SHM files
                java.io.File(dbFile.path + "-wal").delete()
                java.io.File(dbFile.path + "-shm").delete()
            }
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
 * `page_size` is not set here: Requery applies a hardcoded 1024 to each connection before any
 * callback of ours runs. Only `PRAGMA page_size` and a one-time `VACUUM` change it on a file that
 * exists.
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
                    // FILE, not MEMORY: temp B-trees for a large XMLTV ingest or FTS rebuild pushed
                    // native RSS past what the Low Memory Killer tolerates on 1-2GB TV devices. Set
                    // once, here, before Room creates its TEMP `room_table_modification_log`:
                    // changing temp_store drops every TEMP table, so switching it per ingest broke
                    // the invalidation tracker ("no such table: room_table_modification_log").
                    db.execSQL("PRAGMA temp_store = FILE")
                    inner.onConfigure(db)
                }

                override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = inner.onUpgrade(db, oldVersion, newVersion)

                override fun onDowngrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = inner.onDowngrade(db, oldVersion, newVersion)

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
