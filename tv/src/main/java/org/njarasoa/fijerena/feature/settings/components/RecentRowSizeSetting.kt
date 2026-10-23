package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R

// The 25 default (AppSettings.DEFAULT_WATCH_HISTORY_SIZE) is one of them, so a fresh install shows it selected.
private val RECENT_ROW_SIZES = listOf(5, 10, 15, 20, 25, 30, 40, 50, 75, 100)

/** The stored size as shown: a preset as is, anything else (from a source before the move, 1–100) marked custom. */
@Composable
private fun recentRowSizeLabel(size: Int): String =
    if (size in RECENT_ROW_SIZES) size.toString() else stringResource(R.string.settings_custom_value_format, size.toString())

/** Display's "Items in Recent": one size for every source on this device (Settings reorg, D4). */
@Composable
fun RecentRowSizeRow(
    size: Int,
    onOpenPicker: () -> Unit,
    rowFocusRequester: FocusRequester? = null,
) {
    SettingsRow(
        title = stringResource(R.string.settings_recent_row_size_title),
        description = stringResource(R.string.settings_recent_row_size_desc),
        value = recentRowSizeLabel(size),
        onClick = onOpenPicker,
        focusRequester = rowFocusRequester,
    )
}

@Composable
fun RecentRowSizePickerPane(
    size: Int,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // A stored value outside the presets is kept as its own checked option, so nothing is lost.
    val sizes = (RECENT_ROW_SIZES + size).distinct().sorted()
    SettingsPickerPane(
        title = stringResource(R.string.settings_recent_row_size_title),
        options = sizes.map { PickerOption(recentRowSizeLabel(it), it) },
        selectedValue = size,
        onPick = onPick,
        onBack = onBack,
    )
}
