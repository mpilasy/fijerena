package org.njarasoa.fijerena.core.ui.home

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xtream.CatalogPart
import org.njarasoa.fijerena.core.network.xtream.CatalogSyncProgress
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.ui.R
import java.text.NumberFormat
import java.util.Locale

/** What a source's status says about its catalogue sync (and Home's summary line about all of them). */
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

/** One of the profile's sources, as Home's summary line and its sources sheet see it. */
data class SourceSyncState(
    val providerId: Long,
    val name: String,
    val type: String,
    val syncing: Boolean,
    val lastSyncedAtMs: Long,
    val lastSyncError: String?,
) {
    val status: SourceSyncStatus get() = sourceSyncStatus(syncing, lastSyncedAtMs, lastSyncError)
}

/**
 * Home's one line for all the profile's sources (docs/plans/20261012_multi-source-search-history-plan.md
 * → O3), over the sources that have an update status at all ([total]; a Jellyfin or M3U source
 * never syncs its catalogue). A running sync wins ("Updating 1 of 3…"), then failures ("1 failed"),
 * then the least recent update ("Updated 2 hours ago" — the age of the stalest source, so the line
 * never says the library is fresher than it is); [SourceSyncStatus.NONE] when no source syncs.
 */
data class SyncSummary(
    val status: SourceSyncStatus,
    val total: Int,
    val updating: Int,
    val failed: Int,
    /** When the least recently updated source last updated; 0 unless [status] is UPDATED. */
    val updatedAtMs: Long,
    /** The running sync whose phase the line names; null when none runs. */
    val updatingProviderId: Long?,
) {
    /** The line opens the sources sheet: there is something to say. */
    val opensSheet: Boolean get() = status != SourceSyncStatus.NONE
}

fun syncSummary(sources: List<SourceSyncState>): SyncSummary {
    val syncable = sources.filter { it.status != SourceSyncStatus.NONE }
    val updating = syncable.filter { it.syncing }
    val failed = syncable.count { it.status == SourceSyncStatus.FAILED }
    val status =
        when {
            updating.isNotEmpty() -> SourceSyncStatus.UPDATING
            failed > 0 -> SourceSyncStatus.FAILED
            syncable.isNotEmpty() -> SourceSyncStatus.UPDATED
            else -> SourceSyncStatus.NONE
        }
    val updatedAtMs = if (status == SourceSyncStatus.UPDATED) syncable.minOf { it.lastSyncedAtMs } else 0L
    return SyncSummary(status, syncable.size, updating.size, failed, updatedAtMs, updating.firstOrNull()?.providerId)
}

/**
 * The profile's sources with their sync state, as they change: the Room flow re-emits when a sync
 * records its result, and [ProviderSyncRunner.running] when one starts or ends.
 */
fun profileSyncStates(context: Context): Flow<List<SourceSyncState>> =
    combine(
        ProviderRepository(context.applicationContext).profileSources(AppSettings(context.applicationContext).activeProfileId),
        ProviderSyncRunner.running,
    ) { sources, running ->
        sources.map { SourceSyncState(it.id, it.name, it.type, it.id in running, it.lastSyncedAtMs, it.lastSyncError) }
    }

/** [profileSyncStates], collected for a screen; empty until read. */
@Composable
fun rememberProfileSyncStates(): List<SourceSyncState> {
    val context = LocalContext.current
    val states by remember { profileSyncStates(context) }.collectAsStateWithLifecycle(emptyList())
    return states
}

/** "2 hours ago", or "just now" under a minute (getRelativeTimeSpanString would say "0 minutes ago"). */
@Composable
fun updatedAgo(
    atMs: Long,
    now: Long,
): String =
    if (now - atMs < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.live_sync_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(atMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }

/** One source's status, as its row in the sources sheet says it; null for one that never syncs. */
@Composable
fun sourceStatusText(
    state: SourceSyncState,
    now: Long,
): String? =
    when (state.status) {
        SourceSyncStatus.UPDATING -> updatingText(rememberSyncPhase(state.providerId))
        SourceSyncStatus.FAILED -> stringResource(R.string.home_source_update_failed)
        SourceSyncStatus.UPDATED -> stringResource(R.string.home_source_updated, updatedAgo(state.lastSyncedAtMs, now))
        SourceSyncStatus.NONE -> null
    }

/** Home's summary line ([SyncSummary]); null when no source syncs. */
@Composable
fun syncSummaryText(
    summary: SyncSummary,
    now: Long,
): String? =
    when (summary.status) {
        SourceSyncStatus.UPDATING -> {
            val phase = summary.updatingProviderId?.let { rememberSyncPhase(it) }
            when {
                summary.total == 1 -> {
                    updatingText(phase)
                }

                phase != null -> {
                    stringResource(
                        R.string.home_sync_updating_count_phase,
                        summary.updating,
                        summary.total,
                        updatingText(phase),
                    )
                }

                else -> {
                    stringResource(R.string.home_sync_updating_count, summary.updating, summary.total)
                }
            }
        }

        SourceSyncStatus.FAILED -> {
            if (summary.total == 1) {
                stringResource(R.string.home_source_update_failed)
            } else {
                pluralStringResource(R.plurals.home_sync_failed_count, summary.failed, summary.failed)
            }
        }

        SourceSyncStatus.UPDATED -> {
            stringResource(R.string.home_source_updated, updatedAgo(summary.updatedAtMs, now))
        }

        SourceSyncStatus.NONE -> {
            null
        }
    }

/** The wall clock, re-read on each minute boundary, so "Updated 2 minutes ago" keeps counting. */
@Composable
fun rememberMinuteTick(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(DateUtils.MINUTE_IN_MILLIS - now % DateUtils.MINUTE_IN_MILLIS)
        }
    }
    return now
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
