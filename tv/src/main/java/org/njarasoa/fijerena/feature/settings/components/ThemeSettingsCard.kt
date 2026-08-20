package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun ThemeSettingsCard(
    selectedThemeId: String,
    onThemeSelected: (String) -> Unit,
    selectedUiStyleId: String,
    onUiStyleSelected: (String) -> Unit,
    scale: Float,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = stringResource(R.string.settings_theme_section_title),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize =
                            MaterialTheme.typography.titleMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
            Text(
                text = stringResource(R.string.settings_theme_desc),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize =
                            MaterialTheme.typography.bodySmall.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                AllPalettes.chunked(2).forEachIndexed { rowIndex, rowPalettes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                    ) {
                        rowPalettes.forEach { palette ->
                            TvSelectableButton(
                                selected = selectedThemeId == palette.id,
                                onSelect = { onThemeSelected(palette.id) },
                                text = palette.displayName,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = stringResource(R.string.settings_ui_style_section_title),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize =
                            MaterialTheme.typography.titleMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
            Text(
                text = stringResource(R.string.settings_ui_style_desc_tv),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize =
                            MaterialTheme.typography.bodySmall.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                AllUiStyles.chunked(2).forEachIndexed { rowIndex, rowStyles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                    ) {
                        rowStyles.forEach { style ->
                            TvSelectableButton(
                                selected = selectedUiStyleId == style.id,
                                onSelect = { onUiStyleSelected(style.id) },
                                text = style.displayName,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
