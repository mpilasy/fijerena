@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/** Where a setting applies — shown as a small chip on every settings row (plan Part I, A). */
enum class SettingsScope { DEVICE, PROFILE, SOURCE, SYNCED }

@Composable
fun SettingsScope.label(): String =
    stringResource(
        when (this) {
            SettingsScope.DEVICE -> R.string.settings_scope_device
            SettingsScope.PROFILE -> R.string.settings_scope_profile
            SettingsScope.SOURCE -> R.string.settings_scope_source
            SettingsScope.SYNCED -> R.string.settings_scope_synced
        },
    )

/** The scope chip on its own, for cards that are not built on [SettingsRow] (Sources, Live sync, backup). */
@Composable
fun SettingsScopeChip(
    scope: SettingsScope,
    modifier: Modifier = Modifier,
) {
    Text(
        text = scope.label(),
        style = MaterialTheme.typography.labelSmall,
        color = CinemaTextSecondary,
        maxLines = 1,
        modifier =
            modifier
                .border(
                    width = TvFocusTokens.borderThin,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                    shape = RoundedCornerShape(CornerRadius.small),
                ).padding(horizontal = Spacing.xs, vertical = Spacing.xxxs),
    )
}

/**
 * One focusable settings row: title, one-line description with the scope chip at its end, and the
 * current value with a chevron on the right (`Theme · Deep Night ›`). OK opens the row's picker —
 * it never changes the value itself (plan Part I, B, T-6).
 */
@Composable
fun SettingsRow(
    title: String,
    description: String?,
    value: String,
    scope: SettingsScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    TvInputListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        enabled = enabled,
        trailingContent = {
            Text(
                text = "$value ›",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                SettingsScopeChip(scope)
            }
        },
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * A switch setting with its scope. [TvSwitchRow] has no slot for the chip, so the scope rides at
 * the end of the description text instead of as a separate chip.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    description: String,
    scope: SettingsScope,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSwitchRow(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = title,
        description = "$description · ${scope.label()}",
        modifier = modifier,
    )
}
