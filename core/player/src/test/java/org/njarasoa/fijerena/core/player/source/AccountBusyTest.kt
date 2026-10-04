package org.njarasoa.fijerena.core.player.source

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AccountBusyTest {
    @Test
    fun `connection-limit statuses are account busy, others are not`() {
        listOf(456, 458, 460, 511).forEach { assertTrue("$it", AccountBusy.isAccountBusy(it)) }
        listOf(null, 401, 403, 404, 500, 503).forEach { assertFalse("$it", AccountBusy.isAccountBusy(it)) }
    }

    @Test
    fun `refusals keep waiting until the provider's linger has surely passed, then give up`() {
        val wait = AccountBusyWait()
        var now = 1_000L
        while (now - 1_000L < AccountBusy.MAX_WAIT_MS) {
            assertEquals(AccountBusyWait.Decision.RetryLater, wait.next(now))
            now += AccountBusy.RETRY_INTERVAL_MS
        }
        assertEquals(AccountBusyWait.Decision.GiveUp, wait.next(now))
        // Giving up ends that wait: Retry starts a new one.
        assertEquals(AccountBusyWait.Decision.RetryLater, wait.next(now + 1))
    }

    @Test
    fun `a refusal long after the last one starts a new wait`() {
        val wait = AccountBusyWait()
        wait.next(0L)
        wait.next(AccountBusy.MAX_WAIT_MS - 1)
        // Played in between, then refused again much later: not counted against the old wait.
        val later = AccountBusy.MAX_WAIT_MS + AccountBusy.RETRY_INTERVAL_MS * 4
        assertEquals(AccountBusyWait.Decision.RetryLater, wait.next(later))
    }

    @Test
    fun `reset starts over`() {
        val wait = AccountBusyWait()
        wait.next(0L)
        wait.reset()
        assertEquals(AccountBusyWait.Decision.RetryLater, wait.next(AccountBusy.MAX_WAIT_MS * 2))
    }

    @Test
    fun `the loader does not retry an account-busy refusal itself, but retries other errors`() {
        val policy = AdaptiveLoadErrorPolicy()
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(httpError(460))))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(httpError(511))))
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(httpError(503))))
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(IOException("reset"))))
    }

    private fun httpError(code: Int) =
        HttpDataSource.InvalidResponseCodeException(
            code,
            null,
            null,
            emptyMap(),
            DataSpec(mockk<Uri>(relaxed = true)),
            ByteArray(0),
        )

    private fun errorInfo(exception: IOException) =
        LoadErrorHandlingPolicy.LoadErrorInfo(
            mockk<LoadEventInfo>(relaxed = true),
            MediaLoadData(C.DATA_TYPE_MEDIA),
            exception,
            1,
        )
}
