package org.njarasoa.fijerena.core.player.source

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException

/** The mobile data playback plan → Phase 3. */
class LiveDropDataSourceTest {
    /** Gives [bytes], then ends with [ending] (null: end of input). */
    private class OneShotSource(
        private var bytes: Int,
        private val ending: IOException?,
    ) : DataSource {
        val events = mutableListOf<String>()

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            events += "open"
            return C.LENGTH_UNSET.toLong()
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (bytes > 0) {
                val n = minOf(bytes, length)
                bytes -= n
                return n
            }
            ending?.let { throw it }
            return C.RESULT_END_OF_INPUT
        }

        override fun getUri(): Uri? = null

        override fun close() {
            events += "close"
        }
    }

    private val spec = DataSpec(mockk<Uri>(relaxed = true))

    @Test
    fun `a drop closes the connection, asks for a fresh start and is cancelled by it`() {
        val upstream = OneShotSource(10, IOException("reset"))
        val source =
            LiveDropDataSource(
                upstream,
                onDrop = { upstream.events += "drop: $it" },
                // The fresh start cancels the load, which interrupts the loading thread.
                sleep = { throw InterruptedException() },
            )
        source.open(spec)
        assertEquals(10, source.read(ByteArray(10), 0, 10))
        assertThrows(InterruptedIOException::class.java) { source.read(ByteArray(10), 0, 10) }
        // Closed before the fresh start can open another connection.
        assertEquals(listOf("open", "close", "drop: connection dropped: IOException"), upstream.events)
        Thread.interrupted() // clear the flag the source set again
    }

    @Test
    fun `with no fresh start the drop goes to the player as before`() {
        val failure = IOException("reset")
        val source = LiveDropDataSource(OneShotSource(0, failure), onDrop = {}, sleep = {})
        source.open(spec)
        val thrown = assertThrows(IOException::class.java) { source.read(ByteArray(10), 0, 10) }
        assertEquals(failure, thrown)
    }

    @Test
    fun `an end of the stream asks for a fresh start, then ends as before`() {
        val reasons = mutableListOf<String>()
        val source = LiveDropDataSource(OneShotSource(0, null), onDrop = { reasons += it }, sleep = {})
        source.open(spec)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(10), 0, 10))
        assertEquals(listOf("the server ended the stream"), reasons)
    }
}
