# Performance Optimization TODO

## High Impact

### 1. Migrate remaining collectAsState() to collectAsStateWithLifecycle()
- **Scope:** 7 remaining call sites (player screens and AuthViewModel)
- **Files:** `TvPlayerScreen.kt`, `MobileStatsOverlay.kt`, `PlayerScreen.kt`, `MobilePlayerScreen.kt`, `ChapterSelectorDialog.kt`, `StatsOverlay.kt`, `AuthViewModel.kt`
- **Problem:** Flows keep collecting when app is backgrounded, wasting CPU/memory/battery.
- **Fix:** Replace `collectAsState()` with `collectAsStateWithLifecycle()` in these remaining files. Player screens may intentionally use `collectAsState()` to keep collecting during PiP — verify before changing.

### ~~2. Memoize typography.copy() in TvEpgManagementScreen~~ DONE

### ~~3. Eliminate O(n²) loops and duplicate DB queries in SettingsExportManager~~ MOSTLY DONE
- O(n²) `.find`/`.any` loops eliminated (1 harmless `.find` remains at line 468 for active export).
- `getAllProvidersList()` still called 6 times during import — could be consolidated.

### ~~4. Cache MasterKey and EncryptedSharedPreferences in ProviderRepository~~ DONE

### ~~5. Make XtreamStatsManager cache-clearing methods suspend~~ DONE

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
- **File:** `tv/.../ContentTypeSelectionScreen.kt:186,460`
- **Problem:** `Brush.verticalGradient()` allocated every recomposition.
- **Fix:** Extract as `private val` or wrap in `remember`.

### ~~9. Extract gradient listOf() in CategoryList item loop~~ DONE

### 10. Use tick value in ClockDisplay instead of bare Date()
- **Files:** `mobile/.../MobileControlsOverlay.kt:378`, `tv/.../PlayerControlsOverlay.kt:434`, `tv/.../PlayerScreen.kt:260`
- **Problem:** `TimeFormat.formatClockTime(Date())` allocates a new `Date` every tick. The `tick` value already holds `System.currentTimeMillis()`.
- **Fix:** `TimeFormat.formatClockTime(Date(tick))`

### 11. Hoist System.currentTimeMillis() in EPG management screens
- **Files:** `tv/.../TvEpgManagementScreen.kt:326,483`, `mobile/.../MobileEpgManagementScreen.kt:270,403`
- **Problem:** `System.currentTimeMillis()` called per-item per-recomposition for dot color logic.
- **Fix:** Hoist `val nowMs = remember { System.currentTimeMillis() }` to composable scope.

### ~~12. Use Pair instead of string key construction in SettingsExportManager~~ DONE

### 13. Hoist ButtonDefaults.colors() in player selector dialogs
- **Files:** `tv/.../AudioTrackSelectorDialog.kt:134`, `SubtitleSelectorDialog.kt:120,191`
- **Problem:** `ButtonDefaults.colors()` + `Color.copy()` allocated inside `forEachIndexed` loops.
- **Fix:** Hoist color configs above the loop with `remember`.

## Low Impact

### 14. Extract scale options list in UiScaleSettingsCard
- **File:** `tv/.../UiScaleSettingsCard.kt:49`
- **Problem:** `listOf(...).chunked(2)` recreated every recomposition.
- **Fix:** `private val SCALE_OPTIONS = listOf(...).chunked(2)` at file level.

### 15. Key nowEpoch remember in EpgBrowserScreen airing rows
- **Files:** `tv/.../EpgBrowserScreen.kt:521`
- **Problem:** `remember { System.currentTimeMillis() / 1000L }` with no keys — stale if app stays open across program boundaries.
- **Fix:** Key on a shared hoisted time state or `airing.startEpoch`.

### 16. Remove unnecessary .toList() in EpgIndexer batch insert
- **File:** `core/network/.../EpgIndexer.kt:143,155,168,190,193,297,308`
- **Problem:** `.toList()` copies batch before `insertAll()` inside a hot loop processing thousands of entries.
- **Fix:** Pass batch directly, clear after DAO call.

### 17. Hoist AppSettings outside while(true) loop in EpgFileManager
- **File:** `core/network/.../EpgFileManager.kt:829`
- **Problem:** `AppSettings(context)` instantiated every 4 hours inside loop.
- **Fix:** Create once before the loop.

### 18. Reduce lambda allocations in MobileEpgBrowserScreen items
- **File:** `mobile/.../MobileEpgBrowserScreen.kt:449-458`
- **Problem:** Lambda inside `Modifier.clickable {}` captures `airing`, `isOnAir`, `matched` — new allocation per recomposition.
- **Fix:** Wrap in `remember(airing, isOnAir, matched)`.
