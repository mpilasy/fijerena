package org.njarasoa.fijerena.core.ui.components

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.profile.ProfilePins

/** The PIN prompt both apps share (docs/plans/20261017_profile-pin-plan.md, Phase 2). */
class PinPromptStateTest {
    private val context = mockk<Context>(relaxed = true).also { every { it.getString(any()) } returns "msg" }
    private val pins = mockk<ProfilePins>(relaxed = true)
    private var done = 0

    private fun prompt(request: PinRequest) = PinPromptState(context, pins, request) { done++ }

    @Test
    fun `the fourth digit checks the PIN and a right one finishes`() {
        every { pins.check("kid", "1234") } returns ProfilePins.Check.Right
        val state = prompt(PinRequest.Check("kid", "Enter Kid's PIN"))

        "123".forEach(state::type)
        assertEquals(0, done)
        state.type('4')

        assertEquals(1, done)
        assertEquals("", state.typed)
        assertNull(state.message)
    }

    @Test
    fun `a wrong PIN clears the dots and says so`() {
        every { pins.check("kid", "0000") } returns ProfilePins.Check.Wrong(triesLeft = 4, waitUntil = 0L)
        every { context.getString(any(), any()) } returns "Wrong PIN"
        val state = prompt(PinRequest.Check("kid", "Enter Kid's PIN"))

        state.replace("0000")

        assertEquals(0, done)
        assertEquals("", state.typed)
        assertEquals("Wrong PIN", state.message)
        assertTrue(state.messageIsError)
    }

    @Test
    fun `any Expert's PIN finishes an admin prompt`() {
        every { pins.checkAny(listOf("atr", "kid"), "2222") } returns ProfilePins.Check.Right
        val state = prompt(PinRequest.AnyOf(listOf("atr", "kid"), "Enter an Expert's PIN"))

        state.replace("2222")

        assertEquals(1, done)
        assertEquals("Enter an Expert's PIN", state.title)
    }

    @Test
    fun `erase drops the last digit and anything but digits is ignored`() {
        val state = prompt(PinRequest.Check("kid", "t"))
        state.type('1')
        state.type('x')
        state.type('2')
        state.erase()

        assertEquals("1", state.typed)
    }

    @Test
    fun `a new PIN is saved once typed twice the same`() {
        val state = prompt(PinRequest.Choose("kid"))

        state.replace("2580")
        assertEquals(0, done)
        state.replace("2580")

        verify { pins.set("kid", "2580") }
        assertEquals(1, done)
    }

    @Test
    fun `two different PINs start over`() {
        val state = prompt(PinRequest.Choose("kid", warning = "careful"))
        assertEquals("careful", state.message)
        assertFalse(state.messageIsError)

        state.replace("2580")
        state.replace("1111")

        verify(exactly = 0) { pins.set(any(), any()) }
        assertEquals(0, done)
        assertTrue(state.messageIsError)
        state.replace("1111")
        state.replace("1111")
        verify { pins.set("kid", "1111") }
    }
}
