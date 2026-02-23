@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun TvCheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scale = LocalUiScale.current

    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaSurfaceVariant,
            focusedContentColor = CinemaTextPrimary,
            pressedContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.textMedium),
            disabledContainerColor = Color.Transparent,
            disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.md.scaled(scale),
                vertical = Spacing.sm.scaled(scale)
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null, // Handled by Surface click
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = CinemaAccent,
                    uncheckedColor = CinemaTextSecondary,
                    checkmarkColor = CinemaTextPrimary,
                    disabledCheckedColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                    disabledUncheckedColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.scrim),
                    disabledIndeterminateColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim)
                )
            )
            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                )
            )
        }
    }
}
