package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.FavoriteTags
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.model.FavoriteTagTarget
import org.njarasoa.fijerena.core.ui.model.FavoriteTagsDraft
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.input.TvCheckRow
import org.njarasoa.fijerena.ui.components.input.TvSearchField

/**
 * The tag dialog of [target] (favourite tags plan, FT-D7), once [load] has read its start: a check
 * row per tag of the profile, then the New Tag field. Save keeps the checked tags (and a name
 * still in the field); Cancel or Back keeps the old ones.
 */
@Composable
internal fun TvFavoriteTagsEditor(
    target: FavoriteTagTarget,
    load: suspend (FavoriteTagTarget) -> FavoriteTagsDraft,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val start by produceState<FavoriteTagsDraft?>(null, target) { value = load(target) }
    start?.let { TvFavoriteTagsDialog(name = target.name, start = it, onSave = onSave, onDismiss = onDismiss) }
}

/**
 * Focus opens on the first tag, else on the New Tag field. OK on a row checks or clears it; OK on
 * New Tag opens the keyboard, whose Done puts the typed name in the list, checked.
 */
@Composable
private fun TvFavoriteTagsDialog(
    name: String,
    start: FavoriteTagsDraft,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(start) }
    var newName by remember { mutableStateOf("") }
    val entry = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    val addNew = {
        draft = draft.add(newName)
        newName = ""
    }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        initialFocus = if (start.tags.isEmpty()) fieldFocus else entry,
        title = stringResource(R.string.favorite_tags_title, name),
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.dialogBody),
            ) {
                if (draft.tags.isEmpty()) {
                    CinemaText(stringResource(R.string.favorite_tags_none), tone = TextTone.Secondary)
                }
                draft.tags.forEachIndexed { index, tag ->
                    key(tag) {
                        TvCheckRow(
                            checked = tag in draft.checked,
                            onCheckedChange = { draft = draft.toggle(tag) },
                            label = tag,
                            modifier = if (index == 0) Modifier.focusRequester(entry) else Modifier,
                        )
                    }
                }
                // The app's TV add field (as the parental word lists): OK opens the keyboard, its
                // Done adds the name, checked, and empties the field.
                TvSearchField(
                    query = newName,
                    onQueryChange = { newName = it.take(FavoriteTags.MAX_LENGTH) },
                    onSearchSubmit = addNew,
                    onClear = { newName = "" },
                    placeholder = stringResource(R.string.favorite_tags_new),
                    leadingIcon = CinemaIcons.Add,
                    focusRequester = fieldFocus,
                    editing = editing,
                    onEditingChange = { editing = it },
                )
            }
        },
        main = DialogAction(stringResource(R.string.provider_save_button)) { onSave(draft.add(newName).result) },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
