# Performance TODO

Remaining optimizations (updated 2026-02-27).

## High Impact, Higher Effort

### 1. Rewrite `bounceMarquee` as `Modifier.Node` (partially fixed)
- **Files:** `core/ui/.../components/BounceMarquee.kt`
- **Done:** Fixed conditional `rememberInfiniteTransition` composable call violation (now unconditional).
- **Remaining:** `composed {}` defeats Compose's stable-modifier optimizations. Full `Modifier.Node` rewrite would eliminate per-element composition overhead for 100-200+ marquee instances in live TV lists.

### 2. Isolate `clockTick` recomposition in player overlays
- **Files:** `tv/.../player/components/overlays/PlayerControlsOverlay.kt`, `mobile/.../player/components/MobileControlsOverlay.kt`
- **Problem:** `clockTick` changes every second, causing the entire 350-line overlay to recompose — seek controls, EPG info, icon row all rebuild needlessly.
- **Plan:** Extract `ClockDisplay(clockTick: Long)` into its own `@Composable` so only the clock text leaf recomposes each second.

### 3. Rewrite `tvFocusable`/`tvFocusableContent` as `Modifier.Node`
- **Files:** `tv/.../components/modifiers/FocusModifiers.kt:36-60, 103-124`
- **Problem:** `composed {}` creates per-element composition overhead. Every TV card, EPG cell, and focusable element pays this cost.
- **Plan:** Rewrite as `Modifier.Node` implementations for stable animation state.

## Medium Impact

### 4. Wrap `streams: List<MediaItem>` / `categories: List<MediaCategory>` in `@Immutable` wrappers
- **Files:** `TwoColumnLayout.kt`, `StreamList.kt`, `CategoryList.kt`, `MobileChannelListSheet.kt`
- **Problem:** `List` is unstable from Compose's perspective — causes unnecessary recompositions.
- **Plan:** Extend existing `ImmutableMediaList` / `ImmutableNowPlaying` pattern.

## Low Impact

### 5. Memoize remaining `TextStyle.copy(fontSize = ...)` calls in TV screens
- **Files:** `TvAddProviderScreen.kt` (1), `ProviderDialogs.kt` (7), `JellyfinForm.kt` (1)
- **Problem:** Inline `TextStyle.copy()` allocations per recomposition.
- **Note:** These are rarely-visited settings/dialog screens — very low user impact.
