## 2025-03-06 - Initial setup
**Learning:** Checking for basic files
**Action:** Ready to optimize!

## 2025-03-06 - Hash function allocations
**Learning:** Room entity mapping from JSON objects to DB entities currently has `val entity = base.copy(contentHash = base.computeContentHash())`.
In Kotlin, `data class.copy()` allocates a new object. This means every single Xtream entity being processed during syncing (which batches by 2000 items and typically runs over tens of thousands of streams) allocates two objects instead of one.

**Action:** Refactor `XtreamContentManager` so that `computeContentHash` is calculated without allocating a `base` object first, or just create the entity with the hash inline instead of allocating and copying.
