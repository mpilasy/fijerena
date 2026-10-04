package org.njarasoa.fijerena.core.network.smb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-22. */
class SmbSourceConfigTest {
    @Test
    fun `quotes and backslashes round-trip`() {
        val config = Json.parseToJsonElement(smbSourceConfig(host = "nas\"1", share = "media\\films")).jsonObject
        assertEquals("nas\"1", config.getValue("host").jsonPrimitive.content)
        assertEquals("media\\films", config.getValue("share").jsonPrimitive.content)
    }

    @Test
    fun `plain values give the expected JSON`() {
        assertEquals("""{"host":"nas","share":"media"}""", smbSourceConfig(host = "nas", share = "media"))
    }
}
