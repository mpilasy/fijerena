package org.njarasoa.fijerena.core.player.domain

// When a film or an episode counts as watched, and when it is worth resuming. One rule for both,
// and the one the "Up next" card follows: the card appears where the title becomes watched.

/** Share of a title's length, at its end, that counts as watched (mostly credits). */
const val WATCHED_TAIL_FRACTION = 0.12

/** The shortest stretch at a title's end that counts as watched: 90 s. */
const val WATCHED_TAIL_MIN_MS = 90_000L

/** Percentage a title must be in before it is worth resuming. */
const val RESUME_MIN_PERCENT = 2.0

/**
 * The stretch at the end of a title [durationMs] long that counts as watched: [WATCHED_TAIL_FRACTION]
 * of it, or [WATCHED_TAIL_MIN_MS] when that is longer — 5 min 38 s for a 47-minute episode, 90 s
 * for a 5-minute one. 0 for an unknown duration.
 */
fun watchedTailMs(durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    return maxOf(WATCHED_TAIL_MIN_MS, (durationMs * WATCHED_TAIL_FRACTION).toLong())
}

/** Whether playback at [positionMs] of [durationMs] counts as watched: inside [watchedTailMs]. */
fun isWatchedAt(
    positionMs: Long,
    durationMs: Long,
): Boolean = durationMs > 0L && durationMs - positionMs <= watchedTailMs(durationMs)

/** Whether [positionMs] of [durationMs] is worth resuming: at least [RESUME_MIN_PERCENT] in, not yet watched. */
fun isResumableAt(
    positionMs: Long,
    durationMs: Long,
): Boolean = durationMs > 0L && positionMs * 100.0 / durationMs >= RESUME_MIN_PERCENT && !isWatchedAt(positionMs, durationMs)

/** [isResumableAt] as an SQL condition on `watch_state`'s positionMs and durationMs. */
const val RESUMABLE_SQL =
    "durationMs > 0 AND positionMs * 100.0 / durationMs >= $RESUME_MIN_PERCENT " +
        "AND durationMs - positionMs > MAX($WATCHED_TAIL_MIN_MS, durationMs * $WATCHED_TAIL_FRACTION)"
