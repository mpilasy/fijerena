package org.njarasoa.fijerena.core.network.xmltv

import kotlinx.serialization.Serializable

@Serializable
data class XmltvChannel(
    val id: String,
    val displayName: String,
    val iconUrl: String? = null
)

@Serializable
data class XmltvProgramme(
    val channelId: String,
    val startEpoch: Long,
    val endEpoch: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val sourceId: Long = 0
)

@Serializable
data class XmltvData(
    val channels: Map<String, XmltvChannel>,
    val programmes: Map<String, List<XmltvProgramme>>
)

data class XmltvSearchResult(
    val channels: Map<String, XmltvChannel>,
    val programmes: List<XmltvProgramme>,
    val totalScanned: Int,
    val truncated: Boolean,
    val searchedFromIndex: Boolean = false
)
