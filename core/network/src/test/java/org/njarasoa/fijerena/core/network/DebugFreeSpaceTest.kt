package org.njarasoa.fijerena.core.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The debug free-space value (set over adb, the low storage plan → LS-Q6) is what every storage
 * check reads while it is set: the low-storage check and the space check of each large write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugFreeSpaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mb = 1024L * 1024

    @After
    fun tearDown() {
        StorageSpace.debugFreeBytes = null
    }

    @Test
    fun `the checks read the debug value while it is set`() {
        // The test's storage is the build machine's disk: more than 10 GB, so low below 500 MB.
        StorageSpace.debugFreeBytes = 100 * mb
        assertEquals(100 * mb, FreeSpace.of(context).freeBytes())
        assertEquals(100 * mb, StorageSpace.lowFreeBytes(context))
        assertEquals(ErrorKind.STORAGE_LOW, StorageSpace.shortfall(200 * mb, FreeSpace.of(context))?.kind)
    }

    @Test
    fun `without the debug value the checks read the real free space`() {
        StorageSpace.debugFreeBytes = 100 * mb
        StorageSpace.debugFreeBytes = null
        assertNotEquals(100 * mb, FreeSpace.of(context).freeBytes())
        assertNull(StorageSpace.shortfall(0, FreeSpace.of(context)))
    }
}
