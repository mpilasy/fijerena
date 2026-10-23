package org.njarasoa.fijerena.core.network.xmltv

import androidx.room.withTransaction
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity.Companion.REFRESH_OFF
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase

/**
 * Auto-refresh per guide source: each `epg_source.refresh_interval_hours` decides when that source
 * is due, and the one periodic `epg_sync` work runs at the shortest of them. A row without one of
 * its own (null: there before v16, or received from a device on an older version) uses the retired
 * device-wide interval, [legacyIntervalHours] — the `unsetHours` the functions below take. See
 * the sources guide profiles plan → P5, D2.
 */
object EpgRefreshSchedule {
    private const val HOUR_MS = 3_600_000L

    /** What a source whose auto-refresh is off counts as its interval, for the stale colour and Refresh stale. */
    private const val OFF_INTERVAL_MS = 24 * HOUR_MS

    /** Off, or a whole number of hours. */
    fun isValidInterval(hours: Int): Boolean = hours == REFRESH_OFF || hours > 0

    /** The interval a row without one of its own uses: the retired device-wide one. */
    fun legacyIntervalHours(settings: AppSettings): Int = legacyIntervalHours(settings.epgAutoRefreshEnabled, settings.epgRefreshInterval)

    fun legacyIntervalHours(
        autoRefreshEnabled: Boolean,
        intervalHours: Int,
    ): Int = if (autoRefreshEnabled && intervalHours > 0) intervalHours else REFRESH_OFF

    /** The source's interval in hours, [REFRESH_OFF] = off. */
    fun intervalHours(
        source: EpgSourceEntity,
        unsetHours: Int,
    ): Int = source.refreshIntervalHours ?: unsetHours

    /** An interval in ms; 24 h when auto-refresh is off. */
    fun intervalMs(hours: Int): Long = if (hours > 0) hours * HOUR_MS else OFF_INTERVAL_MS

    /**
     * How old a source may get before it counts as stale: its full interval, 24 h when its
     * auto-refresh is off.
     */
    fun staleAfterMs(hours: Int): Long = intervalMs(hours)

    /**
     * How early an automatic run may come and still refresh a source: WorkManager's periodic work
     * has its own jitter, and a run a few minutes before the source turns stale would otherwise skip
     * it and leave it stale for another whole interval.
     */
    private const val EARLY_RUN_ALLOWANCE_MS = HOUR_MS

    /** Stale for a refresh the viewer asks for (Refresh stale): never ingested, or older than [staleAfterMs]. */
    fun isStale(
        source: EpgSourceEntity,
        nowMs: Long,
        unsetHours: Int,
    ): Boolean = source.lastIngestedAtMs == 0L || nowMs - source.lastIngestedAtMs > staleAfterMs(intervalHours(source, unsetHours))

    /**
     * Due for an automatic refresh (the periodic worker, the on-login refresh): as [isStale], up to
     * [EARLY_RUN_ALLOWANCE_MS] early, except that a source whose auto-refresh is off is due only
     * while it has never been ingested.
     */
    fun isDue(
        source: EpgSourceEntity,
        nowMs: Long,
        unsetHours: Int,
    ): Boolean {
        if (source.lastIngestedAtMs == 0L) return true
        val hours = intervalHours(source, unsetHours)
        return hours != REFRESH_OFF && nowMs - source.lastIngestedAtMs > staleAfterMs(hours) - EARLY_RUN_ALLOWANCE_MS
    }

    /** The period of `epg_sync`: the shortest interval among enabled sources of every provider; null when all are off. */
    fun workIntervalHours(
        sources: List<EpgSourceEntity>,
        unsetHours: Int,
    ): Int? =
        sources
            .filter { it.enabled }
            .map { intervalHours(it, unsetHours) }
            .filter { it > 0 }
            .minOrNull()

    /**
     * Once per install: [fillUnsetSources] gets the retired device-wide interval to write into every
     * source that has no interval of its own (null), so nobody's schedule changes on upgrade, and a
     * value already set — say one received from a device on the new version — is never
     * overwritten. Retried on the next start if it throws.
     */
    suspend fun copyLegacyIntervalOnce(
        settings: AppSettings,
        fillUnsetSources: suspend (hours: Int) -> Unit,
    ) {
        if (!settings.epgRefreshIntervalCopied) {
            fillUnsetSources(legacyIntervalHours(settings))
            settings.epgRefreshIntervalCopied = true
        }
    }

    /**
     * Writes [hours] into every source of [db] without an interval of its own, sync triggers
     * silenced: each device fills its own rows from the same synced setting, and sending them could
     * overwrite a value another device on this version has set since.
     */
    suspend fun fillUnsetIntervals(
        db: SettingsDatabase,
        hours: Int,
    ) {
        db.withTransaction {
            db.settingsSyncDao().setApplying(true)
            db.epgSourceDao().fillUnsetRefreshIntervals(hours)
            db.settingsSyncDao().setApplying(false)
        }
    }
}
