# Favorites Durable Storage Plan

**Requirement:** favorites must persist indefinitely. Today they are entries in a JSON list capped
at `providerSettings.favoritesMaxSize` (default 100) and rewritten whole on every change, so
favouriting the 101st item silently evicts the oldest.

**Status:** landed 2026-08-28. All four phases.

---

## What is wrong today

`favorites_v2` and `favorite_categories` are JSON blobs in `media_cache_<providerId>`. Three
defects, the same three watch state had before
[watch-state-durable-storage-plan.md](watch-state-durable-storage-plan.md):

1. **Truncation.** `addFavorite` does `favorites.take(providerSettings.favoritesMaxSize)` on every
   write. Nothing warns; the oldest favourite is simply gone.
2. **Whole-blob rewrite.** Every toggle re-serialises the entire list.
3. **Silent wipe.** Both readers end `catch { emptyList() }`, so one malformed byte turns every
   favourite into "you have none" — indistinguishable from the real empty state.

## Why this port is smaller than watch state's

Watch state had to change its read path, which is why it needed six phases. This one does not.

`MediaRepository` already keeps the authoritative read snapshot in memory — `cachedFavorites`,
`cachedFavoriteCategories`, `favoriteIdSet`, `favoriteCategoryIdSet` — because **Compose calls
`isFavorite()` synchronously during composition**, in ten places across `:tv` and `:mobile`
(`SearchScreen`, `TwoColumnLayout`, `MobileCategoryListScreen`). A per-row suspend Room read during
layout is not an option, so that snapshot has to stay.

So only what sits *underneath* the snapshot changes: blob out, rows in. The public API keeps its
current synchronous shape and **none of the 26 consumer files change**.

Loading happens in `setProvider()` — the existing backfill hook — which
`AppContainer.getMediaRepository()` calls inside `withContext(Dispatchers.IO)`, its only production
call site. So the snapshot is normally filled off the main thread before anything composes.

**That is not a guarantee, and assuming it was crashed the app.** The first build warmed nothing —
`setProvider()` only *invalidated* the snapshot after backfill — so the first read came from
`CategoryViewModel.rebuildVirtualCategories` on `Dispatchers.Main.immediate`, Room's main-thread
assertion fired, and the app died on launch with
`IllegalStateException: Cannot access database on the main thread`. Caught on the emulator, not by
the unit tests, which have no such assertion.

Two changes, both needed: `setProvider()` now fills the snapshot rather than only clearing it, and
`loadFavoriteSnapshotLocked()` wraps its query in `runBlocking(Dispatchers.IO)` so a cold read from
Main is survivable instead of fatal. The blob it replaced did a prefs read plus a JSON parse on that
same thread, so the worst case is no worse than before.

## Target design

### Table

One table, not two. The two blobs hold different shapes (`itemId`/`itemName`/`categoryId` versus
`categoryId`/`categoryName`), but both answer the same question — "is this thing favourited" — and
a `kind` discriminator keeps one set of queries and one index.

```
favorite_state
  providerId        INTEGER  not null
  itemId            TEXT     not null   -- the stream id, or the category id for kind = CATEGORY
  contentType       TEXT     not null
  kind              TEXT     not null   -- STREAM | CATEGORY
  name              TEXT     not null
  parentCategoryId  TEXT     null       -- a stream's owning category; null for kind = CATEGORY
  createdAt         INTEGER  not null
  PRIMARY KEY (providerId, itemId, contentType, kind)
  INDEX (providerId, kind, contentType, createdAt)
```

`createdAt` preserves the blob's ordering semantics — newest first — which the UI relies on.

**No cap column and no cap anywhere.** That is the fix. Rows are inserted and deleted; nothing
truncates.

### `favoritesMaxSize`

The setting exists only to bound the blob. With rows it bounds nothing, so it stops being applied.
The field stays on `ProviderSettings` (removing it would need a settings-JSON migration for no
gain) but its UI row comes out of both provider screens, because a control that silently does
nothing is worse than no control.

### Migration and backfill

`MIGRATION_15_16` creates the table. Backfill and purge hang off `setProvider()` next to
`backfillAndPurgeWatchState()`, gated on a per-provider `favorites_migrated_v1` flag — per-provider
for the reason the watch-state plan spells out at length: a provider nobody has opened since the
release shipped must not have its blob purged before it was copied.

Unlike watch state, backfill and purge ship in the *same* release here, so the "flag set but blob
never copied" window that plan worries about cannot open. The flag is still per-provider.

### Backup and restore

`SettingsExportManager` reads and writes `favorites_v2` by key name (`:275`, `:639`, `:664`). Same
trap Phase 4 hit for watch state, and the same two halves:

- **Export** must serialise rows, nested under the provider's JSON block so import can rewrite
  `providerId` to the matched provider's id (`ProviderEntity.id` is `autoGenerate`, so the id in
  the backup is not necessarily the id on restore).
- **Import** must accept both shapes: a pre-migration backup carrying `favorites_v2` inside
  `media_cache_<id>`, and a post-migration one carrying rows. The existing export-version field
  distinguishes them.

Restoring an old backup must write the rows directly rather than relying on the lazy backfill,
because the restored device may already have `favorites_migrated_v1` set, in which case nothing
would ever look at the blob again.

## Phases

**1 — Schema.** Entity, DAO, `MIGRATION_15_16`, version bump. Ships dark.

**2 — Swap the storage under the snapshot.** Reads populate the snapshot from the table in
`setProvider()`; writes go to the table and update the snapshot in the same `synchronized` block.
Drop the `take(...)` truncation and both `catch { emptyList() }` wipe paths.

**3 — Backfill and purge.** Copy both blobs into the table on first use per provider, then remove
the two keys.

**4 — Backup/restore + settings UI.** `SettingsExportManager` both directions; remove the
Favorites Max Size row from both provider screens.

## Known adjacent problems, out of scope

- **`recent_categories_<contentType>`** is still a capped blob (20, `catch { emptyList() }`). Same
  three defects, much lower stakes — it is a convenience list the user never curates, and eviction
  there is the intended behaviour rather than data loss.
- **`clearFavorites()` leaves `favorite_categories` alone**, which matches both dialogs' wording
  ("all favorited *streams*"). Favourite categories have no clear-all affordance at all. Left as
  is; noted because the asymmetry looks like a bug until you read the copy.
