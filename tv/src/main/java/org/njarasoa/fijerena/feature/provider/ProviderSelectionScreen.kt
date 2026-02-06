@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderUiState
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.*

@Composable
fun TvProviderSelectionScreen(
    onProviderSelected: (ProviderEntity) -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProviderViewModel = viewModel(
        factory = ProviderViewModelFactory(context)
    )
    val uiState by viewModel.uiState.collectAsState()
    var deleteConfirmProvider by remember { mutableStateOf<ProviderEntity?>(null) }

    // Refresh provider list when screen is shown (e.g., after adding a provider)
    LaunchedEffect(Unit) {
        viewModel.loadProviders()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical
            )
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Providers",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            CinemaPrimaryButton(
                onClick = onAddProvider,
                text = "Add Provider"
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        when (val state = uiState) {
            is ProviderUiState.Loading -> {
                Text(
                    text = "Loading providers...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary
                )
            }
            is ProviderUiState.NoProviders -> {
                Text(
                    text = "No providers configured",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary
                )
            }
            is ProviderUiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaError
                )
            }
            is ProviderUiState.SingleProvider -> {
                ProviderList(
                    providers = listOf(state.provider),
                    onSelect = onProviderSelected,
                    onEdit = onEditProvider,
                    onDelete = { deleteConfirmProvider = it }
                )
            }
            is ProviderUiState.MultipleProviders -> {
                ProviderList(
                    providers = state.providers,
                    onSelect = onProviderSelected,
                    onEdit = onEditProvider,
                    onDelete = { deleteConfirmProvider = it }
                )
            }
        }
    }

    // Delete confirmation dialog
    deleteConfirmProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleteConfirmProvider = null },
            title = {
                Text(
                    "Delete Provider?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "Delete \"${provider.name}\"? All cached data for this provider will be removed.",
                    color = CinemaTextSecondary
                )
            },
            confirmButton = {
                androidx.tv.material3.Button(
                    onClick = {
                        viewModel.deleteProvider(provider.id)
                        deleteConfirmProvider = null
                    },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = CinemaError,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                androidx.tv.material3.Button(
                    onClick = { deleteConfirmProvider = null },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface
        )
    }
}

@Composable
private fun ProviderList(
    providers: List<ProviderEntity>,
    onSelect: (ProviderEntity) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (ProviderEntity) -> Unit
) {
    TvLazyColumn(
        contentPadding = PaddingValues(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize()
    ) {
        items(providers, key = { it.id }) { provider ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (provider.isActive) CinemaAccent else CinemaTextPrimary
                        )
                        if (provider.isActive) {
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = CinemaAccent.copy(alpha = CinemaAlpha.textHigh)
                            )
                        }
                    }
                    Text(
                        text = provider.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Text(
                        text = provider.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextTertiary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (!provider.isActive) {
                        CinemaPrimaryButton(
                            onClick = { onSelect(provider) },
                            text = "Select"
                        )
                    }
                    CinemaSecondaryButton(
                        onClick = { onEdit(provider.id) },
                        text = "Edit"
                    )
                    CinemaDangerButton(
                        onClick = { onDelete(provider) },
                        text = "Delete"
                    )
                }
            }
        }
    }
}
