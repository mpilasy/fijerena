# Bolt's Journal - Critical Learnings Only

## 2026-02-27 - SharedPreferences JSON deserialization is the #1 hotspot
**Learning:** `getFavoriteCategoryItems()`, `getFavoriteItems()`, and `getFavoriteShowItems()` all deserialize JSON from SharedPreferences on every call with no in-memory cache. Watch history already has this pattern (`cachedWatchHistory`). The favorite category check is called per-chip inside `LazyRow items {}` in `MobileCategoryListScreen`, meaning 500+ deserializations per recompose for large providers.
**Action:** Apply the same in-memory cache + dirty-flag + debounced-write pattern used for watch history to all favorites lists.
