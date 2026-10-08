package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The one phone empty state (phone UI audit, P8; the TV's is `TvEmptyState`): an optional [icon],
 * one sentence ([message]), an optional [secondary] line and an optional single action, centred
 * in the space it is given. Nothing went wrong — the list or section simply has nothing to show —
 * so nothing is red and there is no Retry; real failures keep the error states.
 *
 * ```
 * MobileEmptyState(
 *     message = stringResource(R.string.epg_management_empty),
 *     icon = CinemaIcons.CalendarMonth,
 *     actionLabel = stringResource(R.string.epg_add_source),
 *     onAction = onAdd,
 * )
 * ```
 */
@Composable
fun MobileEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondary: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            modifier = Modifier.padding(CinemaSpacing.xl),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CinemaTextSecondary,
                    modifier = Modifier.size(MobileDimensions.iconXLarge),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                textAlign = TextAlign.Center,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                CinemaButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
