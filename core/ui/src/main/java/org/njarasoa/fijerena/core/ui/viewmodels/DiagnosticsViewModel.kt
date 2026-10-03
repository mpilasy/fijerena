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
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.diagnostics.ProcessExits
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/**
 * Settings → Diagnostics (developer mode): exceptions this app recorded itself ([CrashLog]) and
 * how its recent processes ended according to the system ([ProcessExits]), newest first. See
 * docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-30.
 */
class DiagnosticsViewModel(
    private val context: Context,
) : ViewModel() {
    data class Entry(
        val timestampMs: Long,
        val title: String,
        val detail: String,
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
            withContext(Dispatchers.IO) { CrashLog.clear() }
            reload()
        }
    }

    /** Everything, as one plain-text block for sharing, login secrets masked ([Redact]). */
    fun asText(entries: List<Entry>): String = Redact.text(entries.joinToString("\n\n") { "${it.title}\n${it.detail}" })

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
        val exits =
            ProcessExits.recent(context).map { exit ->
                Entry(
                    timestampMs = exit.timestampMs,
                    title = "${formatTime(exit.timestampMs)} · exit ${exit.reason} (${exit.processName})",
                    detail = Redact.text(listOfNotNull(exit.description, exit.trace).joinToString("\n")),
                )
            }
        return (recorded + exits).sortedByDescending { it.timestampMs }
    }

    private fun formatTime(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(epochMs))
}
