package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The one-row sync clock of this database — a hybrid logical clock kept as milliseconds:
 * every local change moves [hlc] to `max(now, hlc + 1)`, and every received record to at least its
 * own value, so a device with a slow clock can't keep losing. Advanced by [XtreamSyncTriggers].
 *
 * [applying] is 1 while changes received from another device are written, which stops the
 * triggers from queueing them to be sent straight back. See
 * `docs/plans/20260929_live-sync-plan.md` → Conflicts, Applying remote records.
 */
@Entity(tableName = "sync_clock")
data class SyncClockEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val hlc: Long = 0,
    val applying: Boolean = false,
) {
    companion object {
        const val SINGLE_ROW = 1
    }
}
