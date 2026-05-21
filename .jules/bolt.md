## 2024-05-20 - Optimize SearchUtils string manipulations in hot loops
**Learning:** Performing `String.substring` and `String.startsWith` inside hot loops for every item during text filtering causes extreme redundant object allocations and GC thrashing. Using `ParsedQuery` to categorize prefix and exact matches outside the loop dramatically reduces allocations in search.
**Action:** When filtering across thousands of elements (like EPG or search views), always extract string manipulation out of the iteration bounds and pre-parse criteria into lightweight lookup structures.
