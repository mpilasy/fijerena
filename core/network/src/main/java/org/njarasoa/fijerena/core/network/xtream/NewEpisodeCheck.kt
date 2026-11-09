package org.njarasoa.fijerena.core.network.xtream

import android.util.Log
import kotlinx.coroutines.CancellationException
import org.njarasoa.fijerena.core.network.xtream.db.NewEpisodeCheckState
import org.njarasoa.fijerena.core.player.api.XtreamResponse
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * The check after a successful catalogue sync (the new episode alerts plan): for the followed shows
 * of one source, fetches the new episode lists it can afford. Storing each list runs the compare
 * step (`XtreamContentManager.getSeriesInfo`).
 */
internal object NewEpisodeCheck {
    private const val TAG = "NewEpisodeCheck"

    /** At most this many `get_series_info` calls per source per check, one at a time. */
    const val MAX_CALLS = 20

    /** A followed show not checked for this long can have the fallback call: 7 days. */
    const val FALLBACK_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * The calls of one check, in order, from the followed shows' [states]:
     * - **Changed shows:** a stored list and no `episodesFetchedAt`: the catalogue sync cleared the
     *   stamp when the show's hash changed, and a call that failed or waited for the cap keeps it
     *   clear, so this list needs no storage of its own. Oldest change first (`last_modified`), at
     *   most [MAX_CALLS].
     * - **Fallback:** while calls are left, one followed show not checked for [FALLBACK_AFTER_MS],
     *   or never fetched (its first list is the baseline), for panels that do not change
     *   `last_modified`: the oldest first.
     */
    fun plan(
        states: List<NewEpisodeCheckState>,
        now: Long,
    ): List<Int> {
        val changed =
            states
                .filter { it.episodesFetchedAt == null && it.hasEpisodes }
                .sortedWith(compareBy({ it.lastModified?.toLongOrNull() ?: 0L }, { it.seriesId }))
                .take(MAX_CALLS)
                .map { it.seriesId }
        val fallback =
            if (changed.size >= MAX_CALLS) {
                null
            } else {
                states
                    .filter { state ->
                        state.seriesId !in changed &&
                            (state.episodesFetchedAt == null || now - state.episodesFetchedAt >= FALLBACK_AFTER_MS)
                    }.minWithOrNull(compareBy({ it.episodesFetchedAt ?: 0L }, { it.seriesId }))
                    ?.seriesId
            }
        return changed + listOfNotNull(fallback)
    }

    /**
     * Runs [calls] one at a time through [fetch] (Risk 1 of the plan: a source can rate-limit).
     * [stamp] marks a show that answered as checked: an answer with no episodes is not stamped by
     * the store, and would have the fallback call again on each sync. A call that fails stops the
     * check; what is left waits for the next sync.
     */
    suspend fun run(
        calls: List<Int>,
        fetch: suspend (Int) -> XtreamResponse<*>,
        stamp: (Int) -> Unit,
    ) {
        for (seriesId in calls) {
            val response = fetch(seriesId)
            if (response is XtreamResponse.Failed) {
                Log.w(TAG, "Stopped after a failed call for show $seriesId: ${response.cause.message}")
                break
            }
            if (response is XtreamResponse.Ok) stamp(seriesId)
        }
    }

    /** Runs [check] so that a failure of the check never fails the catalogue sync. */
    suspend fun runSafely(
        sourceName: String,
        check: suspend () -> Unit,
    ) {
        try {
            check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "New episode check failed for $sourceName", e)
            CrashLog.record("new episode check failed: $sourceName", e)
        }
    }
}
