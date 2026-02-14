package org.njarasoa.fijerena.core.network.xmltv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kotlinx.serialization models for the iptv-org API.
 * Data sourced from https://iptv-org.github.io/api/
 */

@Serializable
data class IptvOrgChannel(
    val id: String,
    val name: String,
    @SerialName("alt_names") val altNames: List<String> = emptyList(),
    val country: String? = null,
    val languages: List<String> = emptyList()
)

@Serializable
data class IptvOrgGuide(
    val channel: String,
    val site: String,
    val lang: String
)

/**
 * A selected guide file to download, with its URL and the channels it covers.
 */
data class SelectedGuide(
    val url: String,
    val site: String,
    val lang: String,
    val channelIds: Set<String>
)

/**
 * Simple reference to a user's channel for matching purposes.
 */
data class ChannelRef(
    val epgChannelId: String?,
    val name: String
)
