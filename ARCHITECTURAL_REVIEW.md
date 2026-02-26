# Architectural Review: Fijerena

This document outlines key findings regarding code duplication, performance, and restructuring opportunities within the Fijerena project, focusing on the dual-platform (Mobile/TV) architecture.

## 1. Duplication Analysis

### Critical Logic Duplication in Player Screens
The most significant duplication exists between `MobilePlayerScreen.kt` and `TvPlayerScreen.kt`. Both screens implement identical complex business logic within the Composable layer:
- **Repository Initialization:** Both screens manually instantiate `MediaRepository` and `ProviderRepository` inside a `remember { runBlocking { ... } }` block. This logic is copy-pasted.
- **Stream Resolution:** The `resolvePlayableStream` call and error handling are identical.
- **Channel Navigation:** Logic for `nextChannel()` and `previousChannel()` (iterating through `streamList`) is duplicated.
- **EPG Fetching:** Logic to fetch and filter EPG programs based on the current time is duplicated.
- **Watch History:** Logic to save playback position and update "Last Watched" lists is duplicated.

**Impact:** Changes to core player logic (e.g., how streams are resolved or history saved) require updates in two places, increasing the risk of bugs and inconsistencies.

### UI Component Duplication
While necessary due to different interaction models (Touch vs D-pad), there is potential to share more "State Holders" for UI components.
- **Cards/Lists:** Both platforms have similar list logic (e.g., `LazyColumn` vs `TvLazyColumn`). The logic for data fetching (`CategoryViewModel`) is shared, which is excellent.

## 2. Performance Analysis

### UI Thread Blocking (Critical)
Both `MobilePlayerScreen` and `TvPlayerScreen` utilize `runBlocking` inside a `remember` block to initialize the `MediaRepository`:

```kotlin
val mediaRepository = remember {
    // ...
    kotlinx.coroutines.runBlocking {
        val entity = providerRepo.getActiveProvider()
        // ...
    }
}
```

**Impact:** This blocks the UI thread during composition, potentially causing dropped frames or ANRs (Application Not Responding) on slower devices, especially if database I/O is slow.

### Repository Recreation
The current pattern creates a new instance of `MediaRepository` every time the screen is composed (if keys change) or entered. This is inefficient as repositories should ideally be singletons or scoped to the ViewModel to leverage caching and connection pooling.

### Recomposition Risks
- **Stats Overlay:** The `LaunchedEffect` loop in `TvPlayerScreen` updates state every second. While `delay` is used, ensure that the state updates only trigger recomposition of the *stats overlay* and not the entire player hierarchy.
- **Image Loading:** Ensure `AsyncImage` (Coil) is used with stable keys to avoid reloading images during list scrolling.

## 3. Restructuring Opportunities

### Unified Feature ViewModel (`StreamLoaderViewModel`)
*Status: Implemented*
To resolve the logic duplication and blocking issues in the player, a unified ViewModel (`StreamLoaderViewModel`) was introduced in `core:ui`. It now handles:
- Asynchronously initializing `MediaRepository` via `AppContainer`.
- Resolving playable streams.
- Managing playlist/channel list state.
- Handling `next/prev` logic.
- Fetching and exposing EPG data.
- Handling history saving.
The UI (Mobile/TV) is now a dumb view that observes `state` (Loading, Playing, Error) and passes events to the ViewModel.

### Dependency Injection (DI)
*Status: Partially Implemented (Manual DI Container)*
The project now utilizes a manual DI container (`AppContainer`) in `core:ui` to manage repository singletons.
- This eliminates manual repository creation in ViewModels/UI.
- Ensures `ProviderRepository` and `MediaRepository` are singletons.
- Resolves the UI thread blocking issues caused by synchronous `runBlocking` repository initialization in factories.

### Module Boundaries
- **Core:Player vs Core:Network:** The separation is good (`player` does not depend on `network`).
- **Core:UI:** Properly bridges the gap, depending on both. This is the correct place for the shared ViewModels.

## 4. Completed Action Plan
1.  **Extract Logic:** Created `StreamLoaderViewModel`, `MovieDetailsViewModel`, and `SeriesDetailsViewModel` in `core:ui` to consolidate business logic.
2.  **Refactor UI:** Updated Mobile and TV screens to use these ViewModels, removing `runBlocking` and duplicated logic.
3.  **Implement DI:** Created `AppContainer` to manage repository singletons and updated all ViewModel factories (`CategoryViewModelFactory`, `SearchViewModelFactory`, etc.) to use it.
4.  **Fix Hanging Issues:** Made `MediaRepository` initialization asynchronous in ViewModels and marked the `provider` field as `@Volatile` to ensure thread safety.
