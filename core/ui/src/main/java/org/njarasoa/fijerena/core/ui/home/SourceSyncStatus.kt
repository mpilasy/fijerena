package org.njarasoa.fijerena.core.ui.home

/** What Home's source status says about the active source's catalogue sync. */
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
