# Home row order

**Status:** Planned 2026-11-01, not started. Open questions HO-Q1 to HO-Q3 wait for the user.

## Goal

Each profile puts its Home rows in the order it wants, on TV and phone. The order is the profile's,
and live sync takes it to every device the profile is on (user, 2026-11-01).

## What exists (survey 2026-11-01)

- **Rows are in a fixed order** on both screens, in the order of the `HomeShelf` enum
  (tv/.../contentselection/HomeCardFocus.kt):
  1. The Wishlist banner (titles that just became available).
  2. Continue Watching.
  3. Recent Channels.
  4. The Channels group (a tinted `SectionPanel`): Upcoming Games, Favourite Channels.
  5. The TV Shows group: Favourite Shows, Wishlist shows, Trending, Popular, Top Rated.
  6. The Movies group: the same rows for films.
  A group shows only when one of its rows has content.
- **TV focus follows that order:**
  - The first row to take focus is a fixed `when` chain in `ContentTypeSelectionScreen`.
  - Focus after a removal or an Undo (`cardAfterChange`) goes to the next row in `HomeShelf.entries` order.
  - Back to Home returns to a card by its key, which does not depend on the order.
- **Per-profile settings sync already:**
  - A per-profile setting is a SharedPreferences key `<key>_<profileId>` (`AppSettings.profileKey`).
  - A change is queued as a `SyncKind.SETTING` record (`SettingsSyncQueue.setting`), and other devices apply it (`AppSettings.applyRemoteSetting`).
  - Examples: the Trending switch (`charts_show_missing`) and the profile's theme.
  - A new key needs:
    - an entry in `SYNCED_SETTING_KEYS` and `PER_PROFILE_SETTING_KEYS`;
    - a branch in `applyRemoteSetting` and `syncedSetting`;
    - a getter, and a setter that queues the record;
    - removal when the profile is deleted.
  - No new sync kind is needed, so the one-time re-pull for new kinds does not apply.
- **Hiding rows:** only device-wide Admin switches exist: TMDB Charts (all chart rows) and Sports
  (Upcoming Games). Nothing per profile.

## Open questions

| # | Question | Options |
|---|----------|---------|
| HO-Q1 | What can move? | (a) **Groups and the rows in a group.** The Channels, TV Shows and Movies groups move as blocks among Continue Watching and Recent Channels, and rows move inside their group, so the tinted panels stay. Recommended. (b) **Every row on its own.** The tinted groups go, or break into one panel per row. |
| HO-Q2 | Also hide rows? | (a) **Yes, a switch per row** on the same page. A hidden row stays hidden on every device of the profile. Recommended: it is the same page and the same synced setting. (b) Order only. |
| HO-Q3 | Where is it changed? | (a) **A Home Rows page** on the profile's page (Look section), TV and phone: the rows in their order, each with Move up and Move down icon actions (and its switch, HO-Q2). It works with the D-pad, and a child can change it as with its theme. Recommended. (b) Move up and Move down in each row's long-press menu on Home. This is quicker to reach, but the menu is per card, not per row. (c) Drag and drop on the phone, the page of (a) on TV. |

## Design

### The setting

- One per-profile key, `home_row_order`: the row ids in order, separated by commas, such as
  `continue_watching,channels_group,recent_channels,...`. With HO-Q2, a second key,
  `home_rows_hidden`, holds the hidden row ids.
- The ids are fixed names, not the enum's position, so an update can rename the code without
  changing saved orders.
- **Rows that the setting does not name:**
  - A row added by a later update goes to its default place, next to the row it follows by default.
  - An id the app does not know (from a newer app on another device) is ignored but kept, so an older device does not delete it.
- No setting means today's default order.
- Synced as a `SETTING` record, per profile. A newer change wins, as for the theme.
- The Wishlist banner is not in the list. It stays on top, because it is news.

### Home on TV and phone

- A shared function in core/ui gives the rows in the profile's order (`homeRowOrder(saved, default)`), with unit tests:
  - default order;
  - a saved order;
  - a new row that the saved order does not name;
  - an unknown id;
  - hidden rows.
- **TV, `ContentTypeSelectionScreen`:**
  - each row becomes an entry of a map from row id to its composable, drawn in that order (in groups, HO-Q1 a);
  - the first-focus chain and `cardAfterChange` take the shown order instead of `HomeShelf.entries`.
  This is the largest part, because the file has 1,900 lines and its focus rules are tested (`*FocusTest`).
- **Phone, `MobileHomeScreen`:** the same order for its rows and `HomeSection` groups.
- The device-wide Admin switches (TMDB Charts, Sports) still hide their rows on top of the profile's order.

### The Home Rows page (HO-Q3 a)

- **TV:** a pane like Sources: one row per Home row, with Move up and Move down icon actions
  (labels on focus) and the switch. Focus stays on the row that moves.
- **Phone:** a dialog or a page with the same rows and icon actions.
- A **Reset Order** action brings back the default order.
- en, fr and mg strings. Row names are the Home row titles.

## Phases

| Phase | What | Visible change |
|---|---|---|
| 1 | The setting, its sync and `homeRowOrder`, with tests | None |
| 2 | TV and phone Home draw the rows in the profile's order; focus follows the shown order | Home follows a saved order (none yet) |
| 3 | The Home Rows page on TV and phone (order, hide with HO-Q2, Reset) | Users reorder rows; other devices follow |

## Progress

| Phase | Status |
|---|---|
| 1 | Not started |
| 2 | Not started |
| 3 | Not started |

## Difficulty

Medium. Phase 1 is small, because the synced per-profile setting already exists. Phase 2 has the risk:
TV Home's rows and focus rules are written in a fixed order in one large file, and they must change
without breaking the focus tests and the Back-to-Home return. Phase 3 is a list page like Sources.

## Risks

- TV focus after a move or a hide: every first-focus and removal path must use the shown order.
- Two devices that change the order at the same time: the newer change wins for the whole order.
- A row that is hidden and then gets content (the first followed team) stays hidden; the page must
  make hidden rows easy to find.
