package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.ErrorKind
import org.njarasoa.fijerena.core.network.FreeSpace
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.StoredError
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import java.io.File

/**
 * A catalogue sync with less free space than the catalogue needs (25% of xtream_v2.db, at least
 * 100 MB, and the 50 MB margin) does not start, and is not tried again (the low storage plan,
 * Phase 2).
 */
class ProviderSyncRunnerSpaceTest {
    private val mb = 1024L * 1024
    private val provider = ProviderEntity(id = 11, name = "test", url = "http://example.invalid", username = "u")

    // A storage that is not low as Android counts it (8 GB, 1 GB free) and a 1.24 GB catalogue.
    private val context =
        mockk<Context>(relaxed = true).also {
            every { it.filesDir } returns
                mockk<File> {
                    every { totalSpace } returns 8192 * mb
                    every { usableSpace } returns 1024 * mb
                }
            every { it.getDatabasePath(any()) } returns mockk<File> { every { length() } returns 1240 * mb }
        }

    @Before
    fun setUp() {
        mockkObject(MediaProviderFactory)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun notEnoughSpaceStopsTheSyncBeforeItStarts() =
        runTest {
            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw", FreeSpace { 359 * mb })

            val error = StoredError(ErrorKind.STORAGE_LOW, catalogue = true, raw = "Need 360 MB, 359 MB free")
            assertEquals(ProviderSyncRunner.Outcome.Permanent(error), outcome)
            verify(exactly = 0) { MediaProviderFactory.create(any(), any(), any()) }
        }

    @Test
    fun enoughSpaceStartsTheSync() =
        runTest {
            every { MediaProviderFactory.create(provider, context, "pw") } returns mockk(relaxed = true)

            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw", FreeSpace { 360 * mb })

            assertEquals(ProviderSyncRunner.Outcome.Success(), outcome)
            verify(exactly = 1) { MediaProviderFactory.create(provider, context, "pw") }
        }
}
