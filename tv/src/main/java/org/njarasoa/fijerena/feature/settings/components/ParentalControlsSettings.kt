package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import org.njarasoa.fijerena.core.network.provider.ParentalControlsRepository
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ParentalControlsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.category.components.rowAfterChange
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.TvUndoBar
import org.njarasoa.fijerena.ui.components.input.TvSearchField
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rememberUndoBarState
import org.njarasoa.fijerena.ui.components.undoOnRemoteKeys
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/*
 * Settings › Parental controls on TV (docs/plans/20261020_parental-controls-plan.md, "The page"),
 * for the admin only: the group's rows (one per list, then Check a source), a list's pane and the
 * check view. The panes open in place of the group's rows, like the other Settings pickers.
 */

/** The list's title. */
@get:StringRes
internal val ParentalList.titleRes: Int
    get() =
        when (this) {
            ParentalList.KIDS_CATEGORIES -> R.string.parental_list_kids_categories
            ParentalList.KIDS_FILM_CATEGORIES -> R.string.parental_list_kids_film_categories
            ParentalList.ADULT_CATEGORIES -> R.string.parental_list_adult_categories
            ParentalList.HIDDEN_TITLES -> R.string.parental_list_hidden_titles
            ParentalList.EXCEPTIONS -> R.string.parental_list_exceptions
        }

/** What the list does, in one sentence. */
@get:StringRes
internal val ParentalList.descriptionRes: Int
    get() =
        when (this) {
            ParentalList.KIDS_CATEGORIES -> R.string.parental_list_kids_categories_desc
            ParentalList.KIDS_FILM_CATEGORIES -> R.string.parental_list_kids_film_categories_desc
            ParentalList.ADULT_CATEGORIES -> R.string.parental_list_adult_categories_desc
            ParentalList.HIDDEN_TITLES -> R.string.parental_list_hidden_titles_desc
            ParentalList.EXCEPTIONS -> R.string.parental_list_exceptions_desc
        }

/**
 * The Parental controls group's rows: the description, one row per list with its number of words,
 * then Check a source. [firstRowModifier] carries the pane's entry key; [focusRequesterFor] gives
 * the row that opened a pane its focus back (by [parentalListReturnKey] or [PARENTAL_CHECK_RETURN_KEY]).
 */
@Composable
internal fun ParentalControlsCard(
    onOpenList: (ParentalList) -> Unit,
    onOpenCheck: () -> Unit,
    focusRequesterFor: (String) -> FocusRequester?,
    firstRowModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: ParentalControlsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    // Another device can change the lists while Settings is closed.
    LaunchedEffect(Unit) { viewModel.refresh() }
    SettingsSection(description = stringResource(R.string.parental_controls_desc)) {
        ParentalList.entries.forEachIndexed { index, list ->
            SettingsRow(
                title = stringResource(list.titleRes),
                description = stringResource(list.descriptionRes),
                value = stringResource(R.string.parental_list_entries, lists[list].orEmpty().size),
                onClick = { onOpenList(list) },
                modifier = if (index == 0) firstRowModifier else Modifier,
                focusRequester = focusRequesterFor(parentalListReturnKey(list)),
            )
        }
        SettingsRow(
            title = stringResource(R.string.parental_check_title),
            description = null,
            onClick = onOpenCheck,
            focusRequester = focusRequesterFor(PARENTAL_CHECK_RETURN_KEY),
        )
    }
}

/** The key that gives a list's row its focus back when its pane closes. */
internal fun parentalListReturnKey(list: ParentalList) = "parental:${list.name}"

/** The key that gives Check a source its focus back when the check view closes. */
internal const val PARENTAL_CHECK_RETURN_KEY = "parental:check"

/** One list's pane, bound to [ParentalControlsViewModel]; see [ParentalListContent]. */
@Composable
internal fun ParentalListPane(
    list: ParentalList,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ParentalControlsViewModel = viewModel(factory = SettingsViewModelFactory(context))
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
 * A list: its description and the entry hint, the field to add a word (OK opens the keyboard, Done
 * adds the word), one row per word with what it matches on the sources, then Reset to the app's
 * list (asks first). OK on a word removes it at once: focus goes to the row that takes its place
 * (the field when the list is empty) and the Undo bar puts the word back at the same place (Menu,
 * or hold OK). Focus opens on the field; Back and Left leave, except while the keyboard is up.
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
    var editing by remember { mutableStateOf(false) }
    var draft by rememberSaveable(list) { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    val undoBar = rememberUndoBarState()
    val listState = rememberLazyListState()
    val fieldFocus = remember { FocusRequester() }
    val resetFocus = remember { FocusRequester() }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    val requesterOf: (String) -> FocusRequester = { rowFocus.getOrPut(it) { FocusRequester() } }
    // A row to focus once the list shows it: a word put back by Undo, or the row that takes a
    // removed word's place when it was not composed yet.
    var focusAfterChange by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { fieldFocus.requestFocusWithRetry() }
    LaunchedEffect(entries, focusAfterChange) {
        val key = focusAfterChange ?: return@LaunchedEffect
        val index = entries.indexOf(key)
        if (index < 0) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
        requesterOf(key).requestFocusWithRetry(fallback = fieldFocus)
        focusAfterChange = null
    }
    val remove: (Int, String) -> Unit = { index, entry ->
        // Focus moves before the row goes, so it is never on nothing.
        val target = rowAfterChange(entries, entry, entries - entry)
        val moved = (target?.let(requesterOf) ?: fieldFocus).requestFocus(FocusDirection.Enter)
        if (!moved) focusAfterChange = target
        onRemove(entry)
        undoBar.show(entry) {
            onUndo(entry, index)
            focusAfterChange = entry
        }
    }

    ParentalPaneFrame(
        title = stringResource(list.titleRes),
        onBack = onBack,
        handlesKeys = !editing,
        modifier = Modifier.undoOnRemoteKeys(undoBar),
    ) {
        SettingsNote(stringResource(list.descriptionRes), modifier = Modifier.padding(horizontal = Spacing.xs))
        SettingsNote(stringResource(R.string.parental_entry_hint), modifier = Modifier.padding(horizontal = Spacing.xs))
        TvSearchField(
            query = draft,
            onQueryChange = { draft = it },
            onSearchSubmit = {
                onAdd(draft)
                draft = ""
            },
            onClear = { draft = "" },
            placeholder = stringResource(R.string.parental_entry_add),
            focusRequester = fieldFocus,
            editing = editing,
            onEditingChange = { editing = it },
            // Down goes to the first word (Reset with no word), as in ProfileFavoritesPane.
            modifier = Modifier.focusProperties { down = entries.firstOrNull()?.let(requesterOf) ?: resetFocus },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry }) { index, entry ->
                ParentalEntryRow(
                    entry = entry,
                    count = countOf(entry),
                    onClick = { remove(index, entry) },
                    focusRequester = requesterOf(entry),
                )
            }
            item(key = RESET_ROW_KEY) {
                SettingsRow(
                    title = stringResource(R.string.parental_list_reset),
                    description = null,
                    onClick = { confirmReset = true },
                    chevron = false,
                    focusRequester = resetFocus,
                )
            }
        }
        TvUndoBar(undoBar, Modifier.align(Alignment.CenterHorizontally))
    }

    if (confirmReset) {
        ConfirmActionDialog(
            title = stringResource(R.string.parental_list_reset),
            text = stringResource(R.string.parental_list_reset_confirm),
            confirmText = stringResource(R.string.parental_list_reset),
            onConfirm = {
                confirmReset = false
                undoBar.dismiss()
                onReset()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/**
 * A word and what it matches ("Matches 12"; nothing while it loads); a ✕ on the focused row says
 * OK removes it. The ✕ keeps its slot on the other rows: a trailing slot that comes with focus
 * changes the row's layout, and the row then loses that focus.
 */
@Composable
private fun ParentalEntryRow(
    entry: String,
    count: Int?,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    SettingsRow(
        title = entry,
        description =
            when (count) {
                null -> null
                0 -> stringResource(R.string.parental_entry_matches_none)
                else -> stringResource(R.string.parental_entry_matches, count)
            },
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        chevron = false,
        focusRequester = focusRequester,
        trailing = {
            if (focused) {
                Icon(
                    imageVector = CinemaIcons.Close,
                    contentDescription = stringResource(R.string.edit_remove_row),
                    tint = CinemaTheme.colors.danger,
                    modifier = Modifier.size(TvDimensions.iconSmall),
                )
            } else {
                Spacer(modifier = Modifier.size(TvDimensions.iconSmall))
            }
        },
    )
}

/**
 * Check a source: with several sources, which one first (focus on the first, or on the one
 * checked last); with one, its check at once. A check is a row for the source (with what the view
 * shows), then Shown (n) and Hidden (n), each category with its type. Back and Left go back one
 * step: to the sources, then to the row that opened the view.
 */
@Composable
internal fun ParentalCheckPane(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ParentalControlsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    var chosenId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastChosenId by rememberSaveable { mutableStateOf<Long?>(null) }
    val chosen = sources.firstOrNull { it.id == chosenId } ?: sources.singleOrNull()
    val leave = {
        if (chosenId != null && sources.size > 1) {
            lastChosenId = chosenId
            chosenId = null
        } else {
            onBack()
        }
    }
    ParentalPaneFrame(title = stringResource(R.string.parental_check_title), onBack = leave) {
        if (chosen == null) {
            ParentalSourceChooser(
                sources = sources,
                focusedId = lastChosenId,
                onChosen = { chosenId = it },
            )
        } else {
            LaunchedEffect(chosen.id) { viewModel.loadCheck(chosen.id) }
            ParentalCheckList(source = chosen, categories = check)
        }
    }
}

@Composable
private fun ColumnScope.ParentalSourceChooser(
    sources: List<ParentalControlsRepository.Source>,
    focusedId: Long?,
    onChosen: (Long) -> Unit,
) {
    val entryFocus = remember { FocusRequester() }
    val entryIndex = sources.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
    LaunchedEffect(sources.isEmpty()) { entryFocus.requestFocusWithRetry() }
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (sources.isEmpty()) {
            // One focusable row, so focus has a place and the sentence is not skipped.
            item {
                SettingsRow(
                    title = stringResource(R.string.parental_no_sources),
                    description = null,
                    onClick = {},
                    chevron = false,
                    focusRequester = entryFocus,
                )
            }
        }
        itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
            SettingsRow(
                title = source.name,
                description = null,
                onClick = { onChosen(source.id) },
                focusRequester = if (index == entryIndex) entryFocus else null,
            )
        }
    }
}

@Composable
private fun ColumnScope.ParentalCheckList(
    source: ParentalControlsRepository.Source,
    categories: List<ParentalControlsRepository.CheckedCategory>?,
) {
    val headerFocus = remember { FocusRequester() }
    LaunchedEffect(source.id) { headerFocus.requestFocusWithRetry() }
    val shown = categories.orEmpty().filter { it.shown }
    val hidden = categories.orEmpty().filterNot { it.shown }
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // The source's row stays while the check loads, so focus has a place from the start.
        item {
            SettingsRow(
                title = source.name,
                description =
                    if (categories == null) {
                        stringResource(R.string.common_loading)
                    } else {
                        stringResource(R.string.parental_check_desc)
                    },
                onClick = {},
                chevron = false,
                focusRequester = headerFocus,
            )
        }
        if (categories == null) return@LazyColumn
        item { ParentalCheckHeading(stringResource(R.string.parental_check_shown, shown.size)) }
        if (shown.isEmpty()) {
            item {
                SettingsRow(
                    title = stringResource(R.string.parental_check_none),
                    description = null,
                    onClick = {},
                    chevron = false,
                )
            }
        }
        itemsIndexed(shown) { _, category -> ParentalCategoryRow(category) }
        item { ParentalCheckHeading(stringResource(R.string.parental_check_hidden, hidden.size)) }
        itemsIndexed(hidden) { _, category -> ParentalCategoryRow(category) }
    }
}

/** A group's title in the check view; not a focus stop. */
@Composable
private fun ParentalCheckHeading(text: String) {
    CinemaText(
        text,
        modifier = Modifier.padding(horizontal = Spacing.xs).padding(top = Spacing.sm),
        role = TextRole.Heading,
        tone = TextTone.Accent,
    )
}

/** A category of the check view: its name and its type. OK does nothing. */
@Composable
private fun ParentalCategoryRow(category: ParentalControlsRepository.CheckedCategory) {
    SettingsRow(
        title = category.name,
        description =
            stringResource(
                when (category.type) {
                    XtreamCategoryEntity.TYPE_LIVE -> R.string.provider_live_tv_label
                    XtreamCategoryEntity.TYPE_VOD -> R.string.provider_movies_label
                    else -> R.string.provider_tv_shows_label
                },
            ),
        onClick = {},
        chevron = false,
    )
}

/**
 * A `‹ Title` pane in place of the group's rows. Back and Left run [onBack] while [handlesKeys]
 * (off while a text field has the keyboard, which keeps Back to close it). Back is taken in
 * `onPreviewKeyEvent` on the root (docs/NAVIGATION_GUIDE.md, "TV Back on Detail Screens"), with
 * [BackHandler] as the fallback; Left is taken there too, before the `tvPane` gives it to the rail.
 */
@Composable
private fun ParentalPaneFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    handlesKeys: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = handlesKeys, onBack = onBack)
    Box(
        modifier =
            modifier.fillMaxSize().onPreviewKeyEvent { event ->
                when {
                    !handlesKeys -> {
                        false
                    }

                    event.key == Key.Back -> {
                        if (event.type == KeyEventType.KeyUp) onBack()
                        true
                    }

                    event.key == Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown) onBack()
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CinemaText(
                "‹ $title",
                modifier = Modifier.padding(horizontal = Spacing.xs),
                role = TextRole.Title,
                tone = TextTone.Primary,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            content()
        }
    }
}

private const val RESET_ROW_KEY = "parental:reset"
