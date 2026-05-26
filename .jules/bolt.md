## 2024-05-20 - Optimize SearchUtils string manipulations in hot loops
**Learning:** Performing `String.substring` and `String.startsWith` inside hot loops for every item during text filtering causes extreme redundant object allocations and GC thrashing. Using `ParsedQuery` to categorize prefix and exact matches outside the loop dramatically reduces allocations in search.
**Action:** When filtering across thousands of elements (like EPG or search views), always extract string manipulation out of the iteration bounds and pre-parse criteria into lightweight lookup structures.
## 2026-05-26 - Eliminate intermediate list allocations in hot paths
**Learning:** Chaining operations like `.flatten().map()` or `.flatten().mapNotNull()` on standard collections in Kotlin creates multiple intermediate `ArrayList` objects, causing unnecessary GC pressure, especially when dealing with large lists like media episodes.
**Action:** Unroll such chained operations into imperative nested `for` loops. Pre-allocate collections with an expected capacity (e.g., `ArrayList<T>(expectedSize)`) to avoid array resizing overhead.
