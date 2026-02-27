# Performance Optimization TODO

## High Impact

### 1. Build watch history lookup map in MediaRepository
- **File:** `core/network/.../MediaRepository.kt:714`
- **Problem:** `getPlaybackPosition()` does O(n) linear scan per call. `refreshPerItemData()` calls it per stream (hundreds), making it O(n×m).
- **Fix:** Add a cached `Map<Pair<String,String>, WatchedItem>` (keyed by `itemId+contentType`) alongside existing `cachedWatchHistory`. Invalidate on write. Return O(1) lookup from `getPlaybackPosition`. Same fix needed in `XtreamUserDataManager:280`.

### 2. Move refreshPerItemData off main thread
- **File:** `core/ui/.../CategoryViewModel.kt:374`
- **Problem:** `refreshPerItemData()` runs on main thread, acquires `synchronized` locks per item via `getPlaybackPosition`. Janks UI with 200+ channels.
- **Fix:** Wrap in `withContext(Dispatchers.Default)` or make it a `suspend fun`. Combine with fix #1 to reduce the work itself. Also consider using `distinctUntilChangedBy { it.streams }` on the collector (line 122) to avoid redundant wakeups.

### 3. Convert ChannelListOverlay to TvLazyColumn
- **File:** `tv/.../overlays/ChannelListOverlay.kt:96`
- **Problem:** Uses `Column + verticalScroll` for potentially 500+ channel items. All items composed and laid out at once.
- **Fix:** Replace with `TvLazyColumn`. Move `focusRequester` logic to use `item key` + `BringIntoViewRequester` or `requestFocus` on the initially-selected item only.

### 4. Memoize TextStyle.copy() in hot composables
- **Files:**
  - `mobile/.../MobileControlsOverlay.kt:250,255,307` — 3× identical `.copy(fontSize=11.sp)` at 2Hz
  - `mobile/.../MobileStatsOverlay.kt:263,278,283,298,303` — `.copy()` in StatRow/SectionHeader
  - `tv/.../EpgGridLayout.kt:487-490` — `.copy()` per time-slot item in TimeHeaderRow
  - `tv/.../CategoryList.kt:303-315` — `.copy()` per category item
  - `tv/.../CategoryList.kt:143-146` — `.copy()` in header
- **Fix:** `remember(typography) { typography.bodySmall.copy(fontSize = 11.sp) }` at the composable scope. For TV scaled styles, `remember(scale, typography)` like existing `ProgramCell`/`ChannelItem` patterns.

### 5. Fix contentHash self-referential hash bug
- **File:** `core/network/.../XtreamContentManager.kt:372,450,530`
- **Problem:** `base.hashCode()` includes the `contentHash` field (default 0). Stored entities have non-zero `contentHash`, so hash never matches on re-fetch → spurious DB re-inserts every sync.
- **Fix:** Compute hash manually excluding `contentHash` field, or use `copy(contentHash = 0).hashCode()` consistently... wait, that IS what it does since base has contentHash=0. Actually need to verify the comparison logic — the stored entity has contentHash=X, re-fetched has contentHash=0 before copy. Check if the DB upsert compares full entity including contentHash field. If so, the re-fetched entity (with new contentHash from hashCode) will always differ from stored because hashCode includes contentHash=0 in computation. Fix: exclude contentHash from the data class (use a separate column not in equals/hashCode) or compute hash from specific fields only.

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
