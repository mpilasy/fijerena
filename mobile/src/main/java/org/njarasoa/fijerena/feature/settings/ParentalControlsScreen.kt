package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.ParentalControlsRepository
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.home.SourceChoice
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.viewmodels.ParentalControlsViewModel
import org.njarasoa.fijerena.feature.contentselection.components.MobileSourceChooserDialog
import org.njarasoa.fijerena.feature.settings.components.SettingsGroupHeader
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Settings › Parental controls (`Screen.ParentalControls`): the description, one row per list with
 * its number of words ([onOpenList]), Check a source ([onCheck]; a chooser first when the child
 * profiles have two or more sources), then Content filters of each profile ([onProfiles]).
 */
@Composable
fun MobileParentalControlsScreen(
    viewModel: ParentalControlsViewModel,
    onOpenList: (ParentalList) -> Unit,
    onCheck: (providerId: Long) -> Unit,
    onProfiles: () -> Unit,
    onBack: () -> Unit,
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    var choosingSource by remember { mutableStateOf(false) }
    if (choosingSource) {
        MobileSourceChooserDialog(
            sources = sources.map { it.id to it.name },
            onChosen = { id ->
                choosingSource = false
                onCheck(id)
            },
            onDismiss = { choosingSource = false },
        )
    }
    ParentalScaffold(stringResource(R.string.parental_controls_title), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.lg + LocalDockInset.current),
        ) {
            item { ParentalText(stringResource(R.string.parental_controls_desc)) }
            ParentalList.entries.forEach { list ->
                item(key = list.key) {
                    SettingsListRow(
                        title = stringResource(list.titleRes),
                        summary =
                            stringResource(list.descriptionRes) + "\n" +
                                stringResource(R.string.parental_list_entries, lists[list].orEmpty().size),
                        onClick = { onOpenList(list) },
                    )
                }
            }
            item {
                val choice = SourceChoice.of(sources)
                SettingsListRow(
                    title = stringResource(R.string.parental_check_title),
                    summary =
                        stringResource(
                            if (choice == SourceChoice.None) R.string.parental_no_sources else R.string.parental_check_desc,
                        ),
                    enabled = choice != SourceChoice.None,
                    onClick = {
                        when (choice) {
                            is SourceChoice.One -> onCheck(choice.source.id)
                            is SourceChoice.Ask -> choosingSource = true
                            SourceChoice.None -> Unit
                        }
                    },
                )
            }
            // Each child's own filters are on its page: this row goes back to Settings' profiles.
            item {
                SettingsListRow(
                    title = stringResource(R.string.parental_profile_filters_title),
                    summary = stringResource(R.string.parental_profile_filters_desc),
                    onClick = onProfiles,
                )
            }
        }
    }
}

/**
 * One list of Parental controls (`Screen.ParentalListEdit`): its words, each with its match count
 * on the child profiles' sources (loaded on open), a field to add a word, and Reset to the app's
 * list in the top bar.
 */
@Composable
fun MobileParentalListScreen(
    viewModel: ParentalControlsViewModel,
    list: ParentalList,
    onBack: () -> Unit,
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    LaunchedEffect(list) { viewModel.loadCounts(list) }
    ParentalListContent(
        list = list,
        entries = lists[list].orEmpty(),
        countOf = { counts[list to it] },
        onAdd = { viewModel.add(list, it) },
        onRemove = { viewModel.remove(list, it) },
        onUndo = { entry, index -> viewModel.undo(list, entry, index) },
        onReset = { viewModel.reset(list) },
        onBack = onBack,
    )
}

/**
 * [MobileParentalListScreen] without its view model. A removal is at once, with Undo in a snackbar
 * ([onUndo] gets the word and its old place); Reset asks first.
 */
@Composable
internal fun ParentalListContent(
    list: ParentalList,
    entries: List<String>,
    countOf: (String) -> Int?,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUndo: (entry: String, index: Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var newEntry by rememberSaveable { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    val addEntry = {
        onAdd(newEntry)
        newEntry = ""
    }
    if (confirmReset) {
        ResetListDialog(
            onConfirm = {
                confirmReset = false
                onReset()
            },
            onDismiss = { confirmReset = false },
        )
    }
    ParentalScaffold(
        title = stringResource(list.titleRes),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            IconAction(
                onClick = { confirmReset = true },
                icon = CinemaIcons.Replay,
                label = stringResource(R.string.parental_list_reset),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.lg + LocalDockInset.current),
        ) {
            item {
                ParentalText(stringResource(list.descriptionRes))
                ParentalText(stringResource(R.string.parental_entry_hint), role = TextRole.BodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    CinemaTextField(
                        value = newEntry,
                        onValueChange = { newEntry = it },
                        label = stringResource(R.string.parental_entry_add),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addEntry() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(
                        onClick = addEntry,
                        icon = CinemaIcons.Add,
                        label = stringResource(R.string.parental_entry_add),
                        enabled = newEntry.isNotBlank(),
                    )
                }
            }
            itemsIndexed(entries, key = { _, entry -> entry }) { index, entry ->
                val count = countOf(entry)
                SettingsListRow(
                    title = entry,
                    summary =
                        when (count) {
                            null -> null
                            0 -> stringResource(R.string.parental_entry_matches_none)
                            else -> stringResource(R.string.parental_entry_matches, count)
                        },
                    trailing = {
                        IconAction(
                            onClick = {
                                onRemove(entry)
                                scope.launch {
                                    // A new removal takes the place of the one shown; that one stays removed.
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result =
                                        snackbarHostState.showSnackbar(
                                            message = resources.getString(R.string.parental_entry_removed, entry),
                                            actionLabel = resources.getString(R.string.parental_entry_undo),
                                            duration = SnackbarDuration.Long,
                                        )
                                    if (result == SnackbarResult.ActionPerformed) onUndo(entry, index)
                                }
                            },
                            icon = CinemaIcons.Delete,
                            label = stringResource(R.string.parental_entry_remove, entry),
                        )
                    },
                )
            }
        }
    }
}

/** "Reset to the app's list": the admin's changes to the list are lost, so it asks first. */
@Composable
private fun ResetListDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.parental_list_reset), role = TextRole.Title) },
        text = { CinemaText(stringResource(R.string.parental_list_reset_confirm), tone = TextTone.Secondary) },
        confirmButton = {
            CinemaDialogActionButton(onClick = onConfirm, kind = ButtonKind.Danger) {
                CinemaText(stringResource(R.string.parental_list_reset), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}

/**
 * Parental controls › Check a source (`Screen.ParentalCheck`, P-D3): the categories of [providerId]
 * that a child sees with the lists now, then the ones it does not, each with its type.
 */
@Composable
fun MobileParentalCheckScreen(
    viewModel: ParentalControlsViewModel,
    providerId: Long,
    onBack: () -> Unit,
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    LaunchedEffect(providerId) { viewModel.loadCheck(providerId) }
    val title = sources.firstOrNull { it.id == providerId }?.name ?: stringResource(R.string.parental_check_title)
    ParentalScaffold(title, onBack) { padding ->
        ParentalCheckContent(check, Modifier.fillMaxSize().padding(padding))
    }
}

/** [MobileParentalCheckScreen]'s list; [categories] null while they load. */
@Composable
internal fun ParentalCheckContent(
    categories: List<ParentalControlsRepository.CheckedCategory>?,
    modifier: Modifier = Modifier,
) {
    val (shown, hidden) = categories.orEmpty().partition { it.shown }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.lg + LocalDockInset.current),
    ) {
        item { ParentalText(stringResource(R.string.parental_check_desc)) }
        if (categories == null) {
            item { CinemaSpinner(modifier = Modifier.padding(Spacing.md)) }
        } else {
            item { SettingsGroupHeader(stringResource(R.string.parental_check_shown, shown.size)) }
            if (shown.isEmpty()) {
                item { ParentalText(stringResource(R.string.parental_check_none)) }
            }
            items(shown) { CheckedCategoryRow(it) }
            item { SettingsGroupHeader(stringResource(R.string.parental_check_hidden, hidden.size)) }
            items(hidden) { CheckedCategoryRow(it) }
        }
    }
}

@Composable
private fun CheckedCategoryRow(category: ParentalControlsRepository.CheckedCategory) {
    SettingsListRow(
        title = category.name,
        summary =
            stringResource(
                when (category.type) {
                    XtreamCategoryEntity.TYPE_LIVE -> R.string.provider_live_tv_label
                    XtreamCategoryEntity.TYPE_VOD -> R.string.provider_movies_label
                    else -> R.string.provider_tv_shows_label
                },
            ),
    )
}

@Composable
private fun ParentalText(
    text: String,
    role: TextRole = TextRole.Body,
) {
    CinemaText(
        text,
        role = role,
        tone = TextTone.Secondary,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentalScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { CinemaText(title, role = TextRole.Title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = { actions() },
            )
        },
        // Over the bottom bar, which Settings' screens keep.
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(it, Modifier.padding(bottom = LocalDockInset.current)) }
        },
        content = content,
    )
}

private val ParentalList.titleRes: Int
    get() =
        when (this) {
            ParentalList.KIDS_CATEGORIES -> R.string.parental_list_kids_categories
            ParentalList.KIDS_FILM_CATEGORIES -> R.string.parental_list_kids_film_categories
            ParentalList.ADULT_CATEGORIES -> R.string.parental_list_adult_categories
            ParentalList.HIDDEN_TITLES -> R.string.parental_list_hidden_titles
            ParentalList.EXCEPTIONS -> R.string.parental_list_exceptions
        }

private val ParentalList.descriptionRes: Int
    get() =
        when (this) {
            ParentalList.KIDS_CATEGORIES -> R.string.parental_list_kids_categories_desc
            ParentalList.KIDS_FILM_CATEGORIES -> R.string.parental_list_kids_film_categories_desc
            ParentalList.ADULT_CATEGORIES -> R.string.parental_list_adult_categories_desc
            ParentalList.HIDDEN_TITLES -> R.string.parental_list_hidden_titles_desc
            ParentalList.EXCEPTIONS -> R.string.parental_list_exceptions_desc
        }
