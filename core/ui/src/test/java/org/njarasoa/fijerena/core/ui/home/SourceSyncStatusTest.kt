package org.njarasoa.fijerena.core.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.CatalogPart
import java.util.Locale

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

    @Test
    fun nothingCountedYetIsNoPhase() {
        assertNull(syncPhase(null))
        assertNull(syncPhase(emptyMap()))
        assertNull(syncPhase(mapOf(CatalogPart.FILMS to 0)))
    }

    @Test
    fun theFirstPartWithACountInChannelsFilmsShowsOrder() {
        val counts = mapOf(CatalogPart.SHOWS to 3_100, CatalogPart.FILMS to 12_400)
        assertEquals(SyncPhase(CatalogPart.FILMS, 12_400), syncPhase(counts))
        assertEquals(SyncPhase(CatalogPart.CHANNELS, 7), syncPhase(counts + (CatalogPart.CHANNELS to 7)))
    }

    @Test
    fun aPartNotCountingYetGivesWayToOneThatIs() {
        assertEquals(
            SyncPhase(CatalogPart.SHOWS, 3_100),
            syncPhase(mapOf(CatalogPart.CHANNELS to 0, CatalogPart.SHOWS to 3_100)),
        )
    }

    @Test
    fun countsAreGroupedTheLocaleWay() {
        assertEquals("12,400", formatSyncCount(12_400, Locale.UK))
        // French groups with a narrow no-break space (U+202F) on current JDKs, a no-break space on older ones.
        assertTrue(formatSyncCount(12_400, Locale.FRANCE).matches(Regex("12[  ]400")))
        assertEquals("999", formatSyncCount(999, Locale.FRANCE))
    }
}
