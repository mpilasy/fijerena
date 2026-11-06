package org.njarasoa.fijerena.core.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone

/**
 * A live sync device's build under its name, the same on TV and phone (the device builds plan):
 * "0.9.20261106024835 · debug · Android 11", then "Older build" when [older]; "Build not reported
 * yet" without one.
 */
@Composable
fun DeviceBuildLine(
    build: DeviceBuild?,
    older: Boolean,
) {
    if (build == null || build.versionName.isEmpty()) {
        CinemaText(stringResource(R.string.live_sync_device_build_unknown), role = TextRole.BodySmall, tone = TextTone.Tertiary)
        return
    }
    val parts =
        listOfNotNull(build.versionName, build.buildType.ifEmpty { null }, build.androidRelease.ifEmpty { null }?.let { "Android $it" })
    CinemaText(parts.joinToString(" · "), role = TextRole.BodySmall, tone = TextTone.Secondary)
    if (older) CinemaText(stringResource(R.string.live_sync_device_build_older), role = TextRole.BodySmall, tone = TextTone.Warning)
}

/** Whether [build] is older than the newest of [builds] (by version code); false when either is unknown. */
fun isOlderBuild(
    build: DeviceBuild?,
    builds: Collection<DeviceBuild>,
): Boolean {
    val code = build?.versionCode?.takeIf { it > 0 } ?: return false
    val newest = builds.maxOfOrNull { it.versionCode } ?: return false
    return code < newest
}
