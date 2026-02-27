# Performance TODO

Remaining optimizations (updated 2026-02-27).

## High Impact, Higher Effort

### 1. Isolate `clockTick` recomposition in player overlays
- **Files:** `tv/.../player/components/overlays/PlayerControlsOverlay.kt`, `mobile/.../player/components/MobileControlsOverlay.kt`
- **Problem:** `clockTick` changes every second, causing the entire 350-line overlay to recompose — seek controls, EPG info, icon row all rebuild needlessly.
- **Plan:** Extract `ClockDisplay(clockTick: Long)` into its own `@Composable` so only the clock text leaf recomposes each second.

### ~~2. Rewrite `tvFocusable`/`tvFocusableContent` as `Modifier.Node`~~ ✅
- Done: `TvFocusableNode` with `FocusEventModifierNode` + `LayoutModifierNode` + `DrawModifierNode`, coroutine-based scale animation via `withFrameNanos`.

## Medium Impact

### ~~3. Wrap `streams: List<MediaItem>` / `categories: List<MediaCategory>` in `@Immutable` wrappers~~ ✅
- Done: Added `ImmutableStringSet`, `ImmutableWatchProgress`, moved `ImmutableMediaList` to `ImmutableWrappers.kt`. Wrapped at all caller sites.

## Low Impact

### 4. Memoize remaining `TextStyle.copy(fontSize = ...)` calls in TV screens
- **Files:** `TvAddProviderScreen.kt` (1), `ProviderDialogs.kt` (7), `JellyfinForm.kt` (1)
- **Problem:** Inline `TextStyle.copy()` allocations per recomposition.
- **Note:** These are rarely-visited settings/dialog screens — very low user impact.
