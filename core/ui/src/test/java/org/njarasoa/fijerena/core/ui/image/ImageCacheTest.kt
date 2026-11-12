package org.njarasoa.fijerena.core.ui.image

import org.junit.Assert.assertEquals
import org.junit.Test

/** The image cache's limit: 2% of the storage, from 64 MB to 512 MB (the low storage plan, LS-Q2). */
class ImageCacheTest {
    private val mb = 1024L * 1024

    @Test
    fun `the limit is 2 percent of the storage`() {
        // An 8 GB TV: about 160 MB.
        assertEquals(8 * 1024 * mb / 100 * 2, ImageCache.maxBytes(8 * 1024 * mb))
        assertEquals(163, ImageCache.maxBytes(8 * 1024 * mb) / mb)
    }

    @Test
    fun `a small storage keeps 64 MB`() {
        assertEquals(64 * mb, ImageCache.maxBytes(2 * 1024 * mb))
        // A size that the app cannot read.
        assertEquals(64 * mb, ImageCache.maxBytes(0))
    }

    @Test
    fun `a large storage keeps at most 512 MB`() {
        assertEquals(512 * mb, ImageCache.maxBytes(64 * 1024 * mb))
        assertEquals(512 * mb, ImageCache.maxBytes(256 * 1024 * mb))
    }
}
