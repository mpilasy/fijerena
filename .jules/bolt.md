## 2024-05-20 - Optimize SearchUtils string manipulations in hot loops
**Learning:** Performing `String.substring` and `String.startsWith` inside hot loops for every item during text filtering causes extreme redundant object allocations and GC thrashing. Using `ParsedQuery` to categorize prefix and exact matches outside the loop dramatically reduces allocations in search.
**Action:** When filtering across thousands of elements (like EPG or search views), always extract string manipulation out of the iteration bounds and pre-parse criteria into lightweight lookup structures.
## 2026-05-28 - [Replace .flatten() with nested loops]
**Learning:** Found two places using chained `.flatten().map()`/`.flatten().mapNotNull()` which create unnecessary intermediate list allocations during heavy processing (e.g. collecting season/episode info).
**Action:** Replaced these chained operations with standard nested loops over the data structures, directly populating `mutableSetOf` or `mutableListOf` to avoid intermediate allocations and reduce GC overhead.
## 2026-06-04 - Optimize Kotlin `groupBy` allocations in large datasets
**Learning:** Using tuples (`Pair`) as keys in Kotlin's `groupBy` (e.g., `groupBy { it.id to it.name }`) creates a new temporary object for every single element processed. In large datasets like EPG airings, this causes significant GC pressure and memory churn.
**Action:** When grouping by a composite key where one part is a unique identifier, group solely by that unique ID reference (e.g., `groupBy { it.id }`) to achieve zero allocations. Secondary properties (like `name`) can be cheaply derived later from the first element of the resulting group list (`entry.value.first().name`).
