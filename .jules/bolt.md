## 2024-05-20 - Optimize SearchUtils string manipulations in hot loops
**Learning:** Performing `String.substring` and `String.startsWith` inside hot loops for every item during text filtering causes extreme redundant object allocations and GC thrashing. Using `ParsedQuery` to categorize prefix and exact matches outside the loop dramatically reduces allocations in search.
**Action:** When filtering across thousands of elements (like EPG or search views), always extract string manipulation out of the iteration bounds and pre-parse criteria into lightweight lookup structures.
## 2026-05-28 - [Replace .flatten() with nested loops]
**Learning:** Found two places using chained `.flatten().map()`/`.flatten().mapNotNull()` which create unnecessary intermediate list allocations during heavy processing (e.g. collecting season/episode info).
**Action:** Replaced these chained operations with standard nested loops over the data structures, directly populating `mutableSetOf` or `mutableListOf` to avoid intermediate allocations and reduce GC overhead.

## 2024-06-25 - [Optimize nested flatten and mapNotNull with pre-sized loops]
**Learning:** Found an anti-pattern in network models grouping logic where chained `.flatMap {}` and `.mapNotNull {}` combined with `orEmpty()` was creating many intermediate lists and allocating unneeded iterators per element on data parsing logic inside of hot loop during TMDB fetching.
**Action:** Replace functional programming chaining `.flatMap {}` followed by `.mapNotNull {}` inside highly active loops or large network parses with explicit traditional nested loop collections and `HashMap` pre-allocation.
