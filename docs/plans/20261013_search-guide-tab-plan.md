# Search: Catalogue and Guide tabs

**Status:** In progress, 2026-10-13. Decisions D1–D3 and O1–O4 made 2026-10-13. The "Matched only"
toggle was removed first (95232085; the filter stays on in code). Phase 1 can start.

## Today

Search (`Screen.Search`; `tv/…/feature/search/SearchScreen.kt`, `mobile/…/feature/search/SearchScreen.kt`,
`SearchViewModel`) searches only the catalogue: categories, channels, films and shows. Searching
programme titles in the guide is a separate screen, Search the guide (`Screen.EpgBrowser`;
`TvEpgBrowserScreen`, `MobileEpgBrowserScreen`, `EpgBrowserViewModel`). It opens from TV Home's
"Search the guide" button, the phone's Live TV top bar and the TV Guide's Search (filtered to that
guide's category). See [NAVIGATION_GUIDE.md](../NAVIGATION_GUIDE.md).

## Goal

The Search screen searches either the catalogue or the guide, picked with two tabs.

## Decisions (2026-10-13)

| # | Question | Decision |
|---|---|---|
| D1 | How to switch | **Two tabs**, "Catalogue" and "Guide", under the header. Each tab keeps its own results and filters. The query is shared. |
| D2 | The Search the guide screen | **Kept as it is**, with every entry point. The Guide tab is a second way in. |
| D3 | What runs a search | **The active tab only.** Switching to a tab whose last search was a different query runs the current query there. The guide's FTS search doesn't run unseen. |

## Open points, decided (2026-10-13)

| # | Question | Decision |
|---|---|---|
| O1 | When the Guide tab shows | Only when Search covers live channels (`ALL` or `LIVE_TV`; not a Movies or TV Shows search on TV) and the profile has a source with guide sources (`EpgBrowserViewModel.guideSourceOwners` not empty). Otherwise no tab row: Search looks as today. |
| O2 | Recent searches | Each tab keeps its own history, as the two screens already do: the Catalogue tab shows search history, the Guide tab guide search history. |
| O3 | What the Guide tab carries over from Search the guide | The guide freshness line, the index progress bar, recent searches and results. Not the header actions (TV Guide, Refresh, Guide sources) or the "In <category> only" chip, which stay on Search the guide. ("Matched only" is gone everywhere, 95232085.) |
| O4 | Tab labels | "Catalogue" / "Guide" (en, British spelling); "Catalogue" / "Guide" (fr); "Katalaogy" / "Fandaharana" (mg, as `common_tv_guide`). |

## Design

- **A shared guide-search pane.** The body of each Search the guide screen (TV `EpgBrowserContent`
  and the NoEpgFile/Error states around it; the phone's equivalent) becomes an `internal`
  composable taking the `EpgBrowserViewModel`, a hoisted query and `onQueryChange`. Search the guide
  calls it unchanged in behaviour. Search's Guide tab calls it without the context-category
  arguments.
- **The Guide tab's ViewModel is created only when the tab is first shown** (`viewModel()` inside
  the tab's composable, scoped to the Search back-stack entry like `SearchViewModel`), so a
  catalogue-only search never loads the guide's now-playing or scope.
- **The tab row.** TV: two `TvSelectableButton`s under the header (the chip look the guide filters
  already use), Left to the rail on the first one, Down to the field; the focus contract in
  AGENTS.md applies. Phone: the mode-tab style from the phone UI audit. The selected tab is
  `rememberSaveable`, so Back from a guide result's channel comes back to the Guide tab, on the
  airing that opened it (the pane's own `NavReturnFocus`).
- **The shared query.** The field's text is hoisted to the Search screen. Each tab remembers the
  query it last ran; switching tabs runs the field's query on the new tab when it differs (D3).
- **Opening a result.** A guide result does what it does on Search the guide: `Search` gains an
  `onGuideResult: (EpgBrowserMatchedStream) -> Unit`, wired in `TvNavHost` and `MobileNavHost` to
  the same route `Screen.EpgBrowser`'s `onNavigateToPlayer` uses.

## Phases

| # | Phase | Touches | Status |
|---|---|---|---|
| 1 | Shared guide-search pane, TV and phone; Search the guide uses it (no behaviour change) | `TvEpgBrowserScreen`, `MobileEpgBrowserScreen` | Not started |
| 2 | TV Search: tab row, Guide tab, shared query, result navigation | TV `SearchScreen`, `TvNavHost`, strings | Not started |
| 3 | Phone Search: the same | phone `SearchScreen`, `MobileNavHost`, strings | Not started |
| 4 | Tests (Robolectric Compose: tab switch reruns the query, Guide tab hidden per O1), docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES), emulator check on jellyxtream + iptv | tests, docs | Not started |

## Risks

- **Focus on TV.** A new row between the header and the field changes Up/Down from the field and
  the first-open focus (the field, or the history chips). Check both tabs with and without history.
- **Two ViewModels on one screen.** Each keeps its own state; nothing is shared but the query text.
