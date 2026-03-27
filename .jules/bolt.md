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

## 2026-03-27 - EpgChannelMatcher caching
**Learning:** `EpgBrowserViewModel` processes EPG airings using `applyChannelMatching` to cross-reference each program against `XtreamStreamEntity` instances (using `EpgChannelMatcher`). During the search grouping, each airing may trigger a match. For programs that span several days, or common channels, `EpgChannelMatcher.match()` is called thousands of times with the same channel/name pairs. The final level of fallback inside `EpgChannelMatcher.match()` does an expensive `O(N)` loop (with `contains` string checks) over the normalized streams arrays (which can contain tens of thousands of streams). Without caching, this causes huge performance penalties, locking up search results and slowing down rendering.
**Action:** Adding a simple concurrent hash map cache in `EpgChannelMatcher` (with cache key composed of `$channelId\u0000$channelName`) drops repeated match times down significantly and avoids hitting the heavy fallback loop repeatedly.
