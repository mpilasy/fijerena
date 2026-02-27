# Performance TODO

All optimizations completed (2026-02-27).

### ~~1. Isolate `clockTick` recomposition in player overlays~~ ✅
- Done: `ClockDisplay` is now self-ticking with its own `LaunchedEffect` timer. Removed `clockTick` parameter from both `PlayerControlsOverlay` and `MobileControlsOverlay`, and from `PlayerScreenState`. Only the clock leaf recomposes each second.

### ~~2. Rewrite `tvFocusable`/`tvFocusableContent` as `Modifier.Node`~~ ✅
- Done: `TvFocusableNode` with `FocusEventModifierNode` + `LayoutModifierNode` + `DrawModifierNode`, coroutine-based scale animation via `withFrameNanos`.

### ~~3. Wrap `streams: List<MediaItem>` / `categories: List<MediaCategory>` in `@Immutable` wrappers~~ ✅
- Done: Added `ImmutableStringSet`, `ImmutableWatchProgress`, moved `ImmutableMediaList` to `ImmutableWrappers.kt`. Wrapped at all caller sites.

### ~~4. Memoize remaining `TextStyle.copy(fontSize = ...)` calls in TV screens~~ ✅
- Done: Memoized in `TvAddProviderScreen.kt`, `ProviderDialogs.kt` (CategoryFilterDialog), and `JellyfinForm.kt` using `remember(scale, typography)`.
