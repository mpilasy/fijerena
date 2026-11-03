# Nerd role, sync failures, TV reminder focus, Admin pages

**Status:** In progress since 2026-11-03: Phase 0 and Lanes R, S, A started. Decisions RQ-1 to RQ-6 and NR-Q1 taken
(user, 2026-11-03).

## Goal

Four changes from the user (2026-11-03):

1. **TV reminder banner.** When a reminder shows, the D-pad must reach Watch and Dismiss, and the
   screen underneath must not get the keys.
2. **Nerd role.** A new profile role beside Child and Admin. A Nerd sees the stats and extra
   information that only an Admin sees today. An Admin who is not a Nerd no longer sees them,
   except the sync status on Home.
3. **Sync failures.** The Guide Sources page and the source's Library Data section keep the last
   successful sync. When the last sync failed, they also show the time of that failure, its reason,
   and the number of failures since the last success.
4. **Admin pages.** The phone's Admin page has too much on one page: each section becomes a page
   of its own. TV does the same.

## Decisions (user, 2026-11-03)

| # | Question | Decision |
|---|----------|----------|
| RQ-1 | Reminder banner behaviour | **Take focus always.** Focus goes to Watch (Dismiss when there is no channel) when the banner shows, on every screen, full-screen playback too. Back dismisses. The 30 s auto-close runs while it has focus. |
| RQ-2 | Who sets Nerd | **An Admin, or the profile itself.** A grown-up turns Nerd on or off on their own profile page without the Admin PIN. An Admin sets it for any grown-up. A child never. |
| RQ-3 | Existing Admins on upgrade | **No copy.** Nobody is a Nerd after the update; an Admin who wants the stats turns Nerd on. |
| RQ-5 | Admin sections on TV | **Same as the phone.** The Admin pane lists the sections; OK opens a section's pane in place, Back returns to its row. |
| RQ-6 | Shrink Database | **At the end of Sources & Guide.** |
| NR-Q1 | Role name | **English "Nerd", French "Nerd", Malagasy "Mahay".** |
| RQ-4 | Borderline items | **To Nerd:** the source type on the Home sources sheet, the Search "N hidden" count, raw error text on the Admin-only screens. **Unchanged:** the phone player's Stats button stays for everyone. |

## What exists (survey 2026-11-03)

### Reminder banner (TV)

- `TvGameReminderBanner` (tv/.../ui/components/TvGameReminderBanner.kt) is a `Row` drawn after the
  content `Box` in `TvNavHost`. It is not modal by design: the buttons have
  `canFocus = state.armed`, and only Menu or a held OK arms them (`reminderBannerKeys`). All other
  keys go to the screen underneath. That is the bug the user sees.
- After arming, the banner's own key handling is correct (Left and Right on the buttons, Back
  closes, focus goes back through `contentFocus` and `focusRestorer`).
- The 30 s auto-close runs only while the banner does not hold focus.
- When a `Dialog` window is open (TV `CinemaAlertDialog`, trailer player), the banner is drawn under
  it and cannot get keys at all.
- Tests: `TvGameReminderBannerFocusTest` asserts today's non-modal behaviour.

### Roles

- Admin = the per-profile SharedPreferences key `dev_mode_<id>` (`AppSettings.isDevMode`,
  `devMode(id)`, `observeDevMode()`). There is no separate developer mode. Child = `child_profile_<id>`.
  No Room column; the sync server sees only encrypted records. So Nerd is one new per-profile
  synced key and needs no migration.
- Rules: `ProfileRules` (add, update), `ProfileRepository` (`setChildProfile` clears Admin,
  `clearChildExperts`, `ensureAnExpert`), `SyncApplier` (child clears Admin after a sync),
  `SettingsExportManager` import (child forces Admin off).
- Editors: TV `ProfilesSettingsCard` (Admin section, Admin switch inside `if (!child)`), phone
  `ProfileEditScreen` and the add-profile rows.
- `docs/FEATURES.md` "## Admin" lists what an Admin sees today.
- **Information gates on the Admin flag** (all move to Nerd except where marked):
  - Home: the "Updated …" line (`syncSummary(showUpdated = isDevMode)`): **stays Admin** (the
    user's exception). The source type after each source name in the Home sources sheet: Nerd.
  - Source badges and Source rows everywhere, through `LocalShowSourceBadges`
    (core/ui/.../history/SourceBadges.kt, provided in both `MainActivity`s).
  - TMDB id rows on film, show and episode details (TV and phone).
  - TV player: resolution and codec line, Stats button and overlay, buffering details under the
    wait ring, the raw exception panel. Phone player: buffering details, raw error.
  - Guide: the TV guide stats line, the phone guide stats, the Search the Guide index stats.
  - TV catalogue: payload size and fetch time (`StreamList`), with the getters in
    `MediaRepository` and `XtreamMetricsManager` that return null without the flag.
  - Search: the stats line, raw errors of failed sources, the TMDB raw error, the "N hidden" count.
  - Raw error text ("[dev] …"): `storedErrorText`, every `friendlyErrorMessage(…, includeRaw)` and
    `StoredError.message(…, includeRaw)` caller in core/ui view models, core/network
    (`ProviderSyncManager`, `EpgIndexer`, `EpgFileManager`, `SettingsExportManager`) and screens;
    the TMDB and sports `rawOf` texts. This includes the Admin-only screens (RQ-4): live sync
    last error, source edit, Jellyfin and extra logins.
  - The last Shrink Database figures (TV `DatabaseMaintenanceCard`), on the Admin page: Nerd, by the
    same rule as the raw errors on Admin pages.
- **Stays Admin (management):** Settings groups and the PIN, navigation gates (`isExpertOnly`),
  the profile editor's Admin rows, device-wide setters in `SettingsViewModel`, Guide Sources
  buttons, Admin-only colour themes, quick import, the version 7-tap, key-event logging in the TV
  `MainActivity`.

### Admin page

- **Phone** (mobile/.../feature/settings/SettingsScreen.kt): the Admin row opens one page
  (`adminOpen`, after the PIN) that lists every `ExpertGroup` under its header: Profiles (every
  profile), Parental Controls (one row, already its own screen), This Device (language, Recent size,
  watch delay), Sources & Guide (Sources, Guide Sources, TMDB key, charts refresh, …), Sync & Backup
  (sync, export, import, quick import), then Shrink Database under no header.
- **TV** (tv/.../feature/settings/SettingsScreen.kt): the rail has Your Profile, Admin, About. The
  Admin pane lists the same `AdminPart`s on one scrolling page. Drill-in sub-panes already exist
  (`SettingPicker`: pickers, guide maintenance, a profile's page) with return focus by key.

### Sync status

- Both stores are in `providers.db` (`SettingsDatabase`, version 17). They are local only: the
  live-sync triggers, the settings export and Android backup leave them out.
- **Guide sources** (`epg_source`): `last_ingested_at_ms` is already written only on success;
  `markError` sets only `last_error`.
- **Sources** (`providers`): `ProviderDao.updateSyncStats` writes `lastSyncedAtMs` and
  `lastSyncDurationMs` on every run, failed runs too, so today it is the last attempt. The change
  counts already keep the last successful values.
- Screens: TV `TvEpgManagementScreen`, phone `MobileEpgManagementScreen` (Guide Sources cards);
  TV `CacheManagementSection`, phone `DataManagementSection` (Library Data).
- Other readers of `lastSyncedAtMs`: Home `SourceSyncStatus`, Device info `syncRow` ("failed at"),
  `ProviderSyncRunner` first sync (`== 0`), `AutoXmltvSources`, `ProfileSections`,
  `ProviderGuideSection`.

## Design

### R — Reminder banner takes focus (TV)

- When an alert arrives, the banner arms and takes focus at once (Watch, else Dismiss). Remove the
  Menu / held-OK arming and the "Hold OK or press Menu" hint.
- Trap focus in the banner by the in-tree overlay rule:
  `.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()`.
- Up and Down are swallowed; Left and Right move between the buttons; OK acts; Back dismisses.
  On close, focus goes back to where it was (existing `contentFocus` and `focusRestorer`).
- The 30 s auto-close runs whatever has focus.
- A Dialog window is open: the banner waits and shows when the dialog closes (still inside its
  30 s), so it never sits unreachable under the dialog.
- During full-screen playback the player gets no keys while the banner is up; that is RQ-1.
- Phone: not touched (it shows a system notification). The lane confirms this.

### N — Nerd role

- **Storage:** per-profile synced key `nerd_mode_<id>`, default off, with the same plumbing as
  `dev_mode`: `SYNCED_SETTING_KEYS`, `PER_PROFILE_SETTING_KEYS`, `applyRemoteSetting`, the outgoing
  value, removal on profile delete. API: `AppSettings.isNerdMode`, `nerdMode(id)`,
  `setNerdMode(id, on)`, `observeNerdMode()`. No upgrade copy (RQ-3).
- **Rules** (`ProfileRules`, enforced below the screens like Admin):
  - A child is never a Nerd: turning Child on clears Nerd (`ProfileRepository.setChildProfile`);
    sync (`SyncApplier`) and restore (`SettingsExportManager` import) clear Nerd on a child.
  - Add: a new profile is a Nerd only if an Admin adds it.
  - Update: an Admin changes Nerd on any grown-up; a grown-up changes their own; nobody else.
  - Nerd alone gives no management right. No "last Nerd" guard.
  - Exported in the settings backup next to the Admin flag.
- **Editors** (TV `ProfilesSettingsCard`, phone `ProfileEditScreen`): a Nerd switch in the
  profile's own section (not the Admin section), shown when the profile is not a child, and
  enabled when the viewer is an Admin or is that profile. The Admin's add-profile form gets it too.
- **UI hook:** one `LocalNerd` CompositionLocal fed by `observeNerdMode()`, provided in both
  `MainActivity`s; it replaces `LocalShowSourceBadges`. Composables read `LocalNerd.current`;
  view models and core/network read `AppSettings.isNerdMode`.
- **Gates:** every information site listed in "What exists" moves from the Admin flag to Nerd.
  The Home "Updated …" line stays on Admin.

### A — Admin sections as pages

- **Phone:** the Admin page becomes a list of five rows: Profiles, Parental Controls, This Device,
  Sources & Guide, Sync & Backup. Each row opens its own page with that section's rows; the
  header shows the section name; Back returns to the Admin list. Parental Controls opens its
  existing screen directly. Shrink Database goes to the end of Sources & Guide (RQ-6).
- **TV:** the Admin pane lists the same five rows. OK opens the section in place of the pane
  (a new drill-in like `SettingPicker`), Back returns focus to the section's row (focus contract
  return-by-key). A drill-in inside a section (a picker, a profile's page) returns to the section,
  not the Admin list.
- The PIN stays once, on entering Admin. Leaving Admin by a lock (`adminLocked`) closes the section
  too.
- Rows keep their look and order inside each section; only the page they sit on changes.

### S — Sync failures

- **Settings DB version 18** (`MIGRATION_17_18`):
  - `epg_source`: `last_failed_at_ms INTEGER` (null), `failures_since_success INTEGER NOT NULL
    DEFAULT 0`.
  - `providers`: `lastFailedAtMs INTEGER` (null), `failuresSinceSuccess INTEGER NOT NULL DEFAULT 0`.
  - Rows with an error now: failure time = today's `lastSyncedAtMs` (providers) or now (guide
    sources, no time stored), count = 1. A provider row's `lastSyncedAtMs` then holds a failure
    time, not a success: the lane sets it to 0 ("Never") only if every `== 0` / `> 0` reader is
    safe with it, and otherwise leaves it; the plan records which.
- **Writers:**
  - `EpgSourceDao.markError`: set the error, failure time = now, count + 1.
    `markIngested`, `markUnchanged`, `resetAllIngestionState`, `withIngestionStateReset`: failure
    time null, count 0.
  - `ProviderDao.updateSyncStats`: a success writes time, duration, counts, clears the error,
    failure time and count; a failure keeps time and duration, writes the error, failure time =
    now, count + 1. Cancelled runs still write nothing.
- **Pages** (TV and phone, Guide Sources card and Library Data):
  - "Last sync" shows the last successful sync (or "Never").
  - When the last sync failed: "Last failure" with its date and time, the reason (today's error
    line), and "Failures since last success" with the count.
  - Strings in English, French and Malagasy; British spelling, Title Case labels in English.
- **Other readers:** Device info "failed at" uses the failure time. Home status keeps "Updated"
  from the last success and the failed state from the error. The lane checks each reader in
  "What exists".

## Parallel execution (max 4 agents)

```
t0  ┌ Lane R  (agent) reminder focus (small) ─┐
    ├ Lane S  (agent) sync failures, DB v18 ──┼──────────────┐
    ├ Lane A  (agent) Admin sections as pages ┼──────────────┤
    └ Phase 0 (lead)  Nerd API + LocalNerd    │              │
t1                    Lane N (agent, in R's slot) rules, editors, strings ┐
t1'                   Lane G (agent, next free slot) info gates to Nerd   ┤
t2  lead merges in order of finishing → full suite → emulator walks
```

- Never more than 4 agents: R, S and A start at once; N takes R's slot when R ends (R is small);
  G takes the next free slot.

- **Phase 0 (lead, on main, small):** the Nerd key and API, `LocalNerd` (provided, replacing
  `LocalShowSourceBadges`, still fed by the Admin flag until Lane G), their tests. It fixes the
  names that Lanes N and G use, so they do not wait for each other.
- **Lanes:** each agent works in its own worktree and branch (symlink `local.properties` first),
  one commit per lane with its plan Done note and docs. Agents run the fast checks only: ktlint,
  tests of touched modules, compile, `scripts/check-*.sh` gates.
- **Lane A vs N and G:** A owns both `SettingsScreen.kt` files and the TV Admin pane; N changes
  only the profile editors (`ProfilesSettingsCard`, `ProfileEditScreen`, `ProfilesRows`), not where
  they sit; G changes only the Shrink Database figures gate in `DatabaseMaintenanceCard`, not where
  it sits. If A is merged first, N and G rebase on it.
- **File overlap:** strings files (S, N, G, A) and `docs/FEATURES.md` (R, N, G): the lead resolves
  at merge. Lane G must not touch the profile editors or `ProfileRules` (Lane N's), Lane N must
  not touch gate sites (Lane G's). Lane S owns the Guide Sources and Library Data screens; Lane G
  changes only their `storedErrorText` source, which lives in core/ui, not in those screens.
- **At merge (lead):** full unit suite, `./gradlew lintDebug`, gates. Then, with no known
  regressions left, check other sessions (`ListAgents`) and walk the emulators: TV for R; both for
  N, G, S and A (both in Malagasy; switch TV to English for the focus walk).

## Phases

| Phase | Owner | Work | Tests | Docs |
|-------|-------|------|-------|------|
| 0 | lead | Nerd key and API, `LocalNerd` | `AppSettings` Nerd key: get, set, sync in and out, delete | DATABASE_SCHEMA §5 (`nerd_mode_<id>`, and the missing `child_profile_<id>` row) |
| R | agent | Design R | Rewrite `TvGameReminderBannerFocusTest`: takes focus on show, Left/Right, Back closes and restores focus, keys do not reach the screen, auto-close with focus | FEATURES (reminders), NAVIGATION_GUIDE, CONTRIBUTING (the banner no longer follows the TvUndoBar pattern), RELEASE_NOTES |
| S | agent | Design S | Migration 17→18 JVM test, `SettingsDatabaseMigrationTest` list, DAO tests (count, reset), `FakeEpgSourceDao`, `EpgFileManagerChangeDetectionTest`, `SettingsSyncTriggersTest` (new columns do not queue), Device info and Home status tests | DATABASE_SCHEMA (v18), FEATURES (Guide Sources, Library Data), RELEASE_NOTES |
| A | agent | Design A, TV and phone | TV focus tests: Admin list → section → back to its row; picker inside a section returns to the section; lock closes the section. Phone: each row opens its page, Back returns | FEATURES (Settings), NAVIGATION_GUIDE (Settings), RELEASE_NOTES |
| N | agent | Design N: rules, repository, sync, export, editors, strings | `ProfileRulesTest` (Nerd cases), `SyncApplier` child-clears-Nerd, export child-clears-Nerd, editor row tests TV and phone | FEATURES (new "Roles" text: Child, Admin, Nerd), CONTRIBUTING profile rules, RELEASE_NOTES |
| G | agent | Design N gates: every information site to Nerd; Home "Updated" stays Admin | Update tests that assume the Admin flag shows info (`SyncSummaryTest` stays Admin); a test per kind: badges, raw errors, player stats | FEATURES "## Admin" → "## Nerd" (Admin keeps the Home line), CONTRIBUTING raw-error rule, RELEASE_NOTES |

## Progress

| Phase | Status | Commit |
|-------|--------|--------|
| 0 | Done | (this commit) |
| R | In progress | |
| S | In progress | |
| A | In progress | |
| N | Not started | |
| G | Not started | |
| Walk | Not started | |
