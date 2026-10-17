package org.njarasoa.fijerena.core.ui.components

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.profile.ProfilePins

/** The profile page's PIN row (docs/plans/archive/20261017_profile-pin-plan.md, Phase 2). */
class ProfilePinFlowTest {
    private val pins = mockk<ProfilePins>(relaxed = true)

    private fun flow(
        hasPin: Boolean,
        own: Boolean,
    ): ProfilePinFlow {
        every { pins.hasPin("kid") } returns hasPin
        return ProfilePinFlow(pins, "kid", own, currentTitle = "Enter the current PIN", warning = null)
    }

    @Test
    fun `without a PIN it chooses one`() {
        val flow = flow(hasPin = false, own = true)
        flow.start()

        assertEquals(ProfilePinFlow.Step.CHOOSE, flow.step)
        assertTrue(flow.request is PinRequest.Choose)
        flow.promptDone()
        assertTrue(flow.hasPin)
        assertNull(flow.step)
    }

    @Test
    fun `its own page asks for the current PIN before Change or Remove`() {
        val flow = flow(hasPin = true, own = true)
        flow.start()

        assertEquals(PinRequest.Check("kid", "Enter the current PIN"), flow.request)
        flow.promptDone()
        assertEquals(ProfilePinFlow.Step.OPTIONS, flow.step)
        flow.change()
        assertTrue(flow.request is PinRequest.Choose)
    }

    @Test
    fun `an Expert on another profile's page goes straight to Change or Remove`() {
        val flow = flow(hasPin = true, own = false)
        flow.start()

        assertEquals(ProfilePinFlow.Step.OPTIONS, flow.step)
        flow.remove()
        verify { pins.remove("kid") }
        assertFalse(flow.hasPin)
        assertNull(flow.step)
    }
}
