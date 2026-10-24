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

/** The PIN prompt both apps share (the profile pin plan, Phase 2). */
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
    fun `leaving a child with the target's own PIN unlocks the target too`() {
        PinSession.onActiveProfile("kid")
        every { pins.checkToLeaveChild(listOf("atr", "mum"), "2222") } returns ProfilePins.LeaveCheck(ProfilePins.Check.Right, "mum")
        val state = prompt(PinRequest.LeaveChild(listOf("atr", "mum"), targetId = "mum", title = "Enter an Expert's PIN"))

        state.replace("2222")

        assertEquals(1, done)
        assertTrue(PinSession.isUnlocked("mum"))
        // The switch then keeps it: the profile in use stays unlocked.
        PinSession.onActiveProfile("mum")
        assertTrue(PinSession.isUnlocked("mum"))
    }

    @Test
    fun `leaving a child with another Expert's PIN unlocks nothing`() {
        PinSession.onActiveProfile("kid")
        every { pins.checkToLeaveChild(listOf("atr", "mum"), "1111") } returns ProfilePins.LeaveCheck(ProfilePins.Check.Right, "atr")
        val state = prompt(PinRequest.LeaveChild(listOf("atr", "mum"), targetId = "mum", title = "t"))

        state.replace("1111")

        assertEquals(1, done)
        assertFalse(PinSession.isUnlocked("mum"))
        assertFalse(PinSession.isUnlocked("atr"))
    }

    @Test
    fun `a wrong PIN to leave a child keeps the child and checks only the child lock`() {
        every { pins.checkToLeaveChild(listOf("atr"), "0000") } returns
            ProfilePins.LeaveCheck(ProfilePins.Check.Wrong(triesLeft = 4, waitUntil = 0L), null)
        every { context.getString(any(), any()) } returns "Wrong PIN"
        val state = prompt(PinRequest.LeaveChild(listOf("atr"), targetId = "mum", title = "t"))

        state.replace("0000")

        assertEquals(0, done)
        assertTrue(state.messageIsError)
        verify(exactly = 0) { pins.checkAny(any(), any()) }
        verify(exactly = 0) { pins.check(any(), any()) }
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
