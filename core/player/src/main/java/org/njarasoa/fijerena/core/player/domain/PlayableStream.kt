package org.njarasoa.fijerena.core.player.domain

data class PlayableStream(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val isLive: Boolean = false,
    val title: String = "",
    val mimeType: String? = null
)
