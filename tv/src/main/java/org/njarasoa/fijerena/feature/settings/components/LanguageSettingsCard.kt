package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R

@Composable
private fun languageOptions(): List<PickerOption<String>> =
    listOf(
        PickerOption(stringResource(R.string.settings_language_en), "en"),
        PickerOption(stringResource(R.string.settings_language_mg), "mg"),
        PickerOption(stringResource(R.string.settings_language_fr), "fr"),
    )

@Composable
fun LanguageSettingsRow(
    selectedLanguage: String,
    onOpenPicker: () -> Unit,
    rowFocusRequester: FocusRequester? = null,
) {
    SettingsRow(
        title = stringResource(R.string.settings_language),
        description = stringResource(R.string.settings_language_desc),
        value = languageOptions().firstOrNull { it.value == selectedLanguage }?.label ?: selectedLanguage,
        onClick = onOpenPicker,
        focusRequester = rowFocusRequester,
    )
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
