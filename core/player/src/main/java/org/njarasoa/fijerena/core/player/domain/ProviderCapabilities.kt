package org.njarasoa.fijerena.core.player.domain

data class ProviderCapabilities(
    val supportedContentTypes: Set<String>,
    val supportsEpg: Boolean,
    val supportsSearch: Boolean,
    val supportsAuthentication: Boolean,
    val supportsProgressSync: Boolean
)
