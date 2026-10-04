package org.njarasoa.fijerena.core.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ProviderType

/** docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-22. */
class AddSourceTypesTest {
    @Test
    fun `SMB and Local are hidden outside developer mode`() {
        assertEquals(
            listOf(ProviderType.XTREAM, ProviderType.JELLYFIN, ProviderType.REMOTE_M3U),
            addSourceTypes(isDevMode = false),
        )
    }

    @Test
    fun `developer mode offers every type`() {
        assertEquals(ProviderType.entries.toList(), addSourceTypes(isDevMode = true))
    }

    @Test
    fun `the type of a source being edited is kept`() {
        assertEquals(
            listOf(ProviderType.XTREAM, ProviderType.SMB, ProviderType.JELLYFIN, ProviderType.REMOTE_M3U),
            addSourceTypes(isDevMode = false, editedType = ProviderType.SMB),
        )
    }
}
