package org.njarasoa.fijerena.core.network.ai

/**
 * Unified model for semantic search results across different content types.
 */
data class SemanticCandidate(
    val itemId: String,
    val title: String,
    val description: String? = null,
    val type: String, // "VOD", "SERIES", "CATEGORY", "EPISODE"
    val providerId: Long,
    val thumbnailUrl: String? = null,
    val embedding: ByteArray? = null
)
