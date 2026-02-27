# Bolt's Journal - Critical Learnings Only

## 2026-02-27 - SharedPreferences JSON deserialization is the #1 hotspot
**Learning:** `getFavoriteCategoryItems()`, `getFavoriteItems()`, and `getFavoriteShowItems()` all deserialize JSON from SharedPreferences on every call with no in-memory cache. Watch history already has this pattern (`cachedWatchHistory`). The favorite category check is called per-chip inside `LazyRow items {}` in `MobileCategoryListScreen`, meaning 500+ deserializations per recompose for large providers.
**Action:** Apply the same in-memory cache + dirty-flag + debounced-write pattern used for watch history to all favorites lists.

## 2026-02-27 - Category reference items treated as streams on long-press
**Learning:** Virtual categories ("Favorite Categories", "Recent Categories") render their entries as `MediaItem` objects with `providerData["isCategoryRef"] = "true"`. The `onItemLongPress` / `onStreamLongPress` handlers in both mobile (`MobileCategoryListScreen`) and TV (`TwoColumnLayout`) were blindly creating `Stream` favorite targets for ALL items, including these category references. This caused long-pressing a category in these lists to call `addFavorite` (stream) instead of `addFavoriteCategory`.
**Action:** Always check `providerData["isCategoryRef"]` before deciding the favorite target type in any item long-press handler.
