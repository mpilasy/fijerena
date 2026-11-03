package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.player.diagnostics.ConnectionLog
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.diagnostics.ProcessExits
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.player.diagnostics.TraceCauses
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import java.io.File

/**
 * Settings → Diagnostics (developer mode): exceptions this app recorded itself ([CrashLog]), the
 * server calls that failed ([ConnectionLog]) and how its recent processes ended according to the
 * system ([ProcessExits]), newest first. See
 * the rock solid stability resilience plan → F-30.
 */
class DiagnosticsViewModel(
    private val context: Context,
) : ViewModel() {
    data class Entry(
        val timestampMs: Long,
        val title: String,
        val detail: String,
        /** Not shown, but part of a copy and of Share: a failed server call's stack trace. */
        val trace: String? = null,
        /** The exception chain when [detail] is a stack trace: the entry then opens and closes. Null for a plain message. */
        val causes: TraceCauses? = TraceCauses.parse(detail),
    )

    /** Null while loading. */
    private val _entries = MutableStateFlow<List<Entry>?>(null)
    val entries: StateFlow<List<Entry>?> = _entries.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launchGuarded("DiagnosticsViewModel.reload") {
            _entries.value = withContext(Dispatchers.IO) { load() }
        }
    }

    /** Clears this app's own log; the system's exit history can't be cleared and stays. */
    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CrashLog.clear()
                ConnectionLog.clear()
            }
            reload()
        }
    }

    /** Everything, as one plain-text block for sharing, login secrets masked ([Redact]). */
    fun asText(entries: List<Entry>): String =
        Redact.text(entries.joinToString("\n\n") { listOfNotNull(it.title, it.detail, it.trace).joinToString("\n") })

    /**
     * Writes [entries] as [asText] to a new file in the cache, for Share. The whole log can be near
     * 1 MB, and a text extra that big passes Binder's 1 MB transaction limit: the share crashed
     * with TransactionTooLargeException. A file goes by its content URI instead. Older share files
     * are deleted first, so the folder holds one at most.
     */
    suspend fun writeShareFile(entries: List<Entry>): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
            File(dir, "fijerena-logs-$stamp.txt").apply { writeText(asText(entries)) }
        }

    private fun load(): List<Entry> {
        val recorded =
            CrashLog.read().map(Redact::text).map { raw ->
                val header = raw.substringBefore('\n')
                val timestampMs = header.substringBefore(' ').toLongOrNull() ?: 0L
                Entry(
                    timestampMs = timestampMs,
                    title = "${formatTime(timestampMs)} · ${header.substringAfter(' ')}",
                    detail = raw.substringAfter('\n'),
                )
            }
        val connections =
            ConnectionLog.read().map { raw ->
                val header = raw.substringBefore('\n')
                val timestampMs = header.substringBefore(' ').toLongOrNull() ?: 0L
                val body = raw.substringAfter('\n')
                Entry(
                    timestampMs = timestampMs,
                    title = "${formatTime(timestampMs)} · ${header.substringAfter(' ')}",
                    detail = body.substringBefore(ConnectionLog.TRACE_SEPARATOR),
                    trace = body.substringAfter(ConnectionLog.TRACE_SEPARATOR, "").ifEmpty { null },
                )
            }
        val exits =
            ProcessExits.recent(context).map { exit ->
                Entry(
                    timestampMs = exit.timestampMs,
                    title = "${formatTime(exit.timestampMs)} · exit ${exit.reason} (${exit.processName})",
                    detail = Redact.text(listOfNotNull(exit.description, exit.trace).joinToString("\n")),
                )
            }
        return (recorded + connections + exits).sortedByDescending { it.timestampMs }
    }

    companion object {
        /** The cache subfolder that Share files go in; the FileProvider paths name it too. */
        const val SHARE_DIR = "shared_logs"

        /**
         * Whether [entry] matches a search [query]: each word of the query, in any order and case,
         * is in the entry's title, detail or stack trace. A blank query matches every entry.
         */
        fun matches(
            entry: Entry,
            query: String,
        ): Boolean =
            query.split(' ', '\t').filter { it.isNotEmpty() }.all { word ->
                entry.title.contains(word, ignoreCase = true) ||
                    entry.detail.contains(word, ignoreCase = true) ||
                    entry.trace?.contains(word, ignoreCase = true) == true
            }
    }

    private fun formatTime(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(epochMs))
}
