package org.njarasoa.fijerena.core.network.sync

/**
 * The sync server's clock, as this device's offset from it. TV clocks are often wrong, so two
 * devices compare a time only on this clock ([PlayRequests]). Until the offset is known, [now] is
 * this device's clock.
 */
object ServerClock {
    @Volatile
    private var offsetMs: Long? = null

    val isKnown: Boolean get() = offsetMs != null

    fun now(deviceNow: Long = System.currentTimeMillis()): Long = deviceNow + (offsetMs ?: 0L)

    /** The next sync pass reads the offset again: the device's clock can change while the app is away. */
    fun forget() {
        offsetMs = null
    }

    /**
     * Takes the offset from a `/devices` answer: the server stamps the caller's own row while it
     * checks the token, so that row's [SyncWire.Device.lastSeen] is the server's time of the call.
     * [askedAt] and [answeredAt] are this device's clock around the call.
     */
    fun update(
        devices: List<SyncWire.Device>,
        ownDeviceId: String,
        askedAt: Long,
        answeredAt: Long,
    ) {
        val serverTime = devices.firstOrNull { it.id == ownDeviceId }?.lastSeen ?: return
        offsetMs = serverTime - (askedAt + answeredAt) / 2
    }
}
