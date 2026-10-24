package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Picks one of [sources] (id to name) before a setting edited per source opens — content filters,
 * guide sources — when there is more than one (`SourceChoice.Ask`, O4 of
 * docs/plans/20261012_multi-source-search-history-plan.md). A dialog window: focus opens on the
 * first source (never on Cancel), Back closes it and focus goes back to its opener.
 */
@Composable
fun TvSourceChooserDialog(
    sources: List<Pair<Long, String>>,
    onChosen: (providerId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstRow = remember { FocusRequester() }
    CinemaAlertDialog(
        initialFocus = firstRow,
        onDismissRequest = onDismiss,
        // The darkest surface behind the rows, as the other TV pickers (TV UI audit #3).
        containerColor = CinemaTheme.colors.background,
        titleContentColor = CinemaTheme.colors.textPrimary,
        textContentColor = CinemaTheme.colors.textSecondary,
        title = { CinemaText(stringResource(R.string.source_chooser_title), role = TextRole.Title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                sources.forEachIndexed { index, (id, name) ->
                    TvOptionRow(
                        title = name,
                        role = TextRole.Heading,
                        selected = false,
                        onClick = { onChosen(id) },
                        modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
                    )
                }
            }
        },
        confirmButton = {
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), tone = TextTone.Inherit) }
        },
    )
}
