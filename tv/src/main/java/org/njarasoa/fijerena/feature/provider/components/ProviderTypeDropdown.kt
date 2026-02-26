package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary

@Composable
fun ProviderTypeDropdown(
    selectedType: ProviderType,
    onTypeSelected: (ProviderType) -> Unit
) {
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedType.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provider Type") },
            trailingIcon = {
                Text(
                    text = if (typeDropdownExpanded) "▲" else "▼",
                    color = CinemaAccent
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { typeDropdownExpanded = true }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                    ) {
                        typeDropdownExpanded = true
                        true
                    } else false
                },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CinemaTextPrimary,
                unfocusedTextColor = CinemaTextPrimary,
                cursorColor = CinemaAccent,
                focusedBorderColor = CinemaAccent,
                unfocusedBorderColor = CinemaTextSecondary,
                focusedLabelColor = CinemaAccent,
                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                focusedContainerColor = CinemaSurfaceVariant,
                focusedTrailingIconColor = CinemaAccent,
                unfocusedTrailingIconColor = CinemaTextSecondary
            )
        )
        DropdownMenu(
            expanded = typeDropdownExpanded,
            onDismissRequest = { typeDropdownExpanded = false },
            containerColor = CinemaSurface
        ) {
            ProviderType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = type.displayName,
                            color = if (type == selectedType) CinemaAccent else CinemaTextPrimary
                        )
                    },
                    onClick = {
                        onTypeSelected(type)
                        typeDropdownExpanded = false
                    }
                )
            }
        }
    }
}
