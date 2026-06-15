# Plan: Persistent EPG Channel Matcher Cache

**Status:** Deferred (Memory warming implemented instead).
**Goal:** Reduce the CPU and I/O cost of rebuilding the channel matching maps during an app cold start.

## 1. Problem Statement
On a cold start (app process restart), the `EpgBrowserViewModel` must fetch all live streams and build normalization maps for `EpgChannelMatcher`. This takes ~300-500ms on an NVIDIA Shield. While "Memory Warming" handles background syncs, a hard reboot still incurs this cost on the first search.

## 2. Proposed Architecture

### Database Schema
Add to `SettingsDatabase`:
- **Table:** `epg_matcher_cache`
- **Fields:**
    - `providerId` (PK, Long)
    - `cachedData` (Blob/JSON): Serialized mappings.
    - `versionTag` (String): Hash of the stream data to detect staleness.

### Serialization Logic
`EpgChannelMatcher` should be able to export its internal state:
- Map of `epgChannelId` -> `streamId`
- Map of `normalizedName` -> `streamId`
- Version/Timestamp.

### Caching Hierarchy (The "Fast Path")
`EpgChannelMatcher.getOrCreate()` would be updated to:
1. **L1 (Memory):** If `cachedInstance` exists, return it (0ms).
2. **L2 (Disk Cache):** Query `epg_matcher_cache`. If valid, deserialize and populate maps (~50ms).
3. **L3 (Full Rebuild):** Perform `getAllStreams` + `normalize` loop (>300ms).

### Background Persistence
Update `ProviderSyncManager`:
- After a background sync, build the matcher.
- Serialize the matcher and write it to `epg_matcher_cache`.
- This ensures the "L2" cache is ready even after a reboot.

## 3. Trade-offs
- **Pros:** Saves ~200-300ms on the very first search after a reboot.
- **Cons:** Marginal gains (search already has a loading state), increased DB complexity, serialization overhead during background sync.

## 4. Decision
Keep this plan as a reference. The current **Memory Warming** implementation (populating the cache immediately after sync) covers most user scenarios without adding database complexity.
