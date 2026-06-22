# XtreamApiService Usage Guide

> **Note (2026-03):** This document was written early in development. The package name
> `com.example.firstvideoplayer` no longer exists (now `org.njarasoa.fijerena`). The
> "Next Steps" at the bottom (caching, EPG, VOD/series) have all been implemented.
> Modern code should **never** instantiate `XtreamApiService` directly in ViewModels —
> use `AppContainer` to obtain `MediaRepository` / `ProviderRepository` singletons.
> The low-level API patterns below remain accurate for `XtreamApiService` itself.

## Overview

The `XtreamApiService` class provides a Ktor-based HTTP client for interacting with Xtream IPTV APIs. It uses the OkHttp engine for better stability on Android TV hardware.

## Features

- **OkHttp Engine**: Optimized for Android TV (NVIDIA Shield, Sony Bravia, Chromecast)
- **JSON Serialization**: Automatic parsing with kotlinx.serialization
- **Suspend Functions**: Coroutine-based for async operations
- **Error Tolerant**: Ignores unknown JSON fields, handles malformed data

## Basic Setup

```kotlin
val apiService = XtreamApiService(
    baseUrl = "http://example.com:8080",
    username = "your_username",
    password = "your_password"
)
```

## Usage Examples

### 1. Fetch All Categories

```kotlin
import kotlinx.coroutines.runBlocking

suspend fun loadCategories() {
    try {
        val categories = apiService.getCategories()

        categories.forEach { category ->
            println("${category.categoryId}: ${category.categoryName}")
        }
    } catch (e: Exception) {
        println("Failed to load categories: ${e.message}")
    }
}
```

### 2. Fetch Streams for a Category

```kotlin
suspend fun loadStreams(categoryId: String) {
    try {
        val streams = apiService.getStreams(categoryId)

        streams.forEach { stream ->
            println("${stream.streamId}: ${stream.name}")
            println("  Icon: ${stream.streamIcon}")
            println("  EPG: ${stream.epgChannelId}")
        }
    } catch (e: Exception) {
        println("Failed to load streams: ${e.message}")
    }
}
```

### 3. Build Stream URL for Playback

```kotlin
fun getPlayableUrl(streamId: Int): String {
    // Returns: http://example.com:8080/live/username/password/12345.ts
    return apiService.buildStreamUrl(streamId)
}
```

### 4. Complete Workflow Example

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChannelRepository {
    private val apiService = XtreamApiService(
        baseUrl = "http://example.com:8080",
        username = "user",
        password = "pass"
    )

    fun loadAllChannels(onResult: (List<XtreamStream>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Get all categories
                val categories = apiService.getCategories()

                // Step 2: Get streams for first category
                if (categories.isNotEmpty()) {
                    val streams = apiService.getStreams(categories[0].categoryId)

                    // Step 3: Pass streams to UI
                    onResult(streams)
                }
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
    }

    fun playStream(streamId: Int) {
        val streamUrl = apiService.buildStreamUrl(streamId)

        // Pass to StreamingPlaybackService
        val metadata = PlayerMetadata(
            streamUrl = streamUrl,
            isLive = true
        )

        // playbackViewModel.playStream(metadata)
    }

    fun cleanup() {
        apiService.close()
    }
}
```

## Integration with StreamingPlaybackService

```kotlin
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.player.model.PlayerMetadata

class ChannelPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = XtreamApiService(
        baseUrl = "http://iptv.example.com:8080",
        username = "testuser",
        password = "testpass"
    )

    private val playbackViewModel = PlaybackViewModel(application)

    suspend fun playChannel(streamId: Int, channelName: String) {
        val streamUrl = apiService.buildStreamUrl(streamId)

        val metadata = PlayerMetadata(
            title = channelName,
            streamUrl = streamUrl,
            isLive = true
        )

        playbackViewModel.playStream(metadata)
    }
}
```

## ViewModel Example with StateFlow

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChannelListViewModel : ViewModel() {
    private val apiService = XtreamApiService(
        baseUrl = "http://example.com:8080",
        username = "user",
        password = "pass"
    )

    private val _categories = MutableStateFlow<List<XtreamCategory>>(emptyList())
    val categories: StateFlow<List<XtreamCategory>> = _categories.asStateFlow()

    private val _streams = MutableStateFlow<List<XtreamStream>>(emptyList())
    val streams: StateFlow<List<XtreamStream>> = _streams.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _categories.value = apiService.getCategories()
            } catch (e: Exception) {
                // Handle error
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadStreams(categoryId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _streams.value = apiService.getStreams(categoryId)
            } catch (e: Exception) {
                // Handle error
            } finally {
                _loading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        apiService.close()
    }
}
```

## Compose UI Example

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChannelListScreen(viewModel: ChannelListViewModel = viewModel()) {
    val categories = viewModel.categories.collectAsState().value
    val loading = viewModel.loading.collectAsState().value

    if (loading) {
        CircularProgressIndicator()
    } else {
        LazyColumn {
            items(categories) { category ->
                Text(
                    text = category.categoryName,
                    modifier = Modifier
                        .clickable { viewModel.loadStreams(category.categoryId) }
                        .padding(16.dp)
                )
            }
        }
    }
}
```

## API Response Examples

### Categories Response

```json
[
  {
    "category_id": "1",
    "category_name": "News",
    "parent_id": 0
  },
  {
    "category_id": "2",
    "category_name": "Sports",
    "parent_id": 0
  }
]
```

### Streams Response

```json
[
  {
    "num": 1,
    "name": "CNN HD",
    "stream_type": "live",
    "stream_id": 12345,
    "stream_icon": "http://example.com/icon.png",
    "epg_channel_id": "cnn.us",
    "added": "1609459200",
    "category_id": "1",
    "tv_archive": 1,
    "tv_archive_duration": 7
  }
]
```

## Error Handling

```kotlin
suspend fun safeApiCall() {
    try {
        val categories = apiService.getCategories()
        // Success
    } catch (e: io.ktor.client.network.sockets.ConnectTimeoutException) {
        // Network timeout
        println("Connection timeout")
    } catch (e: io.ktor.client.plugins.ClientRequestException) {
        // 4xx error (e.g., 401 Unauthorized)
        println("Client error: ${e.response.status}")
    } catch (e: io.ktor.serialization.JsonConvertException) {
        // Invalid JSON response
        println("Invalid JSON")
    } catch (e: Exception) {
        // Other errors
        println("Unknown error: ${e.message}")
    }
}
```

## Testing

```kotlin
import kotlinx.coroutines.runBlocking
import org.junit.Test

class XtreamApiServiceTest {
    @Test
    fun testBuildStreamUrl() {
        val service = XtreamApiService(
            baseUrl = "http://example.com:8080",
            username = "user",
            password = "pass"
        )

        val url = service.buildStreamUrl(12345)

        assert(url == "http://example.com:8080/live/user/pass/12345.ts")
    }

    @Test
    fun testGetCategories() = runBlocking {
        val service = XtreamApiService(
            baseUrl = "http://demo.xtream.example.com",
            username = "demo",
            password = "demo"
        )

        val categories = service.getCategories()
        assert(categories.isNotEmpty())

        service.close()
    }
}
```

## Important Notes

1. **Lifecycle Management**: Always call `apiService.close()` when done (in `onCleared()` or activity/fragment cleanup)

2. **Thread Safety**: All suspend functions must be called from a coroutine scope

3. **Base URL Format**:
   - ✅ `http://example.com:8080`
   - ✅ `https://example.com`
   - ✅ `example.com:8080` (auto-prefixes http://)
   - ❌ `http://example.com:8080/` (trailing slash removed automatically)

4. **Android Manifest**: Ensure `INTERNET` permission is declared

5. **Cleartext HTTP**: If using `http://` URLs, ensure `android:usesCleartextTraffic="true"` is set in manifest (already configured)

## Next Steps

- Integrate with `PlaybackViewModel` for channel playback
- Add caching layer for categories/streams
- Implement EPG (Electronic Program Guide) fetching
- Add VOD (Video on Demand) and series support
