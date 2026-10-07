package org.njarasoa.fijerena.core.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSyncStatusTest {
    @Test
    fun updatingWinsOverAPastFailure() {
        assertEquals(SourceSyncStatus.UPDATING, sourceSyncStatus(syncing = true, lastSyncedAtMs = 1_000L, lastSyncError = "timeout"))
    }

    @Test
    fun aFailureWinsOverAnEarlierSuccess() {
        assertEquals(SourceSyncStatus.FAILED, sourceSyncStatus(syncing = false, lastSyncedAtMs = 1_000L, lastSyncError = "timeout"))
    }

    @Test
    fun updatedOnceSyncedWithoutError() {
        assertEquals(SourceSyncStatus.UPDATED, sourceSyncStatus(syncing = false, lastSyncedAtMs = 1_000L, lastSyncError = null))
    }

    @Test
    fun noneWhenNeverSynced() {
        assertEquals(SourceSyncStatus.NONE, sourceSyncStatus(syncing = false, lastSyncedAtMs = 0L, lastSyncError = null))
    }
}
