package org.njarasoa.fijerena.core.player.device

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.os.Build
import android.util.Log

enum class DeviceType {
    NVIDIA_SHIELD,
    SONY_BRAVIA,
    CHROMECAST_TV,
    GENERIC_TV,
    GENERIC_MOBILE,
}

data class DeviceCapabilities(
    val deviceType: DeviceType = DeviceType.GENERIC_MOBILE,
    val supportsHevc: Boolean = false,
    val supportsAv1: Boolean = false,
    val supports4K: Boolean = false,
    val maxResolution: Pair<Int, Int> = 1920 to 1080,
    val preferredCodecs: List<String> = emptyList(),
)

object DeviceDetector {
    @Volatile
    private var cachedCapabilities: DeviceCapabilities? = null

    fun detect(context: Context): DeviceCapabilities =
        cachedCapabilities ?: synchronized(this) {
            cachedCapabilities ?: performDetection(context).also { cachedCapabilities = it }
        }

    private fun performDetection(context: Context): DeviceCapabilities =
        capabilitiesOf(detectDeviceType(context), hardwareVideoDecoders()).also { Log.i("DeviceDetector", "Device capabilities: $it") }

    private fun detectDeviceType(context: Context): DeviceType {
        val manufacturer = Build.MANUFACTURER.uppercase()
        val model = Build.MODEL.uppercase()
        val device = Build.DEVICE.uppercase()

        // Chromecast with Google TV reports MODEL "Chromecast"; its DEVICE is a codename ("sabrina",
        // "boreal").
        return when {
            manufacturer.contains("NVIDIA") || model.contains("SHIELD") -> DeviceType.NVIDIA_SHIELD
            model.contains("BRAVIA") || (manufacturer.contains("SONY") && isAndroidTv(context)) -> DeviceType.SONY_BRAVIA
            manufacturer.contains("GOOGLE") && (model.contains("CHROMECAST") || device.contains("CHROMECAST")) -> DeviceType.CHROMECAST_TV
            isAndroidTv(context) -> DeviceType.GENERIC_TV
            else -> DeviceType.GENERIC_MOBILE
        }
    }

    private fun isAndroidTv(context: Context): Boolean {
        // The leanback feature is what every Android TV device declares (the TV app's launcher
        // needs it); ro.build.characteristics "tv" is kept as a fallback, though not every build
        // sets it (the TV emulator says "emulator"). Class.forName("TvInputManager") is NOT a
        // test: the class exists on all devices since API 21, so phones read as TV.
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        return try {
            @Suppress("PrivateApi")
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            val characteristics = get.invoke(null, "ro.build.characteristics", "") as String
            characteristics.contains("tv", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * The device's hardware video decoders and whether each decodes 3840×2160. Software decoders
     * (Android has HEVC and AV1 ones on every device) do not count: they can't play live 4K.
     */
    private fun hardwareVideoDecoders(): List<VideoDecoder> =
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filter { !it.isEncoder && it.isHardwareAccelerated }
                .flatMap { info ->
                    info.supportedTypes
                        .filter { it.startsWith("video/") }
                        .map { type ->
                            val video = info.getCapabilitiesForType(type).videoCapabilities
                            VideoDecoder(type, video?.isSizeSupported(UHD_WIDTH, UHD_HEIGHT) == true)
                        }
                }
        } catch (e: Exception) {
            emptyList()
        }
}

private const val UHD_WIDTH = 3840
private const val UHD_HEIGHT = 2160

/** A hardware video decoder: its MIME type, and whether it decodes 3840×2160. */
internal data class VideoDecoder(
    val mimeType: String,
    val decodes4K: Boolean,
)

/**
 * The capabilities from the device type and its hardware video decoders. 4K is what a decoder
 * reports, never the device type: the Chromecast HD has HEVC and AV1 decoders up to 1080p only,
 * and a box the app does not know (the onn 4K Plus) decodes 4K. A phone stays at 1080p. AV1 and
 * HEVC come before H.264 on a TV when a hardware decoder has them.
 */
internal fun capabilitiesOf(
    deviceType: DeviceType,
    decoders: List<VideoDecoder>,
): DeviceCapabilities {
    val supportsHevc = decoders.any { it.mimeType == "video/hevc" }
    val supportsAv1 = decoders.any { it.mimeType == "video/av01" }
    val supports4K = deviceType != DeviceType.GENERIC_MOBILE && decoders.any { it.decodes4K }
    val preferredCodecs =
        if (deviceType == DeviceType.GENERIC_MOBILE) {
            listOf("video/avc")
        } else {
            listOfNotNull("video/av01".takeIf { supportsAv1 }, "video/hevc".takeIf { supportsHevc }, "video/avc")
        }
    return DeviceCapabilities(
        deviceType = deviceType,
        supportsHevc = supportsHevc,
        supportsAv1 = supportsAv1,
        supports4K = supports4K,
        maxResolution = if (supports4K) UHD_WIDTH to UHD_HEIGHT else 1920 to 1080,
        preferredCodecs = preferredCodecs,
    )
}
