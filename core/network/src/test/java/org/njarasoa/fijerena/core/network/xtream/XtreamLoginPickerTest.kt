package org.njarasoa.fijerena.core.network.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.ProviderRepository.Login
import org.njarasoa.fijerena.core.network.xtream.XtreamLoginPicker.Candidate

/** docs/plans/20261005_shared-logins-plan.md → Phase 2. */
class XtreamLoginPickerTest {
    private val main = Login("main", "a")
    private val two = Login("two", "b")
    private val three = Login("three", "c")

    private fun status(
        activeCons: Int? = 0,
        max: Int? = 1,
        active: Boolean = true,
    ) = XtreamLoginCheck.Status(active, if (active) "Active" else "Expired", null, activeCons, max)

    private fun pick(
        vararg candidates: Candidate,
        mine: String? = null,
        lastUsed: String? = null,
    ) = XtreamLoginPicker.choose(candidates.toList(), mine, lastUsed)

    @Test
    fun `takes the first free login, main first`() {
        assertEquals(main, pick(Candidate(main, status()), Candidate(two, status())))
    }

    @Test
    fun `skips a full login`() {
        assertEquals(two, pick(Candidate(main, status(activeCons = 1)), Candidate(two, status())))
    }

    @Test
    fun `skips an expired or banned login`() {
        assertEquals(two, pick(Candidate(main, status(active = false)), Candidate(two, status())))
    }

    @Test
    fun `skips a login whose check failed or timed out`() {
        assertEquals(two, pick(Candidate(main, null), Candidate(two, status())))
    }

    @Test
    fun `prefers the login this device used last`() {
        assertEquals(three, pick(Candidate(main, status()), Candidate(two, status()), Candidate(three, status()), lastUsed = "three"))
    }

    @Test
    fun `the login this device plays on now counts as free while only its own stream is open`() {
        val playing = pick(Candidate(main, status()), Candidate(two, status(activeCons = 1)), mine = "two")
        assertEquals(two, playing)
    }

    @Test
    fun `the login this device plays on is full when another device holds a stream too`() {
        assertEquals(main, pick(Candidate(main, status()), Candidate(two, status(activeCons = 2, max = 1)), mine = "two"))
    }

    @Test
    fun `unknown connection counts count as free`() {
        assertEquals(main, pick(Candidate(main, status(activeCons = null, max = null))))
    }

    @Test
    fun `none free gives none`() {
        assertNull(pick(Candidate(main, status(activeCons = 1)), Candidate(two, status(activeCons = 1))))
    }
}
