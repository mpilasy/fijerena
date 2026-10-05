package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderUiState
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.feature.provider.components.CopyProviderDialog
import org.njarasoa.fijerena.feature.provider.components.DuplicateProviderDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileProviderSelectionScreen(
    onProviderSelected: (ProviderEntity) -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (Long) -> Unit,
    onManageEpg: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ProviderViewModel =
        viewModel(
            factory = ProviderViewModelFactory(context),
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val copyResultMessage by viewModel.copyResultMessage.collectAsStateWithLifecycle()
    var deleteConfirmProvider by remember { mutableStateOf<ProviderEntity?>(null) }
    var duplicateProvider by remember { mutableStateOf<ProviderEntity?>(null) }
    var copyFromProvider by remember { mutableStateOf<ProviderEntity?>(null) }

    // Refresh provider list when screen is shown (e.g., after adding a provider)
    LaunchedEffect(Unit) {
        viewModel.loadProviders()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_selection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onAddProvider) {
                        Icon(CinemaIcons.Add, contentDescription = stringResource(R.string.provider_add_title))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(CinemaSpacing.md),
        ) {
            when (val state = uiState) {
                is ProviderUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ProviderUiState.NoProviders -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                CinemaIcons.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                                modifier = Modifier.size(MobileDimensions.iconXLarge),
                            )
                            Spacer(modifier = Modifier.height(CinemaSpacing.md))
                            Text(
                                text = stringResource(R.string.provider_no_providers),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                            )
                            Spacer(modifier = Modifier.height(CinemaSpacing.lg))
                            CinemaButton(onClick = onAddProvider) {
                                Text(stringResource(R.string.provider_add_title))
                            }
                        }
                    }
                }

                is ProviderUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaError,
                    )
                }

                is ProviderUiState.SingleProvider -> {
                    MobileProviderList(
                        providers = listOf(state.provider),
                        onSelect = onProviderSelected,
                        onEdit = onEditProvider,
                        onManageEpg = onManageEpg,
                        onDelete = { deleteConfirmProvider = it },
                        onDuplicate = { duplicateProvider = it },
                        onCopyTo = { copyFromProvider = it },
                    )
                }

                is ProviderUiState.MultipleProviders -> {
                    MobileProviderList(
                        providers = state.providers,
                        onSelect = onProviderSelected,
                        onEdit = onEditProvider,
                        onManageEpg = onManageEpg,
                        onDelete = { deleteConfirmProvider = it },
                        onDuplicate = { duplicateProvider = it },
                        onCopyTo = { copyFromProvider = it },
                    )
                }
            }
        }
    }

    deleteConfirmProvider?.let { provider ->
        CinemaAlertDialog(
            onDismissRequest = { deleteConfirmProvider = null },
            title = { Text(stringResource(R.string.provider_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.provider_delete_confirm_message, provider.name))
            },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        viewModel.deleteProvider(provider.id)
                        deleteConfirmProvider = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                ) {
                    Text(stringResource(R.string.provider_delete_button))
                }
            },
            dismissButton = {
                CinemaOutlinedButton(onClick = { deleteConfirmProvider = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    val allProviders =
        when (val state = uiState) {
            is ProviderUiState.SingleProvider -> listOf(state.provider)
            is ProviderUiState.MultipleProviders -> state.providers
            else -> emptyList()
        }

    duplicateProvider?.let { provider ->
        DuplicateProviderDialog(
            provider = provider,
            onDismiss = { duplicateProvider = null },
            onConfirm = { newName ->
                viewModel.duplicateProvider(provider.id, newName)
                duplicateProvider = null
            },
        )
    }

    copyFromProvider?.let { provider ->
        CopyProviderDialog(
            source = provider,
            targets = allProviders.filter { it.id != provider.id },
            onDismiss = { copyFromProvider = null },
            onConfirm = { targetId, options ->
                viewModel.copyProviderData(provider.id, targetId, options)
                copyFromProvider = null
            },
        )
    }

    copyResultMessage?.let { message ->
        CinemaAlertDialog(
            onDismissRequest = { viewModel.clearCopyResultMessage() },
            title = { Text(stringResource(R.string.provider_copy_to_title)) },
            text = { Text(message) },
            confirmButton = {
                CinemaDialogActionButton(onClick = { viewModel.clearCopyResultMessage() }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
private fun MobileProviderList(
    providers: List<ProviderEntity>,
    onSelect: (ProviderEntity) -> Unit,
    onEdit: (Long) -> Unit,
    onManageEpg: (Long) -> Unit,
    onDelete: (ProviderEntity) -> Unit,
    onDuplicate: (ProviderEntity) -> Unit,
    onCopyTo: (ProviderEntity) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(providers, key = { it.id }, contentType = { "provider" }) { provider ->
            MobileProviderRow(
                provider = provider,
                canCopyTo = providers.size > 1,
                onSelect = onSelect,
                onEdit = onEdit,
                onManageEpg = onManageEpg,
                onDelete = onDelete,
                onDuplicate = onDuplicate,
                onCopyTo = onCopyTo,
            )
        }
    }
}

/**
 * One source: tapping the card edits it (chevron); "Use" switches to it (hidden on the active
 * one); everything else sits in the overflow menu, Delete last and separated.
 */
@Composable
private fun MobileProviderRow(
    provider: ProviderEntity,
    canCopyTo: Boolean,
    onSelect: (ProviderEntity) -> Unit,
    onEdit: (Long) -> Unit,
    onManageEpg: (Long) -> Unit,
    onDelete: (ProviderEntity) -> Unit,
    onDuplicate: (ProviderEntity) -> Unit,
    onCopyTo: (ProviderEntity) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
    GlassPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onEdit(provider.id) },
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = CinemaSpacing.md,
                    top = CinemaSpacing.md,
                    bottom = CinemaSpacing.md,
                    end = CinemaSpacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (provider.isActive) {
                        Text(
                            text = stringResource(R.string.provider_active_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = provider.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = dimmed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = provider.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = dimmed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!provider.isActive) {
                IconAction(
                    onClick = { onSelect(provider) },
                    icon = CinemaIcons.SwapHoriz,
                    label = stringResource(R.string.provider_use_button),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        CinemaIcons.MoreVert,
                        contentDescription = stringResource(R.string.provider_more_actions_for_format, provider.name),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Guide sources only apply to sources that carry live channels
                    if (MediaProviderFactory.hasLiveTv(provider)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.epg_sources_header)) },
                            leadingIcon = { Icon(CinemaIcons.LiveTv, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onManageEpg(provider.id)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.provider_duplicate_button)) },
                        leadingIcon = { Icon(CinemaIcons.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDuplicate(provider)
                        },
                    )
                    if (canCopyTo) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.provider_copy_to_button)) },
                            leadingIcon = { Icon(CinemaIcons.SwapHoriz, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onCopyTo(provider)
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.provider_delete_button)) },
                        leadingIcon = { Icon(CinemaIcons.Delete, contentDescription = null) },
                        colors =
                            MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error,
                            ),
                        onClick = {
                            menuOpen = false
                            onDelete(provider)
                        },
                    )
                }
            }
            Icon(
                CinemaIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = dimmed,
            )
        }
    }
}
