package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.FavoriteTags
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.TvSectionTabs
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * The Favourites list's tag filter on TV (favourite tags plan, FT-D9), under the browse tabs:
 * **All**, then each tag of the section's favourites, as tabs (focus selects one, as in every TV
 * tab row), then Manage Tags. [entry] lands on the selected tab (Down from the browse tabs). Down
 * enters [itemsPane]; Left on the first tab enters [categoriesPane]; Up goes back to the browse tabs.
 */
@Composable
internal fun FavoriteTagTabs(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    entry: FocusRequester,
    categoriesPane: PaneFocusState,
    itemsPane: PaneFocusState,
    modifier: Modifier = Modifier,
) {
    val manage = remember { FocusRequester() }
    val labels = listOf(stringResource(R.string.favorite_tags_all)) + tags
    val selectedIndex = if (selected == null) 0 else tags.indexOf(selected) + 1
    Row(
        modifier =
            modifier
                .onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown && itemsPane.enter() }
                // Entering the row (Up from the list) lands on the selected tag, not on Manage Tags
                // by geometry: focusProperties directly before focusGroup, as in BrowseTabs.
                .focusProperties { onEnter = { entry.requestFocus() } }
                .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TvSectionTabs(
            tabs = labels,
            selectedIndex = selectedIndex,
            onTabSelected = { index -> onSelect(if (index == 0) null else tags.getOrNull(index - 1)) },
            entryFocusRequester = entry,
            endFocusRequester = manage,
            firstTabModifier =
                Modifier.onPreviewKeyEvent {
                    it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft && categoriesPane.enter()
                },
            modifier = Modifier.weight(1f, fill = false),
        )
        TvIconAction(
            onClick = onManage,
            icon = CinemaIcons.Tag,
            label = stringResource(R.string.favorite_tags_manage),
            // The row's end: Right stays, never up to the header's buttons.
            modifier = Modifier.focusRequester(manage).focusProperties { right = FocusRequester.Cancel },
        )
    }
}

/**
 * Manage Tags on TV (FT-D11): each tag of the profile with Rename and Delete; focus opens on the
 * first Rename. A rename into a tag's name merges the two; a delete asks first (focus on Cancel).
 */
@Composable
internal fun TvManageFavoriteTagsDialog(
    load: suspend () -> List<String>,
    onRename: (from: String, to: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val loaded by produceState<List<String>?>(null) { value = load() }
    var tags by remember(loaded) { mutableStateOf(loaded.orEmpty()) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val entry = remember { FocusRequester() }
    renaming?.let { tag ->
        TvRenameFavoriteTagDialog(
            tag = tag,
            onSave = { name ->
                renaming = null
                if (FavoriteTags.clean(name) != null && name.trim() != tag) {
                    onRename(tag, name)
                    tags = FavoriteTags.sorted(tags.map { if (it == tag) name.trim() else it })
                }
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { tag ->
        CinemaAlertDialog(
            onDismissRequest = { deleting = null },
            title = stringResource(R.string.favorite_tags_delete_title),
            text = { CinemaText(stringResource(R.string.favorite_tags_delete_message, tag), tone = TextTone.Secondary) },
            danger =
                DialogAction(stringResource(R.string.favorite_tags_delete)) {
                    deleting = null
                    onDelete(tag)
                    tags = tags - tag
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { deleting = null },
        )
    }
    if (loaded == null || renaming != null || deleting != null) return
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        initialFocus = if (tags.isNotEmpty()) entry else null,
        title = stringResource(R.string.favorite_tags_manage),
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.dialogBody),
            ) {
                if (tags.isEmpty()) CinemaText(stringResource(R.string.favorite_tags_none), tone = TextTone.Secondary)
                tags.forEachIndexed { index, tag ->
                    key(tag) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            CinemaText(
                                tag,
                                role = TextRole.Body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TvIconAction(
                                onClick = { renaming = tag },
                                icon = CinemaIcons.Edit,
                                label = stringResource(R.string.favorite_tags_rename),
                                modifier = if (index == 0) Modifier.focusRequester(entry) else Modifier,
                            )
                            TvIconAction(
                                onClick = { deleting = tag },
                                icon = CinemaIcons.Delete,
                                label = stringResource(R.string.favorite_tags_delete),
                                danger = true,
                            )
                        }
                    }
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_close), onClick = onDismiss),
    )
}

@Composable
private fun TvRenameFavoriteTagDialog(
    tag: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(tag) { mutableStateOf(tag) }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.favorite_tags_rename_title),
        text = {
            CinemaTextField(
                value = name,
                onValueChange = { name = it.take(FavoriteTags.MAX_LENGTH) },
                label = stringResource(R.string.favorite_tags_name),
                keyboardOptions = FieldKeyboards.Name,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().tvDpadEscape(),
            )
        },
        main = DialogAction(stringResource(R.string.provider_save_button), enabled = FavoriteTags.clean(name) != null) { onSave(name) },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
