package org.njarasoa.fijerena.core.player.model

data class AiAudioMetrics(
    val isClearVoiceActive: Boolean = false,
    val isNightModeActive: Boolean = false,
    val currentLatencyMs: Long = 0L,
    val totalSkippedFrames: Long = 0L
)
