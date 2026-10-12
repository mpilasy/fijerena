package org.njarasoa.fijerena.core.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's one line for all the profile's sources (docs/plans/20261012_multi-source-search-history-plan.md → O3). */
class SyncSummaryTest {
    private fun source(
        id: Long,
        syncing: Boolean = false,
        lastSyncedAtMs: Long = 0L,
        error: String? = null,
        type: String = "XTREAM",
    ) = SourceSyncState(id, "S$id", type, syncing, lastSyncedAtMs, error)

    @Test
    fun `nothing when no source ever syncs`() {
        val summary = syncSummary(listOf(source(1, type = "JELLYFIN"), source(2, type = "REMOTE_M3U")))

        assertEquals(SourceSyncStatus.NONE, summary.status)
        assertEquals(0, summary.total)
        assertFalse(summary.opensSheet)
    }

    @Test
    fun `updated says the least recent update, over the sources that sync`() {
        val summary =
            syncSummary(listOf(source(1, lastSyncedAtMs = 5_000L), source(2, lastSyncedAtMs = 2_000L), source(3, type = "JELLYFIN")))

        assertEquals(SourceSyncStatus.UPDATED, summary.status)
        assertEquals(2_000L, summary.updatedAtMs)
        assertEquals(2, summary.total)
        assertTrue(summary.opensSheet)
    }

    @Test
    fun `a failure wins over the others' updates and is counted`() {
        val summary =
            syncSummary(
                listOf(source(1, lastSyncedAtMs = 5_000L), source(2, lastSyncedAtMs = 6_000L, error = "x"), source(3, lastSyncedAtMs = 1L)),
            )

        assertEquals(SourceSyncStatus.FAILED, summary.status)
        assertEquals(1, summary.failed)
        assertEquals(3, summary.total)
        assertEquals(0L, summary.updatedAtMs)
    }

    @Test
    fun `a running sync wins, counted among the sources that sync, and names its source`() {
        val summary =
            syncSummary(
                listOf(
                    source(1, lastSyncedAtMs = 5_000L, error = "x"),
                    source(2, syncing = true),
                    source(3, lastSyncedAtMs = 1L),
                ),
            )

        assertEquals(SourceSyncStatus.UPDATING, summary.status)
        assertEquals(1, summary.updating)
        assertEquals(3, summary.total)
        assertEquals(2L, summary.updatingProviderId)
    }

    @Test
    fun `a new source that hasn't synced yet doesn't count until it does`() {
        val summary = syncSummary(listOf(source(1, lastSyncedAtMs = 5_000L), source(2)))

        assertEquals(SourceSyncStatus.UPDATED, summary.status)
        assertEquals(1, summary.total)
        assertNull(summary.updatingProviderId)
    }
}
