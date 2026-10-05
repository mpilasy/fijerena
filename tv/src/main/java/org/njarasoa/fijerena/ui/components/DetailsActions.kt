package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * What a details screen's "More" opens (movie and series heroes): Refresh info, then Cancel.
 * Focus opens on Refresh; Back or Cancel closes.
 */
@Composable
internal fun DetailsMoreMenu(
    title: String,
    refreshLabel: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                maxLines = 2,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TvInputListItem(
                    selected = false,
                    onClick = {
                        onRefresh()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(firstItemFocusRequester),
                    leadingContent = {
                        Icon(
                            imageVector = CinemaIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(TvDimensions.iconSmall),
                            tint = CinemaAccent,
                        )
                    },
                    headlineContent = {
                        Text(text = refreshLabel, style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
                    },
                )
                TvInputListItem(
                    selected = false,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        Icon(
                            imageVector = CinemaIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(TvDimensions.iconSmall),
                            tint = CinemaTextSecondary,
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextSecondary,
                        )
                    },
                )
            }
        },
        initialFocus = firstItemFocusRequester,
        confirmButton = {},
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
    )
}
