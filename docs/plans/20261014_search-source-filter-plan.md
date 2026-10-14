# Search: filter results by source

**Status:** In progress (2026-10-14).

## Today

In a profile with several sources, Search's Catalogue tab merges every source's results; each
result carries its source (`providerId`) and a source badge (`sourceName`, null when the search
covers one source). The phone has type chips (All, Live TV, Films, Shows) over "ALL"; TV has none,
its results are grouped by type, each group collapsible.

## Goal

When the results come from two or more sources, a row of source choices narrows them to one source,
on TV and phone.

## Decisions (2026-10-14)

- **D1 — Catalogue tab only.** The Guide tab's results are grouped by programme with channels from
  several sources; filtering them is later work.
- **D2 — TV: text buttons** (`TvSelectableButton`, as the Catalogue | Guide tabs): a source has no
  icon. Phone: filter chips, as the type chips.
- **D3 — Counts in the labels:** "All sources (n)", then "<source> (n)", on both.
- **D4 — Kept until Search closes:** saveable like the type chip (a re-search, Back from a result);
  not saved between visits.

## Design

- **Client-side.** The row filters the results already loaded; no search runs. Shared from core/ui
  (`SearchSourceFilter.kt`): each source with its count (none when fewer than two sources), sorted by
  name so the row doesn't reorder as results stream in; the selected source while it still has
  results, else every source (a new query without it shows them all, as the type chips do).
- **One at a time.** "All sources" or one source; tapping the selected source again goes back to all.
- **Shown** whenever the results span two or more sources, any section's Search (not only "ALL").
- **Type and source together.** The type chips (phone) and the type groups (TV) are built from the
  filtered results, so their counts follow the source.
- **TV focus.** The row sits between the field and the results. Down from the field → the selected
  source; Down from the row → the results; Up from the row → the field (not the Catalogue tab, as
  for the recent searches); Left from the first → the rail. Choosing a source keeps focus on it: the
  results' jump to their first item is held while the row has focus.
- **Known gap.** The "N hidden" lines (matches in excluded categories) count every source.

## Phases

| # | Work | Where | Status |
|---|---|---|---|
| 1 | Source counts and the effective selection, with tests | core/ui | Done, 5e70e88c |
| 2 | Phone: source chip row in the Catalogue tab | phone `SearchScreen`, strings | Done, e6732f57 |
| 3 | TV: source button row, focus, focus walk | TV `SearchScreen`, `scripts/focus-walks/search-sources.txt` | Done (this commit); walk written, not recorded |
| 4 | Docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES); emulator check on a two-source profile | docs | |
