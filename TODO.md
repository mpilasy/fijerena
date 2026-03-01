# TODO - Known Issues & Status

## No Active Blockers

The build environment is fully operational. All recent commits compile and deploy successfully.

---

## Open Investigation

### Cellular Streaming Buffering
CDN throttles to ~1.5 Mbps per connection on cellular; stream is 1080p single-quality at ~1.3 Mbps. iMPlayer plays smoothly with same bandwidth — root cause still unknown. See `docs/CELLULAR_STREAMING_INVESTIGATION.md` for details.

**Next step:** Packet capture (PCAPdroid) comparison between our app and iMPlayer.

---

## Testing Notes

Prefer real hardware for validation:
- **NVIDIA Shield**: Best HEVC/4K codec support, AV1 hardware decode
- **Chromecast with Google TV**: General Android TV compatibility
- **Sony Bravia**: TV-specific behaviour, reduced animations

---

## Performance Optimization TODO

### P0: High Impact & Hot Paths
*Crucial for battery life, memory usage, and UI fluidity in data-heavy screens.*

1. ~~**Remove unnecessary .toList() in EpgIndexer batch insert**~~ DONE
2. ~~**Memoize Color.copy() in GlassPanel**~~ DONE
3. ~~**Migrate remaining collectAsState() to collectAsStateWithLifecycle()**~~ DONE
4. ~~**Key nowEpoch remember in EpgBrowserScreen airing rows**~~ DONE

### P1: Medium Impact & UI Polish
*Focuses on reducing allocations in frequent UI updates.*

5. **Hoist System.currentTimeMillis() in EPG management screens**
   - **Files:** `tv/.../TvEpgManagementScreen.kt`, `mobile/.../MobileEpgManagementScreen.kt`
   - **Problem:** Called per-item per-recomposition for dot color logic.
   - **Fix:** Hoist to a single `val nowMs = remember { ... }` at the top of the composable.

6. **Reduce lambda allocations in MobileEpgBrowserScreen items**
   - **File:** `mobile/.../MobileEpgBrowserScreen.kt:449-458`
   - **Problem:** Clicking lambdas capture multiple changing variables, causing allocations during list scrolls.
   - **Fix:** Wrap clickable lambda in `remember`.

7. **Use tick value in ClockDisplay instead of bare Date()**
   - **Files:** `MobileControlsOverlay.kt`, `PlayerControlsOverlay.kt`, `PlayerScreen.kt`
   - **Problem:** New `Date` allocation every second.
   - **Fix:** Use `Date(tick)` as `tick` already contains the timestamp.

8. **Hoist gradient Brush in ContentTypeSelectionScreen**
   - **File:** `tv/.../ContentTypeSelectionScreen.kt:186,460`
   - **Problem:** `Brush.verticalGradient()` allocated every recomposition.

9. **Hoist ButtonDefaults.colors() in player selector dialogs**
   - **Files:** `AudioTrackSelectorDialog.kt`, `SubtitleSelectorDialog.kt`
   - **Problem:** Color configurations allocated inside `forEachIndexed` loops.

### P2: Low Impact & Maintenance
*Minor optimizations and code cleanup.*

10. **Consolidate getAllProvidersList() in SettingsExportManager**
    - **File:** `SettingsExportManager.kt`
    - **Problem:** Still called 6 times during an import process.

11. **Fix virtual categories FilterChipColors not using hoisted chipColors**
    - **File:** `mobile/.../MobileCategoryListScreen.kt:389`
    - **Fix:** Reuse existing `chipColors` variable.

12. **Extract scale options list in UiScaleSettingsCard**
    - **File:** `tv/.../UiScaleSettingsCard.kt:49`
    - **Fix:** Move to a file-level `private val`.

13. **Hoist AppSettings outside while(true) loop in EpgFileManager**
    - **File:** `core/network/.../EpgFileManager.kt:829`
    - **Fix:** Instantiate once before entering the loop.
