package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.serialization.Serializable

/**
 * The app build a live sync device runs, as it reports it to its group (the device builds plan): a
 * shared setting record `device_build:<deviceId>` whose value is this, as JSON. End-to-end
 * encrypted like every record. Every field has a default, so a field added later reads as empty
 * from an older build.
 */
@Serializable
data class DeviceBuild(
    val versionName: String = "",
    val versionCode: Long = 0,
    /** `debug`, `localRelease` or `release` (the manifest's `fijerena.buildType`). */
    val buildType: String = "",
    /** `tv` or `phone`. */
    val app: String = "",
    val model: String = "",
    val androidRelease: String = "",
    val sdkInt: Int = 0,
    /** What this build can do for the others of its group; a build from before a feature reports nothing for it. */
    val features: List<String> = emptyList(),
) {
    /** A TV of this build takes a film, an episode or a channel from a phone (the send to TV plan). */
    val takesPlayRequests: Boolean get() = FEATURE_PLAY_REQUEST in features

    companion object {
        const val META_BUILD_TYPE = "fijerena.buildType"
        const val FEATURE_PLAY_REQUEST = "play_request"

        /** This device's build, from its package and the system. */
        fun current(context: Context): DeviceBuild {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            val buildType =
                pm
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData
                    ?.getString(META_BUILD_TYPE)
                    .orEmpty()
            return DeviceBuild(
                versionName = info.versionName.orEmpty(),
                versionCode = info.longVersionCode,
                buildType = buildType,
                app = if (context.packageName.endsWith(".mobile")) "phone" else "tv",
                model = Build.MODEL.orEmpty(),
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                features = listOf(FEATURE_PLAY_REQUEST),
            )
        }
    }
}
