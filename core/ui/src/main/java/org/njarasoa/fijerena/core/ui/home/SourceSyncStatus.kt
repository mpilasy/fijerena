package org.njarasoa.fijerena.core.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.xtream.CatalogPart
import org.njarasoa.fijerena.core.network.xtream.CatalogSyncProgress
import org.njarasoa.fijerena.core.ui.R
import java.text.NumberFormat
import java.util.Locale

/** What Home's source status says about the active source's catalogue sync. */
enum class SourceSyncStatus { UPDATING, FAILED, UPDATED, NONE }

fun sourceSyncStatus(
    syncing: Boolean,
    lastSyncedAtMs: Long,
    lastSyncError: String?,
): SourceSyncStatus =
    when {
        syncing -> SourceSyncStatus.UPDATING
        lastSyncError != null -> SourceSyncStatus.FAILED
        lastSyncedAtMs > 0L -> SourceSyncStatus.UPDATED
        else -> SourceSyncStatus.NONE
    }

/** The catalogue part a running sync is loading, and how many of its items it has read so far. */
data class SyncPhase(
    val part: CatalogPart,
    val count: Int,
)

/**
 * The phase Home's status names while a sync runs: the first part, in [CatalogPart] order, still
 * downloading with something counted. The parts download side by side, so a fixed order keeps the
 * line from flicking between them. Null before any has counted: the status says "Updating…".
 */
fun syncPhase(counts: Map<CatalogPart, Int>?): SyncPhase? =
    CatalogPart.entries.firstNotNullOfOrNull { part ->
        counts?.get(part)?.takeIf { it > 0 }?.let { SyncPhase(part, it) }
    }

/** [count] grouped the [locale]'s way: "12,400" in English, "12 400" in French. */
fun formatSyncCount(
    count: Int,
    locale: Locale,
): String = NumberFormat.getIntegerInstance(locale).format(count)

/** The running sync's phase for [providerId], collected here so only the status line recomposes as it counts. */
@Composable
fun rememberSyncPhase(providerId: Long): SyncPhase? {
    val counts by CatalogSyncProgress.counts.collectAsStateWithLifecycle()
    return syncPhase(counts[providerId])
}

/** "Loading films… 12 400", or "Updating…" before the sync has counted anything. */
@Composable
fun updatingText(phase: SyncPhase?): String {
    val count = phase?.let { formatSyncCount(it.count, LocalConfiguration.current.locales[0]) }
    return when (phase?.part) {
        CatalogPart.CHANNELS -> stringResource(R.string.home_sync_loading_channels, count.orEmpty())
        CatalogPart.FILMS -> stringResource(R.string.home_sync_loading_films, count.orEmpty())
        CatalogPart.SHOWS -> stringResource(R.string.home_sync_loading_shows, count.orEmpty())
        null -> stringResource(R.string.home_source_updating)
    }
}
