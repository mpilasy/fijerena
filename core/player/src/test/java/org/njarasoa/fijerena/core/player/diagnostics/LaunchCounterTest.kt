package org.njarasoa.fijerena.core.player.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LaunchCounterTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000_000_000L

    private fun counter(file: File = File(tmp.root, "safemode/launches")) = LaunchCounter(file) { now }

    private val minute = 60_000L

    @Test
    fun `three unfinished launches within ten minutes put the next one in safe mode`() {
        val counter = counter()
        assertFalse(counter.recordLaunch())
        now += minute
        assertFalse(counter.recordLaunch())
        now += minute
        assertFalse(counter.recordLaunch())
        now += minute
        assertTrue(counter.recordLaunch())
    }

    @Test
    fun `the count survives a new counter on the same file, as a new process would see it`() {
        val file = File(tmp.root, "launches")
        repeat(LaunchCounter.THRESHOLD) {
            counter(file).recordLaunch()
            now += minute
        }
        assertTrue(counter(file).recordLaunch())
    }

    @Test
    fun `a healthy mark resets the count`() {
        val counter = counter()
        repeat(LaunchCounter.THRESHOLD - 1) {
            counter.recordLaunch()
            now += minute
        }
        counter.recordLaunch()
        counter.markHealthy()
        now += minute
        assertFalse(counter.recordLaunch())
        now += minute
        assertFalse(counter.recordLaunch())
    }

    @Test
    fun `launches spread over more than ten minutes don't trigger`() {
        val counter = counter()
        repeat(6) {
            assertFalse(counter.recordLaunch())
            now += 6 * minute
        }
    }

    @Test
    fun `a corrupt counter file means not active and is replaced`() {
        val file = File(tmp.root, "launches")
        file.writeText("garbage\n\u0000\u0001\nnot-a-number\n")
        val counter = counter(file)
        assertFalse(counter.recordLaunch())
        now += minute
        assertFalse(counter.recordLaunch())
    }

    @Test
    fun `an unreadable counter file never throws and means not active`() {
        // A directory where the file should be: every read and write fails.
        val file = tmp.newFolder("launches")
        val counter = counter(file)
        repeat(LaunchCounter.THRESHOLD + 1) {
            assertFalse(counter.recordLaunch())
            now += minute
        }
        counter.markHealthy()
    }

    @Test
    fun `a launch time in the future (clock moved back) is not counted`() {
        val file = File(tmp.root, "launches")
        file.writeText(List(LaunchCounter.THRESHOLD) { now + 5 * minute }.joinToString("\n"))
        assertFalse(counter(file).recordLaunch())
    }
}
