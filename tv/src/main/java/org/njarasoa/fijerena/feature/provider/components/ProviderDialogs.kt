package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryMatcher
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.MatchType
import org.njarasoa.fijerena.core.network.provider.ProviderCopyManager
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.network.provider.withAddedRules
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.input.TvCheckRow
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.TvRadioRow
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

/** Share of the screen the category-filter panel takes at 100% UI scale. */
private const val PANEL_SCREEN_FRACTION = 0.85f

@Composable
fun ConfirmActionDialog(
    title: String,
    text: String,
    confirmText: String = stringResource(R.string.provider_clear_button),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDanger: Boolean = true,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(title, role = TextRole.Title, tone = TextTone.Primary) },
        text = { CinemaText(text, role = TextRole.Body, tone = TextTone.Secondary) },
        confirmButton = {
            CinemaButton(
                onClick = onConfirm,
                kind = if (isDanger) ButtonKind.Danger else ButtonKind.Main,
            ) { CinemaText(confirmText, role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        containerColor = CinemaTheme.colors.surface,
    )
}

/** The content-filter editor; [title] names whose filters these are (a profile's page: the source). */
@Composable
fun CategoryFilterDialog(
    title: String,
    currentFilters: CategoryFilters,
    onSave: (CategoryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val scale = LocalUiScale.current
    var filterMode by remember { mutableStateOf(currentFilters.mode) }
    var rules by remember { mutableStateOf(currentFilters.rules) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var editingMatchType by remember { mutableStateOf(MatchType.STARTS_WITH) }
    var addRulesText by remember { mutableStateOf("") }
    var pendingAddValues by remember { mutableStateOf<List<String>?>(null) }
    var pendingAddMatchType by remember { mutableStateOf(MatchType.STARTS_WITH) }
    var selectedScripts by remember { mutableStateOf(currentFilters.allowedScripts) }

    // The panel's content is in the dialog's `text` slot, so the default initial-focus target is
    // the Save/Cancel row at the very bottom. Start on the first real control instead.
    val firstControlFocusRequester = remember { FocusRequester() }

    @Composable
    fun matchTypeLabel(type: MatchType): String =
        when (type) {
            MatchType.STARTS_WITH -> stringResource(R.string.provider_filter_match_starts)
            MatchType.ENDS_WITH -> stringResource(R.string.provider_filter_match_ends)
            MatchType.CONTAINS -> stringResource(R.string.provider_filter_match_contains)
            MatchType.EXACT -> stringResource(R.string.provider_filter_match_exact)
        }

    @Composable
    fun MatchTypeChipRow(
        selected: MatchType,
        onSelect: (MatchType) -> Unit,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        ) {
            MatchType.entries.forEach { type ->
                TvSelectableButton(
                    selected = selected == type,
                    onSelect = { onSelect(type) },
                    text = matchTypeLabel(type),
                )
            }
        }
    }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        // The panel is sized as a fraction of the screen, which no dp scaling can reach — fold the
        // UI scale in by hand so the box shrinks with the text inside it instead of framing tiny
        // controls in a full-screen panel.
        modifier = Modifier.fillMaxWidth(PANEL_SCREEN_FRACTION * scale).fillMaxHeight(PANEL_SCREEN_FRACTION * scale),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { CinemaText(title, role = TextRole.Title, tone = TextTone.Primary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
            ) {
                CinemaText(
                    stringResource(R.string.provider_filter_mode_label),
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvSelectableButton(
                        selected = filterMode == FilterMode.EXCLUDE,
                        onSelect = { filterMode = FilterMode.EXCLUDE },
                        text = stringResource(R.string.provider_filter_exclude),
                        modifier = Modifier.focusRequester(firstControlFocusRequester),
                    )
                    TvSelectableButton(
                        selected = filterMode == FilterMode.INCLUDE,
                        onSelect = { filterMode = FilterMode.INCLUDE },
                        text = stringResource(R.string.provider_filter_include_short),
                    )
                }
                CinemaText(
                    if (filterMode == FilterMode.EXCLUDE) {
                        stringResource(R.string.provider_filter_hide_matching_desc)
                    } else {
                        stringResource(R.string.provider_filter_show_matching_desc)
                    },
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                )
                if (filterMode == FilterMode.INCLUDE && rules.isEmpty()) {
                    CinemaText(
                        stringResource(R.string.provider_filter_include_empty_warning),
                        role = TextRole.BodySmall,
                        tone = TextTone.Danger,
                    )
                }

                CinemaText(
                    stringResource(R.string.provider_filter_rules_label),
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                )
                rules.forEachIndexed { index, rule ->
                    if (editingIndex == index) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                            CinemaTextField(
                                value = editingValue,
                                onValueChange = { editingValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            MatchTypeChipRow(selected = editingMatchType, onSelect = { editingMatchType = it })
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                                CinemaButton(
                                    onClick = {
                                        val trimmed = editingValue.trim()
                                        if (trimmed.isNotEmpty()) {
                                            rules =
                                                rules.toMutableList().also {
                                                    it[index] = CategoryMatcher(trimmed, editingMatchType)
                                                }
                                        }
                                        editingIndex = null
                                    },
                                    kind = ButtonKind.Main,
                                ) {
                                    CinemaText(
                                        stringResource(R.string.provider_save_button),
                                        role = TextRole.Label,
                                        tone = TextTone.Inherit,
                                    )
                                }
                                CinemaButton(
                                    onClick = { editingIndex = null },
                                    kind = ButtonKind.Other,
                                ) {
                                    CinemaText(
                                        stringResource(R.string.common_cancel),
                                        role = TextRole.Label,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CinemaText(
                                matchTypeLabel(rule.matchType),
                                modifier = Modifier.width(TvDimensions.iconButtonSize.scaled(scale)),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                            CinemaText(
                                rule.value,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                role = TextRole.Body,
                                tone = TextTone.Primary,
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaIconButton(
                                onClick = {
                                    editingIndex = index
                                    editingValue = rule.value
                                    editingMatchType = rule.matchType
                                },
                                icon = { Icon(CinemaIcons.Edit, contentDescription = stringResource(R.string.provider_filter_edit_rule)) },
                                size = TvDimensions.iconLarge * 0.75f,
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaDangerIconButton(
                                onClick = { rules = rules.toMutableList().also { it.removeAt(index) } },
                                icon = {
                                    Icon(
                                        CinemaIcons.Delete,
                                        contentDescription = stringResource(R.string.provider_filter_delete_rule),
                                    )
                                },
                                size = TvDimensions.iconLarge * 0.75f,
                            )
                        }
                    }
                }

                CinemaText(
                    stringResource(R.string.provider_filter_add_rules_section_label),
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                )
                // A live OutlinedTextField sitting in the D-pad path is a dead end on TV: once
                // focused it swallows every direction key, so the Add button and the entire
                // script filter below it were unreachable by remote. ReadOnlyFieldWithEdit exists
                // for exactly this — the field is entered deliberately with OK and left with
                // Enter or Back, and is an ordinary focus stop the rest of the time.
                ReadOnlyFieldWithEdit(
                    value = addRulesText,
                    onValueChange = { addRulesText = it },
                    label = stringResource(R.string.provider_filter_add_rules_label),
                    placeholder = stringResource(R.string.provider_filter_rules_placeholder),
                )
                CinemaButton(
                    onClick = {
                        val values = addRulesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (values.isNotEmpty()) {
                            pendingAddValues = values
                            pendingAddMatchType = MatchType.STARTS_WITH
                        }
                    },
                    enabled = addRulesText.isNotBlank(),
                    kind = ButtonKind.Other,
                ) { CinemaText(stringResource(R.string.common_add), role = TextRole.Label, tone = TextTone.Inherit) }

                pendingAddValues?.let { values ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                        CinemaText(
                            stringResource(R.string.provider_filter_choose_match_type_prompt),
                            role = TextRole.Body,
                            tone = TextTone.Primary,
                        )
                        MatchTypeChipRow(selected = pendingAddMatchType, onSelect = { pendingAddMatchType = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                            CinemaButton(
                                onClick = {
                                    rules = rules.withAddedRules(values, pendingAddMatchType)
                                    addRulesText = ""
                                    pendingAddValues = null
                                },
                                kind = ButtonKind.Main,
                            ) {
                                CinemaText(
                                    stringResource(R.string.common_ok),
                                    role = TextRole.Label,
                                    tone = TextTone.Inherit,
                                )
                            }
                            CinemaButton(
                                onClick = { pendingAddValues = null },
                                kind = ButtonKind.Other,
                            ) {
                                CinemaText(
                                    stringResource(R.string.common_cancel),
                                    role = TextRole.Label,
                                    tone = TextTone.Inherit,
                                )
                            }
                        }
                    }
                }

                CinemaText(
                    stringResource(R.string.provider_filter_script_title),
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                )
                CinemaText(
                    stringResource(R.string.provider_filter_script_desc),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                    ScriptType.entries.forEach { script ->
                        TvCheckRow(
                            checked = script in selectedScripts,
                            onCheckedChange = { checked ->
                                selectedScripts = if (checked) selectedScripts + script else selectedScripts - script
                            },
                            label = script.displayName,
                        )
                    }
                }
            }
        },
        initialFocus = firstControlFocusRequester,
        confirmButton = {
            CinemaButton(
                onClick = {
                    val newFilters = CategoryFilters(mode = filterMode, rules = rules, allowedScripts = selectedScripts)
                    onSave(newFilters)
                },
                kind = ButtonKind.Main,
            ) { CinemaText(stringResource(R.string.provider_save_button), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        containerColor = CinemaTheme.colors.surface,
    )
}

@Composable
fun QuickConnectDialog(
    url: String,
    onSuccess: (name: String, username: String, token: String, userId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    val scale = LocalUiScale.current
    var qcCode by remember { mutableStateOf("") }
    var qcSecret by remember { mutableStateOf("") }
    var qcError by remember { mutableStateOf<String?>(null) }

    val initFailedText = stringResource(R.string.provider_qc_init_failed)
    val pollFailedFormat = stringResource(R.string.provider_qc_poll_failed)
    val authFailedFormat = stringResource(R.string.provider_qc_auth_failed)
    val timeoutText = stringResource(R.string.provider_qc_timeout)

    LaunchedEffect(Unit) {
        qcCode = ""
        qcSecret = ""
        qcError = null
        val deviceId =
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            ) ?: "fijerena"
        val api = JellyfinApiService(url.trimEnd('/'), deviceId)
        val initResult = api.initiateQuickConnect()
        if (initResult.isFailure) {
            val e = initResult.exceptionOrNull()
            qcError = e?.let { friendlyErrorMessage(it, context, appSettings.isDevMode) } ?: initFailedText
            return@LaunchedEffect
        }
        val init = initResult.getOrThrow()
        qcCode = init.code
        qcSecret = init.secret
        // Poll every 3 s for up to 2 minutes
        repeat(40) {
            delay(3_000)
            val poll = api.pollQuickConnect(qcSecret)
            if (poll.isFailure) {
                val pollError = poll.exceptionOrNull()?.let { friendlyErrorMessage(it, context, appSettings.isDevMode) }
                qcError = String.format(pollFailedFormat, pollError)
                return@LaunchedEffect
            }
            if (poll.getOrThrow().authenticated) {
                val authResult = api.authenticateWithQuickConnect(qcSecret)
                if (authResult.isFailure) {
                    val authError = authResult.exceptionOrNull()?.let { friendlyErrorMessage(it, context, appSettings.isDevMode) }
                    qcError = String.format(authFailedFormat, authError)
                    return@LaunchedEffect
                }
                val auth = authResult.getOrThrow()
                onSuccess(auth.user.name, auth.user.name, auth.accessToken, auth.user.id)
                return@LaunchedEffect
            }
        }
        qcError = timeoutText
    }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(
                stringResource(R.string.provider_quick_connect_title),
                role = TextRole.Title,
                tone = TextTone.Primary,
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    qcError != null -> {
                        CinemaText(
                            qcError!!,
                            role = TextRole.Body,
                            tone = TextTone.Danger,
                        )
                    }

                    qcCode.isEmpty() -> {
                        CinemaSpinner()
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        CinemaText(
                            stringResource(R.string.provider_connecting_server),
                            role = TextRole.Body,
                            tone = TextTone.Secondary,
                        )
                    }

                    else -> {
                        CinemaText(
                            stringResource(R.string.provider_qc_enter_code),
                            role = TextRole.Body,
                            tone = TextTone.Secondary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        CinemaText(
                            text = qcCode,
                            role = TextRole.Display,
                            tone = TextTone.Accent,
                            emphasis = true,
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                        CinemaText(
                            stringResource(R.string.provider_qc_instructions),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                        CinemaSpinner()
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        CinemaText(
                            stringResource(R.string.provider_qc_waiting),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        containerColor = CinemaTheme.colors.surface,
    )
}

/** Name-edit dialog for [ProviderCopyManager.duplicateProvider]. */
@Composable
fun DuplicateProviderDialog(
    provider: ProviderEntity,
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = stringResource(R.string.provider_duplicate_name_default_format, provider.name)
    var name by remember(provider.id) { mutableStateOf(defaultName) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.provider_duplicate_title), role = TextRole.Title, tone = TextTone.Primary) },
        text = {
            ReadOnlyFieldWithEdit(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.provider_duplicate_name_label),
                keyboardOptions = FieldKeyboards.Name,
            )
        },
        confirmButton = {
            CinemaButton(
                onClick = { onConfirm(name.trim().ifBlank { defaultName }) },
                kind = ButtonKind.Main,
            ) {
                CinemaText(
                    stringResource(R.string.provider_duplicate_button),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        },
        dismissButton = {
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        containerColor = CinemaTheme.colors.surface,
    )
}

/**
 * Overflow menu for a source row's actions, in the shared order of
 * the ux overhaul plan Part I ("Source actions"): Duplicate, Copy to…, then Delete last and set
 * apart. Opens with focus on Duplicate; Back closes it (the dialog dismisses on Back, so there is
 * no Cancel row) — T-10. Edit is OK on the row and Guide sources is the row's Guide slot, so the
 * menu does not repeat them (the settings flow review, E6).
 */
@Composable
fun ProviderActionsMenuDialog(
    provider: ProviderEntity,
    canCopyTo: Boolean,
    onDuplicate: () -> Unit,
    onCopyTo: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }

    @Composable
    fun MenuRow(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        TvInputListItem(
            selected = false,
            onClick = {
                onClick()
                onDismiss()
            },
            modifier = modifier.fillMaxWidth(),
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(TvDimensions.iconSmall),
                    tint = CinemaTheme.colors.accent,
                )
            },
            headlineContent = {
                CinemaText(
                    label,
                    role = TextRole.Body,
                    tone = TextTone.Primary,
                )
            },
        )
    }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(
                provider.name,
                maxLines = 2,
                role = TextRole.Heading,
                tone = TextTone.Primary,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                MenuRow(
                    icon = CinemaIcons.ContentCopy,
                    label = stringResource(R.string.provider_duplicate_button),
                    onClick = onDuplicate,
                    modifier = Modifier.focusRequester(firstFocusRequester),
                )
                if (canCopyTo) {
                    MenuRow(
                        icon = CinemaIcons.SwapHoriz,
                        label = stringResource(R.string.provider_copy_to_button),
                        onClick = onCopyTo,
                    )
                }
                CinemaDivider()
                // Outlined in the error colour, filled only while focused: the delete confirm
                // that follows carries the filled error button (T5, follow-up from T4).
                ProviderDangerButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    text = stringResource(R.string.provider_delete_button),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        initialFocus = firstFocusRequester,
        confirmButton = {},
        containerColor = CinemaTheme.colors.surface,
        titleContentColor = CinemaTheme.colors.textPrimary,
        textContentColor = CinemaTheme.colors.textSecondary,
    )
}

/** Target picker + option checkboxes for [ProviderCopyManager.copyProviderData]. */
@Composable
fun CopyProviderDialog(
    source: ProviderEntity,
    targets: List<ProviderEntity>,
    onConfirm: (targetId: Long, options: ProviderCopyManager.CopyOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var targetId by remember(source.id) { mutableStateOf(targets.firstOrNull()?.id) }
    var copyConnection by remember { mutableStateOf(false) }
    var copySettings by remember { mutableStateOf(true) }
    var copyFavorites by remember { mutableStateOf(true) }
    var copyWatchHistory by remember { mutableStateOf(true) }
    val firstFocus = remember { FocusRequester() }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.provider_copy_to_title), role = TextRole.Title, tone = TextTone.Primary) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = TvDimensions.dialogWidth).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CinemaText(
                    stringResource(R.string.provider_copy_to_target_label),
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                )
                targets.forEachIndexed { index, candidate ->
                    TvRadioRow(
                        selected = targetId == candidate.id,
                        onClick = { targetId = candidate.id },
                        label = candidate.name,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                CinemaText(
                    stringResource(R.string.provider_copy_to_options_label),
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                )
                TvCheckRow(checked = copySettings, onCheckedChange = {
                    copySettings = it
                }, label = stringResource(R.string.provider_copy_to_settings_label))
                TvCheckRow(
                    checked = copyFavorites,
                    onCheckedChange = { copyFavorites = it },
                    label = stringResource(R.string.provider_copy_to_favorites_label),
                )
                TvCheckRow(
                    checked = copyWatchHistory,
                    onCheckedChange = { copyWatchHistory = it },
                    label = stringResource(R.string.provider_copy_to_watch_history_label),
                )
                TvCheckRow(
                    checked = copyConnection,
                    onCheckedChange = { copyConnection = it },
                    label = stringResource(R.string.provider_copy_to_connection_label),
                )
                if (copyConnection) {
                    val targetName = targets.firstOrNull { it.id == targetId }?.name.orEmpty()
                    CinemaText(
                        stringResource(R.string.provider_copy_to_connection_warning_format, targetName),
                        role = TextRole.BodySmall,
                        tone = TextTone.Danger,
                    )
                }
            }
        },
        initialFocus = firstFocus,
        confirmButton = {
            CinemaButton(
                onClick = {
                    val resolvedTargetId = targetId ?: return@CinemaButton
                    onConfirm(
                        resolvedTargetId,
                        ProviderCopyManager.CopyOptions(
                            copyConnection = copyConnection,
                            copyProviderSettings = copySettings,
                            copyFavorites = copyFavorites,
                            copyWatchHistory = copyWatchHistory,
                        ),
                    )
                },
                kind = ButtonKind.Main,
            ) { CinemaText(stringResource(R.string.provider_copy_to_button), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        containerColor = CinemaTheme.colors.surface,
    )
}
