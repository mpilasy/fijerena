# EPG Search Optimization Plan

**Status: COMPLETE.** All three phases verified present in current code (2026-06-22): the LIKE fallback is gone (`XmltvSearchService.searchFromIndex()` throws on `isFtsStale()` instead of scanning), `CancellationException` is re-thrown in `EpgBrowserViewModel.performSearch()`, the TV clear button uses `showClearButton = localQuery.isNotEmpty() || hasResults`, `EpgProgrammeFts` now indexes both `title` and `description`, and `rebuildFtsAndUpdateState()` applies the `synchronous = OFF` / `temp_store = MEMORY` / `cache_size = -64000` PRAGMAs. Kept as a historical record of the rationale.

## Objective
Address performance bottlenecks in EPG data ingestion, optimize EPG search queries, fix coroutine cancellation errors, enable program description indexing, and refine the UI clear button behavior.

## Scope & Impact
This plan impacts the EPG indexing engine (`EpgIndexer`, `EpgIndexDao`), search service (`XmltvSearchService`), view models (`EpgBrowserViewModel`), and the search UI (`EpgBrowserScreen`, `TvSearchTextField`). The changes will drastically reduce search times on lower-end devices like the Nvidia Shield, prevent UI freezing (ANRs) during index rebuilds, and provide a smoother user experience.

## Phased Implementation Plan

### Phase 1: Immediate Stability & UI Fixes
**Goal:** Prevent app freezing/hanging during EPG refresh and fix UI glitches related to search cancellation and the clear button.
- **Task 1.1 - Disable Fallback LIKE Query:** Remove the `LIKE` fallback for EPG searches in `XmltvSearchService`. When the index is rebuilding (`ftsStale`), return an empty list or specific error ("Index optimizing, please wait") instead of executing a full table scan that causes a 30-minute hang.
- **Task 1.2 - Fix Coroutine Cancellation Error:** Update the `catch (e: Exception)` block in `EpgBrowserViewModel` to properly handle `CancellationException` by re-throwing it or ignoring it, preventing the "StandaloneCoroutine was cancelled" error from surfacing in the UI.
- **Task 1.3 - Fix Clear Button Logic:** Update the `TvSearchTextField` and mobile search UI to ensure the "Clear" (X) button remains enabled as long as there are search results visible, even if the text field is empty.
- **Validation:** 
  - Starting a search while EPG is refreshing immediately shows an "optimizing" message rather than hanging.
  - Rapidly typing/canceling searches shows no errors.
  - The clear button can dismiss lingering results.

### Phase 2: Index Enhancement & Query Optimization
**Goal:** Include program descriptions in search results and reduce query execution time.
- **Task 2.1 - Add Description to FTS Index:** Update `EpgProgrammeFts` to include the `description` field. Update the FTS triggers and rebuild logic in `EpgIndexer` to populate this new column.
- **Task 2.2 - Optimize FTS Queries:** Review the `searchByTitleFts` query in `EpgIndexDao`. Ensure the `MATCH` query targets both title and description.
- **Task 2.3 - Verify FTS Sanitization:** Confirm that `XmltvSearchService.sanitizeQuery` properly strips out `*`, `"`, `(`, `)`, and `:` to prevent FTS syntax errors.
- **Validation:** 
  - Searching for terms found only in descriptions successfully returns the associated programs.
  - EPG searches return results quickly without executing full table scans.

### Phase 3: Ingestion Performance 
**Goal:** Reduce the time it takes to build the FTS index, especially on low-power devices like the Nvidia Shield.
- **Task 3.1 - Optimize Rebuild Logic:** In `EpgIndexer.rebuildFtsAndUpdateState`, ensure that database PRAGMAs (`temp_store = MEMORY`, `synchronous = OFF`, increased `cache_size`) are applied during the `INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')` operation to maximize throughput.
- **Validation:** 
  - Time the ingestion and FTS rebuild process on a physical device (or emulator) to ensure a measurable decrease in execution time compared to the current baseline.

## Migration & Rollback
- The addition of `description` to the FTS table may require a schema migration (since FTS4 tables are structurally separate). If Room migration fails or is too complex, the fallback is a destructive FTS rebuild since EPG data is inherently ephemeral and re-downloaded.
- If ingestion performance regressions occur, the PRAGMA changes can be isolated and reverted independently.