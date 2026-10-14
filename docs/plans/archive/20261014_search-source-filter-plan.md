# Search: filter results by source

**Status:** Done (2026-10-14), checked on the TV and phone emulators.

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
- **Shown** in Expert mode whenever the results span two or more sources, any section's Search (not only "ALL").
- **Type and source together.** The type chips (phone) and the type groups (TV) are built from the
  filtered results, so their counts follow the source.
- **TV focus.** The row sits between the field and the results. Down from the field → the selected
  source; Down from the row → the results; Up from the row → the field (not the Catalogue tab, as
  for the recent searches); Left from the first → the rail. Choosing a source keeps focus on it: the
  results' jump to their first item is held while the row has focus.
- **Brought along.** A phone result opened in another section's tab brings the chosen source with the
  search (`Screen.Search.initialSource`, as the type chip), so Back finds it still chosen.
- **TV Up from the results** is sent to the chosen source (`onExit` on the results); the geometric
  search went over the row to the field.
- **No badges when narrowed** (2026-10-14): results of the chosen source drop their source badge,
  as an ordinary category's rows carry none; "All sources" keeps them.
- **Known gap.** The "N hidden" lines (matches in excluded categories) count every source.
- **D6 — Phone: an icon in the field** (2026-10-14, after the emulator check: the chip row took too
  much room). A filter icon beside the clear button opens a menu of the sources with counts; while one
  is chosen the icon is accented and "Only <source>" shows under the field. TV keeps its button row.
- **D5 — Expert mode only** (2026-10-14), as the source badges: without them results don't name their
  source, so the row would make no sense. Off, no row and no filtering.

## Phases

| # | Work | Where | Status |
|---|---|---|---|
| 1 | Source counts and the effective selection, with tests | core/ui | Done, 5e70e88c |
| 2 | Phone: source chip row in the Catalogue tab | phone `SearchScreen`, strings | Done, e6732f57; replaced by the field icon (D6) |
| 3 | TV: source button row, focus, focus walk | TV `SearchScreen`, `scripts/focus-walks/search-sources.txt` | Done, afb56c8b; walk written, not recorded |
| 4 | Docs (FEATURES, RELEASE_NOTES; NAVIGATION_GUIDE unchanged, no new route); emulator check on a two-source profile | docs | Done. Emulators 2026-10-14 (profile Kid, bears + xstream4k, Expert mode, "news"): phone menu, "Only bears", type chips follow, choice kept after Back from a film opened in Movies; TV row, focus walk `search-sources.txt` 14/14 after fixing Up from the results; no crashes. Not checked: Expert mode off hides it |
