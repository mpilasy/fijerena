package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.ProviderRepository.Companion.LoginOwner
import org.njarasoa.fijerena.core.network.provider.ProviderRepository.Companion.chooseHouseholdLogin
import org.njarasoa.fijerena.core.network.provider.ProviderRepository.Companion.hasSourceLogin

/**
 * One Jellyfin login per source: which profile's login a source keeps on upgrade (JF-D1), and when
 * a source counts as signed in.
 */
class HouseholdLoginTest {
    private fun owner(
        id: String,
        expert: Boolean = false,
        createdAt: Long = 0,
        lastUsedAt: Long = 0,
    ) = LoginOwner(id, expert, createdAt, lastUsedAt)

    @Test
    fun `the Expert profile in use keeps its login`() {
        val owners = listOf(owner("old-admin", expert = true, createdAt = 1), owner("me", expert = true, createdAt = 5))

        assertEquals("me", chooseHouseholdLogin(owners, activeProfileId = "me"))
    }

    @Test
    fun `else the oldest Expert's, even when the profile in use has one`() {
        val owners =
            listOf(
                owner("kid", lastUsedAt = 100),
                owner("young-admin", expert = true, createdAt = 9),
                owner("old-admin", expert = true, createdAt = 2),
            )

        assertEquals("old-admin", chooseHouseholdLogin(owners, activeProfileId = "kid"))
    }

    @Test
    fun `with no Expert, the profile in use counts as the most recently used`() {
        val owners = listOf(owner("a", lastUsedAt = 500), owner("me", lastUsedAt = 1))

        assertEquals("me", chooseHouseholdLogin(owners, activeProfileId = "me"))
    }

    @Test
    fun `with no Expert and the profile in use without one, the latest watch change wins`() {
        val owners = listOf(owner("a", lastUsedAt = 100), owner("b", lastUsedAt = 300), owner("c"))

        assertEquals("b", chooseHouseholdLogin(owners, activeProfileId = "nobody"))
    }

    @Test
    fun `ties pick the same profile on every device`() {
        val experts = listOf(owner("b", expert = true, createdAt = 1), owner("a", expert = true, createdAt = 1))
        val others = listOf(owner("b"), owner("a"))

        assertEquals("a", chooseHouseholdLogin(experts, activeProfileId = "x"))
        assertEquals("b", chooseHouseholdLogin(others, activeProfileId = "x"))
    }

    @Test
    fun `no login anywhere chooses none`() {
        assertNull(chooseHouseholdLogin(emptyList(), activeProfileId = "me"))
    }

    @Test
    fun `a Jellyfin source is signed in with a username or a session`() {
        assertFalse(hasSourceLogin("JELLYFIN", "", hasSession = false))
        assertFalse(hasSourceLogin("JELLYFIN", "  ", hasSession = false))
        assertTrue(hasSourceLogin("JELLYFIN", "kilonga", hasSession = false))
        assertTrue(hasSourceLogin("JELLYFIN", "", hasSession = true))
    }

    @Test
    fun `every other source needs no sign-in`() {
        assertTrue(hasSourceLogin("XTREAM", "", hasSession = false))
        assertTrue(hasSourceLogin("REMOTE_M3U", "", hasSession = false))
    }
}
