@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextTertiary
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * One choice in a full-width picker list — audio track, subtitle track, quality, chapter.
 *
 * [selected] must be driven by what is actually active, never by what currently holds focus. The
 * player selector dialogs previously set their selected index from `onFocusChanged`, so merely
 * arrowing through the list re-pointed the "active" marker and the dialog reported the wrong
 * current track.
 */
@Composable
fun TvOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    activeLabel: String? = null,
) {
    TvInputListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        trailingContent =
            if (selected) {
                {
                    if (activeLabel != null) {
                        Text(text = activeLabel, style = MaterialTheme.typography.bodySmall, color = CinemaAccent)
                    } else {
                        Icon(imageVector = CinemaIcons.CheckCircle, contentDescription = null, tint = CinemaAccent)
                    }
                }
            } else {
                null
            },
        supportingContent =
            subtitle?.let {
                { Text(text = it, style = MaterialTheme.typography.bodySmall, color = CinemaTextTertiary) }
            },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) TvFocusTokens.emphasisWeight else TvFocusTokens.regularWeight,
        )
    }
}
