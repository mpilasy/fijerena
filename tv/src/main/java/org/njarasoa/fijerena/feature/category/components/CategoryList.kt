package org.njarasoa.fijerena.feature.category.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Card
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.partitionVirtual
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.currentIndicator
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.tvPane
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CategoryList(
    categories: ImmutableCategoryList,
    selectedCategoryId: String?,
    categoriesRefreshing: Boolean,
    contentType: String,
    categoryViewModel: CategoryViewModel,
    favoriteCategoryIds: ImmutableStringSet = ImmutableStringSet(),
    onCategorySelected: (String) -> Unit,
    onRefreshCategories: () -> Unit,
    /** This column's pane; Right leaves it for [itemsPane] (see `Modifier.tvPane`). */
    paneFocus: PaneFocusState,
    itemsPane: PaneFocusState?,
    /**
     * Whether the selected category takes focus when this list first composes. False when the
     * item pane already has rows to land on (a Back return): its own hand-back has the last word.
     */
    focusSelectedOnOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val (virtualCategories, regularCategories) =
        remember(categories) {
            categories.partitionVirtual()
        }

    val listState = rememberLazyListState()
    paneFocus.bind(
        selectedKey = selectedCategoryId,
        firstKey = virtualCategories.firstOrNull()?.id ?: regularCategories.firstOrNull()?.id,
        listState = listState,
        indexOf = { key -> regularCategories.indexOfFirst { it.id == key } },
        // Left from an item lands on the category being browsed, not the one last scrolled past.
        preferSelected = true,
    )

    // Focus follows the selection: on open, and when it changes under the list (a "Recent
    // Categories" row, a deep link). Keyed on the selection's position, not the list itself, so a
    // re-emitted category list doesn't relaunch a focus attempt that can keep failing (and
    // flooding logcat) while the list is hidden — see StreamList's identical effect. The pane
    // scrolls only when the row isn't composed, so OK on a visible category no longer jumps it
    // to the top.
    val selectedIndex = remember(regularCategories, selectedCategoryId) { regularCategories.indexOfFirst { it.id == selectedCategoryId } }
    var skipFirstSelection by remember { mutableStateOf(!focusSelectedOnOpen) }
    LaunchedEffect(selectedCategoryId, selectedIndex) {
        if (skipFirstSelection) {
            skipFirstSelection = false
            return@LaunchedEffect
        }
        if (selectedCategoryId != null) paneFocus.focusKey(selectedCategoryId)
    }

    var targetRotation by remember { mutableStateOf(0f) }

    // Keyed on the flag, not Unit: the loop below never returns, so as a collector body it could
    // never observe refreshing going false again — the spinner kept turning for the lifetime of the
    // screen after the first refresh. As an effect key, a false value cancels it. Same shape as
    // StreamList's identical loop.
    LaunchedEffect(categoriesRefreshing) {
        if (categoriesRefreshing) {
            while (true) {
                targetRotation = (targetRotation + 360f) % 3600f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    val scale = LocalUiScale.current
    val cardStyle = categoryCardStyle(scale)

    // Row actions (UX overhaul plan Part II P3): long-press OK or the Menu key on a category opens
    // this menu, in place of the hidden ★ that was an extra Right stop before the items (F-C-5).
    var actionsCategory by remember { mutableStateOf<MediaCategory?>(null) }
    actionsCategory?.let { category ->
        FavoriteContextMenuDialog(
            target =
                FavoriteMenuTarget.Category(
                    categoryId = category.id,
                    categoryName = category.name,
                    contentType = contentType,
                    isFavorite = category.id in favoriteCategoryIds,
                ),
            onConfirm = { categoryViewModel.toggleFavoriteCategory(category.id, category.name, contentType) },
            onDismiss = { actionsCategory = null },
        )
    }
    val typography = MaterialTheme.typography
    val scaledTitleLarge =
        remember(scale, typography) {
            typography.titleLarge.copy(fontSize = typography.titleLarge.fontSize.scaled(scale))
        }

    // Entrance animation plays once per item: LazyColumn recycles item composition off the ends
    // of the scroll buffer, and D-pad scrolling churns that buffer constantly, so without this
    // guard the fade/slide replays on every focus move instead of just on first appearance.
    // Keyed to categories so it resets (bounded) on category-list change instead of growing
    // unbounded for the composable's whole lifetime.
    val enteredCategoryIds = remember(categories) { mutableSetOf<String>() }

    val palette = LocalCinemaTheme.current
    val panelRadius = CornerRadius.small
    val panelShape = remember(panelRadius) { RoundedCornerShape(panelRadius) }
    val borderBrush =
        remember(palette) {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors =
                    listOf(
                        CinemaGlassBorder,
                        Color.White.copy(alpha = CinemaAlpha.ghost),
                        CinemaGlassBorder,
                    ),
            )
        }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(bottom = Spacing.md.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
        ) {
            Text(
                text = stringResource(R.string.search_tab_categories),
                style = scaledTitleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CinemaIconButton(
                onClick = onRefreshCategories,
                enabled = !categoriesRefreshing,
                size = TvDimensions.iconLarge,
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Refresh,
                        contentDescription = stringResource(R.string.category_refresh_description),
                        tint = CinemaTextPrimary,
                        modifier =
                            Modifier
                                .size(TvDimensions.iconMedium.scaled(scale))
                                .rotate(rotation),
                    )
                },
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        color = CinemaGlassBackground,
                        shape = panelShape,
                    ).border(
                        width = TvDimensions.borderDefault,
                        brush = borderBrush,
                        shape = panelShape,
                    )
                    // The header row above (title, Refresh) stays outside the pane: Up from the
                    // first category reaches it. Left goes nowhere (F-C-4).
                    .tvPane(paneFocus, exitRight = itemsPane),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (virtualCategories.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(Spacing.sm.scaled(scale)),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                LocalUiStyle.current.grid.spacing
                                    .scaled(scale),
                            ),
                    ) {
                        virtualCategories.forEach { category ->
                            CategoryItem(
                                category = category,
                                isSelected = category.id == selectedCategoryId,
                                cardStyle = cardStyle,
                                isFavorite = false,
                                onClick = { onCategorySelected(category.id) },
                                cardModifier = Modifier.paneItem(paneFocus, category.id),
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(TvDimensions.borderFocused.scaled(scale))
                                .padding(horizontal = Spacing.md.scaled(scale))
                                .background(CinemaAccent.copy(alpha = CinemaAlpha.tint)),
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(Spacing.sm.scaled(scale)),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            LocalUiStyle.current.grid.spacing
                                .scaled(scale),
                        ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = regularCategories,
                        key = { _, category -> category.id },
                        contentType = { _, _ -> "category" },
                    ) { index, category ->
                        CategoryItem(
                            category = category,
                            isSelected = category.id == selectedCategoryId,
                            cardStyle = cardStyle,
                            isFavorite = category.id in favoriteCategoryIds,
                            onClick = { onCategorySelected(category.id) },
                            onOpenActions = { actionsCategory = category },
                            cardModifier = Modifier.paneItem(paneFocus, category.id),
                            modifier =
                                // See StreamList: remember-scoped so recomposition of a visible row
                                // doesn't drop the modifier and cancel the animation mid-flight.
                                if (remember(category.id) { enteredCategoryIds.add(category.id) }) {
                                    Modifier.staggeredEntrance(index)
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Row card styling, built once per list composition instead of once per row.
 *
 * `CardDefaults.*` are `@Composable` and so cannot be wrapped in `remember`; hoisting the calls out
 * of the item body is what stops a full `CardColors`/`CardScale`/`CardGlow`/`CardShape` set being
 * allocated per visible row per recomposition. Same pattern, and same reason, as `StreamList`'s
 * `StreamCardStyle`. The selected category keeps these colours and gets the P5 "current" bar and
 * title colour instead of a fill of its own, which looked like focus (F-C-8).
 */
@Immutable
private data class CategoryCardStyle(
    val colors: CardColors,
    val cardScale: CardScale,
    val glow: CardGlow,
    val shape: CardShape,
    val titleMedium: TextStyle,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun categoryCardStyle(
    scale: Float,
    typography: androidx.tv.material3.Typography = MaterialTheme.typography,
): CategoryCardStyle {
    val scaledTitleMedium =
        remember(scale, typography) {
            typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
        }
    return CategoryCardStyle(
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        cardScale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium.scaled(scale))),
        titleMedium = scaledTitleMedium,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryItem(
    category: MediaCategory,
    isSelected: Boolean,
    cardStyle: CategoryCardStyle,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    /** Long-press OK or the Menu key: open the row's action menu (P3). Null (virtual categories) means no menu. */
    onOpenActions: (() -> Unit)? = null,
    /** Applied to the card itself (the focusable), not the row. */
    cardModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val scaledTitleMedium = cardStyle.titleMedium

    // Marquee only while focused — same reasoning as StreamItem: BounceMarqueeNode runs a
    // withFrameNanos loop that invalidates draw every frame for as long as its text overflows, and
    // IPTV category names overflow constantly, so unconditionally every visible row kept its own
    // loop running. At rest the two look identical: fraction is 0, so the node draws the same
    // clipped text a plain Text does.
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        onLongClick = onOpenActions,
        modifier =
            modifier
                .padding(horizontal = Spacing.md.scaled(scale))
                .fillMaxWidth()
                .then(cardModifier)
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { event ->
                    if (onOpenActions == null || event.type != KeyEventType.KeyDown || event.key != Key.Menu) return@onKeyEvent false
                    onOpenActions()
                    true
                },
        colors = cardStyle.colors,
        shape = cardStyle.shape,
        scale = cardStyle.cardScale,
        glow = cardStyle.glow,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .currentIndicator(isSelected)
                    .padding(Spacing.md.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
        ) {
            if (isFavorite) {
                Text(
                    text = "\u2605",
                    style = scaledTitleMedium,
                    color = CinemaAccent,
                )
            }
            Text(
                text = category.name,
                style = scaledTitleMedium,
                color = if (isSelected) TvFocusTokens.currentText else CinemaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // weight(1f) keeps the hint below at the row's end.
                modifier = Modifier.weight(1f).then(if (isFocused) Modifier.bounceMarquee() else Modifier),
            )
            // See StreamItem: a glyph, not a focus stop, on the focused row only.
            if (isFocused && onOpenActions != null) RowActionsHint()
        }
    }
}
