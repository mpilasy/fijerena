package org.njarasoa.fijerena.core.player.source

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The mobile data playback plan → Phase 1: a refused playlist fails at once, so HLS falls back to MPEG-TS. */
class AdaptiveLoadErrorPolicyTest {
    private val policy = AdaptiveLoadErrorPolicy()

    private fun info(
        dataType: Int,
        exception: IOException,
    ) = LoadErrorHandlingPolicy.LoadErrorInfo(mockk<LoadEventInfo>(relaxed = true), MediaLoadData(dataType), exception, 1)

    private fun http(status: Int) =
        HttpDataSource.InvalidResponseCodeException(status, null, null, emptyMap(), DataSpec(mockk<Uri>(relaxed = true)), ByteArray(0))

    @Test
    fun `a refused playlist is not retried`() {
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, http(404))))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, http(403))))
    }

    @Test
    fun `a playlist that doesn't parse is not retried`() {
        assertEquals(
            C.TIME_UNSET,
            policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, ParserException.createForMalformedManifest("x", null))),
        )
    }

    @Test
    fun `a segment 404, a playlist timeout or a 429 keep their retries`() {
        assertTrue(policy.getRetryDelayMsFor(info(C.DATA_TYPE_MEDIA, http(404))) > 0)
        assertTrue(policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, http(429))) > 0)
        assertTrue(policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, IOException("timeout"))) > 0)
    }
}
