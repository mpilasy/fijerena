package org.njarasoa.fijerena.feature.lastwatched

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.WatchedItem
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileLastWatchedScreen(
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, contentType: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var historyItems by remember { mutableStateOf<List<WatchedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val providerRepo = ProviderRepository(context.applicationContext)
            val activeProvider = providerRepo.getActiveProvider()
            if (activeProvider != null) {
                val repository = MediaRepository(context.applicationContext, activeProvider.id)
                historyItems = repository.getWatchHistory()
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Last Watched") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (historyItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No watch history yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                modifier = Modifier.padding(CinemaSpacing.md)
            ) {
                items(historyItems) { item ->
                    ListItem(
                        headlineContent = { Text(item.itemName) },
                        supportingContent = { Text(item.contentType.replace("_", " ")) },
                        leadingContent = {
                            // Placeholder icon or thumbnail could go here
                            Icon(
                                imageVector = when (item.contentType) {
                                    "MOVIES" -> androidx.compose.material.icons.Icons.Default.Movie
                                    "TV_SHOWS" -> androidx.compose.material.icons.Icons.Default.Tv
                                    else -> androidx.compose.material.icons.Icons.Default.LiveTv
                                },
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            if (item.duration > 0 && !item.isCompleted) {
                                val progress = (item.playbackPosition.toFloat() / item.duration.toFloat())
                                CircularProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            onStreamSelected(item.itemId, item.itemName, item.categoryId, item.contentType)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
