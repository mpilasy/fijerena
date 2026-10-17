@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderUiState
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.feature.provider.components.CopyProviderDialog
import org.njarasoa.fijerena.feature.provider.components.DuplicateProviderDialog
import org.njarasoa.fijerena.feature.provider.components.ProviderActionsMenuDialog
import org.njarasoa.fijerena.ui.components.TvContentText
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.*

@Composable
fun TvProviderSelectionScreen(
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
    var actionsMenuProvider by remember { mutableStateOf<ProviderEntity?>(null) }

    // Refresh provider list when screen is shown (e.g., after adding a provider)
    LaunchedEffect(Unit) {
        viewModel.loadProviders()
    }

    // Back from Add, Edit (OK on a row, or the row's overflow menu) or Guide sources (the row's
    // Guide slot, or the menu) lands on the control that led there, once the reloaded list is back
    // on screen (T-9).
    val listState = rememberLazyListState()
    val returnFocus = rememberNavReturnFocus()
    NavReturnFocusEffect(returnFocus, listState = listState) {
        withTimeoutOrNull(RETURN_LIST_WAIT_MS) {
            snapshotFlow { uiState }.first { it is ProviderUiState.SingleProvider || it is ProviderUiState.MultipleProviders }
        }
    }

    // First open lands on the first source's row, not on "+" (T-8). Once per composition: a
    // pending hand-back (Back from a child screen) has the last word instead.
    val entryRowFocusRequester = remember { FocusRequester() }
    val listLoaded = uiState is ProviderUiState.SingleProvider || uiState is ProviderUiState.MultipleProviders
    var entryFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(listLoaded) {
        if (listLoaded && !entryFocusDone) {
            entryFocusDone = true
            if (!returnFocus.isReturn) entryRowFocusRequester.requestFocusWithRetry()
        }
    }

    val allProviders =
        when (val state = uiState) {
            is ProviderUiState.SingleProvider -> listOf(state.provider)
            is ProviderUiState.MultipleProviders -> state.providers
            else -> emptyList()
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical,
                ),
    ) {
        TvScreenHeader(title = stringResource(R.string.provider_selection_title)) {
            TvIconAction(
                onClick = {
                    returnFocus.leaveFrom(RETURN_ADD)
                    onAddProvider()
                },
                icon = CinemaIcons.Add,
                label = stringResource(R.string.provider_add_title),
                modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_ADD),
            )
        }

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
                TvEmptyState(
                    message = stringResource(R.string.provider_no_providers),
                    actionLabel = stringResource(R.string.provider_add_title),
                    onAction = onAddProvider,
                )
            }

            is ProviderUiState.Error -> {
                CinemaText(
                    state.message,
                    role = TextRole.Body,
                    tone = TextTone.Danger,
                )
            }

            is ProviderUiState.SingleProvider, is ProviderUiState.MultipleProviders -> {
                ProviderList(
                    providers = allProviders,
                    onEdit = { id ->
                        returnFocus.leaveFrom(RETURN_ROW_PREFIX + id, listState)
                        onEditProvider(id)
                    },
                    onManageEpg = { id ->
                        returnFocus.leaveFrom(RETURN_EPG_PREFIX + id, listState)
                        onManageEpg(id)
                    },
                    onMoreActions = { actionsMenuProvider = it },
                    listState = listState,
                    returnFocus = returnFocus,
                    entryRowFocusRequester = entryRowFocusRequester,
                )
            }
        }
    }

    deleteConfirmProvider?.let { provider ->
        CinemaAlertDialog(
            onDismissRequest = { deleteConfirmProvider = null },
            title = {
                CinemaText(
                    stringResource(R.string.provider_delete_confirm_title),
                    role = TextRole.Title,
                    tone = TextTone.Primary,
                )
            },
            text = {
                CinemaText(
                    stringResource(R.string.provider_delete_confirm_message, provider.name),
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                )
            },
            confirmButton = {
                CinemaButton(
                    onClick = {
                        viewModel.deleteProvider(provider.id)
                        deleteConfirmProvider = null
                    },
                    kind = ButtonKind.Danger,
                ) {
                    CinemaText(
                        stringResource(R.string.provider_delete_button),
                        role = TextRole.Label,
                        tone = TextTone.Inherit,
                    )
                }
            },
            dismissButton = {
                CinemaButton(
                    onClick = { deleteConfirmProvider = null },
                    kind = ButtonKind.Other,
                ) {
                    CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
                }
            },
            containerColor = CinemaTheme.colors.surface,
        )
    }

    actionsMenuProvider?.let { provider ->
        ProviderActionsMenuDialog(
            provider = provider,
            canCopyTo = allProviders.size > 1,
            onEdit = {
                returnFocus.leaveFrom(RETURN_MORE_PREFIX + provider.id, listState)
                onEditProvider(provider.id)
            },
            onManageEpg =
                if (MediaProviderFactory.hasLiveTv(provider)) {
                    {
                        returnFocus.leaveFrom(RETURN_MORE_PREFIX + provider.id, listState)
                        onManageEpg(provider.id)
                    }
                } else {
                    null
                },
            onDuplicate = { duplicateProvider = provider },
            onCopyTo = { copyFromProvider = provider },
            onDelete = { deleteConfirmProvider = provider },
            onDismiss = { actionsMenuProvider = null },
        )
    }

    duplicateProvider?.let { provider ->
        DuplicateProviderDialog(
            provider = provider,
            onConfirm = { newName ->
                viewModel.duplicateProvider(provider.id, newName)
                duplicateProvider = null
            },
            onDismiss = { duplicateProvider = null },
        )
    }

    copyFromProvider?.let { provider ->
        CopyProviderDialog(
            source = provider,
            targets = allProviders.filter { it.id != provider.id },
            onConfirm = { targetId, options ->
                viewModel.copyProviderData(provider.id, targetId, options)
                copyFromProvider = null
            },
            onDismiss = { copyFromProvider = null },
        )
    }

    copyResultMessage?.let { message ->
        CinemaAlertDialog(
            onDismissRequest = { viewModel.clearCopyResultMessage() },
            title = { CinemaText(stringResource(R.string.provider_copy_to_title), role = TextRole.Title, tone = TextTone.Primary) },
            text = { CinemaText(message, role = TextRole.Body, tone = TextTone.Secondary) },
            confirmButton = {
                CinemaButton(
                    onClick = { viewModel.clearCopyResultMessage() },
                    kind = ButtonKind.Main,
                ) {
                    CinemaText(stringResource(R.string.common_ok), role = TextRole.Label, tone = TextTone.Inherit)
                }
            },
            containerColor = CinemaTheme.colors.surface,
        )
    }
}

@Composable
private fun ProviderList(
    providers: List<ProviderEntity>,
    onEdit: (Long) -> Unit,
    onManageEpg: (Long) -> Unit,
    onMoreActions: (ProviderEntity) -> Unit,
    listState: LazyListState,
    returnFocus: NavReturnFocus,
    entryRowFocusRequester: FocusRequester,
) {
    val scale = LocalUiScale.current
    // Focus opens on the first row: there is no source in use to land on (multi-source plan, Phase 6).
    val entryId = providers.firstOrNull()?.id
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
        modifier = Modifier.fillMaxSize().focusRestorer(),
    ) {
        items(providers, key = { it.id }, contentType = { "provider" }) { provider ->
            // One focus stop per row, OK = Edit; the trailing actions sit in a fixed-width slot that
            // stays in place when empty (Guide on a source without live channels), so Up/Down from
            // a slot lands on the same slot of the next row and Right from the row walks Guide → ⋮
            // (TV focus contract, rule 1; A-10).
            // The row's resting surface spans the whole width, slots included, so a row reads as
            // one full-width row whatever buttons it shows (TV UI audit #18).
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(TvFocusTokens.restingContainer, cornerShape(CornerRadius.small))
                        .padding(end = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvInputListItem(
                    selected = false,
                    onClick = { onEdit(provider.id) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(if (provider.id == entryId) Modifier.focusRequester(entryRowFocusRequester) else Modifier)
                            .navReturnFocusTarget(returnFocus, RETURN_ROW_PREFIX + provider.id)
                            .leftToRail(),
                    headlineContent = {
                        TvContentText(text = provider.name, role = TextRole.Heading)
                    },
                    supportingContent = {
                        Column {
                            CinemaText(
                                provider.url,
                                role = TextRole.Body,
                                tone = TextTone.Secondary,
                            )
                            CinemaText(
                                provider.username,
                                role = TextRole.Body,
                                tone = TextTone.Secondary,
                            )
                        }
                    },
                )
                // Icons at the slot's end: the focused one's name grows into the slot, so nothing
                // beside it moves (icon-buttons plan).
                Box(modifier = Modifier.width(ACTION_SLOT_WIDTH), contentAlignment = Alignment.CenterEnd) {
                    // Guide sources only apply to sources that carry live channels
                    if (MediaProviderFactory.hasLiveTv(provider)) {
                        TvIconAction(
                            onClick = { onManageEpg(provider.id) },
                            icon = CinemaIcons.DateRange,
                            label = stringResource(R.string.provider_guide_button),
                            modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_EPG_PREFIX + provider.id),
                        )
                    }
                }
                // Edit / Guide sources / Duplicate / Copy to… / Delete with real text labels —
                // see ProviderActionsMenuDialog. White like the slots' icons, not the accent.
                CinemaIconButton(
                    onClick = { onMoreActions(provider) },
                    modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_MORE_PREFIX + provider.id),
                    icon = {
                        Icon(
                            CinemaIcons.MoreVert,
                            contentDescription = stringResource(R.string.provider_more_actions_for_format, provider.name),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Width of the Guide slot. Fixed so the ⋮ column lines up across rows whatever a row
 * shows; a Settings input width, since these are the Settings-side controls of a row.
 */
private val ACTION_SLOT_WIDTH = TvDimensions.settingsInputWidth

// Keys for the controls that navigate away — see rememberNavReturnFocus.
private const val RETURN_ADD = "add"
private const val RETURN_ROW_PREFIX = "row:"
private const val RETURN_EPG_PREFIX = "epg:"
private const val RETURN_MORE_PREFIX = "more:"

/** How long Back waits for the reloaded provider list before handing focus back anyway. */
private const val RETURN_LIST_WAIT_MS = 2_000L
