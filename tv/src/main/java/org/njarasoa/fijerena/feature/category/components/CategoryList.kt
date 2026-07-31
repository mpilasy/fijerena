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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

// Extracted as a top-level constant to avoid allocating a new Set on every recomposition
private val VIRTUAL_CATEGORY_IDS =
    setOf(
        CategoryViewModel.FAVORITES_CATEGORY_ID,
        CategoryViewModel.FAVORITE_CATEGORIES_ID,
        CategoryViewModel.LAST_WATCHED_CATEGORY_ID,
        CategoryViewModel.CONTINUE_WATCHING_CATEGORY_ID,
        CategoryViewModel.RECENTLY_VIEWED_CATEGORIES_ID,
    )

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
    onCategoryLongPress: (MediaCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Single-pass partition instead of two separate filter() calls
    val (virtualCategories, regularCategories) =
        remember(categories) {
            categories.partition { it.id in VIRTUAL_CATEGORY_IDS }
        }

    val listState = rememberTvLazyListState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    // Auto-scroll and focus on selected category
    LaunchedEffect(regularCategories, selectedCategoryId) {
        if (selectedCategoryId != null) {
            if (selectedCategoryId in VIRTUAL_CATEGORY_IDS) {
                // Focus virtual category in sidebar
                try {
                    focusRequesters.getOrPut(selectedCategoryId) { FocusRequester() }.requestFocus()
                } catch (_: IllegalStateException) {
                }
            } else if (regularCategories.isNotEmpty()) {
                val selectedIndex = regularCategories.indexOfFirst { it.id == selectedCategoryId }
                if (selectedIndex != -1) {
                    listState.animateScrollToItem(selectedIndex)
                    try {
                        focusRequesters.getOrPut(selectedCategoryId) { FocusRequester() }.requestFocus()
                    } catch (
                        _: IllegalStateException,
                    ) {
                    }
                }
            }
        }
    }

    // Animate rotation when refreshing
    var targetRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        snapshotFlow { categoriesRefreshing }.collect { refreshing ->
            if (refreshing) {
                while (true) {
                    targetRotation = (targetRotation + 360f) % 3600f
                    kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
                }
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledTitleLarge =
        remember(scale, typography) {
            typography.titleLarge.copy(fontSize = typography.titleLarge.fontSize.scaled(scale))
        }

    // Entrance animation plays once per item: LazyColumn recycles item composition off the ends
    // of the scroll buffer, and D-pad scrolling churns that buffer constantly, so without this
    // guard the fade/slide replays on every focus move instead of just on first appearance.
    val enteredCategoryIds = remember { mutableSetOf<String>() }

    val palette = LocalCinemaTheme.current
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
                text = "Categories",
                style = scaledTitleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CinemaIconButton(
                onClick = onRefreshCategories,
                enabled = !categoriesRefreshing,
                size = 40.dp,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh categories",
                        tint = CinemaTextPrimary,
                        modifier =
                            Modifier
                                .size(TvDimensions.iconMedium.scaled(scale))
                                .rotate(rotation),
                    )
                }
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        color = CinemaGlassBackground,
                        shape = RoundedCornerShape(CornerRadius.small),
                    ).border(
                        width = TvDimensions.borderDefault,
                        brush = borderBrush,
                        shape = RoundedCornerShape(CornerRadius.small),
                    ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sticky virtual categories section
                if (virtualCategories.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(Spacing.sm.scaled(scale)),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                    ) {
                        virtualCategories.forEach { category ->
                            CategoryItem(
                                category = category,
                                isSelected = category.id == selectedCategoryId,
                                isFavorite = false,
                                onClick = { onCategorySelected(category.id) },
                                onLongPress = {},
                                focusRequester = focusRequesters.getOrPut(category.id) { FocusRequester() },
                            )
                        }
                    }

                    // Divider between virtual and regular categories
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp.scaled(scale))
                                .padding(horizontal = Spacing.md.scaled(scale))
                                .background(CinemaAccent.copy(alpha = CinemaAlpha.tint)),
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                }

                // Scrollable regular categories section
                TvLazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(Spacing.sm.scaled(scale)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
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
                            isFavorite = category.id in favoriteCategoryIds,
                            onClick = { onCategorySelected(category.id) },
                            onLongPress = { onCategoryLongPress(category) },
                            focusRequester = focusRequesters.getOrPut(category.id) { FocusRequester() },
                            modifier =
                                if (enteredCategoryIds.add(category.id)) {
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryItem(
    category: MediaCategory,
    isSelected: Boolean,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledTitleMedium =
        remember(scale, typography) {
            typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
        }

    Card(
        onClick = onClick,
        modifier =
            modifier
                .padding(horizontal = Spacing.md.scaled(scale))
                .fillMaxWidth()
                .tvLongPress(onLongPress)
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                ),
        colors =
            CardDefaults.colors(
                containerColor =
                    if (isSelected) {
                        CinemaAccent.copy(alpha = CinemaAlpha.glassBorder)
                    } else {
                        CinemaSurface
                    },
                contentColor =
                    if (isSelected) {
                        CinemaAccent
                    } else {
                        CinemaTextPrimary
                    },
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium.scaled(scale))),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    androidx.tv.material3.Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
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
                color = CinemaTextPrimary,
                maxLines = 1,
                modifier = Modifier.bounceMarquee(),
            )
        }
    }
}
