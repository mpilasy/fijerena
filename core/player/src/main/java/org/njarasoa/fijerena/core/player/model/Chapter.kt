package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    val index: Int,
    val title: String,
    val startPositionMs: Long,
    val endPositionMs: Long
)
