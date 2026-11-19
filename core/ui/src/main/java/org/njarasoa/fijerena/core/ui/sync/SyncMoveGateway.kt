package org.njarasoa.fijerena.core.ui.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.njarasoa.fijerena.core.network.sync.MoveBlock
import org.njarasoa.fijerena.core.network.sync.MoveState
import org.njarasoa.fijerena.core.network.sync.OldServerState
import org.njarasoa.fijerena.core.network.sync.SyncApiException
import org.njarasoa.fijerena.core.network.sync.SyncWire

/**
 * TEMPORARY: the [SyncManager] members of the sync server move plan → App interface, with the same
 * names. They are not in [SyncManager] yet. SyncSettingsViewModel calls the move only through this
 * interface. When the [SyncManager] members are on main, use [SyncManager] in the view model and
 * delete this file.
 */
internal interface SyncMoveGateway {
    /** `SyncManager.move`. */
    val move: StateFlow<MoveState>

    /** `SyncManager.moveBlock()`. */
    suspend fun moveBlock(): MoveBlock?

    /** `SyncManager.checkMoveTarget(url)`. */
    suspend fun checkMoveTarget(url: String): SyncWire.Info

    /** `SyncManager.startMove(url, setupSecret)`. */
    fun startMove(
        url: String,
        setupSecret: String?,
    )

    /** `SyncManager.oldServer`. */
    val oldServer: StateFlow<String?>

    /** `SyncManager.oldServerState()`. */
    suspend fun oldServerState(): OldServerState

    /** `SyncManager.purgeOldServer()`. */
    suspend fun purgeOldServer()

    /** `SyncManager.forgetOldServer()`. */
    suspend fun forgetOldServer()

    /** `SyncManager.Status.movedAway`. */
    fun movedAway(status: SyncManager.Status): String?
}

/** TEMPORARY: no move and no old server. Delete it with [SyncMoveGateway]. */
internal object StubSyncMoveGateway : SyncMoveGateway {
    private val idle = MutableStateFlow<MoveState>(MoveState.Idle)
    private val none = MutableStateFlow<String?>(null)

    override val move: StateFlow<MoveState> = idle.asStateFlow()

    override suspend fun moveBlock(): MoveBlock? = null

    override suspend fun checkMoveTarget(url: String): SyncWire.Info =
        throw SyncApiException(SyncApiException.INCOMPATIBLE, "The server move is not in this build yet")

    override fun startMove(
        url: String,
        setupSecret: String?,
    ) = Unit

    override val oldServer: StateFlow<String?> = none.asStateFlow()

    override suspend fun oldServerState(): OldServerState = OldServerState.NoPurge

    override suspend fun purgeOldServer() = Unit

    override suspend fun forgetOldServer() = Unit

    override fun movedAway(status: SyncManager.Status): String? = null
}
