package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton

/**
 * Picks one of [sources] (id to name) before a setting edited per source opens — content filters,
 * guide sources — when there is more than one (`SourceChoice.Ask`, O4 of
 * docs/plans/20261012_multi-source-search-history-plan.md).
 */
@Composable
fun MobileSourceChooserDialog(
    sources: List<Pair<Long, String>>,
    onChosen: (providerId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(
                stringResource(R.string.source_chooser_title),
                role = TextRole.Title,
                tone = TextTone.Inherit,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
            ) {
                sources.forEach { (id, name) ->
                    Surface(
                        onClick = { onChosen(id) },
                        modifier = Modifier.fillMaxWidth(),
                        color = CinemaTheme.colors.surfaceRaised,
                        shape = CinemaTheme.shapes.chip,
                    ) {
                        CinemaText(
                            text = name,
                            modifier = Modifier.padding(CinemaSpacing.md),
                            role = TextRole.Heading,
                            tone = TextTone.Inherit,
                        )
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(
                    stringResource(R.string.common_cancel),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        },
    )
}
