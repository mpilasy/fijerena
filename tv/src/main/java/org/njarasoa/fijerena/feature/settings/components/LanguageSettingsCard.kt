package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
private fun languageOptions(): List<PickerOption<String>> =
    listOf(
        PickerOption(stringResource(R.string.settings_language_en), "en"),
        PickerOption(stringResource(R.string.settings_language_mg), "mg"),
        PickerOption(stringResource(R.string.settings_language_fr), "fr"),
    )

@Composable
fun LanguageSettingsCard(
    selectedLanguage: String,
    onOpenPicker: () -> Unit,
    scale: Float,
    rowFocusRequester: FocusRequester? = null,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        SettingsRow(
            title = stringResource(R.string.settings_language),
            description = stringResource(R.string.settings_language_desc),
            value = languageOptions().firstOrNull { it.value == selectedLanguage }?.label ?: selectedLanguage,
            scope = SettingsScope.DEVICE,
            onClick = onOpenPicker,
            focusRequester = rowFocusRequester,
            modifier = Modifier.padding(Spacing.md.scaled(scale)),
        )
    }
}

/** Picking a language other than the current one recreates the Activity (the caller's [onPick]). */
@Composable
fun LanguagePickerPane(
    selectedLanguage: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPickerPane(
        title = stringResource(R.string.settings_language),
        options = languageOptions(),
        selectedValue = selectedLanguage,
        onPick = { code -> if (code != selectedLanguage) onPick(code) },
        onBack = onBack,
    )
}
