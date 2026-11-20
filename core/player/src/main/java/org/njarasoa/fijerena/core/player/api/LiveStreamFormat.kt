package org.njarasoa.fijerena.core.player.api

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * The live stream format of an Xtream source set to Auto: HLS on mobile data when the server lists
 * it in `allowed_output_formats`, MPEG-TS everywhere else. A rewrite to HLS without that check
 * (February 2026) gave 404s on servers without HLS. A panel whose HLS failed to start gets TS
 * until the app restarts. See the mobile data playback plan → Phase 1.
 */
object LiveStreamFormat {
    /** Never stored: [ProviderSettings][org.njarasoa.fijerena.core.network.provider.ProviderSettings] keeps Auto as its own flag. */
    const val AUTO = "auto"
    const val HLS = "m3u8"
    const val TS = "ts"

    // An Xtream live HLS address: .../live/<user>/<password>/<stream id>.m3u8, maybe with a query.
    private val LIVE_HLS = Regex("""^(.+/live/[^/]+/[^/]+/[^/?#]+)\.m3u8([?#].*)?$""", RegexOption.IGNORE_CASE)

    private val hlsFailedPanels: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun choose(
        onMobileData: Boolean,
        allowedFormats: List<String?>?,
        panel: String?,
    ): String {
        val hlsAllowed = allowedFormats.orEmpty().any { it.equals(HLS, ignoreCase = true) }
        return if (onMobileData && hlsAllowed && panel !in hlsFailedPanels) HLS else TS
    }

    /** The same stream as MPEG-TS when [url] is an Xtream live HLS address; null otherwise. */
    fun tsFallbackFor(url: String): String? = LIVE_HLS.matchEntire(url)?.let { "${it.groupValues[1]}.$TS${it.groupValues[2]}" }

    /** The panel of [url]: its host and port, the key of the HLS failures. */
    fun panelOf(url: String): String? = runCatching { URI(url).rawAuthority?.lowercase() }.getOrNull()

    fun markHlsFailed(panel: String) {
        hlsFailedPanels += panel
    }

    internal fun clearForTest() = hlsFailedPanels.clear()
}
