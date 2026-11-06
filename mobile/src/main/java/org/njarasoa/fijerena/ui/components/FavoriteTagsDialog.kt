package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import org.njarasoa.fijerena.core.network.FavoriteTags
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.model.FavoriteTagTarget
import org.njarasoa.fijerena.core.ui.model.FavoriteTagsDraft
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * The tag dialog of [target] (favourite tags plan, FT-D7), once [load] has read its start: a check
 * row per tag of the profile, then New Tag with its Add. Save keeps the checked tags (and a name
 * still in the field); Cancel keeps the old ones. The phone side of `TvFavoriteTagsEditor`.
 */
@Composable
fun FavoriteTagsEditor(
    target: FavoriteTagTarget,
    load: suspend (FavoriteTagTarget) -> FavoriteTagsDraft,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val start by produceState<FavoriteTagsDraft?>(null, target) { value = load(target) }
    start?.let { FavoriteTagsDialog(name = target.name, start = it, onSave = onSave, onDismiss = onDismiss) }
}

@Composable
private fun FavoriteTagsDialog(
    name: String,
    start: FavoriteTagsDraft,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(start) }
    var newName by remember { mutableStateOf("") }
    val addNew = {
        draft = draft.add(newName)
        newName = ""
    }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.favorite_tags_title, name),
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.dialogBody),
            ) {
                if (draft.tags.isEmpty()) {
                    CinemaText(stringResource(R.string.favorite_tags_none), tone = TextTone.Secondary)
                }
                draft.tags.forEach { tag ->
                    key(tag) {
                        CinemaCheckRow(
                            checked = tag in draft.checked,
                            onCheckedChange = { draft = draft.toggle(tag) },
                            label = tag,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CinemaTextField(
                        value = newName,
                        onValueChange = { newName = it.take(FavoriteTags.MAX_LENGTH) },
                        label = stringResource(R.string.favorite_tags_new),
                        keyboardOptions = FieldKeyboards.Name.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addNew() }),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(
                        onClick = addNew,
                        icon = CinemaIcons.Add,
                        label = stringResource(R.string.favorite_tags_add),
                        enabled = FavoriteTags.clean(newName) != null,
                    )
                }
            }
        },
        main = DialogAction(stringResource(R.string.provider_save_button)) { onSave(draft.add(newName).result) },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
