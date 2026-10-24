package org.njarasoa.fijerena.core.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ProviderType

/** the next level rock solid resilience plan → R-22; the settings flow review, E3. */
class AddSourceTypesTest {
    @Test
    fun `SMB and Local are hidden outside a debug build`() {
        assertEquals(
            listOf(ProviderType.XTREAM, ProviderType.JELLYFIN, ProviderType.REMOTE_M3U),
            addSourceTypes(debugBuild = false),
        )
    }

    @Test
    fun `a debug build offers every type`() {
        assertEquals(ProviderType.entries.toList(), addSourceTypes(debugBuild = true))
    }

    @Test
    fun `the type of a source being edited is kept`() {
        assertEquals(
            listOf(ProviderType.XTREAM, ProviderType.SMB, ProviderType.JELLYFIN, ProviderType.REMOTE_M3U),
            addSourceTypes(debugBuild = false, editedType = ProviderType.SMB),
        )
    }
}
