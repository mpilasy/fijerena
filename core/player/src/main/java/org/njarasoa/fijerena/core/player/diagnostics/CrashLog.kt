package org.njarasoa.fijerena.core.player.diagnostics

import android.content.Context
import android.util.Log
import java.io.File

/**
 * On-device record of what went wrong, for builds that ship without a crash reporter: every
 * uncaught exception (the process is about to die) and every exception an [AppScopes] scope
 * absorbed (the process lives on). Appended to `files/crashlog/crashes.log`, capped at
 * [MAX_BYTES] by dropping the oldest half. Shown in Settings → Diagnostics (developer mode); also
 * readable with `adb shell run-as <package> cat files/crashlog/crashes.log`.
 *
 * Plain blocking file I/O on purpose: the uncaught-exception path has no coroutine to suspend in
 * and must finish writing before the process dies. See
 * docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-30.
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val MAX_BYTES = 256 * 1024L

    /** Separates entries in the file; never part of a stack trace. */
    const val ENTRY_SEPARATOR = "\n=====\n"

    private val lock = Any()

    @Volatile private var file: File? = null

    /** Call first thing in `Application.onCreate()`. Chains the existing handler, so the process still dies normally. */
    fun install(context: Context) {
        file = File(context.filesDir, "crashlog/crashes.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record("uncaught on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Appends one entry. Never throws — a failing crash log must not cause a crash of its own. */
    fun record(
        source: String,
        throwable: Throwable,
    ) {
        val target = file
        if (target != null) {
            try {
                synchronized(lock) {
                    target.parentFile?.mkdirs()
                    trimIfNeeded(target)
                    target.appendText("${System.currentTimeMillis()} $source\n${Log.getStackTraceString(throwable)}$ENTRY_SEPARATOR")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't write crash log", e)
            }
        }
    }

    /** Entries newest first, each as `"<epochMs> <source>\n<stack trace>"`. */
    fun read(): List<String> {
        val target = file
        val text =
            try {
                synchronized(lock) { if (target != null && target.exists()) target.readText() else "" }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read crash log", e)
                ""
            }
        return text.split(ENTRY_SEPARATOR).filter { it.isNotBlank() }.reversed()
    }

    fun clear() {
        synchronized(lock) { file?.delete() }
    }

    private fun trimIfNeeded(target: File) {
        if (target.exists() && target.length() > MAX_BYTES) {
            val text = target.readText()
            val keepFrom = text.indexOf(ENTRY_SEPARATOR, text.length / 2)
            target.writeText(if (keepFrom < 0) "" else text.substring(keepFrom + ENTRY_SEPARATOR.length))
        }
    }
}
