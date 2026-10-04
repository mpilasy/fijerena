package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryMatcher
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.MatchType
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.network.provider.withAddedRules
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
import org.njarasoa.fijerena.ui.theme.*

/**
 * The content-filter editor (exclude / include-only rules by category name, allowed scripts),
 * opened from a profile's page for the source in use (D8); [title] names the source.
 */
@Composable
fun MobileCategoryFilterDialog(
    title: String,
    currentFilters: CategoryFilters,
    onSave: (CategoryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var filterMode by remember { mutableStateOf(currentFilters.mode) }
    var rules by remember { mutableStateOf(currentFilters.rules) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var editingMatchType by remember { mutableStateOf(MatchType.STARTS_WITH) }
    var addRulesText by remember { mutableStateOf("") }
    var pendingAddValues by remember { mutableStateOf<List<String>?>(null) }
    var pendingAddMatchType by remember { mutableStateOf(MatchType.STARTS_WITH) }
    var selectedScripts by remember { mutableStateOf(currentFilters.allowedScripts) }

    @Composable
    fun matchTypeLabel(type: MatchType): String =
        when (type) {
            MatchType.STARTS_WITH -> stringResource(R.string.provider_filter_match_starts)
            MatchType.ENDS_WITH -> stringResource(R.string.provider_filter_match_ends)
            MatchType.CONTAINS -> stringResource(R.string.provider_filter_match_contains)
            MatchType.EXACT -> stringResource(R.string.provider_filter_match_exact)
        }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            ) {
                Text(text = stringResource(R.string.provider_filter_mode_label), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                    CinemaFilterChip(
                        selected = filterMode == FilterMode.EXCLUDE,
                        onClick = { filterMode = FilterMode.EXCLUDE },
                        label = { Text(stringResource(R.string.provider_filter_exclude)) },
                    )
                    CinemaFilterChip(
                        selected = filterMode == FilterMode.INCLUDE,
                        onClick = { filterMode = FilterMode.INCLUDE },
                        label = { Text(stringResource(R.string.provider_filter_include)) },
                    )
                }
                Text(
                    text =
                        if (filterMode == FilterMode.EXCLUDE) {
                            stringResource(R.string.provider_filter_exclude_desc)
                        } else {
                            stringResource(R.string.provider_filter_include_desc)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                if (filterMode == FilterMode.INCLUDE && rules.isEmpty()) {
                    Text(
                        text = stringResource(R.string.provider_filter_include_empty_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Add section first — the thing you open this dialog to do most often.
                Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                    OutlinedTextField(
                        value = addRulesText,
                        onValueChange = { addRulesText = it },
                        label = { Text(stringResource(R.string.provider_filter_add_rules_label)) },
                        placeholder = { Text(stringResource(R.string.provider_filter_prefixes_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                    )
                    CinemaOutlinedButton(
                        onClick = {
                            val values = addRulesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            if (values.isNotEmpty()) {
                                pendingAddValues = values
                                pendingAddMatchType = MatchType.STARTS_WITH
                            }
                        },
                        enabled = addRulesText.isNotBlank(),
                    ) { Text(stringResource(R.string.common_add)) }

                    pendingAddValues?.let { values ->
                        Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                            Text(
                                text = stringResource(R.string.provider_filter_choose_match_type),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            // Manual 2-per-row wrap, not FlowRow — see MatchTypeChipRow note in
                            // tv/ProviderDialogs.kt for why FlowRow is avoided here right now.
                            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                MatchType.entries.chunked(2).forEach { rowTypes ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                        rowTypes.forEach { type ->
                                            CinemaFilterChip(
                                                selected = pendingAddMatchType == type,
                                                onClick = { pendingAddMatchType = type },
                                                label = { Text(matchTypeLabel(type)) },
                                            )
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                CinemaButton(onClick = {
                                    rules = rules.withAddedRules(values, pendingAddMatchType)
                                    addRulesText = ""
                                    pendingAddValues = null
                                }) { Text(stringResource(R.string.common_ok)) }
                                CinemaOutlinedButton(onClick = { pendingAddValues = null }) {
                                    Text(stringResource(R.string.common_cancel))
                                }
                            }
                        }
                    }
                }

                if (rules.isNotEmpty()) {
                    HorizontalDivider()
                }

                Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs)) {
                    rules.forEachIndexed { index, rule ->
                        if (editingIndex == index) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(CinemaCornerRadius.small),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(CinemaSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                                ) {
                                    Text(
                                        text = stringResource(R.string.provider_filter_edit_rule),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    OutlinedTextField(
                                        value = editingValue,
                                        onValueChange = { editingValue = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                        MatchType.entries.chunked(2).forEach { rowTypes ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                                rowTypes.forEach { type ->
                                                    CinemaFilterChip(
                                                        selected = editingMatchType == type,
                                                        onClick = { editingMatchType = type },
                                                        label = { Text(matchTypeLabel(type)) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                        CinemaButton(onClick = {
                                            val trimmed = editingValue.trim()
                                            if (trimmed.isNotEmpty()) {
                                                rules =
                                                    rules.toMutableList().also {
                                                        it[index] = CategoryMatcher(trimmed, editingMatchType)
                                                    }
                                            }
                                            editingIndex = null
                                        }) { Text(stringResource(R.string.common_ok)) }
                                        CinemaOutlinedButton(onClick = { editingIndex = null }) {
                                            Text(stringResource(R.string.common_cancel))
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = CinemaSpacing.xl + CinemaSpacing.xxs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = matchTypeLabel(rule.matchType),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                    modifier = Modifier.width(MobileDimensions.buttonHeight),
                                )
                                Text(
                                    text = rule.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        editingIndex = index
                                        editingValue = rule.value
                                        editingMatchType = rule.matchType
                                    },
                                    modifier = Modifier.size(MobileDimensions.iconLarge),
                                ) {
                                    Icon(
                                        CinemaIcons.Edit,
                                        contentDescription = stringResource(R.string.provider_filter_edit_rule),
                                        modifier = Modifier.size(MobileDimensions.iconSmall - Spacing.xxxs),
                                    )
                                }
                                Spacer(modifier = Modifier.width(CinemaSpacing.xxs))
                                IconButton(
                                    onClick = {
                                        rules = rules.toMutableList().also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.size(MobileDimensions.iconLarge),
                                ) {
                                    Icon(
                                        CinemaIcons.Delete,
                                        contentDescription = stringResource(R.string.provider_filter_delete_rule),
                                        modifier = Modifier.size(MobileDimensions.iconSmall - Spacing.xxxs),
                                    )
                                }
                            }
                        }
                    }
                }

                Text(text = stringResource(R.string.provider_filter_script_title), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.provider_filter_script_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                ScriptType.entries.forEach { script ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = script in selectedScripts,
                            onCheckedChange = { checked ->
                                selectedScripts = if (checked) selectedScripts + script else selectedScripts - script
                            },
                        )
                        Spacer(modifier = Modifier.width(CinemaSpacing.xxs))
                        Text(text = script.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = { onSave(CategoryFilters(mode = filterMode, rules = rules, allowedScripts = selectedScripts)) },
            ) { Text(stringResource(R.string.provider_save_button)) }
        },
        dismissButton = {
            CinemaOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
