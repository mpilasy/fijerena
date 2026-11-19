package org.njarasoa.fijerena.core.network.sync

/*
 * The states of a move of the sync group to another server, as the Live Sync screens show them.
 * See docs/plans/20261119_sync-server-move-plan.md → App interface.
 */

/** Why Move to Another Server is disabled (step 0 and SM-Q7); null when it is not. */
sealed interface MoveBlock {
    /** These devices of the group run a build that can't move (or reported no build): update or remove them. */
    data class UpdateFirst(
        val deviceNames: List<String>,
    ) : MoveBlock

    /** Another device of the group started a move that has not finished. */
    data object MoveInProgress : MoveBlock

    /** An earlier move left an old server that is not purged or forgotten yet. */
    data object OldServerLeft : MoveBlock
}

/** How far a move started on this device is. */
sealed interface MoveState {
    data object Idle : MoveState

    /** [copied] and [total] count records while [stage] is [Stage.COPYING]; [total] is null until known. */
    data class Running(
        val stage: Stage,
        val copied: Int = 0,
        val total: Int? = null,
    ) : MoveState

    /** The move stopped; [message] is the raw error. The move continues from where it stopped when started again. */
    data class Failed(
        val message: String,
    ) : MoveState

    /** This device is on the new server. */
    data object Done : MoveState

    enum class Stage { CHECKING, CREATING, COPYING, SWITCHING }
}

/** The old server after a move, as an open Live Sync screen finds it. */
sealed interface OldServerState {
    /** Devices that are not on the new server yet: Purge Old Server is disabled. */
    data class NotMoved(
        val deviceNames: List<String>,
    ) : OldServerState

    /** Every other device moved or was removed: Purge Old Server is enabled. */
    data object Ready : OldServerState

    /** The old server can't delete an account (no `purge` feature): a hint shows instead of the button. */
    data object NoPurge : OldServerState

    /** The old server did not answer; [message] is the raw error. Forget Old Server shows. */
    data class Unreachable(
        val message: String,
    ) : OldServerState
}
