package org.njarasoa.fijerena.core.player.source

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException

/**
 * A live MPEG-TS stream whose connection drops or ends: the connection closes at once, [onDrop]
 * asks the service for a fresh start of the channel at the live point (the health check's
 * reconnect, which keeps the last frame on screen), and the read waits to be replaced. Without it
 * a drop went through Media3's retries (a byte-range request a live stream can't honour) or the
 * 30 s read timeout, then an error and a hard retry.
 *
 * Not a reopen of the same request inside this source: a server that restarts the channel's
 * encoder sends timestamps from zero again, and the player, still on the old timeline, waited for
 * them for ever (seen on the emulator, 2026-11-19). A fresh start has a fresh timeline.
 *
 * When no fresh start replaces this load within [waitMs], the drop goes to the player as before.
 * Only for live progressive streams, never HLS or a film. See the mobile data playback plan →
 * Phase 3.
 */
@OptIn(UnstableApi::class)
class LiveDropDataSource(
    private val upstream: DataSource,
    private val onDrop: (reason: String) -> Unit,
    private val waitMs: Long = WAIT_MS,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : DataSource {
    private var handedOver = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        handedOver = false
        return upstream.open(dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val result =
            try {
                upstream.read(buffer, offset, length)
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: IOException) {
                handOver("connection dropped: ${e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName}")
                throw e
            }
        if (result == C.RESULT_END_OF_INPUT) handOver("the server ended the stream")
        return result
    }

    // Closes the connection (a 1-connection account must not count it during the fresh start),
    // asks for the fresh start, and waits for it to cancel this load.
    private fun handOver(reason: String) {
        if (handedOver) return
        handedOver = true
        try {
            upstream.close()
        } catch (_: IOException) {
        }
        onDrop(reason)
        try {
            sleep(waitMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Replaced by a fresh start")
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    companion object {
        const val WAIT_MS = 5_000L
    }
}
