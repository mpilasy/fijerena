package org.njarasoa.fijerena.core.ui.deviceinfo

import androidx.annotation.StringRes
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.UiText

/**
 * Settings → About → Device info: facts about the app, the device and its media, network and power
 * state, read from local APIs only. Labels and words such as "Yes" are string resources, resolved
 * where they are shown (the view model holds the application context, which doesn't follow the
 * in-app language). See docs/plans/archive/20261004_device-info-screen-plan.md.
 */
data class DeviceInfoRow(
    val label: UiText,
    val value: UiText,
)

/** A titled group of rows; one focus stop on TV. */
data class DeviceInfoSection(
    @param:StringRes val title: Int,
    val rows: List<DeviceInfoRow>,
)

/** A row with a translated label and a value as the system gives it; null shows "—". */
fun infoRow(
    @StringRes label: Int,
    value: String?,
): DeviceInfoRow = DeviceInfoRow(UiText.StringResource(label), value?.let { UiText.DynamicString(it) } ?: missingValue())

/** A row whose value is itself translated (a yes/no, a state name). */
fun infoRow(
    @StringRes label: Int,
    value: UiText,
): DeviceInfoRow = DeviceInfoRow(UiText.StringResource(label), value)

fun yesNo(value: Boolean?): UiText =
    when (value) {
        true -> UiText.StringResource(R.string.device_info_yes)
        false -> UiText.StringResource(R.string.device_info_no)
        null -> missingValue()
    }

/** Shown for a value the device doesn't report (API too old, refused). */
fun missingValue(): UiText = UiText.StringResource(R.string.device_info_missing)
