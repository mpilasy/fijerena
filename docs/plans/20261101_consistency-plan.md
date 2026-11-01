# Consistency: one behaviour and one word for each thing

**Status:** In progress 2026-11-01: Phases 2 and 3 done, 4 partly (C-Q1); 1 in progress.

## Goal

The app must be consistent everywhere, on TV and phone, before a public release. The style layout
tokens plan (`20261101_style-layout-tokens-plan.md`) makes the look consistent. This plan makes
the behaviour and the words consistent: the same action does the same thing and has the same
name on every screen, every list has the same empty, loading and error states, and dates and
times have one format.

## Audit (2026-11-01)

Already consistent: every core string exists in French and Malagasy, opening a channel goes to the
preview (TV) or the docked player (phone) from every entry point, and the dialog and menu order
rule (removals last, above Cancel) holds everywhere.

| # | Finding | Where |
|---|---|---|
| 1 | "Today" / "Tomorrow" hard-coded in English in Search the Guide's day headers; other days without Malagasy names | `EpgBrowserViewModel.kt:716,720` |
| 2 | "Import failed: " + raw exception text for every user, not only in dev mode | TV `SettingsScreen.kt:184,199`; phone `SettingsScreen.kt:203,579` |
| 3 | Error states without Retry | TV Search, TV Search the Guide, phone Search, phone Search the Guide |
| 4 | "No channels" shown as an error (red, Retry) on the phone guide; an empty state on TV | `MobileEpgGuideScreen.kt:311` |
| 5 | Buttons in Title Case ("Remind Me", "Start from Beginning", "▶ Play Movie") | `strings.xml:127-130,423-458` |
| 6 | One action, several names (start over, watch, favourites, watched) | `strings.xml` |
| 7 | One message, two strings (delete guide source); mixed quotes, full stops, "Failed to" / "Couldn't" | `strings.xml` |
| 8 | "Movie" and "film", "series" and "show" mixed | 17/20, 9/52 strings |
| 9 | Unused and duplicate strings | `strings.xml` |
| 10 | Guide source dates in a fixed "MMM dd, HH:mm" (24-hour, no Malagasy) | `TvEpgManagementScreen.kt:502,513`, `MobileEpgManagementScreen.kt:455,464` |
| 11 | Programme day labels differ between the TV guide, the phone guide, search and sports; only `gameDayLabel` knows Malagasy; overnight end day shown in search only | `TvGuideGrid.kt`, `MobileEpgGuideScreen.kt`, `EpgBrowserModels.kt`, `WatchQuestion.kt` |
| 12 | Dead `formatTimestamp` | `tv/.../SettingsUtils.kt` |
| 13 | Clear Recent and Edit Favourites on TV only | `TwoColumnLayout.kt:268`, `StreamList.kt` |
| 14 | FEATURES.md gives an old Home row order | `FEATURES.md:33,39` |
| 15 | The wishlist "remove after watching?" question is never shown | `WishlistPrompt.kt` (both apps) |
| 16 | Sync `lastError` stored without `Redact` | `SyncManager.kt:314` |
| 17 | `epg_guide_no_listings_index_empty` may name an old Settings path | `strings.xml:1075` |

## Decisions (2026-11-01)

- **C-D1 — films and shows.** "Film" and "show" everywhere in English (British English, as the app
  already is): the Movies tab becomes **Films**, "Series Cast" becomes "Show Cast". French and
  Malagasy follow their own usual words.
- **C-D2 — one name per action.** **Watch now**, **Start over**, **Add to Favourites** (the player
  too), **Mark as watched**, **Mark season as watched**. Buttons in sentence case ("Remind me",
  "Play film").
- **C-D3 — phone lists.** The phone gets **Clear Recent** and a way to reorder Favourites, as TV
  has. Swipe to remove stays.
- **C-D4 — wishlist question.** Wire it: after a film or show from the wishlist is watched to the
  end, ask whether to remove it. TV and phone.
- **C-D5 — one date helper** (no user decision needed): one shared, Malagasy-aware helper for day
  labels and times (today, tomorrow, yesterday, short and full date, overnight end day), used by
  the guides, search, sports and the guide source dates.

## Phases

| # | Phase | State |
|---|---|---|
| 1 | Errors and empty states: findings 2, 3, 4, 16; Retry everywhere, raw errors only in dev mode | In progress |
| 2 | Words: findings 5, 6, 7, 8, 9, 17 with C-D1 and C-D2, in en, fr and mg; Title Case rule kept | Done 2026-11-01 (45b98a5ef); 90 unused or merged strings deleted |
| 3 | Dates and times: findings 1, 10, 11, 12 with C-D5 | Done 2026-11-01 (3ac940c86): `DateText` in core/ui |
| 4 | Phone lists: Clear Recent and reorder Favourites (C-D3) | Clear Recent done 2026-11-01 (6f7e5a4e5). Reorder: TV has none (its Edit mode only removes); open question C-Q1 |
| 5 | Wishlist question (C-D4) | Not started |
| 6 | Docs (finding 14, FEATURES, RELEASE_NOTES, CONTRIBUTING wording rules) and a walk of both emulators | Not started |

Each phase is one commit on main with its tests and an emulator check.
