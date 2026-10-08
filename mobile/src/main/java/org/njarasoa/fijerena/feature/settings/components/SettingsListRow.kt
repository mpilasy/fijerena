package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.ui.theme.Spacing

/** Where a setting lives, shown as a small chip under the row's summary (shared visual rules, A). */
enum class SettingsScope {
    DEVICE,
    PROFILE,
    SOURCE,
    SYNCED,
}

/**
 * Section header of the grouped settings list (one per shared IA group). [description] says once
 * for the whole section where its settings live (e.g. "Applies to this device only"), as on TV.
 */
@Composable
fun SettingsGroupHeader(
    title: String,
    description: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )
        }
    }
}

/**
 * One preference row: title, current value as [summary] (a String or an [AnnotatedString]),
 * optional [scope] chip, optional [leading] content, and [trailing] content — a chevron when the
 * row navigates ([onClick]) and nothing explicit is given.
 */
@Composable
fun SettingsListRow(
    title: String,
    summary: CharSequence? = null,
    scope: SettingsScope? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else CinemaAlpha.textDisabled
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            if (summary != null) {
                val summaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow * contentAlpha)
                if (summary is AnnotatedString) {
                    Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = summaryColor)
                } else {
                    Text(text = summary.toString(), style = MaterialTheme.typography.bodyMedium, color = summaryColor)
                }
            }
            if (scope != null) {
                SettingsScopeChip(scope = scope)
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
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow * contentAlpha),
                )
            }
        }
    }
}

@Composable
private fun SettingsScopeChip(scope: SettingsScope) {
    val label =
        stringResource(
            when (scope) {
                SettingsScope.DEVICE -> R.string.settings_scope_device
                SettingsScope.PROFILE -> R.string.settings_scope_profile
                SettingsScope.SOURCE -> R.string.settings_scope_source
                SettingsScope.SYNCED -> R.string.settings_scope_synced
            },
        )
    Box(
        modifier =
            Modifier
                .padding(top = Spacing.xxs)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.ghost),
                    shape = RoundedCornerShape(CinemaCornerRadius.small),
                ).padding(horizontal = Spacing.xs, vertical = Spacing.xxxs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
    }
}
