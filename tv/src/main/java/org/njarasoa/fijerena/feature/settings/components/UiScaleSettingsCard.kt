package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

private val SCALE_OPTIONS = listOf(
    0.4f to "40%",
    0.6f to "60%",
    0.8f to "80%",
    1.0f to "100%"
).chunked(2)

@Composable
fun UiScaleSettingsCard(
    uiScale: Float,
    onScaleSelected: (Float) -> Unit,
    scale: Float
) {
    Column {
        Text(
            text = "Category/Grid UI Scale",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
        Text(
            text = "Adjust font, spacing, and element sizes for category/grid views",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
            ),
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

        // Scale options as buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
        ) {
            SCALE_OPTIONS.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                ) {
                    rowItems.forEach { (scaleValue, label) ->
                        val isSelected = uiScale == scaleValue
                        if (isSelected) {
                            CinemaPrimaryButton(
                                onClick = { },
                                text = label,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            CinemaSecondaryButton(
                                onClick = {
                                    onScaleSelected(scaleValue)
                                },
                                text = label,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
