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
## 2025-04-27 - EpgChannelMatcher Memoization
**Learning:** EPG mapping requires O(N) string contains fallback on many channels. Caching query results dramatically reduces latency for overlapping strings.
**Action:** Memoize string queries using ConcurrentHashMap.
## 2025-05-15 - String Case Manipulations in Hot Paths
**Learning:** During in-memory client-side searching across thousands of cached items, creating wrapper objects and caching `.lowercase()` text results in excessive short-lived allocations which can impact the GC heavily.
**Action:** Replace `.lowercase()` conversions with `contains(..., ignoreCase = true)` or `startsWith(..., ignoreCase = true)`. These functions under the hood use zero-allocation comparisons (like `regionMatches` in Kotlin JVM).
## 2026-05-06 - Zero-allocation file extension checking
**Learning:** Using `substringAfterLast('.', "").lowercase()` and inline `setOf` creation inside hot paths (like scanning thousands of files or streams) allocates numerous Strings and Iterators. This strains the GC and degrades performance.
**Action:** Replaced String creation with `lastIndexOf('.')` paired with `regionMatches(..., ignoreCase = true)` against a pre-allocated `arrayOf` to perform case-insensitive extension checking without object allocations.
