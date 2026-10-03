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
    @Serializable data object ProfilePicker : Screen  // "Who's watching?"
    @Serializable data object ContentTypeSelection : Screen  // Home
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
    @Serializable data object SyncSettings : Screen  // Settings → Live sync
    @Serializable data object Diagnostics : Screen  // Dev mode: recorded crashes, process exits; also from SafeMode
    @Serializable data object SafeMode : Screen  // Start destination after a crash loop (SafeMode.isActive)
    @Serializable data object NewerData : Screen  // Start destination when providers.db is from a newer build (ProvidersDbGuard.isBlocked)
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
├─ providers.db written by a newer build → NewerData (Close / Reset sources; nothing else runs)
├─ Last 3 launches died within 30 s, inside 10 min → SafeMode (Continue / Clear caches / Show diagnostics)
├─ No provider configured → Settings
├─ Provider configured, TV with more than one profile → ProfilePicker → ContentTypeSelection
└─ Provider configured otherwise (mobile always) → ContentTypeSelection

ContentTypeSelection (Home; ProfilePicker also opens from the header avatar)
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
      ├─→ SyncSettings (Live sync)
      ├─→ CellularBufferSettings (dev mode only, mobile)
      └─→ Diagnostics (dev mode only)
```

### Global Search Routing

Global search (`Screen.Search("ALL")`) is accessible from the home screen (`ContentTypeSelection`). Because results can come from different repositories, navigation from a search result is dynamically routed based on the result's `contentType`:

- **Live TV**: Routes directly to `Screen.Player`.
- **Movies**: Routes to `Screen.MovieDetails`.
- **TV Shows**: Routes to `Screen.EpisodeSelection`.

The `SearchViewModel` manages categories and individual stream results across all supported content types.

### Navigation Rules

1. **Startup → ContentTypeSelection**: Lands on the home screen (`ContentTypeSelection`) if a provider is configured, on TV through `ProfilePicker` first when there is more than one profile (mobile goes straight home).
2. **Startup → Settings**: If no provider configured.
2a. **Startup → NewerData / SafeMode**: checked before the rules above, in that order. Both replace the start destination and skip the nav host's `initializeStartup()` (provider lookups, legacy-credential migration, profile count) — `NewerData` because nothing may open `providers.db`, `SafeMode` because that work may be what keeps crashing. Back on either leaves the app, as from Home; `SafeMode`'s Show diagnostics pushes `Diagnostics` and Back returns to it. Continue / Reset sources restart the process. See `docs/plans/20261002_next-level-rock-solid-resilience-plan.md` → R-10, R-01.
3. **ContentTypeSelection → CategoryList**: Standard push.
4. **CategoryList → Player**: Standard push (content-type aware routing).
5. **Settings → ProviderSelection → AddProvider**: Standard push chain.
6. **Provider switch**: Navigate to ContentTypeSelection, clearing back stack — `popUpTo(navController.graph.id) { inclusive = true }`, as a profile switch does, so no screen holding the previous provider's (closed) repository survives underneath. A profile switch can itself change provider: the device moves to the provider the new profile last picked, when it differs from the current one (`docs/plans/20261002_profile-last-provider-plan.md`).
7. **Logout**: Clear auth session, navigate to Settings, clear back stack to ContentTypeSelection.
8. **Remote Stop (Live sync)**: when another device of the sync group stops this device's playback, whichever playing screen is up — TV `TvPlayerScreen` or the `LiveTvSplitLayout` preview/promoted player, mobile `MobilePlayerScreen` or the Live TV dock (docked or promoted) — finalises the session as Back does, stops, and pops back to ContentTypeSelection (`popBackStack(Screen.ContentTypeSelection, inclusive = false)`, the screens' `onHome`), with a "Playback stopped from <device>" toast. See `RemoteStopEffect` and `docs/plans/20261001_live-sync-now-playing-plan.md` → Remote Stop.

### Live TV Preview / Dock Back-Stack

Live TV always shows a channel playing alongside the browse list (see `docs/FEATURES.md`). Because the preview is entered differently on each platform, each has its own way of guaranteeing Back never skips straight past the browse screen and out of Live TV:

- **TV** (`TvNavHost.kt`, `ContentTypeSelection` handler): selecting Live TV pushes `CategoryList(showPreviewPane = false)` with `popUpTo(ContentTypeSelection)`, then immediately pushes a second `CategoryList(showPreviewPane = true)` on top. These are two real back-stack entries — Back from the preview pops to the bare (silent) entry underneath for free via normal nav semantics.
- **Mobile** (`MobileCategoryListScreen.kt`): there's only ever one `CategoryList` entry — the dock/preview is local composable state (`dockTarget`, `fullScreen`), not a navigation route, and it auto-seeds on entry so Live TV never shows a bare list first. Two `BackHandler`s provide the equivalent stopover: `fullScreen -> false` (full-screen collapses to dock), then `dockTarget -> null` (dock clears to bare list). Only a third Back (falling through to the `onBack` callback) actually leaves the screen. The toolbar's Back, Search and TV Guide buttons leave without those handlers, so each stops the dock first (the engine is Activity-scoped and would keep playing behind the next screen).

When touching either flow, preserve the "Back always has a real stopover before exiting" property — it's the reason both look more convoluted than a single `navigate()` call.

### TV Back on Detail Screens: intercept at `onPreviewKeyEvent`

On TV, `BackHandler` alone is **not** enough on a screen where a `Button`/`Surface` holds focus. Confirmed on a real Shield: the first Back press reaches Compose's key dispatch, but something downstream marks it handled before it reaches the `BackHandler` / `OnBackPressedDispatcher` bridge — so the press only clears focus, the D-pad goes dead, and it takes a second press to actually navigate. (`androidx.tv:tv-material`'s `Surface` is not the culprit — it only intercepts `DPAD_CENTER`/`ENTER`.)

`:tv`'s `MovieDetailsScreen` and `EpisodeSelectionScreen` therefore intercept Back in `onPreviewKeyEvent` on their root `LazyColumn`. Preview dispatch runs top-down, before any descendant sees the event, so it wins the race regardless of what swallows it further down — the same pattern `TvDpadEscape.kt` uses for the analogous Up/Down-in-a-text-field problem. The `BackHandler`s remain as an inert fallback.

Use this pattern for any new TV screen whose base state has focusable buttons and a Back action — and for overlays/panels that *replace* that root (the episode detail panel in `EpisodeSelectionScreen` intercepts on the screen's root `Box` while it is open, since the `LazyColumn` with the interceptor isn't composed then).

The player's "Up next" card (Play next episode automatically) follows it too: while the card is up over the playing episode, `PlayerScreen`'s root `onPreviewKeyEvent` takes Back — it hides the card and playback carries on, without leaving the player. While focus is in the card its buttons get the D-pad and OK; when the card is up but not focused (OSD hidden), Down moves onto it; otherwise the player's own key handling runs as usual. The card takes focus on "Play now" when it appears. On mobile a `BackHandler` hides it.

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

On startup, the app checks for a configured provider via `ProviderRepository`. If a provider exists, it navigates to the home screen (`ContentTypeSelection`), on TV through `ProfilePicker` when there is more than one profile; mobile goes straight home. If not, it navigates to Settings.

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

Focus must land somewhere visible when a screen or panel appears, and return to where the user was:
- **Back from details** (movie/series) focuses the row that was opened — `StreamList` remembers it per category (`rememberSaveable`), falling back to the last played item; Live TV follows the playing channel.
- **Back to any other screen** focuses the control that navigated away, at the scroll position its list had — Home's hero cards, header buttons and Continue Watching cards, Settings' Manage / Live Sync / Diagnostics buttons, Search results, the category screen's Search and TV Guide buttons, a details screen's Start Over / Category button / related title, TV Guide programme, channel and search-result cells, EPG Browser airing rows, Manage Sources' Add / EPG / overflow buttons. Navigation Compose rebuilds a screen on Back with nothing focused, and Compose then focuses the first focusable (the "Switch Source" chip, the top Settings card). The pattern (`tv/ui/components/input/NavReturnFocus.kt`): `val returnFocus = rememberNavReturnFocus()` (saveable, so it survives the round trip); in the control's `onClick`, `returnFocus.leaveFrom(key, listState)` before navigating; `Modifier.navReturnFocusTarget(returnFocus, key)` on the control; `NavReturnFocusEffect(returnFocus, listState, fallback) { key -> /* wait for data, scroll an inner row */ }`. The effect runs when the screen is RESUMED again (after the pop transition and the screen's own first-open effects), restores the list position, then `requestFocusWithRetry` — once: the key is cleared whether or not it landed. A screen's own first-open focus checks `returnFocus.key == null` (or `isReturn`, when it can fire again after the hand-back) so it doesn't fight it. Back from the player still lands on Play / the resume card, which those screens already focus.
- **Closing the episode detail panel** focuses that episode's card (the tab row if Next/Previous crossed into another season).
- **TV Guide** opens on the current programme of the first channel that has programmes (separator rows without programmes are skipped), retrying for a few frames until the row is composed.
- **End of a row:** the last Continue Watching card cancels Right (`focusProperties { right = FocusRequester.Cancel }`) so focus doesn't escape to the top bar.
- Land focus from a `LaunchedEffect` with `requestFocusWithRetry` (`tv/ui/components/input/FocusRetry.kt`): it retries each frame (about 0.5 s) on the Boolean result of `requestFocus(FocusDirection.Enter)`, then an optional `fallback`. Never `try { requestFocus() } catch (IllegalStateException)` or `runCatching`: since Compose 1.10 an unattached target logs and returns `false` instead of throwing, so those catches retried nothing (R-05). Act on the result — e.g. `StreamList` marks a Back-restore handled only when it returned true. `scripts/check-focus-retry.sh` (CI) rejects the old pattern.
- **Error states** use `TvErrorState` (`tv/ui/components/TvErrorState.kt`): focus lands on Retry on entry, Back is taken in `onPreviewKeyEvent` when the screen has one, and `RetryWhenOnline` retries once when the network comes back (R-15).

### Panes (`tvPane`)

Two-column TV screens (Live TV browse, Movies, TV Shows: `TwoColumnLayout`) are built from two panes, `tv/ui/components/input/TvPane.kt` (UX overhaul plan, Part II P1/P2). Without them the columns were plain siblings and Compose's geometric search decided every move: Down at the end of the items jumped into a category, Left from an item landed on the category level with it, Left from a category landed on Search.

- `val pane = rememberPaneFocus()` per column in the screen; the list calls `pane.bind(selectedKey, firstKey, listState, indexOf)` every composition; each row's card takes `Modifier.paneItem(pane, key)`; the list's container takes `Modifier.tvPane(pane, exitLeft = …, exitRight = …)`.
- **Left/Right are taken by the pane, not by geometry:** they go to the neighbour pane named in `exitLeft` / `exitRight`, or nowhere. A row's own key handling (the hidden ★/✓ buttons) runs first and keeps its Left/Right inside the row. **Down at the end of a pane stays put; Up at the top leaves** to whatever is above — the Refresh icon and the Search / TV Guide header buttons stay reachable from the first row.
- **Entering a pane lands on its remembered row** (the last one focused — `rememberSaveable`, so it survives Back), else the selected row, else the first; a categories pane binds with `preferSelected = true`, so Left from an item always lands on the category being browsed. A new selection (the row opened, the channel playing) becomes the memory, which is how Back from details / the player lands on the right row. A remembered row that is scrolled out of composition is scrolled to, then focused with `requestFocusWithRetry`.
- Entry focus on open: the selected category while the items load, then the items' entry row once they are there (the last played / opened item when it is in the list, else the first); an empty category keeps the category. OK on a category keeps focus on the category; Right enters its items.
- Programmatic focus (`requestFocusWithRetry`, `NavReturnFocus`) passes through a pane untouched; only D-pad moves are redirected (`focusProperties { onEnter }` on the group, `onKeyEvent` for Left/Right, `onExit` + `cancelFocusChange()` for Down). `focusRestorer` is not used: it defines the same `onEnter` and the outer definition wins, and it cannot express the selected-row fallback.

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

