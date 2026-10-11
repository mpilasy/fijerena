package org.njarasoa.fijerena.core.network.xtream

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A part of an Xtream catalogue that a sync downloads item by item. */
enum class CatalogPart { CHANNELS, FILMS, SHOWS }

/**
 * How many items the catalogue downloads running now have read, per source id and part — what
 * Home's source status shows during a long sync ("Loading films… 12 400"). A part is listed only
 * while its download runs. The counts are the sync's own running tallies; nothing is queried.
 */
object CatalogSyncProgress {
    private val state = MutableStateFlow<Map<Long, Map<CatalogPart, Int>>>(emptyMap())

    val counts: StateFlow<Map<Long, Map<CatalogPart, Int>>> = state.asStateFlow()

    fun report(
        providerId: Long,
        part: CatalogPart,
        count: Int,
    ) {
        state.update { all -> all + (providerId to (all[providerId].orEmpty() + (part to count))) }
    }

    fun done(
        providerId: Long,
        part: CatalogPart,
    ) {
        state.update { all ->
            val left = all[providerId].orEmpty() - part
            if (left.isEmpty()) all - providerId else all + (providerId to left)
        }
    }
}

/**
 * Lets a running count through at most once per [intervalMs] (the first at once), so a download of
 * 100 000 items redraws Home a few times a second rather than 100 000 times. Not thread-safe: one
 * per download, which reads its items one after another.
 */
class ProgressThrottle(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
) {
    private var lastMs: Long? = null

    fun ready(): Boolean {
        val now = clockMs()
        val last = lastMs
        val ready = last == null || now - last >= intervalMs
        if (ready) lastMs = now
        return ready
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 300L
        const val NANOS_PER_MS = 1_000_000L
    }
}
