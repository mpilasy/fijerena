package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The features a build reports to its group (the send to TV plan, ST-D3). */
class DeviceBuildTest {
    @Test
    fun `a build of this version reports play requests`() {
        val pm = mockk<PackageManager>()
        val info = mockk<PackageInfo>(relaxed = true)
        every { info.longVersionCode } returns 90L
        every { pm.getPackageInfo("org.njarasoa.fijerena", 0) } returns info
        every { pm.getApplicationInfo("org.njarasoa.fijerena", PackageManager.GET_META_DATA) } returns
            ApplicationInfo().apply { metaData = mockk<Bundle>(relaxed = true) }
        val context =
            mockk<Context> {
                every { packageName } returns "org.njarasoa.fijerena"
                every { packageManager } returns pm
            }

        val build = DeviceBuild.current(context)

        assertEquals("tv", build.app)
        assertTrue(build.takesPlayRequests)
    }

    @Test
    fun `a build record from before the feature reads as no features`() {
        val text = """{"versionName":"0.9.1","versionCode":5,"buildType":"debug","app":"tv"}"""

        val build = SyncPayloads.json.decodeFromString(DeviceBuild.serializer(), text)

        assertEquals(emptyList<String>(), build.features)
        assertFalse(build.takesPlayRequests)
    }

    @Test
    fun `features survive the record's round trip`() {
        val build = DeviceBuild(app = "tv", features = listOf(DeviceBuild.FEATURE_PLAY_REQUEST))

        val back =
            SyncPayloads.json.decodeFromString(
                DeviceBuild.serializer(),
                SyncPayloads.json.encodeToString(DeviceBuild.serializer(), build),
            )

        assertEquals(build, back)
        assertTrue(back.takesPlayRequests)
    }
}
