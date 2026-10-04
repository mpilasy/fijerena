package org.njarasoa.fijerena.core.player.diagnostics

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * An exception's own text — ExoPlayer, OkHttp and Ktor put the request URL in it — is masked before
 * it reaches the file that Diagnostics shares. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-16.
 */
class CrashLogRedactionTest {
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
        // Log.getStackTraceString returns null in JVM tests; format the trace as the device would.
        CrashLog.stackTraceOf = { it.stackTraceToString() }
    }

    @After
    fun tearDown() {
        CrashLog.stackTraceOf = { Log.getStackTraceString(it) }
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun `a recorded exception's message and cause are masked in the file`() {
        val cause = IOException("GET http://p.example/live/alice/s3cret/1.ts?password=s3cret failed")
        val failure = IllegalStateException("Source error /live/alice/s3cret/1.ts?password=x", cause)

        CrashLog.record("playback", failure)

        val text = File(tmp.root, "crashlog/crashes.log").readText()
        assertTrue(text, text.contains("Source error /live/***/***/1.ts?password=***"))
        assertTrue(text, text.contains("IOException"))
        assertFalse(text, text.contains("s3cret"))
        assertFalse(text, text.contains("password=x"))
    }
}
