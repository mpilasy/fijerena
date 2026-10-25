package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every list of profiles is by name, ignoring case and accents; same names keep their stored order. */
class ProfileOrderTest {
    private fun profile(
        id: String,
        name: String,
    ) = ProfileUi(id, name, 0, false)

    @Test
    fun `by name, ignoring case and accents`() {
        val stored = listOf(profile("1", "teen"), profile("2", "Émile"), profile("3", "Atr"), profile("4", "kid"), profile("5", "Eva"))

        assertEquals(listOf("Atr", "Émile", "Eva", "kid", "teen"), sortedByName(stored).map { it.name })
    }

    @Test
    fun `same names keep the stored order`() {
        val stored = listOf(profile("old", "Kid"), profile("new", "kid"))

        assertEquals(listOf("old", "new"), sortedByName(stored).map { it.id })
    }
}
