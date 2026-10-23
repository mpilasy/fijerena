package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.ui.theme.autoUiScale
import kotlin.math.roundToInt

// Named after the screen each suits best; the same in every language.
private val SCALE_OPTIONS = listOf(0.4f to "8K", 0.6f to "4K", 0.8f to "HD", 1.0f to "SD")

private fun scaleName(value: Float): String = SCALE_OPTIONS.firstOrNull { it.first == value }?.second ?: "${(value * 100).roundToInt()}%"

/** [choice]'s name; null is Auto, with what it chose for this screen ("Auto (4K)"). */
@Composable
private fun scaleLabel(choice: Float?): String =
    if (choice == null) {
        stringResource(R.string.settings_ui_scale_auto_format, scaleName(autoUiScale(LocalContext.current)))
    } else {
        scaleName(choice)
    }

@Composable
fun UiScaleSettingsRow(
    uiScale: Float?,
    onOpenPicker: () -> Unit,
    rowFocusRequester: FocusRequester? = null,
    /** Display's first row: the pane's entry row. */
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        modifier = modifier,
        title = stringResource(R.string.settings_ui_scale_section_title),
        description = stringResource(R.string.settings_ui_scale_desc),
        value = scaleLabel(uiScale),
        onClick = onOpenPicker,
        focusRequester = rowFocusRequester,
    )
}

@Composable
fun UiScalePickerPane(
    uiScale: Float?,
    onPick: (Float?) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPickerPane(
        title = stringResource(R.string.settings_ui_scale_section_title),
        options =
            listOf(PickerOption<Float?>(scaleLabel(null), null)) +
                SCALE_OPTIONS.map { (value, name) ->
                    PickerOption<Float?>(name, value, preview = {
                        // "Aa" at that size: a static sample, not a live preview of the app.
                        // look-ok: a sample of the text size this scale gives: the Title role's size times the scale
                        Text(text = "Aa", style = textStyleOf(TextRole.Title).let { it.copy(fontSize = it.fontSize * value) })
                    })
                },
        selectedValue = uiScale,
        onPick = onPick,
        onBack = onBack,
    )
}
