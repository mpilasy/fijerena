package org.njarasoa.fijerena.core.player.diagnostics

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import java.io.File
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * The last [MAX_ENTRIES] server calls that failed — no connection, a timeout, an error status —
 * with the address asked and the error, logins masked ([Redact]). The app handles these (a
 * message, a saved copy), so they never reach [CrashLog]; this is where an unreachable source
 * shows in Settings → Diagnostics. Kept in `files/crashlog/connections.log`. Fed by
 * [recordFailedCalls] on the Xtream, Jellyfin and TMDB clients.
 */
object ConnectionLog {
    private const val TAG = "ConnectionLog"
    const val MAX_ENTRIES = 500

    /** Separates entries in the file; never part of an address or an error message. */
    const val ENTRY_SEPARATOR = "\n=====\n"

    /**
     * Separates an entry's short part (what failed, the address, the error) from the error's stack
     * trace. Diagnostics shows only the short part; a copy and Share take the trace too.
     */
    const val TRACE_SEPARATOR = "\n----- stack trace -----\n"

    /** Stack trace lines kept per entry: enough for the cause chain, so 500 entries stay near 1 MB. */
    const val MAX_TRACE_LINES = 40

    private val lock = Any()

    @Volatile private var file: File? = null

    /** Call in `Application.onCreate()`, next to [CrashLog.install]. */
    fun install(context: Context) {
        file = File(context.filesDir, "crashlog/connections.log")
    }

    /** For tests: where entries go. */
    internal fun installAt(target: File) {
        file = target
    }

    /** Adds one entry for a failed call to [url]. Never throws. */
    fun record(
        url: String,
        error: Throwable,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val target = file ?: return
        try {
            val host = runCatching { URI(url).host }.getOrNull() ?: url
            val what = if (error is ResponseException) "HTTP ${error.response.status.value} from $host" else "No answer from $host"
            val trace =
                error
                    .stackTraceToString()
                    .lineSequence()
                    .take(MAX_TRACE_LINES)
                    .joinToString("\n")
            val entry = Redact.text("$nowMs $what\n$url\n${error::class.simpleName}: ${error.message}$TRACE_SEPARATOR$trace")
            synchronized(lock) {
                target.parentFile?.mkdirs()
                val kept = readEntries(target).takeLast(MAX_ENTRIES - 1)
                target.writeText((kept + entry).joinToString(ENTRY_SEPARATOR, postfix = ENTRY_SEPARATOR))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write connection log", e)
        }
    }

    /**
     * Entries newest first, each as `"<epochMs> <what>\n<address>\n<error>"`, then
     * [TRACE_SEPARATOR] and the stack trace (none in entries recorded before 2026-10-21).
     */
    fun read(): List<String> {
        val target = file ?: return emptyList()
        return try {
            synchronized(lock) { readEntries(target) }.reversed()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read connection log", e)
            emptyList()
        }
    }

    fun clear() {
        synchronized(lock) { file?.delete() }
    }

    private fun readEntries(target: File): List<String> =
        if (target.exists()) target.readText().split(ENTRY_SEPARATOR).filter { it.isNotBlank() } else emptyList()
}

/**
 * Records every call of this client that fails into [ConnectionLog]: no connection, a timeout, an
 * error status. A call cancelled because its screen closed isn't a failure and is left out.
 */
fun HttpClientConfig<*>.recordFailedCalls() {
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, request ->
            if (cause !is CancellationException || cause is HttpRequestTimeoutException) {
                ConnectionLog.record(request.url.toString(), cause)
            }
        }
    }
}
