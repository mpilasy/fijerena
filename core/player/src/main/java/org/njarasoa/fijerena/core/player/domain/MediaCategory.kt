package org.njarasoa.fijerena.core.player.domain

data class MediaCategory(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val iconUrl: String? = null,
    val isVirtual: Boolean = false
)
