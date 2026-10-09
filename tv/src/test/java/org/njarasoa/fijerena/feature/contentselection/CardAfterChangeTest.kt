package org.njarasoa.fijerena.feature.contentselection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardAfterChangeTest {
    private val channels = listOf("a", "b", "c")

    @Test
    fun theNextCardTakesTheRemovedCardsPlace() {
        val rows = mapOf(HomeShelf.CHANNELS to listOf("a", "c"))
        assertEquals(HomeCard(HomeShelf.CHANNELS, "c"), cardAfterChange(HomeShelf.CHANNELS, channels, "b", rows))
    }

    @Test
    fun thePreviousCardWhenTheLastOneWent() {
        val rows = mapOf(HomeShelf.CHANNELS to listOf("a", "b"))
        assertEquals(HomeCard(HomeShelf.CHANNELS, "b"), cardAfterChange(HomeShelf.CHANNELS, channels, "c", rows))
    }

    @Test
    fun anUndoLandsOnTheCardPutBack() {
        val rows = mapOf(HomeShelf.CHANNELS to channels)
        assertEquals(HomeCard(HomeShelf.CHANNELS, "b"), cardAfterChange(HomeShelf.CHANNELS, listOf("a", "c"), "b", rows))
    }

    @Test
    fun anEmptiedRowHandsFocusToTheFirstCardOfTheNextRowWithCards() {
        val rows =
            mapOf(
                HomeShelf.CONTINUE_WATCHING to listOf("m1"),
                HomeShelf.CHANNELS to emptyList(),
                HomeShelf.FAVORITE_CHANNELS to emptyList(),
                HomeShelf.FAVORITE_MOVIES to listOf("f1", "f2"),
            )
        assertEquals(HomeCard(HomeShelf.FAVORITE_MOVIES, "f1"), cardAfterChange(HomeShelf.CHANNELS, listOf("a"), "a", rows))
    }

    @Test
    fun theLastRowEmptiedHandsFocusToTheNearestRowAbove() {
        val rows =
            mapOf(
                HomeShelf.CONTINUE_WATCHING to listOf("m1"),
                HomeShelf.CHANNELS to listOf("a", "b"),
                HomeShelf.FAVORITE_SHOWS to emptyList(),
            )
        assertEquals(HomeCard(HomeShelf.CHANNELS, "a"), cardAfterChange(HomeShelf.FAVORITE_SHOWS, listOf("s1"), "s1", rows))
    }

    @Test
    fun nothingWhenHomeHasNoCardLeft() {
        val rows = mapOf(HomeShelf.CONTINUE_WATCHING to emptyList<String>())
        assertNull(cardAfterChange(HomeShelf.CONTINUE_WATCHING, listOf("m1"), "m1", rows))
    }
}

class WithCardPutBackTest {
    private val before = listOf("a", "b", "c")

    @Test
    fun theCardGoesBackWhereItWas() {
        assertEquals(before, listOf("a", "c").withCardPutBack(before, "b") { it })
    }

    @Test
    fun theLastCardGoesBackAtTheEndOfAShorterRow() {
        assertEquals(listOf("a", "c"), listOf("a").withCardPutBack(before, "c") { it })
    }

    @Test
    fun aCardAlreadyThereIsNotAddedTwice() {
        assertEquals(before, before.withCardPutBack(before, "b") { it })
    }
}
