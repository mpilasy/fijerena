## 2024-05-20 - Optimize SearchUtils string manipulations in hot loops
**Learning:** Performing `String.substring` and `String.startsWith` inside hot loops for every item during text filtering causes extreme redundant object allocations and GC thrashing. Using `ParsedQuery` to categorize prefix and exact matches outside the loop dramatically reduces allocations in search.
**Action:** When filtering across thousands of elements (like EPG or search views), always extract string manipulation out of the iteration bounds and pre-parse criteria into lightweight lookup structures.
## 2026-05-28 - [Replace .flatten() with nested loops]
**Learning:** Found two places using chained `.flatten().map()`/`.flatten().mapNotNull()` which create unnecessary intermediate list allocations during heavy processing (e.g. collecting season/episode info).
**Action:** Replaced these chained operations with standard nested loops over the data structures, directly populating `mutableSetOf` or `mutableListOf` to avoid intermediate allocations and reduce GC overhead.
## 2026-05-30 - Replace runBlocking with Sync DAO calls
**Learning:** Found synchronous Room DB operations wrapped in `runBlocking` inside `getProviderSettingsSync` which instantiates coroutines overhead unnecessarily and blocks threads.
**Action:** Always prefer declaring explicit synchronous DAO queries over using `runBlocking { suspending_query() }` within non-suspending contexts where performance is critical.
## 2026-06-08 - [Performance Insight: Avoid Multiple Optimizations at Once & Incorrect Assumptions]
**Learning:** In a previous pass, attempting multiple small optimizations at once led to a regression. I incorrectly assumed runBlocking removal on Room queries was safe without considering main-thread exceptions, and falsely assumed array lookup length was faster than the JVM-optimized .length on String arrays. Also learned mapNotNullTo is inline and compiles exactly like an explicit loop, so rewriting it manually is useless noise.
**Action:** Stick to ONE single, highly measurable optimization (like removing .flatMap{} allocations in a hot render loop) as requested by the persona rules. Do not attempt unproven micro-optimizations.
## 2026-05-28 - [Memoize expensive EPG filtering in Compose]
**Learning:** The `MobileEpgBrowserScreen` and `EpgBrowserScreen` were executing a chained functional pipeline (`mapNotNull`, `filter`, and `.copy()`) over large `dateGroups` datasets directly inside the `@Composable` function body. Since `matchedOnly` is a derived state or parameter, this meant the entire heavy computation was blocking the UI thread on *every* recomposition (e.g., when scrolling or when minor UI state changes).
**Action:** Wrapped the entire `displayDateGroups` computation in a `remember(results.dateGroups, matchedOnly) { ... }` block to memoize the result, ensuring the expensive O(N) nested filtering is only evaluated when the underlying search results or filter toggle state actually changes.

## 2024-05-18 - Avoid dynamic evaluation in `sortedWith(compareBy { ... })` for expensive operations
**Learning:** In Kotlin, using `sortedWith(compareBy { ... })` dynamically evaluates the lambda expression for every comparison step (O(N log N)). When the lambda contains expensive operations, such as string matching or `ignoreCase` validation, the redundant evaluations cause substantial CPU overhead.
**Action:** For categorization-based sorting, implement an O(N) bucketing approach instead. Iterate through the collection exactly once to bucket items, then sort the individual buckets. This ensures expensive checks are evaluated only once per item.
## 2026-06-09 - [Avoid .flatMap with chunked database calls]
**Learning:** Using `.chunked(N).flatMap { dao.get...() }` followed by `.groupBy` or `.associateBy` creates multiple intermediate list allocations per chunk, a final flattened list, and Map.Entry allocations which is inefficient when querying massive EPG index datasets.
**Action:** Replace chunked `.flatMap` and subsequent grouping with standard `for` loops iterating over `.chunked(N)` results and directly populating a `mutableMapOf`.

## 2024-06-24 - [Bulk Fetch vs Short-Circuiting Trade-offs]
**Learning:** When trying to fix an N+1 query loop by switching to a bulk-fetch strategy (e.g. `getPlaybackPositionsSuspend`), I learned that the bulk strategy might actually introduce performance overhead if the original loop utilized short-circuit evaluation (`return@LaunchedEffect`). By pre-fetching a massive dataset (like an entire multi-season TV show's playback statuses) when the loop historically exited after the very first item, we trade network roundtrips for excessive memory allocation and database reads.
**Action:** Before converting a loop to a bulk query, explicitly check for `break` or `return` statements inside the loop. If they exist, evaluate the statistical likelihood of an early exit. If early exits are common, consider alternative strategies like batching queries in small chunks rather than querying the entire dataset at once.
