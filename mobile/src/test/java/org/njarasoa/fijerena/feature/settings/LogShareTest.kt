package org.njarasoa.fijerena.feature.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Logs → Share on the phone: the log goes as a file through the FileProvider, never as the text
 * extra, which crashed the app with a large log (TransactionTooLargeException).
 */
@RunWith(RobolectricTestRunner::class)
class LogShareTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `share sends a content uri of the log file and no large text`() {
        // Larger than the binder transaction limit (about 1 MB).
        val text = "x".repeat(2 * 1024 * 1024)
        val file = writeLogShareFile(context.cacheDir, text, nowMs = 0L)
        val uri = logShareUri(context, file)

        val chooser = buildLogShareIntent(uri, subject = "Fijerena logs", chooserTitle = "Share")

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        send!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        @Suppress("DEPRECATION")
        val stream = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals("content", stream?.scheme)
        assertEquals("${context.packageName}.fileprovider", stream?.authority)
        assertNull(send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Fijerena logs", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val clip: ClipData? = send.clipData
        assertEquals(uri, clip?.getItemAt(0)?.uri)
        assertEquals(text.length.toLong(), file.length())
    }

    @Test
    fun `a new log file replaces the older ones`() {
        val first = writeLogShareFile(context.cacheDir, "first", nowMs = 0L)
        val second = writeLogShareFile(context.cacheDir, "second", nowMs = 60_000L)

        assertFalse(first.exists())
        assertEquals("second", second.readText())
        assertEquals(listOf(second.name), File(context.cacheDir, SHARED_CACHE_DIR).list()?.toList())
    }
}
