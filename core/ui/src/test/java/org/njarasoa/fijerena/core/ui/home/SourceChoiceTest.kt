package org.njarasoa.fijerena.core.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** A setting edited per source asks which source only when there is a choice (multi-source plan, O4). */
class SourceChoiceTest {
    @Test
    fun `no source, no entry`() {
        assertEquals(SourceChoice.None, SourceChoice.of(emptyList<Long>()))
    }

    @Test
    fun `one source opens straight away`() {
        assertEquals(SourceChoice.One(7L), SourceChoice.of(listOf(7L)))
    }

    @Test
    fun `the chooser is shown with two or more`() {
        assertEquals(SourceChoice.Ask(listOf(7L, 9L)), SourceChoice.of(listOf(7L, 9L)))
    }
}
