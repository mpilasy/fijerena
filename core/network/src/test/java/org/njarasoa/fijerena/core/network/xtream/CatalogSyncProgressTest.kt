package org.njarasoa.fijerena.core.network.xtream

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSyncProgressTest {
    @After
    fun tearDown() {
        CatalogPart.entries.forEach { part ->
            CatalogSyncProgress.done(SOURCE, part)
            CatalogSyncProgress.done(OTHER, part)
        }
    }

    @Test
    fun reportsKeepTheLatestCountPerSourceAndPart() {
        CatalogSyncProgress.report(SOURCE, CatalogPart.FILMS, 500)
        CatalogSyncProgress.report(SOURCE, CatalogPart.FILMS, 12_400)
        CatalogSyncProgress.report(SOURCE, CatalogPart.SHOWS, 3_100)
        CatalogSyncProgress.report(OTHER, CatalogPart.CHANNELS, 40)

        assertEquals(
            mapOf(CatalogPart.FILMS to 12_400, CatalogPart.SHOWS to 3_100),
            CatalogSyncProgress.counts.value[SOURCE],
        )
        assertEquals(mapOf(CatalogPart.CHANNELS to 40), CatalogSyncProgress.counts.value[OTHER])
    }

    @Test
    fun aFinishedPartLeavesAndTheSourceWithIt() {
        CatalogSyncProgress.report(SOURCE, CatalogPart.FILMS, 10)
        CatalogSyncProgress.report(SOURCE, CatalogPart.SHOWS, 20)

        CatalogSyncProgress.done(SOURCE, CatalogPart.FILMS)
        assertEquals(mapOf(CatalogPart.SHOWS to 20), CatalogSyncProgress.counts.value[SOURCE])

        CatalogSyncProgress.done(SOURCE, CatalogPart.SHOWS)
        assertFalse(SOURCE in CatalogSyncProgress.counts.value)
    }

    @Test
    fun doneForAPartNeverReportedChangesNothing() {
        CatalogSyncProgress.done(SOURCE, CatalogPart.CHANNELS)
        assertFalse(SOURCE in CatalogSyncProgress.counts.value)
    }

    @Test
    fun throttleLetsTheFirstCountThroughThenOnePerInterval() {
        var now = 1_000L
        val throttle = ProgressThrottle(intervalMs = 300L, clockMs = { now })

        assertTrue(throttle.ready())
        now += 299
        assertFalse(throttle.ready())
        now += 1
        assertTrue(throttle.ready())
        assertFalse(throttle.ready())
        now += 1_000
        assertTrue(throttle.ready())
    }

    private companion object {
        const val SOURCE = 41L
        const val OTHER = 42L
    }
}
