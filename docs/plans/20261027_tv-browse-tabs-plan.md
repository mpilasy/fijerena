# TV browse: virtual lists as tabs

**Status:** Agreed 2026-10-27 (mocks and the category tab); Phase 1 built (df252349c); Phase 2 (emulator walk, focus walks, docs) next.

## Goal

On the TV browse screens (Live TV, Movies, TV Shows), Recent, Favourites, Wanted, Favourite
Categories and Recent Categories become one fixed tab row at the top of the right pane. They leave
the top of the categories pane. Every D-pad move keeps working. Asked by the user 2026-10-27; the
place (top of the right pane, fixed, not scrolling) is the user's choice.

## Today

- `CategoryList` shows the virtual categories in a fixed `Column` above an accent divider, then the
  real categories in a `LazyColumn`, all in one `tvPane` (left pane, 30 %).
- `StreamList` (right pane, 70 %) shows the list that is open.
- OK on a category row opens it; focus alone does nothing.
- The Live TV preview's channel panel (`LiveTvChannelPanel`) already has tabs: the picked category,
  Recent and Favourites, through `TvSectionTabs` (icon tabs, label on focus, focus selects).

## Mocks

Movies, Recent open (a category was picked before):

```
Rail│ Movies                                   [✎] [⟳]
    │ jellyxtream · 24 categories
    │ Categories               ┌──────────────────────────────────────┐
    │ ┌────────────────────┐   │ [Action] [⟲] [★] [⚑] [☆] [⧗]         │ ← fixed
    │ │▌Action             │   │           ▔▔▔  (selected: bottom bar) │
    │ │ Comedy             │   │ ──────────────────────────────────── │
    │ │ Drama              │   │  Dune: Part Two          2024  ▰▰▱   │
    │ │ Family             │   │  The Holdovers           2023        │
    │ │ Horror             │   │  Perfect Days            2023        │
    │ │ ...                │   │  ...                    (scrolls)    │
    │ └────────────────────┘   └──────────────────────────────────────┘
```

A focused tab shows its label (as in the preview panel); the others are icons:

```
    │ [Action] [⟲] [★ Favourites (2 gone)] [⚑] [☆] [⧗]
```

Live TV, a category open (no Wanted tab on Live TV):

```
Rail│ Live TV                           [Guide] [⟳]
    │ iptv · 212 categories
    │ Categories               ┌──────────────────────────────────────┐
    │ ┌────────────────────┐   │ [News] [⟲] [★] [☆] [⧗]               │
    │ │ Kids               │   │  ▔▔▔▔                                 │
    │ │▌News               │   │  1  BBC News                          │
    │ │ Sports             │   │  2  France 24                         │
    │ │ ...                │   │  ...                                  │
    │ └────────────────────┘   └──────────────────────────────────────┘
```

## Design

1. **Tab row:** at the top of the right pane, outside the list's scroll, so it never moves.
   Tabs in this order: the picked category (its name), Recent, Favourites, Wanted (films and
   shows), Favourite Categories, Recent Categories. A virtual tab shows only when it shows today
   (same `rebuildVirtualCategories` rules). The category tab shows once a category was picked.
2. **Look:** `TvSectionTabs`, as in the preview panel: virtual tabs are icons
   (`virtualCategoryIcon`), the label (with its count, for example "Favourites (2 gone)") shows on
   focus; the selected tab has the bottom `currentIndicator`.
3. **Selecting:** focus selects a tab, as in every TV tab row. Left/Right through the tabs switch
   the list; Up from the list lands on the selected tab, so passing through to the header does not
   switch.
4. **Categories pane:** real categories only, full height. The picked category keeps its current
   mark while a virtual tab is open, because it is still the category tab.
5. **Unchanged:** the header and its actions, the Live TV preview layer and its panel, the phone,
   the view model's lists.

## D-pad map

| From | Key | Goes to |
|---|---|---|
| Header actions | Down | the selected tab |
| Tab | Left / Right | the tab beside it (and selects it) |
| First tab | Left | categories pane: the picked category, else the first one |
| Last tab | Right | stays |
| Tab | Up | header actions |
| Tab | Down | the list: its remembered row, else the first row; an empty list's "Browse categories" button |
| First row of the list | Up | the selected tab |
| Any row of the list | Left | categories pane: the picked category (as today) |
| Category row | Right | the list's remembered row (as today) |
| Category row | OK | opens it; the category tab takes its name and is selected; focus stays (as today) |
| First category row | Up | header actions (as today) |
| Category row | Left | the rail (as today) |

Back and edit mode (the pencil on Recent and Favourites) work as today. Switching tab leaves edit
mode, as switching category does today.

## Phases

1. **TV:** move the virtual categories from `CategoryList` to a tab row above `StreamList` in
   `TwoColumnLayout`; the focus wiring of the map above (the tab row's own Left/Right before
   `tvPane` takes them, as the preview panel does); `TwoColumnBrowseFocusTest` covers every row of
   the map; ktlint, TV tests, focus gates.
2. **Walk and docs:** walk Live TV, Movies and TV Shows on the TV emulator (Malagasy and English);
   re-record `scripts/focus-walks/live-tv-browse.txt` and the Recent and Favourites walks; update
   NAVIGATION_GUIDE (Panes), FEATURES, RELEASE_NOTES, and CONTRIBUTING rule 4 (browse tabs join the
   preview panel as a tab row using Left/Right).

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. TV | Done | df252349c |
| 2. Walk and docs | Not started | |

## Done notes

- **Phase 1:** entering the tab row landed on the tab nearest by geometry, which focus then
  selected; `BrowseTabs` sends entry to the selected tab, as the Live TV panel does. An empty
  list's "Browse categories" button took focus from a tab as its list opened; `TvEmptyState`
  takes `takeFocus`, read once, and the list passes its entry-focus rule to it.
