# Performance Optimization TODO

## High Impact — DONE

### ~~1. Build watch history lookup map in MediaRepository~~
- **Status:** DONE — Added `watchHistoryLookup` map in both `MediaRepository` and `XtreamUserDataManager` for O(1) `getPlaybackPosition()`.

### ~~2. Move refreshPerItemData off main thread~~
- **Status:** DONE — Made `refreshPerItemData()` a `suspend fun` with `withContext(Dispatchers.Default)`.

### ~~3. Convert ChannelListOverlay to TvLazyColumn~~
- **Status:** DONE — Replaced `Column + verticalScroll` with `TvLazyColumn + itemsIndexed`. Single `FocusRequester` for first item only.

### ~~4. Memoize TextStyle.copy() in hot composables~~
- **Status:** DONE — Added `remember(typography)` / `remember(scale, typography)` in MobileControlsOverlay, MobileStatsOverlay, EpgGridLayout, CategoryList.

### ~~5. Fix contentHash self-referential hash bug~~
- **Status:** DONE — Added `computeContentHash()` to all 3 entity classes, excluding `contentHash` field from computation. Updated `XtreamContentManager` to use it.

## Medium Impact

### 6. Replace System.currentTimeMillis() in composition with derived state
- **Files:**
  - `tv/.../PlayerControlsOverlay.kt:257,306` — reads at 1Hz tick
  - `mobile/.../MobileControlsOverlay.kt:241,291` — reads at 2Hz tick
- **Fix:** Derive `nowEpoch` from the existing tick state that drives recomposition, or wrap in `remember { derivedStateOf { System.currentTimeMillis() / 1000 } }`.

### 7. Remember dev stats buildMap in CategoryGridScreen
- **File:** `tv/.../CategoryGridScreen.kt:198-227`
- **Problem:** `buildMap{}` + 2 ViewModel method calls rebuilt every recomposition.
- **Fix:** Wrap in `remember(state, contentType, epgIndexState) { buildMap { ... } }`.

### 8. Optimize EPG channel matching fallback
- **Files:**
  - `core/network/.../XmltvEpgService.kt:308-316`
  - `core/network/.../EpgChannelMatcher.kt:56-63`
- **Problem:** Level-6 fallback iterates all `normalizedEntries` per unmatched channel. 5000 entries × 100 items = 500K string `contains` ops.
- **Fix:** Pre-filter entries by length (needle must be ≤ haystack). Or build a token-based inverted index from normalized names for O(1) substring lookups.

### 9. Hoist FilterChipDefaults.filterChipColors() out of LazyRow items
- **File:** `mobile/.../MobileCategoryListScreen.kt:385-389,430-433`
- **Problem:** New `FilterChipColors` object per chip per recomposition.
- **Fix:** `val chipColors = remember { FilterChipDefaults.filterChipColors(...) }` at row level.

## Low Impact

### 10. Remember TextStyle in CinemaThumbnail TypographyFallback
- **File:** `core/ui/.../CinemaThumbnail.kt:169-175`
- **Fix:** `remember(palette) { TextStyle(color = palette.textPrimary, ...) }`

### 11. Extract CinemaTextSecondary.copy(alpha) as top-level val
- **File:** `tv/.../TwoColumnLayout.kt:173`
- **Fix:** `private val CinemaTextSecondaryHigh = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)` at file level.

### 12. Use epoch arithmetic instead of Date+SimpleDateFormat in groupByDate
- **File:** `core/ui/.../EpgBrowserViewModel.kt:291`
- **Fix:** Replace `dayFormat.format(Date(epoch * 1000L))` with `epoch / 86400` for day-key grouping. Format display strings separately.

### 13. Reduce lambda allocations in MobileEpgTimeline LazyColumn items
- **File:** `mobile/.../MobileEpgTimeline.kt:136-147`
- **Fix:** Use `remember(row.channel) { { program -> onProgramSelected(program, row.channel) } }` or accept the channel as a parameter to avoid captures.
