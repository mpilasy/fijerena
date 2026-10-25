package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
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
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.sourceStatusLine
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderUiState
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SourceStatus
import org.njarasoa.fijerena.feature.provider.components.CopyProviderDialog
import org.njarasoa.fijerena.feature.provider.components.DuplicateProviderDialog
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.mobileClockTopBarInsets
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileProviderSelectionScreen(
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
    val statuses by viewModel.sourceStatuses.collectAsStateWithLifecycle()
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
                windowInsets = mobileClockTopBarInsets(),
                title = { CinemaText(stringResource(R.string.provider_selection_title), role = TextRole.Title) },
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
                        CinemaSpinner()
                    }
                }

                is ProviderUiState.NoProviders -> {
                    MobileEmptyState(
                        message = stringResource(R.string.provider_no_providers),
                        actionLabel = stringResource(R.string.provider_add_title),
                        onAction = onAddProvider,
                    )
                }

                is ProviderUiState.Error -> {
                    CinemaText(state.message, tone = TextTone.Danger)
                }

                is ProviderUiState.SingleProvider -> {
                    MobileProviderList(
                        statuses = statuses,
                        providers = listOf(state.provider),
                        onEdit = onEditProvider,
                        onManageEpg = onManageEpg,
                        onDelete = { deleteConfirmProvider = it },
                        onDuplicate = { duplicateProvider = it },
                        onCopyTo = { copyFromProvider = it },
                    )
                }

                is ProviderUiState.MultipleProviders -> {
                    MobileProviderList(
                        statuses = statuses,
                        providers = state.providers,
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
            title = { CinemaText(stringResource(R.string.provider_delete_confirm_title), role = TextRole.Title) },
            text = {
                CinemaText(stringResource(R.string.provider_delete_confirm_message, provider.name), tone = TextTone.Secondary)
            },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        viewModel.deleteProvider(provider.id)
                        deleteConfirmProvider = null
                    },
                    kind = ButtonKind.Danger,
                ) {
                    CinemaText(stringResource(R.string.provider_delete_button), role = TextRole.Label, tone = TextTone.Inherit)
                }
            },
            dismissButton = {
                CinemaOutlinedButton(onClick = { deleteConfirmProvider = null }) {
                    CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
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
            title = { CinemaText(stringResource(R.string.provider_copy_to_title), role = TextRole.Title) },
            text = { CinemaText(message, tone = TextTone.Secondary) },
            confirmButton = {
                CinemaDialogActionButton(onClick = { viewModel.clearCopyResultMessage() }, kind = ButtonKind.Main) {
                    CinemaText(stringResource(R.string.common_ok), role = TextRole.Label, tone = TextTone.Inherit)
                }
            },
        )
    }
}

@Composable
private fun MobileProviderList(
    providers: List<ProviderEntity>,
    statuses: Map<Long, SourceStatus>,
    onEdit: (Long) -> Unit,
    onManageEpg: (Long) -> Unit,
    onDelete: (ProviderEntity) -> Unit,
    onDuplicate: (ProviderEntity) -> Unit,
    onCopyTo: (ProviderEntity) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalDockInset.current),
    ) {
        items(providers, key = { it.id }, contentType = { "provider" }) { provider ->
            MobileProviderRow(
                provider = provider,
                status = statuses[provider.id],
                canCopyTo = providers.size > 1,
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
 * One source: tapping the card edits it (the one way in, no chevron); everything else sits in the
 * overflow menu, Delete last and separated.
 */
@Composable
private fun MobileProviderRow(
    provider: ProviderEntity,
    status: SourceStatus?,
    canCopyTo: Boolean,
    onEdit: (Long) -> Unit,
    onManageEpg: (Long) -> Unit,
    onDelete: (ProviderEntity) -> Unit,
    onDuplicate: (ProviderEntity) -> Unit,
    onCopyTo: (ProviderEntity) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
                CinemaText(provider.name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CinemaText(
                    provider.url,
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                status?.let {
                    // Expired in the error colour, as the expiry banner says it.
                    CinemaText(
                        sourceStatusLine(it),
                        role = TextRole.BodySmall,
                        tone = if (it.isExpired) TextTone.Danger else TextTone.Secondary,
                    )
                }
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
                            text = {
                                CinemaText(
                                    stringResource(R.string.epg_sources_header),
                                    role = TextRole.Label,
                                    tone = TextTone.Inherit,
                                )
                            },
                            leadingIcon = { Icon(CinemaIcons.LiveTv, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onManageEpg(provider.id)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            CinemaText(
                                stringResource(R.string.provider_duplicate_button),
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        },
                        leadingIcon = { Icon(CinemaIcons.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDuplicate(provider)
                        },
                    )
                    if (canCopyTo) {
                        DropdownMenuItem(
                            text = {
                                CinemaText(
                                    stringResource(R.string.provider_copy_to_button),
                                    role = TextRole.Label,
                                    tone = TextTone.Inherit,
                                )
                            },
                            leadingIcon = { Icon(CinemaIcons.SwapHoriz, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onCopyTo(provider)
                            },
                        )
                    }
                    CinemaDivider()
                    DropdownMenuItem(
                        text = {
                            CinemaText(
                                stringResource(R.string.provider_delete_button),
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        },
                        leadingIcon = { Icon(CinemaIcons.Delete, contentDescription = null) },
                        colors =
                            MenuDefaults.itemColors(
                                textColor = CinemaTheme.colors.danger,
                                leadingIconColor = CinemaTheme.colors.danger,
                            ),
                        onClick = {
                            menuOpen = false
                            onDelete(provider)
                        },
                    )
                }
            }
        }
    }
}
