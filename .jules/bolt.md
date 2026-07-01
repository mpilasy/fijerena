## 2024-05-24 - Optimize Coroutine Collection Result Accumulation
**Learning:** Chaining functional collection operators like `.flatMap { it.await() }.toMap()` on lists of Deferred coroutine results generates multiple intermediate collections and Pair allocations, significantly degrading memory performance in hot data-sync paths.
**Action:** Replace `flatMap/toMap` chains on Deferred collections with a pre-allocated `HashMap` and nested `forEach` loops to process the `.await()` results directly into the destination map without intermediate allocations.
