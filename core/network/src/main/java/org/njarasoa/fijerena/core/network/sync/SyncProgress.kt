package org.njarasoa.fijerena.core.network.sync

/**
 * How far a sync pass is (the sync progress plan): records received and applied, or sent, so far,
 * and the total when it is known. A server before the plan sends no total for a pull, so [total] is
 * null then.
 */
data class SyncProgress(
    val stage: Stage,
    val done: Int,
    val total: Int?,
) {
    enum class Stage { RECEIVING, SENDING }
}
