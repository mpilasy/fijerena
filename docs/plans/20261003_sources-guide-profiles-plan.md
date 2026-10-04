# Sources, Guide Sources, Profiles and Home Plan — TV + mobile

**Status:** In progress (since 2026-10-03). All decisions taken (D1–D8, 2026-10-03).

## Progress

| Lane | Phases, in order | Done | Current | Next |
|---|---|---|---|---|
| A TV Live TV | P8 | P8 (2026-10-03, TV) | — | — |
| B Profile page | P1 → P2 → P9 | P1, P2, P9 (2026-10-04, TV; phone pending) | — | — |
| C Sources & guide UI | P3 → P4 → P7 | — | P3–P7 (since 2026-10-03) | — |
| D Guide refresh | P5a backend → P5b UI | P5a (2026-10-04, TV upgrade) | P5b (since 2026-10-04) | — |
| Last | P6 | — | — | after all lanes |

| Phase | State |
|---|---|
| P1 Switch to a profile from its edit page | **Done 2026-10-04** (`53c7847e`), checked on the TV: atr's page → Switch to this profile → Home with atr (header avatar), Back stays on Home; not shown on the profile in use. |
| P2 Per-profile settings in the profile edit page (developer mode, play next episode) | **Done 2026-10-04** (`89d017b1`), checked on the TV: both switches on the profile page; after switching to atr (developer mode on) About & advanced shows Open Diagnostics and no Developer Mode row; Playback has only Count as watched after. |
| P3 "Provides a guide" per source | In progress (lane C, since 2026-10-03) |
| P4 Guide sources under the source; Settings shows Manage sources only | In progress (lane C) |
| P5a Auto-refresh per guide source: database, worker, sync, export | **Done 2026-10-04** (`e585911f`): nullable `refresh_interval_hours`, `providers.db` 16, guarded one-time copy (flag `epg_refresh_interval_copied_v1`, writes without sync triggers), worker at the shortest interval (rescheduled by watching `epg_source`), sync and export fields, Robolectric migration test (Robolectric 4.17) + 21 unit tests. Upgrade checked on the TV emulator: version 15 → 16, both guide sources got the old 24 h, flag set, `epg_sync` periodic at 24 h. Accepted side effect: with every guide source off, `epg_sync` is cancelled, so the orphan-catalogue sweep then runs only at app start. Left for P5b: the old Settings rows still write the retired keys, which still sync and export; intervals like 4/8/48 h copied as they are, the picker must show them. |
| P5b Auto-refresh per guide source: guide sources rows, Settings | In progress (lane D, since 2026-10-04) |
| P6 Section-root button at depth 4 | Todo (last) |
| P7 Home keeps Search the guide; search → grid; no channel search | In progress (lane C) |
| P8 Live TV preview plays on OK, not on focus (TV only) | **Done 2026-10-03** (`6bda98f9`), checked on the TV emulator with bearstv: Down/Up through rows and Right to another tab left the channel alone; OK on another row tuned it ("Tuning · …"), OK again went full screen; Back → preview on that channel → browse on it; new hint line. Playback itself returned HTTP 511 (bearstv's one-connection limit, a Shield was using it). OK on a row with nothing playing yet plays it. Rechecked on iptv with real playback (9 Plus News: OK plays it, OK again full screen, same stream); `live-tv-preview.txt` and `live-tv-back.txt` re-recorded on iptv. |
| P9 Content filters from the profile, not from the source | **Done 2026-10-04** (`87715009`), checked on the TV: the profile page (name, Colour ›, Developer mode, Play next episode, Content filters ›, Switch to this profile, Save, Cancel, Delete last) opens in place in Settings → Profiles; Content filters › on Kid's page (atr in use, bearstv) opens the editor titled "Content filters · bearstv", Back returns to the row; hidden on iptv (filters act on Xtream only). Not changed on purpose: no filters edited on the emulator. Edit Source's filters section and `focusFilters` removed. |

Rows get **In progress (since date)** when work starts and **Done date** with what was verified
and where when merged. Plan edits go in their own `docs:` commit.

## Why

Raised by the user on 2026-10-03 about Settings on both platforms:

1. Developer mode is a per-profile setting but is switched in Settings → About & advanced.
2. A source that has no guide of its own should not get a guide source made for it.
3. A guide source belongs to a source; it should be managed from the source, not from a
   global-looking Settings row.
4. The profile edit dialog has no way to switch to that profile.
5. Auto-refresh is a property of each guide source, not one device-wide setting.
6. Deep screens need a button that goes straight back and discards the back stack — shown from 4
   screens deep, returning to the root of the section (Movies, TV Shows, Search, Settings…) rather
   than Home.
7. Settings' "Switch source" should be "Manage sources": editing the current source from Settings
   and from the Sources page is odd and duplicated.
8. Search the guide should have a button next to its Refresh to edit the guide sources.
9. Home doesn't need both the TV Guide (calendar) and the Search the guide (book) buttons; keep
   one, and the search is the one used most.
10. Search the guide should have a button that opens the TV Guide grid.
11. Search the guide's channel mode (TV "What's on", mobile "Chan.") is no longer needed.
12. (TV only) The Live TV preview should not change channel by itself as focus moves: OK on a channel plays
    it, and only OK on the channel already playing goes full screen.
13. From the profile's edit page the source must not be editable at all — only that profile's
    content filters for it.
14. "Play next episode automatically" belongs on the profile edit page too.

## How it works today (checked in the code, `main` at `b905113e`)

- **Developer mode** is already per profile: `AppSettings.isDevMode` reads and writes
  `devModeKey(activeProfileId)`, queues a settings-sync record, and falls back to the old
  install-wide `dev_mode` key. It can only be changed for the profile in use, from the Developer
  Mode row in About & advanced (TV `SettingsScreen` / `DeveloperSettingsCard`, mobile
  `SettingsScreen`). The profile edit dialog (TV `ProfilesSettingsCard.ProfileEditDialog`, mobile
  `ProfilesSettingsRows`) has the name, Save, Cancel and Delete.
- **Switching profile** happens in the profile picker and from Home's profile button, through
  `AppContainer.switchProfile(profileId)`. Settings' Profiles group only adds, edits and deletes.
- **Automatic guide sources**: only an Xtream source gets one (`<server>/xmltv.php?username=…`),
  and only when it has live channels — `AutoXmltvSources.reconcile`, called from
  `XtreamSessionManager.reconcileAutoXmltvSource` (GD0c, 2026-10-03). Jellyfin, Remote M3U, SMB
  and Local never get one (`ProviderCapabilities.supportsEpg` is false for them). Nothing records
  whether a source's own guide is wanted or useful: an Xtream server whose `xmltv.php` is empty
  still gets the source, and the viewer can't turn it off for good (deleting it, it comes back on
  the next login).
- **Guide sources UI**: `EpgManagement(providerId)` (TV `TvEpgManagementScreen`, mobile
  `MobileEpgManagementScreen`) is already scoped to one source. It opens from two places: the
  Sources list's **Guide** button (shown when `MediaProviderFactory.hasLiveTv`: Xtream, Remote
  M3U, Local with an M3U) and Settings → Source & guide → **Guide Sources** (the active source),
  which reads as a global setting. Edit Source (TV `TvAddProviderScreen`, mobile
  `MobileAddProviderScreen`) doesn't mention guides.
- **Home's header** (TV and mobile, GD5) has both **TV Guide** (calendar icon, the grid for
  Recent) and **Search the guide** (book icon, the EPG browser), plus Search All. **Search the
  guide** has two modes, Programme and channel (TV "What's on", mobile "Chan."; strings
  `epg_browser_search_mode_channel`, `epg_browser_mode_channel`), `EpgBrowserViewModel.SearchMode`
  PROGRAM / CHANNEL; the channel mode searches channel names and lists what is on now and next. It
  has no way to the grid unless it was opened from one (Back).
- **Live TV preview (TV)**: focus resting on a channel row for 800 ms tunes the preview to it
  (`LiveTvSplitLayout`: `focusedItemFlow` + `collectLatest { delay(PREVIEW_SETTLE_MS) }` →
  `previewTarget`; UX overhaul LT5, the overhaul's Live TV decision 1 "preview tunes on focus").
  OK on any row goes full screen (`onStreamPromote`: tunes to that row first if it isn't the one
  playing, then `fullScreen = true`). The hint line reads "OK Full screen · Hold OK Options"
  (`live_preview_hint`). Moving through a list, or switching tabs with Left/Right, keeps changing
  the channel. In full screen the panel already works on OK only (OK tunes and closes it).
- **Play next episode automatically** is per profile too (`KEY_AUTOPLAY_NEXT_EPISODE`, in
  `AppSettings.PER_PROFILE_SETTING_KEYS` with developer mode and the last source), synced, and also
  switched only for the profile in use, from Settings → Playback (next to the device-wide "Count as
  watched after").
- **Content filters** are stored per source *and* per profile (`CategoryFiltersStore`, keyed
  provider × profile, synced through `SettingsSyncQueue.categoryFilters`; 2026-09-30 profile-scoped
  settings plan). They are edited only inside Edit Source (TV `ProviderFiltersSection` →
  `CategoryFilterDialog`, mobile its own section), always for the profile in use — so changing a
  profile's filters means switching to it and opening the source's full edit screen.
- **Settings → Source & guide** has Switch source (opens the Sources list, where each source has
  Use, Edit, Guide and ⋮), **Edit this source** (Edit Source for the active one), Guide Sources and
  Guide auto-refresh; the Profiles group's content-filters row also opens Edit Source on the
  active source's filters (`Screen.AddProvider(focusFilters = true)`). So the active source can be
  edited from three places.
- **Auto-refresh** is device-wide: `AppSettings.epgAutoRefreshEnabled`, `epgRefreshInterval`
  (hours, `-1` = never) and `epgRefreshTime` (`HH:mm`), edited in Settings → Source & guide →
  Guide auto-refresh. `EpgFileManager.updateAutoRefreshSchedule` enqueues one periodic
  `EpgSyncWorker` (`epg_sync`) at that interval, first run at that time;
  `getStaleSources(providerId)` picks the active source's enabled guide sources older than
  `staleThresholdMs` (interval / 2). Only the active source's guide sources are refreshed.
- **Storage**: `epg_source` is in `providers.db` (`SettingsDatabase`, version 15), synced as
  `SyncPayloads.EpgSource(url, label, timezoneOffsetHours, enabled)` and exported as
  `ExportedEpgSource`. Per-source options live in `ProviderEntity.providerSettings`, a JSON
  `ProviderSettings` that is synced and exported whole — new fields there need no migration.
- **Going Home**: there is no Home button. Home is `Screen.ContentTypeSelection`; Back walks the
  stack one screen at a time (on TV, Back on Home does nothing). The only "go Home" path is the
  Live TV preview's remote Stop, `navController.popBackStack(Screen.ContentTypeSelection,
  inclusive = false)` in `TvNavHost`. Home is not always on the stack: the start destination is
  Settings when there is no source, and the profile picker when profiles ask for it. 17
  destinations per nav host; Home, ProfilePicker, SafeMode and NewerData are entry screens.

## Decisions

| # | Question | Recommendation |
|---|---|---|
| D1 | Is "Provides a guide" a switch the viewer flips, or set by detection only? | **Decided 2026-10-03:** detected automatically (on for an Xtream source with live channels, off when its own guide comes back empty), and the viewer can change it later in Edit Source. Once the viewer has set it, detection no longer changes it. Not shown for other types: they never get an automatic guide. Turning it off **disables** the automatic guide source (`enabled = false`) instead of deleting it; turning it on again re-enables the same row with its stats; reconcile never re-enables or duplicates it while off (added 2026-10-03). |
| D2 | Per-source auto-refresh: its own time of day too, or only its own interval? | **Decided 2026-10-03:** an interval per guide source only (Off / every 6 h / 12 h / daily / weekly). No time-of-day setting: the device-wide "Guide refresh time" goes with the rest of the device-wide auto-refresh. |
| D3 | Keep the Sources list's **Guide** button once guide sources live in Edit Source? | **Decided 2026-10-03 (no preference):** keep it as a shortcut to the same screen; remove only Settings → Source & guide → Guide Sources. |
| D4 | Where does the Home button go? | **Decided 2026-10-03:** a **section-root button** on any screen 4 or more entries above Home (`HOME_BUTTON_MIN_DEPTH = 4`, depth counted on the nav back stack), except the player, its OSD and the Live TV preview. It returns to the first screen above Home in the current stack — the root of the section (Movies, TV Shows, Live TV, Search, Settings) — discarding everything above it; that screen keeps its saved state (category, scroll, search results). Labelled with its destination ("Movies", "Settings"…). Same rule for every section; in Live TV it rarely shows, since preview and full screen are layers (LT7) and deep Live TV chains end on a preview. |
| D5 | What does Home do when there is no source (Home can't show anything)? | **Dropped 2026-10-03:** without a source there are no catalogue screens, so the question doesn't arise. |
| D6 | What stays of the source rows in Settings? | **Decided 2026-10-03:** Settings → Source & guide becomes one **Manage sources ›** row (its value: the source in use) opening the Sources list, where switching, editing and guide sources live. Switch source, Edit this source and Guide Sources leave Settings, and so does the content-filters shortcut (filters are on each profile's page, D8). |
| D7 | What does the grid button on Search the guide open? | **Decided 2026-10-03:** it always opens the TV Guide grid for Recent; arriving from a grid, Back still returns there. |
| D8 | Where are content filters edited? | **Decided 2026-10-03:** the profile page gets **Content filters ›**: it opens the filter editor for the source in use, for that profile, headed with the source's name — nothing else of the source shown or editable. Edit Source no longer shows filters. |

## Target

**Profile edit page (both platforms, D8):** name; the profile's own settings, for whichever
profile is being edited — **Developer mode** and **Play next episode automatically**; **Content filters ›** (the filter editor for the source in use, for this
profile, headed with the source's name — nothing else of the source shown); **Switch to this profile** button (hidden for the
profile in use); Save / Cancel; Delete last. Adding a profile asks only for the name (filters are
copied from the profile in use, as today; developer mode starts off).

**Edit Source (both platforms), sources with live channels:**
- Xtream: **Provides a guide** switch — "This source's own guide (xmltv.php) is added
  automatically" — with the state it detected.
- **Guide sources ›** row (count and last refresh as its value) opening the existing guide sources
  screen for this source. Jellyfin and SMB, which have no live channels, show neither.

**Guide sources screen:** each guide source shows its auto-refresh interval as part of its row
and changes it from the row's action menu (TV: long-press OK / Menu, a picker; mobile: the row's
⋮). New guide sources start at the default (daily).

**Section-root button (both platforms, D4):** on any screen 4 or more entries above Home, except
the player, its OSD and the Live TV preview. It returns to the root of the section — the first
screen above Home in the stack (Movies, TV Shows, Live TV, Search, Settings) — discarding
everything above it, and is labelled with that destination. The root keeps its saved state, so
Movies comes back on the category and scroll where browsing started; Home stays one Back away. It
leaves for Home and clears the back stack: Home becomes the only entry, so Back on Home does
nothing (TV) / leaves the app (mobile), as after launch. Anything that stops on leaving (the Live
TV preview and player) stops as it does on Back. A screen with unsaved edits (mobile Edit Source's
discard prompt, M4) asks first, the same way Back does.

**Live TV preview (TV):** moving focus never changes the channel. OK on a row that isn't playing
plays it in the preview ("Tuning · <channel>" as today); OK on the row that is playing goes full
screen. Hint line: "OK  Play · OK again  Full screen · Hold OK  Options". This replaces the
overhaul's "preview tunes on focus" decision. The phone's dock is unchanged (it already plays only
what is tapped).

**Home (both platforms):** the header keeps **Search the guide** and Search All; the TV Guide
button goes (the grid stays reachable from each category's header, the player's Guide button and
Search the guide).

**Search the guide (both platforms):** one mode, programme search (the mode chips go); a **TV
Guide** icon button in the header opening the grid for Recent (D7); next to its Refresh button, a **Guide
sources** icon button opening the guide sources screen of the source in use (the guides the search
runs over). Back returns to it. Shown when that source can have guide sources.

**Settings → Source & guide (D6):** **Manage sources ›** (value: the source in use). No Edit this
source, Guide Sources or content-filters shortcut, and no device-wide guide auto-refresh (each
guide source has its own interval, D2). Backup & storage keeps Guide data maintenance (the guide index is one database per
device). About & advanced: version, build; Diagnostics when the profile in use has developer mode
on.

## Phases (one commit each, each shippable)

| Phase | Scope | Main files | Effort | Risk |
|---|---|---|---|---|
| P1 | **Switch to this profile** in the edit dialog: calls `AppContainer.switchProfile`, closes the dialog and goes Home as the picker does; hidden for the active profile. Strings ×3. | TV `ProfilesSettingsCard.kt`, mobile `ProfilesSettingsRows.kt`, both Settings screens' callbacks | S | Low |
| P2 | **Per-profile settings in the edit page**: `AppSettings` gets per-profile getters/setters for the keys in `PER_PROFILE_SETTING_KEYS` that the viewer sets — developer mode and play next episode (same keys and sync records as today); the page's switches edit the edited profile's values; the Developer Mode row leaves About & advanced and "Play next episode automatically" leaves Settings → Playback (which keeps the device-wide "Count as watched after"); everything gated on them keeps reading the active profile's value. | `AppSettings.kt`, `SettingsViewModel`, both profile dialogs, `DeveloperSettingsCard`, TV `PlaybackSettingsCard`, mobile Settings | S | Low |
| P3 | **Provides a guide**: `ProviderSettings.providesGuide: Boolean? = null` (null = detect). `AutoXmltvSources.reconcile` adds (or re-enables) the automatic guide source only when the effective value is on, and **disables** it (`enabled = false`, never deleted; only the automatic one — hand-added guide sources are never touched) when it is off. Detection: on for Xtream with live channels (today's rule); after an ingest of the automatic source with 0 channels, set `providesGuide = false` (empty `xmltv.php`). Switch in Edit Source for Xtream. Unit tests for reconcile with each value. | `ProviderSettings.kt`, `AutoXmltvSources.kt`, `XtreamSessionManager.kt`, `EpgFileManager.kt` (empty-ingest hook), both Edit Source screens, strings ×3 | M | Med (changes a synced row; must keep hand-added sources) |
| P4 | **Guide sources under the source; Settings shows Manage sources only**: "Guide sources ›" row in Edit Source (sources with live channels) opening `EpgManagement(providerId)`; the Sources list's Guide button stays (D3). Settings → Source & guide becomes **Manage sources ›** (renamed from Switch source, value = the source in use) — Edit this source, Guide Sources and the Profiles group's content-filters shortcut (`focusFilters` deep link) are removed (D6); unused strings removed ×3. Search the guide gets a Guide sources icon button next to Refresh, opening `EpgManagement(activeProviderId)`; Back returns focus to it. Back from the guide sources screen returns to the row it opened from (`NavReturnFocus`). Focus walks updated. | both Edit Source screens, both Settings screens, both Search the guide screens (`TvEpgBrowserScreen`, `MobileEpgBrowserScreen`), both nav hosts, `scripts/focus-walks/settings*.txt`, `edit-source.txt` | M | Low |
| P5 | **Auto-refresh per guide source**: `epg_source.refresh_interval_hours` (`SettingsDatabase` 15 → 16, nullable — `NULL` = not set yet, `-1` = off), a one-time startup step copying today's device-wide interval **only into rows that have no value of their own** (a row synced from a device already on the new version keeps its interval; until filled, `NULL` counts as the old interval, so nothing changes for anyone on upgrade), then the device-wide switch, interval and time of day retire (D2). `getStaleSources` uses each row's own interval (stale after half of it, as today); the periodic `EpgSyncWorker` runs at the shortest interval among enabled guide sources, cancelled when all are off. Sync payload and export carry the field (optional, default keeps the local value, so older app versions keep working). Guide sources screen: interval in the row and a picker in the row actions. Room migration test as a Robolectric unit test (`MigrationTestHelper` on the JVM, no device — decided 2026-10-03, since `connectedAndroidTest` wipes app data on connected devices), `docs/DATABASE_SCHEMA.md` updated in the same commit. | `EpgSourceEntity.kt`, `SettingsDatabase.kt` (+ schema JSON 16), `EpgSourceDao.kt`, `EpgFileManager.kt`, `EpgSyncWorker.kt`, `AppSettings.kt`, `SyncPayloads.kt` / sync applier, `SettingsExportManager.kt`, both guide sources screens, both Settings screens (`GuideSettingsRows`), strings ×3 | L | Med-High (schema migration on every device, sync format, worker scheduling) |
| P6 | **Section-root button at depth 4** (D4): per nav host, `depth` = entries above Home in `navController.currentBackStack`, and `goToSectionRoot()` popping one entry at a time until the first entry above Home is on top (a plain `popBackStack<CategoryList>()` would stop at the nearest of several category lists). Shown when `depth >= HOME_BUTTON_MIN_DEPTH` (4) and the screen is not the player / Live TV preview; label = the root's name (Movies, TV Shows, Live TV, Search, Settings). TV: the last button of each screen's header row (appearing doesn't move the others); mobile: a top-bar action. Focus walks updated (`details.txt`, `search.txt`, `settings*.txt`). Strings ×3. | both nav hosts; a shared header slot on the TV screens with a header (details, episodes, category lists, Search, TV Guide, Search the guide, Settings sub-screens, Sources, Edit Source, guide sources, Live sync) and the mobile top bars; `scripts/focus-walks/*` | M | Low-Med (every screen header; back-stack popping) |
| P7 | **Home keeps Search the guide; search ↔ grid; no channel search**: Home drops its TV Guide button (TV and mobile; focus walk `home.txt`); Search the guide gets a TV Guide icon button opening `EpgGuide` for Recent (D7); the channel mode goes — the mode chips, `SearchMode.CHANNEL`, the channel-search path in `EpgBrowserViewModel` and `XmltvSearchService.searchByChannel` if nothing else calls it, and its strings ×3. | both Home screens (`ContentTypeSelectionScreen`), both Search the guide screens, `EpgBrowserViewModel.kt`, `XmltvSearchService.kt`, both nav hosts, `scripts/focus-walks/home.txt` | S | Low |

| P9 | **Content filters from the profile, not from the source** (D8): the profile edit dialog becomes a page (TV: a sub-pane of Settings' Profiles group; mobile: `Screen.ProfileEdit(profileId)`) holding P1's and P2's controls plus **Content filters ›**, which opens the existing filter editor (`CategoryFilterDialog` on TV, the mobile equivalent) for the source in use and *that* profile through `CategoryFiltersStore`, headed with the source's name only. Editing a profile not in use only stores its filters (they apply when it becomes active). Edit Source drops its filters section and the `focusFilters` deep link (with D6). Focus walks (`settings.txt`, `edit-source.txt`). | both profile dialogs → pages, both Edit Source screens, filter editor entry point, `CategoryFiltersStore` (read/write for a given profile), mobile nav host (route), strings ×3 | M | Med (filters for a profile not in use; the catalogue's `excluded` flags must still follow only the active profile) |
| P8 | **Live TV preview plays on OK, not on focus (TV only; the phone is unchanged)**: drop the focus-driven tuning (`focusedItemFlow` / `PREVIEW_SETTLE_MS` / its `collectLatest`; the entry seed still sets `previewTarget` directly); the docked panel's `onStreamPromote` becomes: row ≠ playing → `previewTarget = item` (tune in the preview), row = playing → full screen as today. `live_preview_hint` reworded ×3. `NAVIGATION_GUIDE` / `FEATURES` / `AGENTS` lines on "focus tunes the preview" updated; `live-tv-preview.txt` / `live-tv-back.txt` comments. | `LiveTvSplitLayout.kt`, strings ×3, docs, focus walks | S | Low-Med (LiveTvSplitLayout has ANR history; one engine, one loader — no new player) |

Order: P1 and P2 (one lane, the profile dialog), P3 then P4 (one lane, Edit Source), P5 after P4
(its UI is on the screen P4 links). P1/P2 and P3/P4 can run in parallel. P6 is independent of the
rest but touches every screen's header and both nav hosts, so it runs alone, after P4 (which also
edits the nav hosts and Edit Source). P7 runs with P4 (both edit Search the guide's header) or
right after it. P8 is independent and small; it can go first. P9
builds on P1–P2 (same page) and goes with P4 (both edit Edit Source), so it joins lane 1 after P2
and lands after P4.

## Data, sync and safety notes

- P3 and P5 change synced data. A device on an older version ignores `providesGuide` (unknown
  JSON key in `providerSettings`) and the new sync field; check `SyncCodec`'s JSON settings
  (`ignoreUnknownKeys`) before relying on that, and add a test.
- P3 turning "Provides a guide" off disables the automatic `epg_source` row (keyed on the
  automatic URL shape only, never a hand-added source); nothing is deleted, and turning it on
  again re-enables the same row.
- P5's one-time interval copy fills only rows whose `refresh_interval_hours` is `NULL`.
- P5's migration is additive (one nullable column). Its test runs under Robolectric in
  `testDebugUnitTest`, never `connectedAndroidTest` (which wipes app data on every connected device).
- Backups before installing on real devices, as always (`scripts/deploy-tv-ip.sh`).

## Verification

- Decoder tests (an older app version can't be run, so its view is simulated): the sync decoder,
  the settings-import decoder and `ProviderSettings` decode (1) payloads carrying the new fields
  plus an unknown extra field and (2) old payloads without them (defaults / `NULL`, local value
  kept).
- Unit tests: `AutoXmltvSources.reconcile` with `providesGuide` null / true / false, disable instead
  of delete, re-enable the same row, no re-enable or duplicate while off, and an empty ingest; the
  guarded interval copy (a row with its own value keeps it, a `NULL` row gets the old interval); `getStaleSources` with mixed intervals; the startup copy of the old interval; sync and
  export round-trips with and without the new fields; Room migration 15 → 16.
- TV emulator (bearstv for guide data, jellyxtream, iptv): profile dialog (switch profile from
  Settings lands on Home with that profile; developer mode toggled for a profile not in use and
  checked after switching); Edit Source shows Provides a guide (Xtream) and Guide sources ›;
  turning Provides a guide off removes the automatic guide source and it stays gone after a
  re-login; per-source interval shown and changed; focus walks re-recorded.
- Section-root button: absent at depths 1–3; at depth 4 (Home → Movies → film → Related film →
  collection) it shows "Movies", and pressing it lands on Movies at the category and scroll where
  browsing started, with only Home behind it; same from Search (back to the results) and Settings
  (Home → Settings → Sources → Edit Source → Guide sources → "Settings"); never on the player or
  the Live TV preview; TV focus walks: it is the header row's last stop when shown.
- P7: Home shows Search the guide and Search All, no TV Guide; Search the guide has no mode
  chips; its TV Guide button opens Recent's grid; Back from that grid returns to Search the guide.
- P2: developer mode and play next episode switched on the page of a profile not in use don't change the profile in use; after switching to it both apply; Settings → Playback keeps only "Count as watched after".
- P9: from Settings → Profiles → a profile not in use → Content filters → a source: the filter
  editor opens for that profile; hiding a category there doesn't change what the profile in use
  sees; after switching to that profile the category is hidden; Edit Source has no filters
  section; nothing of the source is editable from the profile page.
- P8: in the preview, Down / Up through rows and Left / Right across tabs leave the playing
  channel alone; OK on another row plays it ("Tuning · …"), OK again goes full screen; Back from
  full screen returns to the preview on that channel; Home → Live TV still opens on the last
  channel playing.
- Phone emulator: the same flows.
- Upgrade check on the emulators: existing guide sources keep refreshing at the old device-wide
  interval after the update.

## Docs to update with the code

`docs/FEATURES.md` (Settings reference, guide sources, profiles, Home button),
`docs/NAVIGATION_GUIDE.md` (Edit Source → Guide sources, Settings rows, the Home button and its
back-stack rule), `AGENTS.md` (every new screen gets the Home button), `docs/epg_guide.md` (automatic guide sources,
refresh scheduling), `docs/DATABASE_SCHEMA.md` (P5), `docs/RELEASE_NOTES.md`, this plan's Progress
table and `docs/plans/README.md`.
