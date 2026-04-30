## 2025-02-14 - [Optimize case-insensitive sorting]
**Learning:** In Kotlin, using `.sortedBy { it.lowercase() }` on large collections evaluates the lowercase conversion on every comparison during the sort. This generates $O(N \log N)$ new String allocations and creates significant GC pressure, especially for high-frequency EPG streams.
**Action:** Use `.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })` instead. This uses an existing comparator to compare characters in place, eliminating the allocations entirely while achieving the exact same result.
