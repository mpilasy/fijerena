@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.player.model.XtreamCategory

/**
 * TV-optimized category grid screen with D-pad navigation.
 *
 * Features:
 * - 4-5 column grid based on screen width
 * - Focus management with StandardCardContainer
 * - Scale animation (1.1x) on focus
 * - Glow border for focused items (4K TV optimized)
 * - Performance optimized with item keys
 * - 5% padding for TV overscan safety
 * - Scroll state restoration
 *
 * @param onCategorySelected Callback when category is selected
 * @param onLogout Callback for logout action
 */
@Composable
fun CategoryGridScreen(
    onCategorySelected: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: CategoryViewModel = viewModel(
        factory = CategoryViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // Determine column count based on screen width
    val columnCount = if (screenWidthDp >= 1920) 5 else 4

    // Scroll state with restoration
    val gridState = rememberTvLazyGridState()
    var lastScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastScrollOffset by rememberSaveable { mutableIntStateOf(0) }

    // Restore scroll position
    LaunchedEffect(uiState) {
        if (uiState is CategoryViewModel.UiState.Success) {
            gridState.scrollToItem(lastScrollIndex, lastScrollOffset)
        }
    }

    // Save scroll position
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        lastScrollIndex = gridState.firstVisibleItemIndex
        lastScrollOffset = gridState.firstVisibleItemScrollOffset
    }

    // 5% padding for TV overscan safety
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = (screenWidthDp * 0.05).dp,
                vertical = (configuration.screenHeightDp * 0.05).dp
            )
    ) {
        when (val state = uiState) {
            is CategoryViewModel.UiState.Loading -> {
                LoadingScreen()
            }
            is CategoryViewModel.UiState.Success -> {
                CategoryGrid(
                    categories = state.categories,
                    columnCount = columnCount,
                    gridState = gridState,
                    onCategorySelected = { categoryId ->
                        viewModel.onCategorySelected(categoryId)
                        onCategorySelected(categoryId)
                    },
                    onLogout = onLogout
                )
            }
            is CategoryViewModel.UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<XtreamCategory>,
    columnCount: Int,
    gridState: androidx.tv.foundation.lazy.grid.TvLazyGridState,
    onCategorySelected: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with title and logout button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Logout")
            }
        }

        // Category grid
        TvLazyVerticalGrid(
            columns = TvGridCells.Fixed(columnCount),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = categories,
                key = { it.categoryId }  // Performance optimization
            ) { category ->
                CategoryCard(
                    category = category,
                    onClick = { onCategorySelected(category.categoryId) }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: XtreamCategory,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Scale animation on focus (1.0 -> 1.1)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        label = "card_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .scale(scale)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                }
                .then(
                    if (isFocused) {
                        // High-contrast glow border for 4K visibility
                        Modifier.border(
                            BorderStroke(4.dp, Color(0xFF00FF00)), // Bright green glow
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f), // Disable default scale (we use our own)
            shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.categoryName,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // Category name below the card
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = category.categoryName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Loading Categories...",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    // Auto-logout if session expired
    val isSessionExpired = message.contains("Session expired", ignoreCase = true) ||
                          message.contains("login again", ignoreCase = true)

    LaunchedEffect(isSessionExpired) {
        if (isSessionExpired) {
            onLogout()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (isSessionExpired) "Session Expired" else "Error",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.error
            )

            Text(
                text = if (isSessionExpired) {
                    "Your session has expired. Redirecting to login..."
                } else {
                    message
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!isSessionExpired) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Retry")
                    }

                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Logout")
                    }
                }
            }
        }
    }
}
