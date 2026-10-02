package org.njarasoa.fijerena.core.network.diagnostics

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import java.io.File

/**
 * Lives in core:network rather than core:player because only this module's unit tests run with
 * `isReturnDefaultValues` (android.util.Log) and mockk. See
 * docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-24, F-30.
 */
class AppScopesCrashLogTest {
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
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun `a failing launch is recorded and the scope keeps running its siblings`() =
        runBlocking {
            val scope = AppScopes.create("test", Dispatchers.IO)
            scope.launch { throw IllegalStateException("boom") }.join()

            val sibling = CompletableDeferred<Unit>()
            scope.launch { sibling.complete(Unit) }
            withTimeout(5_000) { sibling.await() }

            assertTrue(scope.isActive)
            val entries = CrashLog.read()
            assertEquals(1, entries.size)
            assertTrue(entries.single().substringBefore('\n').endsWith("scope test"))
        }

    @Test
    fun `entries come back newest first and clear empties the log`() {
        CrashLog.record("first", RuntimeException())
        CrashLog.record("second", RuntimeException())

        assertEquals(listOf("second", "first"), CrashLog.read().map { it.substringBefore('\n').substringAfter(' ') })
        CrashLog.clear()
        assertTrue(CrashLog.read().isEmpty())
    }

    @Test
    fun `the log stays bounded`() {
        val big = "x".repeat(10_000)
        repeat(100) { CrashLog.record(big, RuntimeException()) }

        val file = File(tmp.root, "crashlog/crashes.log")
        assertTrue("log grew to ${file.length()} bytes", file.length() <= 256 * 1024 + 11_000)
        assertTrue(CrashLog.read().isNotEmpty())
    }

    @Test
    fun `uncaught exceptions are recorded and still reach the previous handler`() {
        var forwarded: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> forwarded = e }
        val context = mockk<Context>()
        every { context.filesDir } returns tmp.root
        CrashLog.install(context)

        val crash = IllegalArgumentException("fatal")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), crash)

        assertSame(crash, forwarded)
        assertTrue(
            CrashLog
                .read()
                .single()
                .substringBefore('\n')
                .contains("uncaught on"),
        )
    }
}
