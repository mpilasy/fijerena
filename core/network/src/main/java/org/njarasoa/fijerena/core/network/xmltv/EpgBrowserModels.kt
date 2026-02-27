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
    val endEpoch: Long,
    val sourceId: Long = 0,
    val matchedStream: EpgBrowserMatchedStream? = null
)

data class EpgBrowserMatchedStream(
    val streamId: Int,
    val streamName: String,
    val categoryId: String
)

data class EpgBrowserDateGroup(
    val dateLabel: String,
    val dayStartEpoch: Long,
    val programs: List<EpgBrowserProgram>
)
