# Navigation Guide - Type-Safe Navigation

Complete guide for the type-safe navigation system using kotlinx.serialization.

## Module Structure

```
:core:navigation    → Screen sealed interface definitions
:core:ui            → ProviderViewModel, shared ViewModels
:core:network       → Room database, ProviderRepository, provider implementations
:mobile             → MobileNavHost (Material3 transitions)
:tv                 → TvNavHost (TV Material3, D-pad focus)
```

## Screen Definitions

Located in `:core:navigation/Screen.kt`

```kotlin
sealed interface Screen {
    @Serializable data object ProviderSelection : Screen
    @Serializable data class AddProvider(val editId: Long = -1L) : Screen
    @Serializable data object Login : Screen  // Legacy, not in nav graph
    @Serializable data object ContentTypeSelection : Screen
    @Serializable data object EditProvider : Screen  // Legacy
    @Serializable data object Settings : Screen
    @Serializable data class CategoryList(
        val contentType: String, val initialCategoryId: String? = null,
        val initialStreamId: String? = null, val showPreviewPane: Boolean = true
    ) : Screen
    @Serializable data class EpisodeSelection(
        val seriesId: String, val seriesName: String, val categoryId: String,
        val initialEpisodeId: String? = null
    ) : Screen
    @Serializable data class MovieDetails(val movieId: String, val movieName: String, val categoryId: String) : Screen
    @Serializable data class Search(val contentType: String, val initialQuery: String? = null) : Screen
    @Serializable data class EpgGuide(val categoryId: String, val categoryName: String) : Screen
    @Serializable data object EpgBrowser : Screen
    @Serializable data class EpgManagement(val providerId: Long) : Screen
    @Serializable data object CellularBufferSettings : Screen  // Dev mode only
    @Serializable data class Player(
        val streamId: String, val streamName: String, val categoryId: String, val contentType: String,
        val episodeId: String? = null, val episodeExtension: String? = null,
        val seriesId: String? = null, val seriesName: String? = null,
        val startFromBeginning: Boolean = false
    ) : Screen
}
```

**Note:** All IDs are `String` (not `Int`) for compatibility across Xtream (numeric), Jellyfin (UUID), SMB, and Local providers.

### Usage

```kotlin
// Navigate to content type selection
navController.navigate(Screen.ContentTypeSelection)

// Navigate to category list
navController.navigate(Screen.CategoryList(contentType = "LIVE_TV"))

// Navigate to player with parameters
navController.navigate(Screen.Player(streamId = "12345", streamName = "CNN", categoryId = "1", contentType = "LIVE_TV"))

// Navigate to provider management
navController.navigate(Screen.ProviderSelection)
navController.navigate(Screen.AddProvider(editId = 5L))  // Edit provider with ID 5
```

## Navigation Flow

```
App Startup
├─ No provider configured → Settings
└─ Provider configured → last content type → CategoryList (or ContentTypeSelection)

ContentTypeSelection
├─→ EpgBrowser (calendar/date range icon, visible when EPG indexed)
├─→ Search("ALL") [Global Search]
│     ├─→ Player (if result is LIVE_TV)
│     ├─→ MovieDetails → Player (if result is MOVIES)
│     └─→ EpisodeSelection → Player (if result is TV_SHOWS)
├─→ CategoryList(LIVE_TV)  [TV: pushed twice — see Live TV Preview / Dock below]
│     ├─→ Player (direct)
│     ├─→ Search(LIVE_TV)
│     └─→ EpgGuide (TV Guide)
├─→ CategoryList(MOVIES)
│     ├─→ MovieDetails → Player
│     └─→ Search(MOVIES)
├─→ CategoryList(TV_SHOWS)
│     ├─→ EpisodeSelection → Player
│     └─→ Search(TV_SHOWS)
└─→ Settings
      ├─→ ProviderSelection
      │     ├─→ AddProvider (new)
      │     └─→ AddProvider(editId) (edit)
      ├─→ EpgManagement(providerId)
      └─→ CellularBufferSettings (dev mode only)
```

### Global Search Routing

Global search (`Screen.Search("ALL")`) is accessible from the Content Type Selection screen. Because results can come from different repositories, navigation from a search result is dynamically routed based on the result's `contentType`:

- **Live TV**: Routes directly to `Screen.Player`.
- **Movies**: Routes to `Screen.MovieDetails`.
- **TV Shows**: Routes to `Screen.EpisodeSelection`.

The `SearchViewModel` manages categories and individual stream results across all supported content types.

### Navigation Rules

1. **Startup → ContentTypeSelection**: Always lands on Content Type Selection if a provider is configured.
2. **Startup → Settings**: If no provider configured.
3. **ContentTypeSelection → CategoryList**: Standard push.
4. **CategoryList → Player**: Standard push (content-type aware routing).
5. **Settings → ProviderSelection → AddProvider**: Standard push chain.
6. **Provider switch**: Navigate to ContentTypeSelection, clearing back stack.
7. **Logout**: Clear auth session, navigate to Settings, clear back stack to ContentTypeSelection.

### Live TV Preview / Dock Back-Stack

Live TV always shows a channel playing alongside the browse list (see `docs/FEATURES.md`). Because the preview is entered differently on each platform, each has its own way of guaranteeing Back never skips straight past the browse screen and out of Live TV:

- **TV** (`TvNavHost.kt`, `ContentTypeSelection` handler): selecting Live TV pushes `CategoryList(showPreviewPane = false)` with `popUpTo(ContentTypeSelection)`, then immediately pushes a second `CategoryList(showPreviewPane = true)` on top. These are two real back-stack entries — Back from the preview pops to the bare (silent) entry underneath for free via normal nav semantics.
- **Mobile** (`MobileCategoryListScreen.kt`): there's only ever one `CategoryList` entry — the dock/preview is local composable state (`dockTarget`, `fullScreen`), not a navigation route, and it auto-seeds on entry so Live TV never shows a bare list first. Two `BackHandler`s provide the equivalent stopover: `fullScreen -> false` (full-screen collapses to dock), then `dockTarget -> null` (dock clears to bare list). Only a third Back (falling through to the `onBack` callback) actually leaves the screen.

When touching either flow, preserve the "Back always has a real stopover before exiting" property — it's the reason both look more convoluted than a single `navigate()` call.

### TV Back on Detail Screens: intercept at `onPreviewKeyEvent`

On TV, `BackHandler` alone is **not** enough on a screen where a `Button`/`Surface` holds focus. Confirmed on a real Shield: the first Back press reaches Compose's key dispatch, but something downstream marks it handled before it reaches the `BackHandler` / `OnBackPressedDispatcher` bridge — so the press only clears focus, the D-pad goes dead, and it takes a second press to actually navigate. (`androidx.tv:tv-material`'s `Surface` is not the culprit — it only intercepts `DPAD_CENTER`/`ENTER`.)

`:tv`'s `MovieDetailsScreen` and `EpisodeSelectionScreen` therefore intercept Back in `onPreviewKeyEvent` on their root `LazyColumn`. Preview dispatch runs top-down, before any descendant sees the event, so it wins the race regardless of what swallows it further down — the same pattern `TvDpadEscape.kt` uses for the analogous Up/Down-in-a-text-field problem. The `BackHandler`s remain as an inert fallback.

Use this pattern for any new TV screen whose base state has focusable buttons and a Back action.

## AuthViewModel

Shared authentication state across Mobile and TV modules.

Located in `:core:data/AuthViewModel.kt`

```kotlin
val authViewModel: AuthViewModel = viewModel()
val authResponse by authViewModel.authResponse.collectAsState()

// Set session after authentication
authViewModel.setAuthSession(authResponse, serverUrl)

// Clear session on logout
authViewModel.clearAuthSession()
```

## Mobile Navigation

Located in `:mobile/navigation/MobileNavHost.kt`

### Features
- Standard Material3 components
- Slide + fade transitions
- Auto-session restore from stored credentials
- No login screen (Settings-based provider configuration)

### Startup Logic

On startup, the app checks for a configured provider via `ProviderRepository`. If a provider exists, it navigates to the Content Type Selection screen. If not, it navigates to Settings.

### Transitions
- **Enter**: Slide left + fade in
- **Exit**: Slide left + fade out
- **Pop Enter**: Slide right + fade in
- **Pop Exit**: Slide right + fade out

## TV Navigation

Located in `:tv/navigation/TvNavHost.kt`

### Features
- androidx.tv.material3 components
- D-pad focus management
- Auto-focus restoration
- TV-safe UI spacing
- Same startup logic as mobile (no login screen)

### D-Pad Focus Handling

Each screen should implement:
- `FocusRequester` for initial focus
- `Modifier.focusable()` on interactive elements
- `Modifier.focusRestorer()` for returning focus
- TV-safe padding for overscan (56dp horizontal, 32dp vertical)

## Adding New Screens

### 1. Define Screen in :core:navigation

```kotlin
@Serializable
data class NewScreen(val someParam: String) : Screen
```

### 2. Add Composable to Both NavHosts

```kotlin
// In MobileNavHost.kt and TvNavHost.kt
composable<Screen.NewScreen> { backStackEntry ->
    val screen = backStackEntry.toRoute<Screen.NewScreen>()
    NewScreenComposable(
        someParam = screen.someParam,
        onBack = { navController.navigateUp() }
    )
}
```

### 3. Navigate to New Screen

```kotlin
navController.navigate(Screen.NewScreen(someParam = "value"))
```

---

## Deployment & Build

For comprehensive instructions on building and deploying the app via ADB to emulators and physical devices, see [RUN_GUIDE.md](RUN_GUIDE.md).

