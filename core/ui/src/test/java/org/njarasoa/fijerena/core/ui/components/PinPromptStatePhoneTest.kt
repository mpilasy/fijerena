package org.njarasoa.fijerena.core.ui.components

import android.content.Context
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sync.PinOnPhone

/** A PIN prompt answered on another device (the PIN on the phone plan, Phase 2). */
class PinPromptStatePhoneTest {
    private val context = mockk<Context>(relaxed = true)
    private val pins = mockk<ProfilePins>(relaxed = true)
    private var done = 0

    private val check = PinRequest.Check("anna", "Enter Anna's PIN")

    private fun prompt(request: PinRequest = check) = PinPromptState(context, pins, request) { done++ }

    private fun answer(
        result: String,
        requestId: String = "r1",
    ) = SyncPayloads.PinAnswer(requestId, result)

    @Before
    fun setup() {
        every { context.getString(R.string.pin_phone_cancelled) } returns "Cancelled on the phone."
        every { context.getString(R.string.pin_phone_no_answer) } returns "No answer from the phone."
        mockkObject(PinOnPhone)
        every { PinOnPhone.canAsk(any()) } returns true
        every { PinOnPhone.ask(any(), "anna") } returns "r1"
        every { PinOnPhone.close(any(), any(), any()) } just runs
        PinSession.onActiveProfile("someone else")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `asking the phone waits for its answer`() {
        val state = prompt()

        state.usePhone()
        state.usePhone()

        assertEquals("r1", state.phoneRequestId)
        verify(exactly = 1) { PinOnPhone.ask(any(), "anna") }
    }

    @Test
    fun `unlocked on the phone does what a right PIN does`() {
        val state = prompt()
        state.usePhone()

        state.onPhoneAnswer(answer(SyncPayloads.PinAnswer.UNLOCKED))

        assertEquals(1, done)
        assertTrue(PinSession.isUnlocked("anna"))
        assertNull(state.phoneRequestId)
        verify { PinOnPhone.close(any(), "r1", "anna") }
    }

    @Test
    fun `cancelled on the phone shows the pad again and says so`() {
        val state = prompt()
        state.usePhone()

        state.onPhoneAnswer(answer(SyncPayloads.PinAnswer.CANCELLED))

        assertEquals(0, done)
        assertNull(state.phoneRequestId)
        assertEquals("Cancelled on the phone.", state.message)
        assertFalse(state.messageIsError)
    }

    @Test
    fun `no answer in time shows the pad again and closes the request`() {
        val state = prompt()
        state.usePhone()

        state.phoneTimedOut()

        assertNull(state.phoneRequestId)
        assertEquals("No answer from the phone.", state.message)
        verify { PinOnPhone.close(any(), "r1", "anna") }
    }

    @Test
    fun `an answer to another request does nothing`() {
        val state = prompt()
        state.usePhone()

        state.onPhoneAnswer(answer(SyncPayloads.PinAnswer.UNLOCKED, requestId = "r9"))

        assertEquals(0, done)
        assertEquals("r1", state.phoneRequestId)
    }

    @Test
    fun `a digit typed here while the phone is asked types the PIN here`() {
        every { pins.check("anna", "1234") } returns ProfilePins.Check.Right
        val state = prompt()
        state.usePhone()

        state.replace("1234")

        assertNull(state.phoneRequestId)
        assertEquals(1, done)
        verify { PinOnPhone.close(any(), "r1", "anna") }
    }

    @Test
    fun `choosing a new PIN never offers the phone`() {
        val state = prompt(PinRequest.Choose("anna"))

        state.usePhone()

        assertFalse(state.canUsePhone)
        assertNull(state.phoneRequestId)
    }
}
