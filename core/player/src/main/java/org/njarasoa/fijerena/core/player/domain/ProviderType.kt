package org.njarasoa.fijerena.core.player.domain

enum class ProviderType(
    val displayName: String,
) {
    XTREAM("Xtream IPTV"),
    LOCAL("Local Media"),
    SMB("Network Share (SMB)"),
    JELLYFIN("Jellyfin"),
    REMOTE_M3U("Remote M3U"),
}
