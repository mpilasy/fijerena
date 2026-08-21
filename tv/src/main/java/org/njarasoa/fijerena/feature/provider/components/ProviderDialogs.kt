package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryMatcher
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.MatchType
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.network.provider.withAddedRules
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerIconButton
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.input.TvCheckRow
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

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
        title = { Text(title, color = CinemaTextPrimary) },
        text = { Text(text, color = CinemaTextSecondary) },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = onConfirm,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isDanger) CinemaError else CinemaAccent,
                        contentColor = if (isDanger) Color.White else CinemaTextPrimary,
                    ),
            ) { Text(confirmText) }
        },
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary,
                    ),
            ) { Text(stringResource(R.string.common_cancel)) }
        },
        containerColor = CinemaSurface,
    )
}

@Composable
fun CategoryFilterDialog(
    currentFilters: CategoryFilters,
    onSave: (CategoryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
            }
        }
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
    fun MatchTypeChipRow(selected: MatchType, onSelect: (MatchType) -> Unit) {
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
        title = { Text(stringResource(R.string.provider_category_filters_title), color = CinemaTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
            ) {
                Text(stringResource(R.string.provider_filter_mode_label), style = scaledStyles.titleSmall, color = CinemaTextPrimary)
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
                Text(
                    if (filterMode == FilterMode.EXCLUDE) {
                        stringResource(R.string.provider_filter_hide_matching_desc)
                    } else {
                        stringResource(R.string.provider_filter_show_matching_desc)
                    },
                    style = scaledStyles.bodySmall,
                    color = CinemaTextSecondary,
                )
                if (filterMode == FilterMode.INCLUDE && rules.isEmpty()) {
                    Text(
                        stringResource(R.string.provider_filter_include_empty_warning),
                        style = scaledStyles.bodySmall,
                        color = CinemaError,
                    )
                }

                Text(stringResource(R.string.provider_filter_rules_label), style = scaledStyles.titleSmall, color = CinemaTextPrimary)
                rules.forEachIndexed { index, rule ->
                    if (editingIndex == index) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                            OutlinedTextField(
                                value = editingValue,
                                onValueChange = { editingValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CinemaSurface,
                                        unfocusedContainerColor = CinemaSurface,
                                        focusedBorderColor = CinemaAccent,
                                        unfocusedBorderColor = CinemaSurfaceVariant,
                                        focusedTextColor = CinemaTextPrimary,
                                        unfocusedTextColor = CinemaTextPrimary,
                                        cursorColor = CinemaAccent,
                                    ),
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
                                    colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = CinemaAccent),
                                ) { Text(stringResource(R.string.provider_save_button)) }
                                CinemaButton(
                                    onClick = { editingIndex = null },
                                    colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = CinemaSurfaceVariant),
                                ) { Text(stringResource(R.string.common_cancel)) }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = matchTypeLabel(rule.matchType),
                                style = scaledStyles.bodySmall,
                                color = CinemaTextSecondary,
                                modifier = Modifier.width(72.dp.scaled(scale)),
                            )
                            Text(
                                text = rule.value,
                                style = scaledStyles.bodyMedium,
                                color = CinemaTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaIconButton(
                                onClick = {
                                    editingIndex = index
                                    editingValue = rule.value
                                    editingMatchType = rule.matchType
                                },
                                icon = { Icon(CinemaIcons.Edit, contentDescription = stringResource(R.string.provider_filter_edit_rule)) },
                                size = 36.dp,
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaDangerIconButton(
                                onClick = { rules = rules.toMutableList().also { it.removeAt(index) } },
                                icon = { Icon(CinemaIcons.Delete, contentDescription = stringResource(R.string.provider_filter_delete_rule)) },
                                size = 36.dp,
                            )
                        }
                    }
                }

                Text(stringResource(R.string.provider_filter_add_rules_section_label), style = scaledStyles.titleSmall, color = CinemaTextPrimary)
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
                    colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = CinemaSurfaceVariant),
                ) { Text(stringResource(R.string.common_add)) }

                pendingAddValues?.let { values ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                        Text(stringResource(R.string.provider_filter_choose_match_type_prompt), style = scaledStyles.bodyMedium, color = CinemaTextPrimary)
                        MatchTypeChipRow(selected = pendingAddMatchType, onSelect = { pendingAddMatchType = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                            CinemaButton(
                                onClick = {
                                    rules = rules.withAddedRules(values, pendingAddMatchType)
                                    addRulesText = ""
                                    pendingAddValues = null
                                },
                                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = CinemaAccent),
                            ) { Text(stringResource(R.string.common_ok)) }
                            CinemaButton(
                                onClick = { pendingAddValues = null },
                                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = CinemaSurfaceVariant),
                            ) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                }

                Text(stringResource(R.string.provider_filter_script_title), style = scaledStyles.titleSmall, color = CinemaTextPrimary)
                Text(
                    stringResource(R.string.provider_filter_script_desc),
                    style = scaledStyles.bodySmall,
                    color = CinemaTextSecondary,
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
            CinemaDialogActionButton(
                onClick = {
                    val newFilters = CategoryFilters(mode = filterMode, rules = rules, allowedScripts = selectedScripts)
                    onSave(newFilters)
                },
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaAccent,
                        contentColor = CinemaTextPrimary,
                    ),
            ) { Text(stringResource(R.string.provider_save_button)) }
        },
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary,
                    ),
            ) { Text(stringResource(R.string.common_cancel)) }
        },
        containerColor = CinemaSurface,
    )
}

@Composable
fun QuickConnectDialog(
    url: String,
    onSuccess: (name: String, username: String, token: String, userId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
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
            qcError = initResult.exceptionOrNull()?.message ?: initFailedText
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
                qcError = String.format(pollFailedFormat, poll.exceptionOrNull()?.message)
                return@LaunchedEffect
            }
            if (poll.getOrThrow().authenticated) {
                val authResult = api.authenticateWithQuickConnect(qcSecret)
                if (authResult.isFailure) {
                    qcError = String.format(authFailedFormat, authResult.exceptionOrNull()?.message)
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
        title = { Text(stringResource(R.string.provider_quick_connect_title), color = CinemaTextPrimary) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    qcError != null -> {
                        Text(
                            text = qcError!!,
                            color = CinemaError,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    qcCode.isEmpty() -> {
                        CircularProgressIndicator(color = CinemaAccent)
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = stringResource(R.string.provider_connecting_server),
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.provider_qc_enter_code),
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = qcCode,
                            color = CinemaAccent,
                            style =
                                MaterialTheme.typography.displayMedium.copy(
                                    fontSize =
                                        MaterialTheme.typography.displayMedium.fontSize
                                            .scaled(scale),
                                ),
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                        Text(
                            text = stringResource(R.string.provider_qc_instructions),
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                        CircularProgressIndicator(color = CinemaAccent)
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = stringResource(R.string.provider_qc_waiting),
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary,
                    ),
            ) { Text(stringResource(R.string.common_cancel)) }
        },
        containerColor = CinemaSurface,
    )
}
