package org.njarasoa.fijerena.core.network

import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * A full disk (ENOSPC, SQLITE_FULL) is its own error: it read as "network error" and was tried
 * again (the low storage plan, Phase 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiskFullTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // What libcore throws when a write finds no free space, with the errno as its cause.
    private val enospc = IOException("write failed: ENOSPC (No space left on device)", ErrnoException("write", OsConstants.ENOSPC))

    @Test
    fun `each form of a full disk is found, also as a cause`() {
        assertTrue(SQLiteFullException("database or disk is full").isDiskFull())
        assertTrue(enospc.isDiskFull())
        assertTrue(IOException("write failed", ErrnoException("write", OsConstants.ENOSPC)).isDiskFull())
        assertTrue(IOException("No space left on device").isDiskFull())
        assertTrue(Exception("Deserialization failed", enospc).isDiskFull())
        assertTrue(IllegalStateException("insert failed", SQLiteFullException("database or disk is full")).isDiskFull())
    }

    @Test
    fun `other failures are not a full disk`() {
        assertFalse(IOException("connection reset").isDiskFull())
        assertFalse(IOException("read failed", ErrnoException("read", OsConstants.EIO)).isDiskFull())
        assertFalse(SocketTimeoutException("timeout").isDiskFull())
        assertFalse(Exception("No space left on device").isDiskFull())
    }

    @Test
    fun `a full disk reads as storage low, never as a network error`() {
        assertEquals(ErrorKind.STORAGE_LOW, errorKind(enospc, context))
        assertEquals(ErrorKind.STORAGE_LOW, errorKind(SQLiteFullException("database or disk is full"), context))
        assertEquals(ErrorKind.STORAGE_LOW, errorKind(Exception("Deserialization failed", enospc), context))
        assertEquals(ErrorKind.NETWORK, errorKind(IOException("connection reset"), context))
        assertEquals(ErrorKind.STORAGE_LOW, StoredError.of(enospc, context).kind)
    }

    @Test
    fun `a full disk is not a network cause`() {
        assertNull(enospc.networkCause())
        assertNull(Exception("Deserialization failed", enospc).networkCause())
        val reset = IOException("connection reset")
        assertSame(reset, Exception("Deserialization failed", reset).networkCause())
    }

    @Test
    fun `the tries stop after a full disk`() {
        var failure: Throwable? = null
        val run = mutableListOf<Int>()
        triesUntilDiskFull(5) { failure }.forEach { attempt ->
            run += attempt
            failure = if (attempt == 1) IOException("connection reset") else enospc
        }
        assertEquals(listOf(1, 2), run)
    }

    @Test
    fun `the tries go on to the last without a full disk`() {
        assertEquals(listOf(1, 2, 3), triesUntilDiskFull(3) { IOException("connection reset") }.toList())
    }

    // The low storage plan, Phase 2: a job that did not start for lack of space.
    @Test
    fun `not enough space reads as storage low and stops the tries`() {
        val notEnough = NotEnoughSpaceException(StoredError(ErrorKind.STORAGE_LOW, raw = StorageSpace.needText(420L shl 20, 180L shl 20)))

        assertTrue(notEnough.isDiskFull())
        assertNull(notEnough.networkCause())
        assertEquals(ErrorKind.STORAGE_LOW, errorKind(notEnough, context))
        assertEquals(StoredError(ErrorKind.STORAGE_LOW, raw = "Need 420 MB, 180 MB free"), StoredError.of(notEnough, context))
        var failure: Throwable? = null
        assertEquals(listOf(1), triesUntilDiskFull(5) { failure }.onEach { failure = notEnough }.toList())
    }
}
