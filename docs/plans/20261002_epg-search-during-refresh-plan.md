# EPG Search During Refresh Plan

**Status:** In progress — Phases 1-2 landed (2026-10-02)

While an EPG refresh was running, the EPG browser's programme search showed nothing — no
results and no reason. The user asked for search to work through every stage of a refresh, and
to be told why when it can't.

## Today

- `XmltvSearchService.search` throws `EpgIndexBusyException` whenever `EpgIndexer.isFtsStale()`.
- Staging path (normal): ingest writes only to staging tables, so the FTS index is consistent
  until `executeSwapToMain` marks it stale; it stays stale until `rebuildFtsAndUpdateState`
  finishes. The swap and the rebuild are separate transactions, so readers in between see the new
  `epg_programme` rows with the old FTS — search has to be blocked for the whole rebuild (minutes
  on a Shield with 2M+ programmes).
- Direct path (low storage, `shouldUseStaging() == false`): ingest writes straight to
  `epg_programme` with the FTS triggers dropped, so FTS is stale for the whole download + ingest +
  rebuild.
- `EpgBrowserViewModel` maps the exception to `UiState.Indexing`, which both screens draw exactly
  like `Idle` — the search hint comes back and the user sees "nothing".
- `EpgBrowserViewModel.init` shows `NoEpgFile` ("no EPG file") whenever the index state isn't
  `Indexed`, so opening the browser mid-refresh claims there is no guide.

## Phase 1 — say why, retry automatically

- `EpgIndexBusyException` carries an `EpgIndexBusyReason` (`UPDATING` while the index is
  `Indexing`/`Optimizing`, `REBUILD_PENDING` otherwise — an interrupted rebuild being resumed).
- `UiState.Indexing` → `UiState.IndexBusy(query, reason)`, drawn on TV and mobile as a centred
  message saying the guide is updating and the search will run when it's done.
- The view model watches `EpgIndexer.state`; when it reaches `Indexed` while the screen is waiting
  on a busy search, it reruns the query.
- `rebuildFtsAndUpdateState` clears the stale flag before publishing `Indexed`, so that rerun
  can't race the flag and hit the busy path again.
- `NoEpgFile` at open only when the state is `NotIndexed`.

**Done (2026-10-02).** The reason is decided in the view model, not carried on the exception:
the index state alone can't tell a direct-path refresh (state stays `Indexed` while FTS is stale)
from a pending rebuild, so `IndexBusy.refreshing` also checks `EpgFileManager.state`. The TV
screen previously drew nothing at all for this state (it fell to `else -> {}`).

## Phase 2 — normal refresh never blocks search

- New `EpgIndexer.swapAndRebuildFts(sourceIds)`: the staging → primary swap, the FTS `'rebuild'`
  and the metadata write run in **one** transaction. WAL readers keep seeing the old guide and the
  old FTS until the commit, then the new pair — never a mismatched one — so nothing needs to be
  marked stale. A crash or failure rolls both back together; the previous state is restored.
- Pragmas (`wal_checkpoint`, `synchronous`, `cache_size`) are set outside the transaction —
  SQLite refuses to change `synchronous` inside one.
- `EpgFileManager` (multi-source and single-source) calls it in place of
  `executeSwapToMain` + `rebuildFtsAndUpdateState` on the staging path. The direct path keeps the
  separate rebuild.
- Cost: the WAL grows by roughly the FTS size before it can checkpoint. The staging path is only
  taken with 1.5× the database size free, which covers it. Other EPG writers wait on the writer
  lock for the rebuild, as before.

**Done (2026-10-02).** `executeSwapToMain` is gone (its only callers were the two swap sites).
A failed rebuild on the staging path now fails the refresh (the swap rolls back with it, the old
guide stays live) instead of being logged and leaving a swapped guide with a stale index. The
Finalizing phase label stays "Swapping to primary guide…" — the screens map that exact text to a
translated string.

## Phase 3 — low-storage refresh: slower title search

- When FTS is stale (direct-path refresh, or a pending rebuild after a crash), `search` runs a
  `LIKE` substring match on `epg_programme.title_lowercase` within the same window and source
  filter, capped at 500 rows, with the same 10 s timeout. Title only — the description scan would
  be too slow.
- Results carry `EpgSearchPath.LIKE_FALLBACK`; both screens show a note that the guide is updating
  and results may be incomplete. When the index becomes `Indexed`, the view model reruns the query
  so the full FTS results replace them.
- A timeout or SQL failure still throws `EpgIndexBusyException` → Phase 1 message.

## Verification

- Unit tests: `XmltvSearchService` query helpers where testable on the JVM; view model retry is
  covered on emulators.
- Emulator: trigger a sync with `EpgSyncDebugReceiver`, search while it runs (staging path), and
  again with the direct path forced.
