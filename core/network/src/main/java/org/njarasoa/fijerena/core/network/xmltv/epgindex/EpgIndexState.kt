package org.njarasoa.fijerena.core.network.xmltv.epgindex

sealed interface EpgIndexState {
    data object NotIndexed : EpgIndexState
    data class Indexing(
        val progressPercent: Int,
        val channelsIndexed: Int,
        val programmesIndexed: Int
    ) : EpgIndexState
    data class Indexed(
        val channelCount: Int,
        val programmeCount: Int,
        val indexedAtMs: Long
    ) : EpgIndexState
    data class Failed(val reason: String) : EpgIndexState
}
