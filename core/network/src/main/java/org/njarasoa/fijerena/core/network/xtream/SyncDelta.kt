package org.njarasoa.fijerena.core.network.xtream

/**
 * How many rows a sync actually changed. `XtreamContentManager` already computes this per row
 * (content-hash diff against what's stored) to decide what to write — this just keeps the count
 * instead of discarding it. All-zero means the provider's data didn't change since the last sync.
 */
data class SyncDelta(
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
) {
    val isEmpty: Boolean get() = inserted == 0 && updated == 0 && deleted == 0

    operator fun plus(other: SyncDelta) =
        SyncDelta(
            inserted = inserted + other.inserted,
            updated = updated + other.updated,
            deleted = deleted + other.deleted,
        )
}
