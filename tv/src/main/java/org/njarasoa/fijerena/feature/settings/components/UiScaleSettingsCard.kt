package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import kotlin.math.roundToInt

private val SCALE_OPTIONS = listOf(0.4f, 0.6f, 0.8f, 1.0f)

private fun scaleLabel(value: Float): String = "${(value * 100).roundToInt()}%"

@Composable
fun UiScaleSettingsRow(
    uiScale: Float,
    onOpenPicker: () -> Unit,
    rowFocusRequester: FocusRequester? = null,
) {
    SettingsRow(
        title = stringResource(R.string.settings_ui_scale_section_title),
        description = stringResource(R.string.settings_ui_scale_desc),
        value = scaleLabel(uiScale),
        onClick = onOpenPicker,
        focusRequester = rowFocusRequester,
    )
}

@Composable
fun UiScalePickerPane(
    uiScale: Float,
    onPick: (Float) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPickerPane(
        title = stringResource(R.string.settings_ui_scale_section_title),
        options =
            SCALE_OPTIONS.map { value ->
                PickerOption(scaleLabel(value), value) {
                    // "Aa" at that size: a static sample, not a live preview of the app.
                    // look-ok: a sample of the text size this scale gives: the Title role's size times the scale
                    Text(text = "Aa", style = textStyleOf(TextRole.Title).let { it.copy(fontSize = it.fontSize * value) })
                }
            },
        selectedValue = uiScale,
        onPick = onPick,
        onBack = onBack,
    )
}
