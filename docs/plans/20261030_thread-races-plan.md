# Thread races

**Status:** Done 2026-10-30, walked on both emulators.

## Goal

Remove the races between threads that an audit found on 2026-10-30, after the Chromecast crash in
`StreamCallTracker.cancelAll()` (a weak set copied while the collector cleared it). No other
collection of that kind exists. The audit found 13 races that cause a playback error or wrong data,
and one bug in the buffer byte caps.

## Findings and fixes

| # | Where | Race | Fix |
|---|-------|------|-----|
| 1 | `core/player/.../config/AdaptiveLoadControl.kt` `updateForNetwork`, `updateContentType` | Main sets the new delegate before the replay flag; the playback thread can read the new delegate before `onPrepared` reaches it → NullPointerException in Media3 → playback error | Main only builds the new delegate into a pending slot; the playback thread installs it and replays `onPrepared` and `onTracksSelected` on it before its first use |
| 2 | `AdaptiveLoadControl.getAllocator` | Not a race: the raw shared allocator bypasses `DefaultLoadControl`'s per-player count, so the byte caps never trip | Count the allocated segments in an allocator of our own and give the delegates a total that survives a swap |
| 3 | `SeriesDetailsViewModel`, `MovieDetailsViewModel` loads | A slow load finishes after a switch to another copy and publishes old data under the new copy | One load job, cancelled on each switch and refresh; the ids read into locals when the load starts |
| 4 | `StreamLoaderViewModel` first load | The first load is not in `loadJob`, so a preview load of another channel cannot cancel it | The first load is a `loadJob` too; a load waits for the repository instead of returning |
| 5 | `StreamLoaderViewModel.toggleFavorite`, details pages favourite and enrich writes | State read before a suspend call is written back after it, over a newer state | `_state.update {}` that changes only the same title's state |
| 6 | `RecentWrite` | `job` written from IO and Main without a lock: a cancel between the two writes is lost, so an abandoned channel goes into Recent | The methods synchronised |
| 7 | `ProviderRepository.getProviderSettings` | A slow read puts old settings back into the cache after an invalidation | A generation number per invalidation; a read stores only when it did not change |
| 8 | `MediaRepository` Recent Categories | Two quick opens read the same old list; the last commit drops the other entry | Read and write the list under one lock, from the memory copy |
| 9 | `PersonViewModel.loadRows` | `rowsJob` written from IO and Main | All calls on Main |
| 10 | `SyncManager.runPasses` | A request set between the last loop check and the clear of the running flag is lost | Check the request flag again after the clear |
| 11 | `SyncManager` reconnect | A reconnect checks the foreground before the app goes to the background, then opens a socket | Check the foreground inside the socket lock |
| 12 | `SmbMediaProvider` | `isConnected()` is true before the share scan ends, so a second load sees no items; two connects cancel each other | Connect and scan under one Mutex; connected only after the scan |
| 13 | `BaseM3uMediaProvider` | `trimMemory` on Main empties the lists between a reader's check and its read | One snapshot of the lists, read once |
| 14 | `TmdbChartPages.memory` | One HashMap written under six different mutexes | `ConcurrentHashMap` |

## Phases

The phases touch different files and run at the same time, each on its own branch; each merges as
one commit.

| Phase | Work | Status |
|-------|------|--------|
| A | Player: 1, 2, with unit tests | Done (493f780e6) |
| B | Stream loader and details pages: 3, 4, 5, 6 | Done (702ab537b, 8452f1391) |
| C | Data layer: 7, 8, 12, 13, 14 | Done (07cf66150, 9dd01c9ab) |
| D | Sync and person page: 9, 10, 11 | Done (f2322bbb5) |
| E | Full checks, emulator walks (zap, preview, details copy switch, favourites, sync), release notes | Done |

## Done notes (2026-10-30)

- **Byte caps now apply:** Wi-Fi VOD stops loading at the device-scaled cap (64–160 MB), live TV
  at 32 MB above its minimum buffer. Before, only the time targets limited the buffer.
- **Walk finding, fixed (8452f1391):** an abandoned loader (Live TV preview, a channel on another
  source) kept the channel of its cancelled load as "asked", so a return to it loaded nothing and
  the preview named one channel while another played. Seen once on the TV emulator; not
  reproduced in 12 later runs on one source.
- **Not tested on the emulators:** the switch to another copy on a details page (no title has two
  copies in the test sources); the unit tests cover it. The SMB fix has no unit test.
- **Clear Cache:** a cleared Recent Categories list no longer comes back from the prefs before the
  clear is written (9dd01c9ab).
