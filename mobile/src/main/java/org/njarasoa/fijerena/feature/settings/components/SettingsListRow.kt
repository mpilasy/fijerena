package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.colorOf
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Section header of the grouped settings list (one per shared IA group). [description] says once
 * for the whole section where its settings live (e.g. "Applies to this device only"), as on TV.
 */
@Composable
fun SettingsGroupHeader(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.xs),
    ) {
        CinemaText(title, role = TextRole.Label, tone = TextTone.Primary)
        if (description != null) {
            CinemaText(description, role = TextRole.BodySmall, tone = TextTone.Secondary)
        }
    }
}

/**
 * One preference row: title, current value as [summary] (a String or an [AnnotatedString]),
 * optional [leading] content, and [trailing] content — a chevron when the row navigates
 * ([onClick]) and nothing explicit is given. [titleRole]: [TextRole.Heading] when the
 * title is a name (a profile).
 */
@Composable
fun SettingsListRow(
    title: String,
    summary: CharSequence? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    titleRole: TextRole = TextRole.Body,
) {
    val titleTone = if (enabled) TextTone.Primary else TextTone.Disabled
    val summaryTone = if (enabled) TextTone.Secondary else TextTone.Disabled
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            CinemaText(title, role = titleRole, tone = titleTone)
            if (summary != null) {
                if (summary is AnnotatedString) {
                    CinemaText(summary, tone = summaryTone)
                } else {
                    CinemaText(summary.toString(), tone = summaryTone)
                }
            }
        }
        when {
            trailing != null -> {
                Spacer(modifier = Modifier.width(Spacing.sm))
                trailing()
            }

            onClick != null -> {
                Icon(
                    CinemaIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorOf(summaryTone),
                )
            }
        }
    }
}
