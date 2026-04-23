# TODO - Known Issues & Status

## No Active Blockers

The build environment is fully operational. All recent commits compile and deploy successfully.

---

## Open Investigation

### Cellular Streaming Buffering
CDN throttles to ~1.5 Mbps per connection on cellular; stream is 1080p single-quality at ~1.3 Mbps. iMPlayer plays smoothly with same bandwidth — root cause still unknown.

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

5. ~~**Hoist System.currentTimeMillis() in EPG management screens**~~ DONE
6. ~~**Reduce lambda allocations in MobileEpgBrowserScreen items**~~ DONE
7. ~~**Use tick value in ClockDisplay instead of bare Date()**~~ DONE
8. ~~**Hoist gradient Brush in ContentTypeSelectionScreen**~~ DONE
9. ~~**Hoist ButtonDefaults.colors() in player selector dialogs**~~ DONE

### P2: Low Impact & Maintenance
*Minor optimizations and code cleanup.*

10. ~~**Consolidate getAllProvidersList() in SettingsExportManager**~~ DONE
11. ~~**Fix virtual categories FilterChipColors not using hoisted chipColors**~~ DONE
12. ~~**Extract scale options list in UiScaleSettingsCard**~~ DONE
13. ~~**Hoist AppSettings outside while(true) loop in EpgFileManager**~~ DONE
14. ~~**Optimize audio processing and media source allocation**~~ DONE
