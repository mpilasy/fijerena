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
To resolve the logic duplication and blocking issues in the player, a new ViewModel should be introduced in `core:ui`.
- **Name:** `StreamLoaderViewModel` (or `PlayerFeatureViewModel`).
- **Responsibility:**
    - Asynchronously initialize `MediaRepository`.
    - Resolve playbable streams.
    - Manage the playlist/channel list state.
    - Handle `next/prev` logic.
    - Fetch and expose EPG data.
    - Handle history saving.
- **Benefit:** The UI (Mobile/TV) becomes a dumb view that observes `state` (Loading, Playing, Error) and passes events to the ViewModel.

### Dependency Injection (DI)
The project currently lacks a DI framework (Hilt/Koin). Introducing Hilt would:
- Eliminate manual repository creation in ViewModels/UI.
- Ensure repositories are singletons.
- Simplify testing by allowing mock injection.

**Recommendation:** Start by introducing a shared `Container` or `ViewModelFactory` that holds repository singletons, if full Hilt integration is too large a scope.

### Module Boundaries
- **Core:Player vs Core:Network:** The separation is good (`player` does not depend on `network`).
- **Core:UI:** Properly bridges the gap, depending on both. This is the correct place for the proposed `StreamLoaderViewModel`.

## 4. Immediate Action Plan
1.  **Extract Logic:** Create `StreamLoaderViewModel` in `core:ui` to consolidate player business logic.
2.  **Refactor UI:** Update `MobilePlayerScreen` and `TvPlayerScreen` to use this ViewModel, removing `runBlocking` and duplicated logic.
