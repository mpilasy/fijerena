package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CinemaThemePalette
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun ThemeSettingsCard(
    selectedThemeId: String,
    onOpenThemePicker: () -> Unit,
    selectedUiStyleId: String,
    onOpenUiStylePicker: () -> Unit,
    scale: Float,
    themeRowFocusRequester: FocusRequester? = null,
    uiStyleRowFocusRequester: FocusRequester? = null,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(
            modifier = Modifier.padding(Spacing.md.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_theme_section_title),
                description = stringResource(R.string.settings_theme_desc),
                value = AllPalettes.firstOrNull { it.id == selectedThemeId }?.displayName ?: selectedThemeId,
                scope = SettingsScope.DEVICE,
                onClick = onOpenThemePicker,
                focusRequester = themeRowFocusRequester,
            )
            SettingsRow(
                title = stringResource(R.string.settings_ui_style_section_title),
                description = stringResource(R.string.settings_ui_style_desc_tv),
                value = AllUiStyles.firstOrNull { it.id == selectedUiStyleId }?.displayName ?: selectedUiStyleId,
                scope = SettingsScope.DEVICE,
                onClick = onOpenUiStylePicker,
                focusRequester = uiStyleRowFocusRequester,
            )
        }
    }
}

@Composable
fun ThemePickerPane(
    selectedThemeId: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPickerPane(
        title = stringResource(R.string.settings_theme_section_title),
        options = AllPalettes.map { palette -> PickerOption(palette.displayName, palette.id) { PaletteSwatches(palette) } },
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
        options = AllUiStyles.map { style -> PickerOption(style.displayName, style.id) },
        selectedValue = selectedUiStyleId,
        onPick = onPick,
        onBack = onBack,
    )
}

/** Four dots of the palette's colours — a static sample next to the theme's name. */
@Composable
private fun PaletteSwatches(palette: CinemaThemePalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        listOf(palette.accent, palette.accentLight, palette.orange, palette.surfaceLight).forEach { color ->
            Box(modifier = Modifier.size(Spacing.md).background(color, CircleShape))
        }
    }
}
