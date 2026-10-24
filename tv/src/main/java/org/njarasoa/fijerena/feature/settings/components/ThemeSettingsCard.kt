package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.PaletteSwatches
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.palettesFor
import org.njarasoa.fijerena.ui.theme.ProvideLook
import java.text.Collator

/** The Theme and Look and Feel rows of a profile's page, in its Look section (ProfileEditPane). */
@Composable
fun ThemeSettingsRows(
    selectedThemeId: String,
    onOpenThemePicker: () -> Unit,
    selectedUiStyleId: String,
    onOpenUiStylePicker: () -> Unit,
    themeRowFocusRequester: FocusRequester? = null,
    uiStyleRowFocusRequester: FocusRequester? = null,
    /** Goes on the Theme row. */
    themeRowModifier: Modifier = Modifier,
    /** False while a packaged UI theme sets both. */
    enabled: Boolean = true,
) {
    SettingsRow(
        title = stringResource(R.string.settings_theme_section_title),
        description = stringResource(R.string.settings_theme_desc),
        value = AllPalettes.firstOrNull { it.id == selectedThemeId }?.let { stringResource(it.nameRes) } ?: selectedThemeId,
        onClick = onOpenThemePicker,
        focusRequester = themeRowFocusRequester,
        modifier = themeRowModifier,
        enabled = enabled,
    )
    SettingsRow(
        title = stringResource(R.string.settings_ui_style_section_title),
        description = stringResource(R.string.settings_ui_style_desc_tv),
        value = AllUiStyles.firstOrNull { it.id == selectedUiStyleId }?.let { stringResource(it.nameRes) } ?: selectedUiStyleId,
        onClick = onOpenUiStylePicker,
        focusRequester = uiStyleRowFocusRequester,
        enabled = enabled,
    )
}

@Composable
fun ThemePickerPane(
    selectedThemeId: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    admin: Boolean = true,
) {
    SettingsPickerPane(
        title = stringResource(R.string.settings_theme_section_title),
        options =
            // Alphabetical in the app's language.
            palettesFor(admin, selectedThemeId)
                .map { palette ->
                    PickerOption(stringResource(palette.nameRes), palette.id, preview = { PaletteSwatches(palette) })
                }.sortedWith(compareBy(Collator.getInstance()) { it.label }),
        selectedValue = selectedThemeId,
        onPick = onPick,
        onBack = onBack,
    )
}

@Composable
fun UiStylePickerPane(
    selectedUiStyleId: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPickerPane(
        title = stringResource(R.string.settings_ui_style_section_title),
        options =
            AllUiStyles
                // Each row drawn in its own Look: font, corners, focus.
                .map { style -> PickerOption(stringResource(style.nameRes), style.id, inLook = { ProvideLook(style, it) }) }
                .sortedWith(compareBy(Collator.getInstance()) { it.label }),
        selectedValue = selectedUiStyleId,
        onPick = onPick,
        onBack = onBack,
    )
}
