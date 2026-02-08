package org.njarasoa.fijerena.core.network.xmltv

data class EpgBrowserProgram(
    val title: String,
    val description: String?,
    val category: String?,
    val airings: List<EpgBrowserAiring>
)

data class EpgBrowserAiring(
    val channelId: String,
    val channelName: String,
    val channelIconUrl: String?,
    val startEpoch: Long,
    val endEpoch: Long
)
