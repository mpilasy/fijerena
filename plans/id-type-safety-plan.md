# Plan: Watch-History Schema Versioning & Cleanup (Phase 3)

## Status
Phases 0, 1, 2, 4, and 5 of the Type Safety & Routing Plan are complete. **Phase 3 is open and unstarted** — it cleans up legacy read-time fallback guards for pre-2026 watch history blobs.

---

## Phase 3 — Version the Watch-History Schema and Migrate Once

- **Goal:** Bump the storage key to `watch_history_v3`, with a one-time migration from `watch_history_v2` that normalizes legacy rows:
  - `TV_SHOWS` rows always receive a valid `episodeId` (falling back to `itemId` if unassigned).
  - Any corrupted row where `seriesId == itemId` is normalized.
- **Cleanup:** Once `watch_history_v3` is active, remove defensive `?:` fallback read guards from `toRecentMediaItem` in `MediaRepository.kt`, allowing the `BrowseTarget` shape to drive routing directly without redundant checks.
- **Migration Strategy:** Maintain a fallback v2 reader for one release cycle before removing it.

---

## Non-Goals
- Rewriting `MediaItem` into a sealed hierarchy per provider.
- Modifying Room database schemas or Xtream DB tables.
- Retrofitting typed IDs into EPG/XMLTV pipeline components.
