package org.njarasoa.fijerena.core.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings' Display and Playback rows open a profile's page at one of its rows (G1). */
class ProfilePageRowTest {
    @Test
    fun `the route keeps the row to show first`() {
        val route = Screen.ProfileEdit(profileId = "p1", at = ProfilePageRow.AUTOPLAY.name)
        val decoded = Json.decodeFromString(Screen.ProfileEdit.serializer(), Json.encodeToString(Screen.ProfileEdit.serializer(), route))
        assertEquals(route, decoded)
        assertEquals(ProfilePageRow.AUTOPLAY, ProfilePageRow.of(decoded.at))
    }

    @Test
    fun `no row or an unknown one opens the page at the top`() {
        assertNull(ProfilePageRow.of(Screen.ProfileEdit(profileId = "p1").at))
        assertNull(ProfilePageRow.of("SOMETHING_ELSE"))
        assertEquals(ProfilePageRow.LOOK, ProfilePageRow.of("LOOK"))
    }
}
