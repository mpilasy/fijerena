package org.njarasoa.fijerena.core.ui.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.DeviceBuild

/** The "Older build" mark of the Live Sync device list (the device builds plan, DB-Q2). */
class DeviceBuildLineTest {
    private val new = DeviceBuild(versionName = "0.9.2", versionCode = 87)
    private val old = DeviceBuild(versionName = "0.9.1", versionCode = 86)

    @Test
    fun `a build below the newest in the list is older`() {
        assertTrue(isOlderBuild(old, listOf(new, old)))
        assertFalse(isOlderBuild(new, listOf(new, old)))
    }

    @Test
    fun `an unknown build is never older`() {
        assertFalse(isOlderBuild(null, listOf(new)))
        assertFalse(isOlderBuild(DeviceBuild(), listOf(new)))
    }
}
