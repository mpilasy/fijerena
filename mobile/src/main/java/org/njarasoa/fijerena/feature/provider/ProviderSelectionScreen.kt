package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderUiState
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileProviderSelectionScreen(
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Providers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddProvider) {
                        Icon(Icons.Default.Add, contentDescription = "Add Provider")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(CinemaSpacing.md)
        ) {
            when (val state = uiState) {
                is ProviderUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ProviderUiState.NoProviders -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No providers configured",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                        )
                    }
                }
                is ProviderUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaError
                    )
                }
                is ProviderUiState.SingleProvider -> {
                    MobileProviderList(
                        providers = listOf(state.provider),
                        onSelect = onProviderSelected,
                        onEdit = onEditProvider,
                        onDelete = { deleteConfirmProvider = it }
                    )
                }
                is ProviderUiState.MultipleProviders -> {
                    MobileProviderList(
                        providers = state.providers,
                        onSelect = onProviderSelected,
                        onEdit = onEditProvider,
                        onDelete = { deleteConfirmProvider = it }
                    )
                }
            }
        }
    }

    // Delete confirmation
    deleteConfirmProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleteConfirmProvider = null },
            title = { Text("Delete Provider?") },
            text = {
                Text("Delete \"${provider.name}\"? All cached data for this provider will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProvider(provider.id)
                        deleteConfirmProvider = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmProvider = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MobileProviderList(
    providers: List<ProviderEntity>,
    onSelect: (ProviderEntity) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (ProviderEntity) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        modifier = Modifier.fillMaxSize()
    ) {
        items(providers, key = { it.id }, contentType = { "provider" }) { provider ->
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (provider.isActive) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = provider.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = provider.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs, Alignment.End)
                    ) {
                        if (!provider.isActive) {
                            IconButton(onClick = { onSelect(provider) }) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Select",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { onEdit(provider.id) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { onDelete(provider) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = CinemaError
                            )
                        }
                    }
                }
            }
        }
    }
}
