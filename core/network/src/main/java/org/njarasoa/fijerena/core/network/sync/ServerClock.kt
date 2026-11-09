package org.njarasoa.fijerena.core.network.sync

/**
 * The sync server's clock, as read once and counted on from there with a timer that a change of
 * the device's clock does not move. TV clocks are often wrong, so two devices compare a time only
 * on this clock ([PlayRequests]). Until it is read, [now] is this device's clock.
 */
object ServerClock {
    private class Reading(
        val serverMs: Long,
        val elapsedMs: Long,
    )

    @Volatile
    private var reading: Reading? = null

    val isKnown: Boolean get() = reading != null

    /** The timer [update] wants around its call. It does not count while the device sleeps: [forget] covers that. */
    fun elapsed(): Long = System.nanoTime() / 1_000_000L

    fun now(
        deviceNow: Long = System.currentTimeMillis(),
        elapsedNow: Long = elapsed(),
    ): Long = reading?.let { it.serverMs + (elapsedNow - it.elapsedMs) } ?: deviceNow

    /** The next sync pass reads the clock again: the timer stops while the device sleeps. */
    fun forget() {
        reading = null
    }

    /**
     * Reads a `/devices` answer: the server stamps the caller's own row while it checks the token,
     * so that row's [SyncWire.Device.lastSeen] is the server's time of the call. [askedAt] and
     * [answeredAt] are [elapsed] around the call.
     */
    fun update(
        devices: List<SyncWire.Device>,
        ownDeviceId: String,
        askedAt: Long,
        answeredAt: Long,
    ) {
        val serverTime = devices.firstOrNull { it.id == ownDeviceId }?.lastSeen ?: return
        reading = Reading(serverTime, (askedAt + answeredAt) / 2)
    }
}
