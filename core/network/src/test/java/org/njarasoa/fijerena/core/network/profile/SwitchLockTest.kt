package org.njarasoa.fijerena.core.network.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** The child lock: who may switch where without an Expert's PIN (the settings flow review plan, SF-D6 to SF-D9). */
class SwitchLockTest {
    private val children = setOf("kid", "teen")
    private val adminPins = mutableSetOf("atr")

    private fun lock(
        activeId: String,
        targetId: String,
        profileIds: List<String>? = listOf("atr", "mum", "kid", "teen"),
    ) = SwitchLock.of(activeId, targetId, profileIds, { it in children }, { it in adminPins })

    @Test
    fun `leaving a child asks an Expert's PIN, for a grown-up or another child`() {
        assertEquals(SwitchLock.AskPin(listOf("atr")), lock("kid", "mum"))
        assertEquals(SwitchLock.AskPin(listOf("atr")), lock("kid", "atr"))
        assertEquals(SwitchLock.AskPin(listOf("atr")), lock("kid", "teen"))
    }

    @Test
    fun `picking the same child asks nothing`() {
        assertEquals(SwitchLock.Free, lock("kid", "kid"))
    }

    @Test
    fun `a grown-up switches freely, to a child too`() {
        assertEquals(SwitchLock.Free, lock("mum", "atr"))
        assertEquals(SwitchLock.Free, lock("atr", "kid"))
    }

    @Test
    fun `with no Expert PIN there is no lock`() {
        adminPins.clear()

        assertEquals(SwitchLock.Free, lock("kid", "mum"))
    }

    @Test
    fun `every Expert with a PIN is offered, oldest first`() {
        adminPins += "mum"

        assertEquals(SwitchLock.AskPin(listOf("atr", "mum")), lock("kid", "mum"))
    }

    @Test
    fun `profiles that can't be read refuse to leave a child`() {
        assertEquals(SwitchLock.Refused, lock("kid", "mum", profileIds = null))
        assertEquals(SwitchLock.Refused, lock("kid", "mum", profileIds = emptyList()))
        // Not a child: nothing to lock.
        assertEquals(SwitchLock.Free, lock("mum", "kid", profileIds = null))
    }
}
