package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfilePins.Check

/** A profile's optional PIN: hashed, checked, throttled per device, synced (docs/plans/20261017_profile-pin-plan.md, Phase 1). */
class ProfilePinsTest {
    private lateinit var settings: AppSettings
    private var clock = 1_000_000L
    private lateinit var pins: ProfilePins

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
        pins = ProfilePins(settings) { clock }
    }

    @Test
    fun `a profile without a PIN lets anything in`() {
        assertFalse(pins.hasPin(KID))
        assertEquals(Check.Right, pins.check(KID, "0000"))
    }

    @Test
    fun `only the PIN set lets in, and only a hash is kept`() {
        pins.set(KID, "1234")

        assertTrue(pins.hasPin(KID))
        assertEquals(Check.Right, pins.check(KID, "1234"))
        assertTrue(pins.check(KID, "4321") is Check.Wrong)
        assertFalse(settings.pinHash(KID)!!.contains("1234"))
        assertFalse(pins.hasPin(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `the same PIN twice is stored differently`() {
        pins.set(KID, "1234")
        val first = settings.pinHash(KID)
        pins.set(KID, "1234")

        assertNotEquals(first, settings.pinHash(KID))
    }

    @Test
    fun `only four digits make a PIN`() {
        assertTrue(ProfilePins.isValid("0042"))
        listOf("", "123", "12345", "12a4", "１２３４").forEach { assertFalse(it, ProfilePins.isValid(it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setting a bad PIN is refused`() {
        pins.set(KID, "12")
    }

    @Test
    fun `five wrong PINs start a wait that doubles and the right one resets it`() {
        pins.set(KID, "1234")
        repeat(4) { assertEquals(Check.Wrong(triesLeft = 4 - it, waitUntil = 0L), pins.check(KID, "0000")) }

        assertEquals(Check.Wrong(triesLeft = 0, waitUntil = clock + 30_000L), pins.check(KID, "0000"))
        // Even the right PIN waits.
        assertEquals(Check.Waiting(clock + 30_000L), pins.check(KID, "1234"))

        clock += 30_000L
        assertEquals(Check.Wrong(triesLeft = 0, waitUntil = clock + 60_000L), pins.check(KID, "0000"))

        clock += 60_000L
        assertEquals(Check.Right, pins.check(KID, "1234"))
        assertNull(pins.waitingUntil(KID))
        assertEquals(Check.Wrong(triesLeft = 4, waitUntil = 0L), pins.check(KID, "0000"))
    }

    @Test
    fun `the wait never passes 15 minutes`() {
        assertEquals(30_000L, ProfilePins.waitAfter(5))
        assertEquals(15 * 60_000L, ProfilePins.waitAfter(50))
    }

    @Test
    fun `the PIN syncs per profile, and so does its removal`() {
        pins.set(KID, "1234")
        val sent = settings.syncedSetting(KEY, KID)!!

        val otherDevice =
            AppSettings(
                mockk<Context>().also {
                    every { it.getSharedPreferences("app_settings", any()) } returns
                        FakeSharedPreferences()
                },
            )
        otherDevice.applyRemoteSetting(KEY, KID, sent)
        assertEquals(Check.Right, ProfilePins(otherDevice).check(KID, "1234"))

        pins.remove(KID)
        otherDevice.applyRemoteSetting(KEY, KID, settings.syncedSetting(KEY, KID)!!)
        assertFalse(ProfilePins(otherDevice).hasPin(KID))
    }

    @Test
    fun `the wrong-PIN count stays on this device`() {
        pins.set(KID, "1234")
        repeat(5) { pins.check(KID, "0000") }

        assertNull(settings.syncedSetting("pin_failures", KID))
        assertEquals(JsonPrimitive(settings.pinHash(KID)), settings.syncedSetting(KEY, KID))
    }

    @Test
    fun `any Expert's PIN will do, and wrong tries count against the first one's`() {
        pins.set("atr", "1111")
        pins.set(KID, "2222")
        val experts = listOf("atr", KID)

        assertEquals(Check.Right, pins.checkAny(experts, "2222"))
        repeat(4) { pins.checkAny(experts, "0000") }
        assertEquals(Check.Wrong(triesLeft = 0, waitUntil = clock + 30_000L), pins.checkAny(experts, "0000"))
        assertEquals(Check.Waiting(clock + 30_000L), pins.checkAny(experts, "1111"))
        assertEquals(0, settings.pinFailures(KID))
        assertEquals(Check.Right, pins.checkAny(emptyList(), "0000"))
    }

    @Test
    fun `deleting the profile drops its PIN`() {
        pins.set(KID, "1234")
        settings.removePin(KID)

        assertFalse(pins.hasPin(KID))
    }

    private companion object {
        const val KID = "kid"
        const val KEY = "pin_hash"
    }
}
