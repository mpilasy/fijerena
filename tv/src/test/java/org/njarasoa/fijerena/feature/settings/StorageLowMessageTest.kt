package org.njarasoa.fijerena.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.njarasoa.fijerena.core.network.R as NetworkR

/**
 * The storage-low message says where to clear space, with the Settings paths in the words of the
 * screens (the low storage plan, LS-Q4). A renamed row or section fails this test, in each
 * language, so the message never names a page that is not there.
 */
@RunWith(RobolectricTestRunner::class)
class StorageLowMessageTest {
    @Test
    fun `the message names the Settings pages as the screens do`() {
        val messages =
            listOf("en", "fr", "mg").map { language ->
                RuntimeEnvironment.setQualifiers(language)
                ApplicationProvider.getApplicationContext<Context>().getString(NetworkR.string.error_storage_low)
            }
        // Each language has its own text, so each check below reads that language.
        assertEquals(3, messages.toSet().size)
        listOf("en", "fr", "mg").forEach { language ->
            RuntimeEnvironment.setQualifiers(language)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val message = context.getString(NetworkR.string.error_storage_low)
            val settings = context.getString(R.string.settings_title)
            val deviceInfo =
                listOf(R.string.settings_about_section_title, R.string.device_info_open)
                    .joinToString(" → ", prefix = "$settings → ") { context.getString(it) }
            val guideData =
                listOf(R.string.settings_group_source_guide, R.string.settings_guide_maintenance_title)
                    .joinToString(" → ", prefix = "$settings → ") { context.getString(it) }
            val imageCache = context.getString(R.string.device_info_image_cache)
            assertTrue("$language: \"$deviceInfo\" in \"$message\"", deviceInfo in message)
            assertTrue("$language: \"$guideData\" in \"$message\"", guideData in message)
            assertTrue("$language: \"$imageCache\" in \"$message\"", message.contains(imageCache, ignoreCase = true))
        }
    }
}
