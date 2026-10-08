package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * The source type, as a row like Settings' (TV UI audit, X5): "Source Type … Xtream IPTV". Adding a
 * source, OK on the row opens the list of types. With [enabled] false (editing an existing source)
 * it is read-only text, not focusable: the type of a source is fixed.
 */
@Composable
fun ProviderTypeDropdown(
    types: List<ProviderType>,
    selectedType: ProviderType,
    onTypeSelected: (ProviderType) -> Unit,
    enabled: Boolean = true,
) {
    val label = stringResource(R.string.provider_type_label)
    if (!enabled) {
        // The same insets as a list row's content, so it lines up with the rows under it.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = CinemaTextSecondary, modifier = Modifier.weight(1f))
            Text(text = selectedType.displayName, style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
        }
        return
    }

    var typeDropdownExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        TvInputListItem(
            selected = false,
            onClick = { typeDropdownExpanded = true },
            modifier = Modifier.fillMaxWidth(),
            trailingContent = {
                Text(text = "${selectedType.displayName} ›", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            },
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
        }
        DropdownMenu(
            expanded = typeDropdownExpanded,
            onDismissRequest = { typeDropdownExpanded = false },
            containerColor = CinemaSurface,
        ) {
            // The menu is a Popup, so it gets its own window and loses the scaled density.
            ProvideUiScaledDensity {
                Column {
                    types.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = type.displayName,
                                    color = if (type == selectedType) CinemaAccent else CinemaTextPrimary,
                                )
                            },
                            onClick = {
                                onTypeSelected(type)
                                typeDropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
