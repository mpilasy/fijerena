package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.SyncWire

/** Which devices the picker lists and how each shows (the send to TV plan, ST-D2). */
class SendToTvPickerTest {
    private val now = 1_800_000_000_000L
    private val feature = listOf(DeviceBuild.FEATURE_PLAY_REQUEST)

    private fun device(
        id: String,
        lastSeen: Long = now - 5_000,
        revoked: Boolean = false,
        current: Boolean = false,
    ) = SyncWire.Device(id, "Name $id", 0, lastSeen, revoked, current)

    private fun rows(
        vararg devices: SyncWire.Device,
        builds: Map<String, DeviceBuild>,
        names: Map<String, String> = emptyMap(),
    ) = tvRows(devices.toList(), builds, names, now)

    @Test
    fun `a TV with the feature seen recently is open`() {
        val rows = rows(device("tv"), builds = mapOf("tv" to DeviceBuild(app = "tv", features = feature)))

        assertEquals(listOf(TvRow("tv", "Name tv", TvStatus.OPEN)), rows)
    }

    @Test
    fun `a TV seen 90 seconds ago is not open`() {
        val rows = rows(device("tv", lastSeen = now - 90_000), builds = mapOf("tv" to DeviceBuild(app = "tv", features = feature)))

        assertEquals(TvStatus.NOT_OPEN, rows.single().status)
    }

    @Test
    fun `a TV with an older build or without a record needs an update`() {
        val rows =
            rows(
                device("old"),
                device("none"),
                builds = mapOf("old" to DeviceBuild(app = "tv")),
            )

        assertEquals(listOf(TvStatus.NEEDS_UPDATE, TvStatus.NEEDS_UPDATE), rows.map { it.status })
    }

    @Test
    fun `phones, revoked devices and this device are left out`() {
        val tv = DeviceBuild(app = "tv", features = feature)
        val builds = mapOf("phone" to DeviceBuild(app = "phone", features = feature), "gone" to tv, "me" to tv)

        val rows =
            rows(
                device("phone"),
                device("gone", revoked = true),
                device("me", current = true),
                builds = builds,
            )

        assertEquals(emptyList<TvRow>(), rows)
    }

    @Test
    fun `the group's name wins over the server's`() {
        val rows =
            rows(
                device("tv"),
                builds = mapOf("tv" to DeviceBuild(app = "tv", features = feature)),
                names = mapOf("tv" to "Living Room"),
            )

        assertEquals("Living Room", rows.single().name)
    }
}
