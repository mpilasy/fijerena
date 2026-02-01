package org.njarasoa.fijerena.core.player.device

import android.media.MediaCodecList
import android.os.Build

enum class DeviceType {
    NVIDIA_SHIELD,
    SONY_BRAVIA,
    CHROMECAST_TV,
    GENERIC_TV,
    GENERIC_MOBILE
}

data class DeviceCapabilities(
    val deviceType: DeviceType = DeviceType.GENERIC_MOBILE,
    val supportsHevc: Boolean = false,
    val supportsAv1: Boolean = false,
    val supports4K: Boolean = false,
    val maxResolution: Pair<Int, Int> = 1920 to 1080,
    val preferredCodecs: List<String> = emptyList()
)

object DeviceDetector {
    fun detect(): DeviceCapabilities {
        val deviceType = detectDeviceType()
        val supportsHevc = supportsCodec("video/hevc")
        val supportsAv1 = supportsCodec("video/av01")
        val supports4K = supportsCodec("video/hevc") && supportsCodec("video/av01")

        val maxResolution = when (deviceType) {
            DeviceType.NVIDIA_SHIELD -> if (supports4K) 3840 to 2160 else 1920 to 1080
            DeviceType.SONY_BRAVIA -> if (supports4K) 3840 to 2160 else 1920 to 1080
            DeviceType.CHROMECAST_TV -> if (supports4K) 3840 to 2160 else 1920 to 1080
            DeviceType.GENERIC_TV -> 1920 to 1080
            DeviceType.GENERIC_MOBILE -> 1920 to 1080
        }

        val preferredCodecs = when (deviceType) {
            DeviceType.NVIDIA_SHIELD -> listOfNotNull(
                if (supportsAv1) "video/av01" else null,
                if (supportsHevc) "video/hevc" else null,
                "video/avc"
            )
            DeviceType.SONY_BRAVIA -> listOfNotNull(
                if (supportsHevc) "video/hevc" else null,
                "video/avc"
            )
            DeviceType.CHROMECAST_TV -> listOfNotNull(
                if (supportsAv1) "video/av01" else null,
                if (supportsHevc) "video/hevc" else null,
                "video/avc"
            )
            else -> listOf("video/avc")
        }

        return DeviceCapabilities(
            deviceType = deviceType,
            supportsHevc = supportsHevc,
            supportsAv1 = supportsAv1,
            supports4K = supports4K,
            maxResolution = maxResolution,
            preferredCodecs = preferredCodecs
        )
    }

    private fun detectDeviceType(): DeviceType {
        val manufacturer = Build.MANUFACTURER.uppercase()
        val model = Build.MODEL.uppercase()
        val device = Build.DEVICE.uppercase()

        return when {
            manufacturer.contains("NVIDIA") || model.contains("SHIELD") -> DeviceType.NVIDIA_SHIELD
            manufacturer.contains("SONY") || model.contains("BRAVIA") -> DeviceType.SONY_BRAVIA
            manufacturer.contains("GOOGLE") && device.contains("chromecast") -> DeviceType.CHROMECAST_TV
            isAndroidTv() -> DeviceType.GENERIC_TV
            else -> DeviceType.GENERIC_MOBILE
        }
    }

    private fun isAndroidTv(): Boolean {
        return try {
            val tvManager = Class.forName("android.media.tv.TvInputManager")
            tvManager != null
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun supportsCodec(mimeType: String): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.contains(mimeType)
            }
        } catch (e: Exception) {
            false
        }
    }
}
