package org.njarasoa.fijerena.core.player.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDownTest {
    @Test
    fun `gateway and Cloudflare statuses are a server down`() {
        listOf(502, 503, 504, 520, 521, 522, 523, 524, 525, 526, 527).forEach { assertTrue("$it", ServerDown.isServerDown(it)) }
    }

    @Test
    fun `other statuses and none are not`() {
        listOf(null, 200, 404, 458, 500, 501, 505, 519, 528).forEach { assertFalse("$it", ServerDown.isServerDown(it)) }
    }
}
