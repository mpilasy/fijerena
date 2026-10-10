package org.njarasoa.fijerena.core.player.domain

import org.njarasoa.fijerena.core.player.model.EpgProgram

/**
 * The stretch of a channel's archive that replays one programme: from [startEpochSec] for
 * [durationSec], both on whole minutes (the panel's timeshift takes minutes). The programme itself
 * starts [programOffsetSec] into it, after the lead-in.
 */
data class CatchupWindow(
    val startEpochSec: Long,
    val durationSec: Long,
    val programOffsetSec: Long,
)

/**
 * Which guide programmes can be replayed from their channel's archive, and the window that
 * replays one. Every screen asks here so they can't disagree. See docs/plans/archive/20261010_catchup-plan.md.
 */
object CatchupAvailability {
    private const val SECONDS_PER_DAY = 86_400L
    private const val SECONDS_PER_MINUTE = 60L

    // The panel's guide is cached for up to 6 hours (XtreamCacheKeys.EPG_CACHE_EXPIRY_MS), and
    // the programme on air says `has_archive = 0` until it has ended. A 0 read from a cached copy
    // is only trusted for a programme that had ended before any copy still in use was fetched.
    private const val ARCHIVE_FLAG_TRUSTED_AFTER_SEC = 6 * 60 * 60L

    // Guide times are approximate: start a little early and end a little late.
    private const val LEAD_IN_SEC = 2 * SECONDS_PER_MINUTE
    private const val LEAD_OUT_SEC = 5 * SECONDS_PER_MINUTE

    /**
     * Whether [program] can be played from the archive of a channel that keeps [archiveDays] days
     * (0 for none) at [nowEpochSec]: one that has ended and is still in the archive, or the one on
     * air (start over). The programme's own `has_archive` decides when the panel's guide gave one.
     */
    fun isPlayable(
        archiveDays: Int,
        program: EpgProgram,
        nowEpochSec: Long,
    ): Boolean {
        val started = program.startTime in 1..nowEpochSec
        val ended = program.endTime <= nowEpochSec
        val inArchive =
            when {
                !ended -> true
                program.hasArchive == 1 -> true
                program.hasArchive == 0 && program.endTime < nowEpochSec - ARCHIVE_FLAG_TRUSTED_AFTER_SEC -> false
                else -> program.startTime >= nowEpochSec - archiveDays * SECONDS_PER_DAY
            }
        return archiveDays > 0 && started && program.endTime > program.startTime && inArchive
    }

    /** Whether [program] is on air at [nowEpochSec] — replaying it is "start over". */
    fun isOnAir(
        program: EpgProgram,
        nowEpochSec: Long,
    ): Boolean = program.startTime <= nowEpochSec && nowEpochSec < program.endTime

    /** The archive window that replays [program], with a lead-in and a lead-out. */
    fun window(program: EpgProgram): CatchupWindow {
        val start = (program.startTime - LEAD_IN_SEC).floorToMinute()
        val end = program.endTime + LEAD_OUT_SEC
        val minutes = (end - start + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
        return CatchupWindow(
            startEpochSec = start,
            durationSec = minutes * SECONDS_PER_MINUTE,
            programOffsetSec = program.startTime - start,
        )
    }

    private fun Long.floorToMinute(): Long = this - Math.floorMod(this, SECONDS_PER_MINUTE)
}
