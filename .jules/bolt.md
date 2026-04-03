## 2025-03-06 - Initial setup
**Learning:** Checking for basic files
**Action:** Ready to optimize!

## 2025-03-06 - Hash function allocations
**Learning:** Room entity mapping from JSON objects to DB entities currently has `val entity = base.copy(contentHash = base.computeContentHash())`.
In Kotlin, `data class.copy()` allocates a new object. This means every single Xtream entity being processed during syncing (which batches by 2000 items and typically runs over tens of thousands of streams) allocates two objects instead of one.

**Action:** Refactor `XtreamContentManager` so that `computeContentHash` is calculated without allocating a `base` object first, or just create the entity with the hash inline instead of allocating and copying.

## 2025-03-13 - Avoid Object Copying in Room Batches
**Learning:** During large-scale local synchronization tasks (like parsing Xtream M3U streams, which can contain tens of thousands of items), accumulating entities in a `MutableList` and then passing `batch.toList()` to Room `insertAll` functions creates massive arrays to perform shallow copies. Because Android Room implementations execute the DB transaction synchronously within a suspend function, we can just pass the mutable `batch` list directly. The insertion completes before `batch.clear()` is called.
**Action:** Removed `.toList()` allocations from Room batch inserts. Going forward, avoid `.toList()` or other defensive copies unless strictly necessary (e.g., passing mutable state to true async jobs without suspend blocking).

## 2024-05-24 - Memoizing EpgChannelMatcher searches
**Learning:** Performing expensive fallback O(N) string matches for EPG airings in `EpgChannelMatcher.kt` repeatedly for the same `channelId` and `channelName` pairings leads to performance bottlenecks, particularly during search grouping in `EpgBrowserViewModel.applyChannelMatching`.
**Action:** Memoize the results using a `ConcurrentHashMap` with a composite key of `channelId` and `channelName` to prevent redundant evaluations of identical channel pairings.
