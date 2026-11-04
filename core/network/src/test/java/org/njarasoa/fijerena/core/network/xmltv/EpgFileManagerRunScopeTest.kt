package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which screens see a refresh run's state (`EpgFileManager.stateFor`, one guide source per URL plan). */
class EpgFileManagerRunScopeTest {
    @Test
    fun `before the first run every screen sees the state`() {
        assertTrue(EpgFileManager.isRunFor(null, setOf(1L)))
    }

    @Test
    fun `a run of a shared guide source shows on the screen of each source that reads it`() {
        val run = setOf(1L, 2L)

        assertTrue(EpgFileManager.isRunFor(run, setOf(1L)))
        assertTrue(EpgFileManager.isRunFor(run, setOf(2L)))
        assertTrue(EpgFileManager.isRunFor(run, setOf(2L, 3L)))
    }

    @Test
    fun `a run of other sources shows Idle`() {
        assertFalse(EpgFileManager.isRunFor(setOf(1L, 2L), setOf(3L)))
        assertFalse(EpgFileManager.isRunFor(emptySet(), setOf(3L)))
    }
}
