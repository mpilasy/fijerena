## 2024-05-20 - Optimize SearchUtils string manipulations in hot loops
**Learning:** Performing `String.substring` and `String.startsWith` inside hot loops for every item during text filtering causes extreme redundant object allocations and GC thrashing. Using `ParsedQuery` to categorize prefix and exact matches outside the loop dramatically reduces allocations in search.
**Action:** When filtering across thousands of elements (like EPG or search views), always extract string manipulation out of the iteration bounds and pre-parse criteria into lightweight lookup structures.
## 2026-05-28 - [Replace .flatten() with nested loops]
**Learning:** Found two places using chained `.flatten().map()`/`.flatten().mapNotNull()` which create unnecessary intermediate list allocations during heavy processing (e.g. collecting season/episode info).
**Action:** Replaced these chained operations with standard nested loops over the data structures, directly populating `mutableSetOf` or `mutableListOf` to avoid intermediate allocations and reduce GC overhead.
## 2026-06-08 - [Performance Insight: Avoid Multiple Optimizations at Once & Incorrect Assumptions]
**Learning:** In a previous pass, attempting multiple small optimizations at once led to a regression. I incorrectly assumed runBlocking removal on Room queries was safe without considering main-thread exceptions, and falsely assumed array lookup length was faster than the JVM-optimized .length on String arrays. Also learned mapNotNullTo is inline and compiles exactly like an explicit loop, so rewriting it manually is useless noise.
**Action:** Stick to ONE single, highly measurable optimization (like removing .flatMap{} allocations in a hot render loop) as requested by the persona rules. Do not attempt unproven micro-optimizations.
