package org.njarasoa.fijerena.core.network.local

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import java.io.File
import kotlin.system.measureTimeMillis

class LocalFileScannerTest {
    @Before
    fun setup() {
        mockkStatic(android.provider.DocumentsContract::class)
        mockkStatic(androidx.documentfile.provider.DocumentFile::class)
        mockkStatic(Uri::class)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun testResolveToFileOptimization() {
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        val rootUri = mockk<Uri>()

        every { context.contentResolver } returns contentResolver
        every { rootUri.scheme } returns "content"
        every { rootUri.toString() } returns "content://media/external/file/123"
        every { rootUri.path } returns "/media/external/file/123"

        // Setup a real temporary directory
        val tempDir =
            java.nio.file.Files
                .createTempDirectory("test_media")
                .toFile()
        val catDir1 = File(tempDir, "cat1").apply { mkdir() }
        File(catDir1, "video1.mp4").createNewFile()
        File(catDir1, "video2.mp4").createNewFile()

        val subDir = File(catDir1, "sub").apply { mkdir() }
        File(subDir, "video3.mp4").createNewFile()

        val catDir2 = File(tempDir, "cat2").apply { mkdir() }
        File(catDir2, "video4.mkv").createNewFile()
        File(catDir2, "notavideo.txt").createNewFile()

        // Mock ContentResolver to return the _data path
        val cursor = mockk<Cursor>()
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns tempDir.absolutePath
        every { cursor.close() } returns Unit

        every {
            contentResolver.query(rootUri, arrayOf("_data"), null, null, null)
        } returns cursor

        // Mock Uri.fromFile
        val mockFileUri = mockk<Uri>()
        every { mockFileUri.scheme } returns "file"
        every { mockFileUri.path } returns tempDir.absolutePath
        every { Uri.fromFile(any()) } answers {
            val f = it.invocation.args[0] as File
            val u = mockk<Uri>()
            every { u.scheme } returns "file"
            every { u.path } returns f.absolutePath
            every { u.toString() } returns "file://${f.absolutePath}"
            u
        }

        // Also mock DocumentsContract throwing exception to simulate fallback
        every { android.provider.DocumentsContract.isDocumentUri(context, rootUri) } returns false
        every { android.provider.DocumentsContract.getTreeDocumentId(rootUri) } throws IllegalArgumentException("Not a tree uri")

        // We shouldn't hit DocumentFile.fromTreeUri because it should resolve, but mock it just in case
        every {
            androidx.documentfile.provider.DocumentFile
                .fromTreeUri(context, rootUri)
        } returns null

        // Run scanner
        var result: Pair<List<MediaCategory>, List<MediaItem>>? = null
        val time =
            measureTimeMillis {
                result = LocalFileScanner.scanDirectory(context, rootUri)
            }

        assertEquals(2, result?.first?.size)
        assertEquals(4, result?.second?.size)

        println("Resolved File optimization took $time ms")

        // Cleanup
        tempDir.deleteRecursively()
    }
}
