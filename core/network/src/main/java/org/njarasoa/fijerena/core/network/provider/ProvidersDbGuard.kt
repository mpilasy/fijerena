package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import java.io.File

/**
 * Keeps an older build from opening a `providers.db` written by a newer one (an older APK
 * installed over a newer one, or a newer backup restored). Room has no way down and throws on
 * open, which crashed every launch, and the only way out was clearing the app's data. Unlike
 * `XtreamDatabase.setAsideIfNewer`, nothing is moved automatically: sources, profiles and sync
 * state live here, so the app shows the newer-data screen and only an explicit "Reset sources"
 * calls [setAside]. See docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-01.
 */
object ProvidersDbGuard {
    private const val TAG = "ProvidersDbGuard"
    private const val DB_NAME = "providers.db"

    /** The version of `providers.db` on disk when it is newer than this build's, else 0. Set by [check]. */
    @Volatile var newerFileVersion: Int = 0
        private set

    /** Whether this process must not open `providers.db`. Fixed after [check]. */
    val isBlocked: Boolean get() = newerFileVersion > 0

    /** The highest `providers.db` version this build reads. */
    val supportedVersion: Int get() = SettingsDatabase.DB_VERSION

    /**
     * Call in `Application.onCreate()`, before anything opens `providers.db`. Reads the file's
     * header with the framework SQLite, read-only; a missing or unreadable file counts as not newer.
     */
    fun check(context: Context) {
        val version = fileVersion(context.getDatabasePath(DB_NAME))
        newerFileVersion = if (version > SettingsDatabase.DB_VERSION) version else 0
        if (isBlocked) {
            val notice = IllegalStateException("$DB_NAME is v$version, newer than this build's v${SettingsDatabase.DB_VERSION}")
            Log.e(TAG, notice.message, notice)
            CrashLog.record("providers.db from a newer build", notice)
        }
    }

    /**
     * "Reset sources": moves `providers.db` (and its `-wal`/`-shm`) to `providers.db.v<N>.bak`,
     * keeping only the latest such backup. The next start creates an empty one. Installing the
     * newer build and restoring the `.bak` brings the sources back.
     */
    fun setAside(context: Context) {
        val file = context.getDatabasePath(DB_NAME)
        val version = fileVersion(file)
        val backupBase = "$DB_NAME.v$version.bak"
        file.parentFile?.listFiles { f -> f.name.startsWith("$DB_NAME.v") && f.name.contains(".bak") }?.forEach { it.delete() }
        listOf("", "-wal", "-shm").forEach { suffix ->
            val part = File(file.path + suffix)
            if (part.exists()) part.renameTo(File(file.parentFile, backupBase + suffix))
        }
        CrashLog.record("providers.db set aside", IllegalStateException("$DB_NAME v$version moved to $backupBase by the user"))
    }

    private fun fileVersion(file: File): Int {
        var version = 0
        if (file.exists()) {
            try {
                version = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            } catch (e: Exception) {
                // cancellation-ok: non-suspend
                Log.w(TAG, "Couldn't read the version of $DB_NAME", e)
            }
        }
        return version
    }
}
