# Sources, Guide Sources, Profiles and Home Plan — TV + mobile

**Status:** Proposed (2026-10-03), not started. Decisions D1–D6 below are recommendations waiting
for the user's confirmation.

## Progress

| Phase | State |
|---|---|
| P1 Switch to a profile from its edit dialog | Todo |
| P2 Developer mode in the profile edit dialog | Todo |
| P3 "Provides a guide" per source | Todo |
| P4 Guide sources under the source; Settings shows Manage sources only | Todo |
| P5 Auto-refresh per guide source | Todo |
| P6 Home button on every page | Todo |

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
6. Every page except Home should have a Home button that goes straight to Home and discards the
   back stack.
7. Settings' "Switch source" should be "Manage sources": editing the current source from Settings
   and from the Sources page is odd and duplicated.
8. Search the guide should have a button next to its Refresh to edit the guide sources.

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
| D1 | Is "Provides a guide" a switch the viewer flips, or set by detection only? | A switch on Xtream sources, preset by detection (on when the source has live channels), plus automatic off when the source's own guide comes back empty (P3). Not shown for other types: they never get an automatic guide. |
| D2 | Per-source auto-refresh: its own time of day too, or only its own interval? | Own interval (Off / every 6 h / 12 h / daily / weekly) per guide source; one device-wide time of day ("Guide refresh time") for when the daily run starts. |
| D3 | Keep the Sources list's **Guide** button once guide sources live in Edit Source? | Keep it as a shortcut to the same screen; remove only Settings → Source & guide → Guide Sources. |
| D4 | Where does the Home button go? | TV: a house icon at the right end of each screen's header row (the same slot everywhere, after the screen's own buttons), reached by Up like the other header buttons; in the full-screen player, a **Home** button in the OSD's ⋮ More group. Mobile: a house icon in each top app bar's actions (and the player's controls). Not on Home, the profile picker, Safe mode or the newer-data screen. |
| D6 | What stays of the source rows in Settings? | One **Manage sources ›** row (its value: the source in use) opening the Sources list, where switching, editing and guide sources already live. Edit this source, Guide Sources and the content-filters shortcut leave Settings; the Profiles group keeps a one-line note that filters are set per source in Edit Source. |
| D5 | What does Home do when there is no source (Home can't show anything)? | Hide the button on those screens — Settings is the start screen then, and Home would only send the viewer back to it. |

## Target

**Profile edit dialog (both platforms):** name; **Developer mode** switch (this profile's own,
whichever profile is being edited); **Switch to this profile** button (hidden for the profile in
use); Save / Cancel; Delete last. The Add dialog gets the Developer mode switch too (off by
default).

**Edit Source (both platforms), sources with live channels:**
- Xtream: **Provides a guide** switch — "This source's own guide (xmltv.php) is added
  automatically" — with the state it detected.
- **Guide sources ›** row (count and last refresh as its value) opening the existing guide sources
  screen for this source. Jellyfin and SMB, which have no live channels, show neither.

**Guide sources screen:** each guide source shows its auto-refresh interval as part of its row
and changes it from the row's action menu (TV: long-press OK / Menu, a picker; mobile: the row's
⋮). New guide sources start at the default (daily).

**Home button (both platforms, D4):** on every screen but Home and the entry screens. It
leaves for Home and clears the back stack: Home becomes the only entry, so Back on Home does
nothing (TV) / leaves the app (mobile), as after launch. Anything that stops on leaving (the Live
TV preview and player) stops as it does on Back. A screen with unsaved edits (mobile Edit Source's
discard prompt, M4) asks first, the same way Back does.

**Search the guide (both platforms):** next to its Refresh button in the header, a **Guide
sources** icon button opening the guide sources screen of the source in use (the guides the search
runs over). Back returns to it. Shown when that source can have guide sources.

**Settings → Source & guide (D6):** **Manage sources ›** (value: the source in use) and **Guide
refresh time** (device, when daily refreshes start). No Edit this source, Guide Sources or
content-filters shortcut, no device-wide auto-refresh switch or interval. Backup & storage keeps Guide data maintenance (the guide index is one database per
device). About & advanced: version, build; Diagnostics when the profile in use has developer mode
on.

## Phases (one commit each, each shippable)

| Phase | Scope | Main files | Effort | Risk |
|---|---|---|---|---|
| P1 | **Switch to this profile** in the edit dialog: calls `AppContainer.switchProfile`, closes the dialog and goes Home as the picker does; hidden for the active profile. Strings ×3. | TV `ProfilesSettingsCard.kt`, mobile `ProfilesSettingsRows.kt`, both Settings screens' callbacks | S | Low |
| P2 | **Developer mode in the edit dialog**: `AppSettings` gets `isDevMode(profileId)` / `setDevMode(profileId, value)` (same key and sync record as today); the dialog's switch edits the edited profile's value; the Developer Mode row leaves About & advanced; anything gated on dev mode keeps reading the active profile's value. | `AppSettings.kt`, `SettingsViewModel`, both profile dialogs, `DeveloperSettingsCard` / mobile Settings | S | Low |
| P3 | **Provides a guide**: `ProviderSettings.providesGuide: Boolean? = null` (null = detect). `AutoXmltvSources.reconcile` adds the automatic guide source only when the effective value is on, and removes it (only the automatic one — hand-added guide sources are never touched) when it is off. Detection: on for Xtream with live channels (today's rule); after an ingest of the automatic source with 0 channels, set `providesGuide = false` (empty `xmltv.php`). Switch in Edit Source for Xtream. Unit tests for reconcile with each value. | `ProviderSettings.kt`, `AutoXmltvSources.kt`, `XtreamSessionManager.kt`, `EpgFileManager.kt` (empty-ingest hook), both Edit Source screens, strings ×3 | M | Med (deletes a synced row; must keep hand-added sources) |
| P4 | **Guide sources under the source; Settings shows Manage sources only**: "Guide sources ›" row in Edit Source (sources with live channels) opening `EpgManagement(providerId)`; the Sources list's Guide button stays (D3). Settings → Source & guide becomes **Manage sources ›** (renamed from Switch source, value = the source in use) — Edit this source, Guide Sources and the Profiles group's content-filters shortcut (`focusFilters` deep link) are removed (D6); unused strings removed ×3. Search the guide gets a Guide sources icon button next to Refresh, opening `EpgManagement(activeProviderId)`; Back returns focus to it. Back from the guide sources screen returns to the row it opened from (`NavReturnFocus`). Focus walks updated. | both Edit Source screens, both Settings screens, both Search the guide screens (`TvEpgBrowserScreen`, `MobileEpgBrowserScreen`), both nav hosts, `scripts/focus-walks/settings*.txt`, `edit-source.txt` | M | Low |
| P5 | **Auto-refresh per guide source**: `epg_source.refresh_interval_hours` (`SettingsDatabase` 15 → 16, `NOT NULL DEFAULT 24`, `-1` = off), a one-time startup step copying today's device-wide interval into every row (so nothing changes for anyone on upgrade), then the device-wide interval and switch retire (`epgRefreshTime` stays as "Guide refresh time"). `getStaleSources` uses each row's own interval (stale after half of it, as today); the periodic `EpgSyncWorker` runs at the shortest interval among enabled guide sources, first run at the refresh time, cancelled when all are off. Sync payload and export carry the field (optional, default keeps the local value, so older app versions keep working). Guide sources screen: interval in the row and a picker in the row actions. Room migration test, `docs/DATABASE_SCHEMA.md` updated in the same commit. | `EpgSourceEntity.kt`, `SettingsDatabase.kt` (+ schema JSON 16), `EpgSourceDao.kt`, `EpgFileManager.kt`, `EpgSyncWorker.kt`, `AppSettings.kt`, `SyncPayloads.kt` / sync applier, `SettingsExportManager.kt`, both guide sources screens, both Settings screens (`GuideSettingsRows`), strings ×3 | L | Med-High (schema migration on every device, sync format, worker scheduling) |

| P6 | **Home button on every page**: one `goHome()` per nav host — `popBackStack(Screen.ContentTypeSelection, inclusive = false)` when Home is on the stack, else `navigate(Screen.ContentTypeSelection) { popUpTo(0) { inclusive = true } }` — and one button per platform (TV `TvHomeButton` for the header slot, mobile a top-bar action), passed as `onHome` to every screen in D4's list; TV player OSD and mobile player controls get it too; hidden when there is no source (D5). TV focus: a header stop at the row's end, reachable by Up from the content like the existing header buttons. Focus walks updated. Strings ×3 ("Home"). | both nav hosts; TV screens' headers (Settings, Sources, Edit Source, guide sources, Live sync, Diagnostics, Live TV / Movies / TV Shows browse and preview, details, episodes, Search, TV Guide, Search the guide), `TvPlayerControlsOverlay`; mobile top bars and `MobileControlsOverlay`; `scripts/focus-walks/*` | M | Med (touches every screen header; TV focus order in each) |

Order: P1 and P2 (one lane, the profile dialog), P3 then P4 (one lane, Edit Source), P5 after P4
(its UI is on the screen P4 links). P1/P2 and P3/P4 can run in parallel. P6 is independent of the
rest but touches every screen's header and both nav hosts, so it runs alone, after P4 (which also
edits the nav hosts and Edit Source).

## Data, sync and safety notes

- P3 and P5 change synced data. A device on an older version ignores `providesGuide` (unknown
  JSON key in `providerSettings`) and the new sync field; check `SyncCodec`'s JSON settings
  (`ignoreUnknownKeys`) before relying on that, and add a test.
- P3's "remove the automatic guide source" deletes a synced `epg_source` row, as GD0c's cleanup
  does: keyed on the automatic URL shape only, never a hand-added source; the index rows of that
  source go with it (`deleteProviderEpgSources` path).
- P5's migration is additive (one column with a default). Run the migration test on a dedicated
  emulator only: `connectedAndroidTest` wipes app data on every connected device.
- Backups before installing on real devices, as always (`scripts/deploy-tv-ip.sh`).

## Verification

- Unit tests: `AutoXmltvSources.reconcile` with `providesGuide` null / true / false and an empty
  ingest; `getStaleSources` with mixed intervals; the startup copy of the old interval; sync and
  export round-trips with and without the new fields; Room migration 15 → 16.
- TV emulator (bearstv for guide data, jellyxtream, iptv): profile dialog (switch profile from
  Settings lands on Home with that profile; developer mode toggled for a profile not in use and
  checked after switching); Edit Source shows Provides a guide (Xtream) and Guide sources ›;
  turning Provides a guide off removes the automatic guide source and it stays gone after a
  re-login; per-source interval shown and changed; focus walks re-recorded.
- Home button: from a deep stack (Home → Live TV → category → preview → full screen → OSD →
  Home; Home → Settings → Sources → Edit Source → Guide sources → Home) lands on Home with
  nothing behind it (TV: Back stays on Home; mobile: Back leaves the app); the preview and player
  stop; mobile Edit Source with unsaved edits asks first; hidden on the entry screens and when
  there is no source; TV focus walks: the icon is the header row's last stop on each screen.
- Phone emulator: the same flows.
- Upgrade check on the emulators: existing guide sources keep refreshing at the old device-wide
  interval after the update.

## Docs to update with the code

`docs/FEATURES.md` (Settings reference, guide sources, profiles, Home button),
`docs/NAVIGATION_GUIDE.md` (Edit Source → Guide sources, Settings rows, the Home button and its
back-stack rule), `AGENTS.md` (every new screen gets the Home button), `docs/epg_guide.md` (automatic guide sources,
refresh scheduling), `docs/DATABASE_SCHEMA.md` (P5), `docs/RELEASE_NOTES.md`, this plan's Progress
table and `docs/plans/README.md`.
