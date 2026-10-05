package org.njarasoa.fijerena.core.player.model

/**
 * The connection is too slow for the playing stream: what it needs and what it gets, in bits per
 * second — both null when the stream doesn't state its bitrate (most TS and MKV).
 */
data class SlowConnection(
    val neededBps: Long?,
    val measuredBps: Long?,
)
