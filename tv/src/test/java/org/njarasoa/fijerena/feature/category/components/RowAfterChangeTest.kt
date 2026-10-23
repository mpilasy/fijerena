package org.njarasoa.fijerena.feature.category.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowAfterChangeTest {
    private val rows = listOf("a", "b", "c", "d")

    @Test
    fun theNextRowTakesTheRemovedRowsPlace() {
        assertEquals("c", rowAfterChange(rows, "b", listOf("a", "c", "d")))
    }

    @Test
    fun thePreviousRowWhenTheLastOneWent() {
        assertEquals("c", rowAfterChange(rows, "d", listOf("a", "b", "c")))
    }

    @Test
    fun theFirstRowGoingLandsOnTheNewFirst() {
        assertEquals("b", rowAfterChange(rows, "a", listOf("b", "c", "d")))
    }

    @Test
    fun nothingWhenTheListIsEmpty() {
        assertNull(rowAfterChange(listOf("a"), "a", emptyList()))
    }

    @Test
    fun anUndoLandsOnTheRowPutBack() {
        assertEquals("b", rowAfterChange(listOf("a", "c", "d"), "b", rows))
    }

    @Test
    fun aRowThatStaysKeepsFocus() {
        // The Live TV panel's Recent keeps the playing channel listed even once removed.
        assertEquals("b", rowAfterChange(rows, "b", listOf("b", "a", "c", "d")))
    }

    @Test
    fun skipsRowsThatWentToo() {
        // A series removal can take more than one row; the list may also change elsewhere.
        assertEquals("d", rowAfterChange(rows, "b", listOf("a", "d")))
        assertEquals("a", rowAfterChange(rows, "c", listOf("a")))
    }
}
