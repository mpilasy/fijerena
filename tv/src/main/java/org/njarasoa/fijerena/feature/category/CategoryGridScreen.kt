@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.feature.common.StatsOverlay

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
    contentType: String,
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String) -> Unit,
    onSearchClick: () -> Unit = {},
    onEpgClick: (categoryId: String, categoryName: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: CategoryViewModel = viewModel(
        factory = CategoryViewModelFactory(
            context = LocalContext.current.applicationContext,
            contentType = contentType
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val isDevMode by remember { mutableStateOf(appSettings.isDevMode) }

    // 5% padding for TV overscan safety
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
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
                    categoriesRefreshing = state.categoriesRefreshing,
                    lastPlayedStreamId = state.lastPlayedStreamId,
                    contentType = contentType,
                    onCategorySelected = { categoryId ->
                        viewModel.loadStreams(categoryId)
                    },
                    onStreamSelected = { streamId, streamName, categoryId ->
                        onStreamSelected(streamId, streamName, categoryId)
                    },
                    onRefreshCategories = {
                        viewModel.refreshCategories()
                    },
                    onRefreshStreams = { categoryId ->
                        viewModel.refreshStreams(categoryId)
                    },
                    onSearchClick = onSearchClick,
                    onEpgClick = onEpgClick,
                    onBack = onBack
                )
            }
                is CategoryViewModel.UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.retry() }
                    )
                }
            }
        }

        // Stats overlay (only visible in dev mode)
        when (val state = uiState) {
            is CategoryViewModel.UiState.Success -> {
                val stats = buildMap {
                    state.categoriesPayloadSize?.let { put("Categories", it) }
                    state.streamsPayloadSize?.let { put("Streams", it) }
                    viewModel.getCategoriesFetchTime()?.let { put("Cat. Time", it) }
                    state.selectedCategoryId?.let {
                        viewModel.getFetchTime(it)?.let { time -> put("Stream Time", time) }
                    }
                    put("Categories", "${state.categories.size}")
                    state.streams?.let { put("Streams", "${it.size}") }
                }

                StatsOverlay(
                    visible = isDevMode,
                    stats = stats,
                    interactive = false  // Non-interactive on category screen
                )
            }
            else -> { /* No stats in loading/error states */ }
        }
    }
}

@Composable
private fun TwoColumnLayout(
    categories: List<XtreamCategory>,
    selectedCategoryId: String?,
    streams: List<XtreamStream>?,
    streamsLoading: Boolean,
    categoriesRefreshing: Boolean,
    lastPlayedStreamId: Int?,
    contentType: String,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onSearchClick: () -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val repository = remember {
        XtreamRepository(AccountManager(context.applicationContext), context.applicationContext)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onBack) {
                    Text("← Back")
                }
                Button(
                    onClick = onSearchClick,
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search")
                }
                // EPG button - only for Live TV
                if (contentType == "LIVE_TV" && selectedCategoryId != null) {
                    val selectedCategoryName = categories.find { it.categoryId == selectedCategoryId }?.categoryName
                    if (selectedCategoryName != null) {
                        Button(
                            onClick = { onEpgClick(selectedCategoryId, selectedCategoryName) },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "TV Guide",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TV Guide")
                        }
                    }
                }
                Column {
                    Text(
                        text = "IPTV.atr",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = contentType.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${categories.size} categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                text = providerName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
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
                categoriesRefreshing = categoriesRefreshing,
                onCategorySelected = onCategorySelected,
                onRefreshCategories = onRefreshCategories,
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
            )

            // Right column: Streams (70% width)
            StreamList(
                streams = streams,
                streamsLoading = streamsLoading,
                selectedCategoryId = selectedCategoryId,
                selectedCategoryName = categories.find { it.categoryId == selectedCategoryId }?.categoryName,
                lastPlayedStreamId = lastPlayedStreamId,
                contentType = contentType,
                repository = repository,
                onStreamSelected = onStreamSelected,
                onRefreshStreams = onRefreshStreams,
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
    categoriesRefreshing: Boolean,
    onCategorySelected: (String) -> Unit,
    onRefreshCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberTvLazyListState()
    val focusRequesters = remember(categories) {
        categories.associate { it.categoryId to FocusRequester() }
    }

    // Auto-scroll and focus on selected category
    LaunchedEffect(categories, selectedCategoryId) {
        if (categories.isNotEmpty() && selectedCategoryId != null) {
            val selectedIndex = categories.indexOfFirst { it.categoryId == selectedCategoryId }
            if (selectedIndex != -1) {
                listState.animateScrollToItem(selectedIndex)
                // Request focus on the selected category
                focusRequesters[selectedCategoryId]?.requestFocus()
            }
        }
    }

    // Animate rotation when refreshing
    var targetRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(categoriesRefreshing) {
        if (categoriesRefreshing) {
            while (categoriesRefreshing) {
                targetRotation += 360f
                kotlinx.coroutines.delay(600)
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "refresh_rotation"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onRefreshCategories,
                enabled = !categoriesRefreshing,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh categories",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
        }

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
                        onClick = { onCategorySelected(category.categoryId) },
                        focusRequester = focusRequesters[category.categoryId]
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
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
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
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .scale(scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
                    isFocused -> Color.White
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
    selectedCategoryId: String?,
    selectedCategoryName: String?,
    lastPlayedStreamId: Int?,
    contentType: String,
    repository: XtreamRepository,
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String) -> Unit,
    onRefreshStreams: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate rotation when refreshing
    var targetRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(streamsLoading) {
        if (streamsLoading) {
            while (streamsLoading) {
                targetRotation += 360f
                kotlinx.coroutines.delay(600)
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "refresh_rotation"
    )
    val listState = rememberTvLazyListState()
    val focusRequesters = remember(streams) {
        streams?.associate { it.streamId to FocusRequester() } ?: emptyMap()
    }

    // Auto-scroll and focus on last played stream
    LaunchedEffect(streams, lastPlayedStreamId) {
        if (!streams.isNullOrEmpty() && lastPlayedStreamId != null) {
            val lastPlayedIndex = streams.indexOfFirst { it.streamId == lastPlayedStreamId }
            if (lastPlayedIndex != -1) {
                listState.animateScrollToItem(lastPlayedIndex)
                // Request focus on the last played stream
                focusRequesters[lastPlayedStreamId]?.requestFocus()
            }
        }
    }

    Column(modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = selectedCategoryName ?: "Select a category",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Always show refresh button when a category is selected
                selectedCategoryId?.let { categoryId ->
                    IconButton(
                        onClick = { onRefreshStreams(categoryId) },
                        enabled = !streamsLoading,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh streams",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotation)
                        )
                    }
                }
            }
            // Show stream count
            if (streams != null) {
                Text(
                    text = "${streams.size} streams",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

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
                            // Get watch progress for this stream
                            val watchedStream = repository.getPlaybackPosition(stream.streamId, contentType)
                            val progress = watchedStream?.let {
                                if (it.duration > 0) {
                                    (it.playbackPosition.toFloat() / it.duration.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                            } ?: 0f

                            StreamItem(
                                stream = stream,
                                isFavorite = repository.isFavorite(stream.streamId, contentType),
                                watchProgress = progress,
                                onClick = { onStreamSelected(stream.streamId, stream.name, stream.categoryId) },
                                focusRequester = focusRequesters[stream.streamId]
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
    isFavorite: Boolean = false,
    watchProgress: Float = 0f,  // 0.0 to 1.0 (0% to 100%)
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
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
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .scale(scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Column {  // Wrap in Column for progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorite star indicator
                        if (isFavorite) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFD700)  // Gold
                            )
                        }

                        Text(
                            text = stream.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    stream.streamIcon?.let { icon ->
                        if (icon.isNotBlank()) {
                            Text(
                                text = "HD",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Progress bar (only show if > 0%)
            if (watchProgress > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
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
    onRetry: () -> Unit
) {
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
                text = "Error",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.error
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Retry")
            }
        }
    }
}
