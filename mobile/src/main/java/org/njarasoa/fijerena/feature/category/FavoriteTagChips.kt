package org.njarasoa.fijerena.feature.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
import org.njarasoa.fijerena.ui.components.scrollEdgeFade

/**
 * The Favourites list's tag filter (favourite tags plan, FT-D8): **All**, then each tag of the
 * section's favourites, one chosen at a time; Manage Tags at the row's end.
 */
@Composable
internal fun FavoriteTagChipRow(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    colors: SelectableChipColors,
) {
    val listState = rememberLazyListState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = CinemaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).scrollEdgeFade(listState),
            contentPadding = PaddingValues(horizontal = CinemaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        ) {
            item(key = "all") {
                CinemaFilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { CinemaText(stringResource(R.string.favorite_tags_all), tone = TextTone.Inherit) },
                    colors = colors,
                )
            }
            items(tags, key = { "tag_$it" }) { tag ->
                CinemaFilterChip(
                    selected = tag == selected,
                    onClick = { onSelect(tag) },
                    label = { CinemaText(tag, tone = TextTone.Inherit, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = colors,
                )
            }
        }
        IconAction(
            onClick = onManage,
            icon = CinemaIcons.Tag,
            label = stringResource(R.string.favorite_tags_manage),
            tone = TextTone.Primary,
        )
    }
}

/**
 * Manage Tags (FT-D11): each tag of the profile with Rename and Delete. A rename into a tag's name
 * merges the two; a delete asks first and takes the tag off every favourite.
 */
@Composable
internal fun ManageFavoriteTagsDialog(
    load: suspend () -> List<String>,
    onRename: (from: String, to: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val loaded by produceState<List<String>?>(null) { value = load() }
    var tags by remember(loaded) { mutableStateOf(loaded.orEmpty()) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    renaming?.let { tag ->
        RenameFavoriteTagDialog(
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
        title = stringResource(R.string.favorite_tags_manage),
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (tags.isEmpty()) CinemaText(stringResource(R.string.favorite_tags_none), tone = TextTone.Secondary)
                tags.forEach { tag ->
                    key(tag) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CinemaText(
                                tag,
                                role = TextRole.Body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconAction(
                                onClick = { renaming = tag },
                                icon = CinemaIcons.Edit,
                                label = stringResource(R.string.favorite_tags_rename),
                            )
                            IconAction(
                                onClick = { deleting = tag },
                                icon = CinemaIcons.Delete,
                                label = stringResource(R.string.favorite_tags_delete),
                                tone = TextTone.Danger,
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
private fun RenameFavoriteTagDialog(
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
                modifier = Modifier.fillMaxWidth(),
            )
        },
        main = DialogAction(stringResource(R.string.provider_save_button), enabled = FavoriteTags.clean(name) != null) { onSave(name) },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
