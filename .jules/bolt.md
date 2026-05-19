## 2024-05-19 - Pre-parse Query Words for Search Lookups
**Learning:** In hot-loop operations like scanning tens of thousands of cached items, the client-side search performs repeated string allocations (e.g. `substring()`, `startsWith()`) per record due to parsing the user's query each time within the matching utility.
**Action:** Pre-parse the query into categorized lists (positive and negative search terms) outside of the loop and pass the structured query to the `matchesQuery` fast-path to eliminate redundant memory allocations and reduce GC thrashing.
