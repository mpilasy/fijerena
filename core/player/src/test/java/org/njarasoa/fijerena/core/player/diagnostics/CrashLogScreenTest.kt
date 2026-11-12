package org.njarasoa.fijerena.core.player.diagnostics

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Each new crash log entry records the screen on top, and [CrashLog.parse] splits it out again (the device diagnostics sync plan). */
class CrashLogScreenTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var originalHandler: Thread.UncaughtExceptionHandler

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler() ?: Thread.UncaughtExceptionHandler { _, _ -> }
        val context = mockk<Context>()
        every { context.filesDir } returns tmp.root
        CrashLog.install(context)
        CrashLog.clear()
        CrashLog.stackTraceOf = { it.stackTraceToString() }
    }

    @After
    fun tearDown() {
        CrashLog.screen = null
        CrashLog.stackTraceOf = { Log.getStackTraceString(it) }
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun `an entry records the screen and parses back into its parts`() {
        CrashLog.screen = "MovieDetails"

        CrashLog.note("guide refresh failed", "HTTP 404")

        val raw = CrashLog.read().single()
        assertTrue(raw, raw.substringBefore('\n').endsWith(" guide refresh failed · screen: MovieDetails"))
        val entry = CrashLog.parse(raw)
        assertEquals("guide refresh failed", entry.source)
        assertEquals("MovieDetails", entry.screen)
        assertEquals("HTTP 404", entry.body)
        assertTrue(entry.timeMs > 0)
    }

    @Test
    fun `an entry with no screen, as written before, parses with none`() {
        val entry = CrashLog.parse("1700000000000 uncaught on main\njava.lang.IllegalStateException: boom\n\tat a.b(C.kt:1)")

        assertEquals(1_700_000_000_000L, entry.timeMs)
        assertEquals("uncaught on main", entry.source)
        assertNull(entry.screen)
        assertTrue(entry.body.startsWith("java.lang.IllegalStateException: boom"))
    }
}
