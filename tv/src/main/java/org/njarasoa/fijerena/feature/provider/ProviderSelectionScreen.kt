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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.*

@Composable
fun TvProviderSelectionScreen(
    onProviderSelected: (ProviderEntity) -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ProviderViewModel =
        viewModel(
            factory = ProviderViewModelFactory(context),
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteConfirmProvider by remember { mutableStateOf<ProviderEntity?>(null) }
    val appSettings =
        remember {
            org.njarasoa.fijerena.core.network
                .AppSettings(context.applicationContext)
        }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    // Refresh provider list when screen is shown (e.g., after adding a provider)
    LaunchedEffect(Unit) {
        viewModel.loadProviders()
    }

    val scale = LocalUiScale.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical,
                ),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Providers",
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        fontSize =
                            MaterialTheme.typography.displaySmall.fontSize
                                .scaled(scale),
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            CinemaIconButton(
                onClick = onAddProvider,
                icon = {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Provider", tint = CinemaAccent)
                },
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

        when (val state = uiState) {
            is ProviderUiState.Loading -> {
                Text(
                    text = "Loading providers...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary,
                )
            }
            is ProviderUiState.NoProviders -> {
                Text(
                    text = "No providers configured",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary,
                )
            }
            is ProviderUiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaError,
                )
            }
            is ProviderUiState.SingleProvider -> {
                ProviderList(
                    providers = listOf(state.provider),
                    onSelect = onProviderSelected,
                    onEdit = onEditProvider,
                    onDelete = { deleteConfirmProvider = it },
                )
            }
            is ProviderUiState.MultipleProviders -> {
                ProviderList(
                    providers = state.providers,
                    onSelect = onProviderSelected,
                    onEdit = onEditProvider,
                    onDelete = { deleteConfirmProvider = it },
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    "Delete \"${provider.name}\"? All cached data for this provider will be removed.",
                    color = CinemaTextSecondary,
                )
            },
            confirmButton = {
                androidx.tv.material3.Button(
                    onClick = {
                        viewModel.deleteProvider(provider.id)
                        deleteConfirmProvider = null
                    },
                    colors =
                        androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = CinemaError,
                            contentColor = CinemaTextPrimary,
                        ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                androidx.tv.material3.Button(
                    onClick = { deleteConfirmProvider = null },
                    colors =
                        androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = CinemaTextPrimary,
                        ),
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface,
        )
    }
}

@Composable
private fun ProviderList(
    providers: List<ProviderEntity>,
    onSelect: (ProviderEntity) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (ProviderEntity) -> Unit,
) {
    val scale = LocalUiScale.current
    TvLazyColumn(
        contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(providers, key = { it.id }, contentType = { "provider" }) { provider ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (provider.isActive) CinemaAccent else CinemaTextPrimary,
                        )
                        if (provider.isActive) {
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = CinemaAccent.copy(alpha = CinemaAlpha.textHigh),
                            )
                        }
                    }
                    Text(
                        text = provider.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                    Text(
                        text = provider.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextTertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (!provider.isActive) {
                        CinemaIconButton(
                            onClick = { onSelect(provider) },
                            icon = {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Select", tint = CinemaAccent)
                            },
                        )
                    }
                    CinemaIconButton(
                        onClick = { onEdit(provider.id) },
                        icon = {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = CinemaAccent)
                        },
                    )
                    CinemaDangerIconButton(
                        onClick = { onDelete(provider) },
                        icon = {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        },
                    )
                }
            }
        }
    }
}
