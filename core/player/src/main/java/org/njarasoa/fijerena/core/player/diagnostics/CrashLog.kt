package org.njarasoa.fijerena.core.player.diagnostics

import android.content.Context
import android.util.Log
import java.io.File

/**
 * On-device record of what went wrong, for builds that ship without a crash reporter: every
 * uncaught exception (the process is about to die) and every exception an [AppScopes] scope
 * absorbed (the process lives on). Appended to `files/crashlog/crashes.log`, capped at
 * [MAX_BYTES] by dropping the oldest half. Shown in Settings → Diagnostics (Admin); also
 * readable with `adb shell run-as <package> cat files/crashlog/crashes.log`.
 *
 * Plain blocking file I/O on purpose: the uncaught-exception path has no coroutine to suspend in
 * and must finish writing before the process dies. See
 * the rock solid stability resilience plan → F-30.
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val MAX_BYTES = 256 * 1024L

    /** Separates entries in the file; never part of a stack trace. */
    const val ENTRY_SEPARATOR = "\n=====\n"

    /**
     * Marks the screen in the first line of an entry: `"<epochMs> <source> · screen: <route>"`.
     * Logs shows the line as it is; [parse] splits it again.
     */
    const val SCREEN_MARK = " · screen: "

    private val lock = Any()

    @Volatile private var file: File? = null

    /**
     * The route name of the top screen (`Settings`, `MovieDetails`). The nav hosts set it on each
     * change, and each new entry records it. Null before the first screen shows.
     */
    @Volatile var screen: String? = null

    /**
     * Formats a throwable for an entry. A seam for unit tests, where `Log.getStackTraceString`
     * returns null and an entry would hold no exception text to redact.
     */
    internal var stackTraceOf: (Throwable) -> String? = { Log.getStackTraceString(it) }

    /** Call first thing in `Application.onCreate()`. Chains the existing handler, so the process still dies normally. */
    fun install(context: Context) {
        file = File(context.filesDir, "crashlog/crashes.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record("uncaught on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Appends one entry, with login secrets masked ([Redact]) in the source, the messages and every
     * cause. Never throws — a failing crash log must not cause a crash of its own.
     */
    fun record(
        source: String,
        throwable: Throwable,
    ) {
        val target = file
        if (target != null) {
            try {
                val entry = Redact.text("${header(source)}\n${stackTraceOf(throwable)}")
                synchronized(lock) {
                    target.parentFile?.mkdirs()
                    trimIfNeeded(target)
                    target.appendText("$entry$ENTRY_SEPARATOR")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't write crash log", e)
            }
        }
    }

    /**
     * Appends one entry for a failure that has no exception worth a stack trace — a sync that
     * failed, a guide source that couldn't be read — with [message] in its place. Masked and
     * never throws, as [record].
     */
    fun note(
        source: String,
        message: String,
    ) {
        val target = file
        if (target != null) {
            try {
                val entry = Redact.text("${header(source)}\n$message")
                synchronized(lock) {
                    target.parentFile?.mkdirs()
                    trimIfNeeded(target)
                    target.appendText("$entry$ENTRY_SEPARATOR")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't write crash log", e)
            }
        }
    }

    /** The first line of a new entry: the time, [source], and the screen when one is known. */
    private fun header(source: String): String = "${System.currentTimeMillis()} $source" + screen?.let { "$SCREEN_MARK$it" }.orEmpty()

    /** One entry of [read], in its parts. */
    data class Entry(
        val timeMs: Long,
        val source: String,
        /** The route name of the screen at that time; null for an entry from before screens were recorded. */
        val screen: String?,
        /** The stack trace or the message. */
        val body: String,
    )

    /** Splits an entry of [read] into its parts. */
    fun parse(raw: String): Entry {
        val header = raw.substringBefore('\n')
        val rest = header.substringAfter(' ', "")
        return Entry(
            timeMs = header.substringBefore(' ').toLongOrNull() ?: 0L,
            source = rest.substringBefore(SCREEN_MARK),
            screen = rest.substringAfter(SCREEN_MARK, "").ifEmpty { null },
            body = raw.substringAfter('\n', ""),
        )
    }

    /** Entries newest first, each as `"<epochMs> <source>\n<stack trace or message>"`. */
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
