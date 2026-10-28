package org.njarasoa.fijerena.core.network.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.HttpStatusException
import org.njarasoa.fijerena.core.network.serverAnswer
import java.net.SocketTimeoutException

/**
 * Which catalogue failures are tried again, and how long the sync waits first: a timeout inside a
 * JSON error and a panel down behind Cloudflare (521) were taken as permanent and never retried.
 */
class ProviderSyncRunnerRetryTest {
    private val timeoutInJson = Exception("Deserialization failed", SocketTimeoutException("timeout"))

    @Test
    fun aTimeoutInsideAJsonErrorIsTransient() {
        assertTrue(ProviderSyncRunner.isTransient(timeoutInJson))
        assertTrue(ProviderSyncRunner.isTransient(CatalogSyncException(listOf(timeoutInJson))))
    }

    @Test
    fun serverErrorsAreTransientAndRefusalsAreNot() {
        assertTrue(ProviderSyncRunner.isTransient(serverAnswer(521)))
        assertTrue(ProviderSyncRunner.isTransient(serverAnswer(429)))
        assertTrue(ProviderSyncRunner.isTransient(HttpStatusException(503)))
        assertFalse(ProviderSyncRunner.isTransient(HttpStatusException(401)))
        assertFalse(ProviderSyncRunner.isTransient(HttpStatusException(404)))
        assertFalse(ProviderSyncRunner.isTransient(Exception("Stored credentials are invalid")))
    }

    @Test
    fun aServerAnswerWaitsMinutesAndANetworkFailureSeconds() {
        assertEquals(5_000L, ProviderSyncRunner.backoffMs(timeoutInJson, attempt = 1))
        assertEquals(10_000L, ProviderSyncRunner.backoffMs(timeoutInJson, attempt = 2))
        assertEquals(120_000L, ProviderSyncRunner.backoffMs(serverAnswer(521), attempt = 1))
        assertEquals(120_000L, ProviderSyncRunner.backoffMs(CatalogSyncException(listOf(serverAnswer(521))), attempt = 1))
        assertEquals(30_000L, ProviderSyncRunner.backoffMs(serverAnswer(503, retryAfter = "30"), attempt = 1))
        assertEquals(300_000L, ProviderSyncRunner.backoffMs(serverAnswer(503, retryAfter = "3600"), attempt = 1))
    }
}
