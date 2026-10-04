package org.njarasoa.fijerena.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionRootTest {
    private fun rootOf(vararg stack: String): Int? = sectionRootIndex(stack.toList()) { it == HOME }

    @Test
    fun `hidden at depths 1 to 3`() {
        assertNull(rootOf(GRAPH, HOME))
        assertNull(rootOf(GRAPH, HOME, "Movies"))
        assertNull(rootOf(GRAPH, HOME, "Movies", "film"))
        assertNull(rootOf(GRAPH, HOME, "Movies", "film", "related"))
    }

    @Test
    fun `at depth 4 and beyond the root is the entry directly above Home`() {
        assertEquals(2, rootOf(GRAPH, HOME, "Movies", "film", "related", "category"))
        assertEquals(2, rootOf(GRAPH, HOME, "Settings", "Sources", "Edit Source", "Guide sources"))
        assertEquals(2, rootOf(GRAPH, HOME, "Movies", "film", "Movies", "film", "Movies"))
    }

    @Test
    fun `the graph entry below Home does not count`() {
        assertEquals(1, rootOf(HOME, "Search", "film", "related", "related"))
    }

    @Test
    fun `hidden without Home on the stack`() {
        assertNull(rootOf(GRAPH, "Settings", "Sources", "Edit Source", "Guide sources", "more"))
    }

    @Test
    fun `counts from the last Home`() {
        assertNull(rootOf(GRAPH, HOME, "a", "b", HOME, "Movies", "film"))
        assertEquals(5, rootOf(GRAPH, HOME, "a", "b", HOME, "Movies", "film", "related", "category"))
    }

    private companion object {
        const val GRAPH = "graph"
        const val HOME = "home"
    }
}
