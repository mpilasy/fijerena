# Performance Optimization TODO

## High Impact

### 1. Migrate collectAsState() to collectAsStateWithLifecycle()
- **Scope:** 60+ call sites across all screens
- **Problem:** Flows keep collecting when app is backgrounded, wasting CPU/memory/battery.
- **Fix:** Add `lifecycle-runtime-compose` dependency, replace `collectAsState()` with `collectAsStateWithLifecycle()` in all non-player screens.

### 2. Memoize typography.copy() in TvEpgManagementScreen
- **File:** `tv/.../TvEpgManagementScreen.kt` — 20+ sites
- **Problem:** Every `Text()` calls `.copy(fontSize = *.scaled(scale))` — 20+ TextStyle allocations per recomposition, including inside `items {}` blocks.
- **Fix:** Hoist `val scaledBodyLarge = remember(scale) { typography.bodyLarge.copy(...) }` etc. above the `items {}` block.

### 3. Eliminate O(n²) loops and duplicate DB queries in SettingsExportManager
- **File:** `core/network/.../SettingsExportManager.kt`
- **Problem:** `.find`/`.any` inside loops over provider/source lists (lines 327, 428, 491, 516, 559, 600). `getAllProvidersList()` called 4 separate times during import.
- **Fix:** Build lookup `Map`/`Set` before loops, fetch providers once and reuse.

### 4. Cache MasterKey and EncryptedSharedPreferences in ProviderRepository
- **File:** `core/network/.../ProviderRepository.kt:231-242`
- **Problem:** `MasterKey.Builder.build()` performs keystore I/O on every `getProviderPrefs()` call. `clearAllCacheForProvider`/`getCacheStatsForProvider` instantiate `AccountManager` + `XtreamRepository` (2 keystore ops each).
- **Fix:** Cache `MasterKey` as lazy singleton, cache `EncryptedSharedPreferences` in `ConcurrentHashMap<Long, SharedPreferences>`.

### 5. Make XtreamStatsManager cache-clearing methods suspend
- **File:** `core/network/.../XtreamStatsManager.kt:53-137`
- **Problem:** `clearCache()`, `clearCacheForContentType()` etc. execute 6+ synchronous Room DAO DELETE calls without `Dispatchers.IO`.
- **Fix:** Make these `suspend fun` with `withContext(Dispatchers.IO)`.

## Medium Impact

### 6. Memoize Color.copy() in GlassPanel
- **File:** `core/ui/.../GlassPanel.kt:37`
- **Problem:** `palette.glassBackground.copy(alpha = ...)` runs bare every recomposition. GlassPanel is the primary container for every item row in the TV layout.
- **Fix:** `val bg = remember(palette.glassBackground, backgroundAlpha) { ... }`

### 7. Fix virtual categories FilterChipColors not using hoisted chipColors
- **File:** `mobile/.../MobileCategoryListScreen.kt:389`
- **Problem:** Regular categories row uses hoisted `chipColors`, but virtual categories row still creates fresh `FilterChipDefaults.filterChipColors(...)` per chip.
- **Fix:** Replace with existing `chipColors` val (1-line change).

### 8. Hoist gradient Brush + Color.copy() in ContentTypeSelectionScreen
- **File:** `tv/.../ContentTypeSelectionScreen.kt:186-192`
- **Problem:** `listOf()` + `Brush.verticalGradient()` + `Color.copy()` allocated every recomposition.
- **Fix:** Extract as `private val` or wrap in `remember`.

### 9. Extract gradient listOf() in CategoryList item loop
- **File:** `tv/.../CategoryList.kt:175-181`
- **Problem:** `listOf(CinemaGlassBorder, Color.White.copy(...), CinemaGlassBorder)` created per item per recomposition.
- **Fix:** Extract as top-level `private val`.

### 10. Use tick value in ClockDisplay instead of bare Date()
- **Files:** `mobile/.../MobileControlsOverlay.kt:378`, `tv/.../PlayerControlsOverlay.kt:434`, `tv/.../PlayerScreen.kt:260`
- **Problem:** `TimeFormat.formatClockTime(Date())` allocates a new `Date` every tick. The `tick` value already holds `System.currentTimeMillis()`.
- **Fix:** `TimeFormat.formatClockTime(Date(tick))`

### 11. Hoist System.currentTimeMillis() in EPG management screens
- **Files:** `tv/.../TvEpgManagementScreen.kt:298,464`, `mobile/.../MobileEpgManagementScreen.kt:227,342`
- **Problem:** `System.currentTimeMillis()` called per-item per-recomposition for dot color logic.
- **Fix:** Hoist `val nowMs = remember { System.currentTimeMillis() }` to composable scope.

### 12. Use Pair instead of string key construction in SettingsExportManager
- **File:** `core/network/.../SettingsExportManager.kt:534/536, 576/578, 617/619`
- **Problem:** `"${itemId}::${contentType}"` string built once for the set, then again per item in `.filter`. Appears 3 times.
- **Fix:** Use `Set<Pair<String,String>>` instead of synthetic string keys.

### 13. Hoist ButtonDefaults.colors() in player selector dialogs
- **Files:** `tv/.../AudioTrackSelectorDialog.kt:135`, `SubtitleSelectorDialog.kt:121,192`, `QualitySelectorDialog.kt:121,199`, `ChapterSelectorDialog.kt:142`
- **Problem:** `ButtonDefaults.colors()` + `Color.copy()` allocated inside `forEachIndexed` loops.
- **Fix:** Hoist color configs above the loop with `remember`.

## Low Impact

### 14. Extract scale options list in UiScaleSettingsCard
- **File:** `tv/.../UiScaleSettingsCard.kt:49`
- **Problem:** `listOf(...).chunked(2)` recreated every recomposition.
- **Fix:** `private val SCALE_OPTIONS = listOf(...).chunked(2)` at file level.

### 15. Key nowEpoch remember in EpgBrowserScreen airing rows
- **Files:** `tv/.../EpgBrowserScreen.kt:535`, `mobile/.../MobileEpgBrowserScreen.kt:441`
- **Problem:** `remember { System.currentTimeMillis() / 1000L }` with no keys — stale if app stays open across program boundaries.
- **Fix:** Key on a shared hoisted time state or `airing.startEpoch`.

### 16. Remove unnecessary .toList() in EpgIndexer batch insert
- **File:** `core/network/.../EpgIndexer.kt:133,145,156,186,189`
- **Problem:** `.toList()` copies batch before `insertAll()` inside a hot loop processing thousands of entries.
- **Fix:** Pass batch directly, clear after DAO call.

### 17. Hoist AppSettings outside while(true) loop in EpgFileManager
- **File:** `core/network/.../EpgFileManager.kt:763`
- **Problem:** `AppSettings(context)` instantiated every 4 hours inside loop.
- **Fix:** Create once before the loop.

### 18. Reduce lambda allocations in MobileEpgBrowserScreen items
- **File:** `mobile/.../MobileEpgBrowserScreen.kt:449-458`
- **Problem:** Lambda inside `Modifier.clickable {}` captures `airing`, `isOnAir`, `matched` — new allocation per recomposition.
- **Fix:** Wrap in `remember(airing, isOnAir, matched)`.
