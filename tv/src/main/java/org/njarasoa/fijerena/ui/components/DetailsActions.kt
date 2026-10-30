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
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
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
 * Focus opens on Refresh; Back or Cancel closes. With [removes] (the one action is a removal, such
 * as Remove from Wishlist), focus opens on Cancel: a removal is never the first focus (UX-D1).
 */
@Composable
internal fun DetailsMoreMenu(
    title: String,
    refreshLabel: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    // The one action's icon: Refresh info on a details screen; Remove from Wanted on Home's Wanted row.
    icon: ImageVector = CinemaIcons.Refresh,
    removes: Boolean = false,
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val firstFocus: (Boolean) -> Modifier = { takes -> if (takes) Modifier.focusRequester(firstItemFocusRequester) else Modifier }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(text = title, role = TextRole.Heading, maxLines = 2)
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
                    modifier = firstFocus(!removes).fillMaxWidth(),
                    leadingContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(TvDimensions.iconSmall),
                            tint = CinemaTheme.colors.accent,
                        )
                    },
                    headlineContent = {
                        CinemaText(text = refreshLabel)
                    },
                )
                TvInputListItem(
                    selected = false,
                    onClick = onDismiss,
                    modifier = firstFocus(removes).fillMaxWidth(),
                    leadingContent = {
                        Icon(
                            imageVector = CinemaIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(TvDimensions.iconSmall),
                            tint = CinemaTheme.colors.textSecondary,
                        )
                    },
                    headlineContent = {
                        CinemaText(text = stringResource(R.string.common_cancel), tone = TextTone.Secondary)
                    },
                )
            }
        },
        initialFocus = firstItemFocusRequester,
        confirmButton = {},
        containerColor = CinemaTheme.colors.surface,
        titleContentColor = CinemaTheme.colors.textPrimary,
        textContentColor = CinemaTheme.colors.textSecondary,
    )
}
