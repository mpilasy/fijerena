@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream

/**
 * TV two-column layout: Categories on left, Streams on right.
 *
 * Features:
 * - Left column: Vertical list of categories
 * - Right column: Vertical list of streams for selected category
 * - D-pad navigation between columns
 * - Focus management with visual indicators
 * - 5% padding for TV overscan safety
 * - Scroll state restoration
 */
@Composable
fun CategoryGridScreen(
    onStreamSelected: (Int) -> Unit,
    onLogout: () -> Unit,
    viewModel: CategoryViewModel = viewModel(
        factory = CategoryViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current

    // 5% padding for TV overscan safety
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = (configuration.screenWidthDp * 0.05).dp,
                vertical = (configuration.screenHeightDp * 0.05).dp
            )
    ) {
        when (val state = uiState) {
            is CategoryViewModel.UiState.Loading -> {
                LoadingScreen()
            }
            is CategoryViewModel.UiState.Success -> {
                TwoColumnLayout(
                    categories = state.categories,
                    selectedCategoryId = state.selectedCategoryId,
                    streams = state.streams,
                    streamsLoading = state.streamsLoading,
                    onCategorySelected = { categoryId ->
                        viewModel.loadStreams(categoryId)
                    },
                    onStreamSelected = { streamId ->
                        onStreamSelected(streamId)
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
private fun TwoColumnLayout(
    categories: List<XtreamCategory>,
    selectedCategoryId: String?,
    streams: List<XtreamStream>?,
    streamsLoading: Boolean,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (Int) -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IPTV.atr",
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

        // Two-column content
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left column: Categories (30% width)
            CategoryList(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = onCategorySelected,
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
            )

            // Right column: Streams (70% width)
            StreamList(
                streams = streams,
                streamsLoading = streamsLoading,
                selectedCategoryName = categories.find { it.categoryId == selectedCategoryId }?.categoryName,
                onStreamSelected = onStreamSelected,
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<XtreamCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberTvLazyListState()

    Column(modifier = modifier) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            TvLazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = categories,
                    key = { it.categoryId }
                ) { category ->
                    CategoryItem(
                        category = category,
                        isSelected = category.categoryId == selectedCategoryId,
                        onClick = { onCategorySelected(category.categoryId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: XtreamCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "category_scale"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .then(
                if (isFocused) {
                    Modifier.border(
                        BorderStroke(3.dp, Color(0xFF00FF00)),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary
        ),
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = category.categoryName,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    isFocused -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2
            )
        }
    }
}

@Composable
private fun StreamList(
    streams: List<XtreamStream>?,
    streamsLoading: Boolean,
    selectedCategoryName: String?,
    onStreamSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberTvLazyListState()

    Column(modifier = modifier) {
        Text(
            text = selectedCategoryName ?: "Select a category",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            when {
                streamsLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                streams.isNullOrEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (streams == null) {
                                "Select a category to view channels"
                            } else {
                                "No channels in this category"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    TvLazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = streams,
                            key = { it.streamId }
                        ) { stream ->
                            StreamItem(
                                stream = stream,
                                onClick = { onStreamSelected(stream.streamId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamItem(
    stream: XtreamStream,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "stream_scale"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .then(
                if (isFocused) {
                    Modifier.border(
                        BorderStroke(3.dp, Color(0xFF00FF00)),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.primary
        ),
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1
                )
                stream.streamIcon?.let { icon ->
                    if (icon.isNotBlank()) {
                        Text(
                            text = "HD",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFocused) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
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
                text = "Loading...",
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
