# Plan: UI Responsiveness - Open Performance Findings

## Status
Steps 1–8 of the UI Responsiveness Audit are fixed and landed in `main` (§A1, B1, B2, B4, C2-C4, D1-D3, E1-E2).

The open performance findings below remain for future optimization.

---

## Open Items & Findings

### 1. De-gate First-Frame Playback on EPG / Watch-History (§A2)
- **Issue:** Video initialization currently waits for metadata/EPG/watch-history lookups before starting playback.
- **Optimization:** Initiate stream decoding immediately using the stream URI. Defer EPG/history resolution so they populate asynchronously after the decoder begins playback.

### 2. Double Image-Request on Thumbnail First Appearance (§D4)
- **File:** `core/ui/.../components/CinemaThumbnail.kt:85-95`
- **Issue:** `ImageRequest` applies `size(...)` only once `measuredSize` is non-zero. On initial composition, `measuredSize` is zero, triggering an initial full-resolution image fetch followed by a second sized fetch after layout measurement.
- **Optimization:** Provide explicit placeholder dimensions or default sizing constraints to Coil before initial layout measurement.

### 3. Mobile Category List Favorite Lookup Hoisting (§E2 follow-up)
- **File:** `mobile/.../feature/category/MobileCategoryListScreen.kt:1026`
- **Issue:** `categoryViewModel.isFavoriteCategory(...)` is evaluated inside the `LazyRow` item lambda per category on every recomposition.
- **Optimization:** Pass a pre-computed `Set<String>` of favorite category IDs (matching TV's `CategoryList.kt:82` optimization pattern).
