# Reminders page

**Status:** Planned 2026-11-04. Phase 1 done (26935d276); all phases done and walked on both emulators. The user answered RP-Q1 and RP-Q2 on 2026-11-04 (see Answers).

## Goal

The user (2026-11-04): a place to manage the active and upcoming reminders.

Today a reminder can only be seen or cleared from the bell of its own game, or from Remind Me on
its own airing. This plan adds one **Reminders** page, on TV and phone, that lists every reminder
of the profile in use and lets the user open or remove each one, stop a team's "every game", or
clear all.

## What exists (survey 2026-11-04)

- **Storage.** `game_reminder` in `xtream_v2.db` (`GameReminderEntity`, `GameReminderDao`), primary
  key (`profileId`, `source`, `gameId`). Local to the device, never synced (sports teams plan →
  Reminders). Three kinds of row:
  - **A game reminder:** `source` = the sports source, `startEpoch` > 0. It rings 10 minutes before
    the start (`ReminderAlarms`), and moves when the game moves.
  - **A team rule** ("every game of this team"): `gameId` = `team:<teamId>`, `startEpoch` = 0,
    `isTeamRule`. It is never scheduled. Its games are game rows with `teamKey` set.
  - **A muted game of a team rule:** `startEpoch` < 0, no alarm. It stops the rule from adding that
    game again.
  - **A guide programme reminder:** `source` = `guide` (`GameReminders.PROGRAMME_SOURCE`), `gameId`
    = `<providerId>:<streamId>:<start>`, title in `homeName`, channel name in `competitionName`, the
    channel to tune in `lookupTeam` (`ReminderChannel` JSON).
- **Life of a reminder.** `GameReminders` deletes a reminder that rang, or whose game started
  (`rescheduleAll`, `onAlarm`). So every stored row is active or upcoming: the page needs no
  "past" section.
- **API.** `GameReminders.observe(profileId): Flow<Reminded>`, `clear(...)`, `clearForTeam(...)`,
  `setProgramme(...)`; `GameReminderDao.observeAll(profileId)`, `deleteProfile(profileId)`.
- **Where reminders are set.** The bell on a game (team and league pages, the Sports section, the
  game sheet: `TvGameBell`, `GameBells.kt`); "every game" on a team page; **Remind me** on a Search
  the Guide airing (`ProgrammeReminders.kt`); the reminder banner on TV (`TvGameReminderBanner`) and
  the notification (`GameReminderNotifications`) when one rings.
- **Navigation.** Routes in `core/navigation/.../Screen.kt`; the Sports section is
  `SportsSectionScreen` (TV) and `MobileSportsSectionScreen` (phone, `MobileScreenHeader` with icon
  actions).

## Decisions (proposed)

| # | Subject | Decision |
|---|---|---|
| RP-D1 | Data | No schema change. A `RemindersViewModel` in core/ui maps `GameReminderDao.observeAll(profileId)` to rows; writes go through `GameReminders` so alarms stay right. |
| RP-D2 | Sections | **Upcoming**: game and programme reminders together, by start time. **Every Game Of**: one row per team rule. Muted rows do not show (they only stop a rule from adding a game again). |
| RP-D3 | A row | Title (both teams, or the programme), the competition or channel, the day and time, and "Rings at <time>" (start − 10 min). A programme row also shows its channel's source badge for a Nerd, as elsewhere. |
| RP-D4 | Row actions | Icon actions (TV label on focus, phone tooltip): **Open** — a game opens its game sheet, a programme its channel (asking first while it has not started, as Search the Guide does); **Remove** — bell off, at once, with the app's undo notice where the app has one for a removal. |
| RP-D5 | Team rule row | The team (crest, name), its next game, and how many coming games it covers. **Stop** clears the rule and its game reminders (`clearForTeam`). |
| RP-D6 | Page actions | **Clear All** in the header, asking first ("Remove all reminders of <profile>?"). A note under the title: "Reminders ring on this device only". |
| RP-D7 | Empty state | The shared empty state: "No reminders. Use the bell on a game, or Remind Me in Search the Guide." |
| RP-D8 | Profile | The page shows the profile in use. A child profile has no Sports section; the page opens only from where that profile can set reminders. |
| RP-D9 | Time | Times in the device's 12/24-hour clock and the app language, with the shared date helper; a reminder today shows "Today", tomorrow "Tomorrow". |
| RP-D11 | Lead time (user, 2026-11-04) | One setting per device, as reminders ring only on their device: **at the start, 5, 10 (default), 15, 30 or 60 minutes before**, for games and programmes. A "Ring <n> min before" action in the Reminders page header opens the picker; the same setting is in Settings → This Device. A change moves the alarms of the reminders already set (`GameReminders.rescheduleAll`). Not synced. |
| RP-D10 | TV focus | Entry focus on the first row (Upcoming, else Every Game Of, else Clear All in the header). Right from a row walks its icon actions; Up from the first row reaches the header actions. After Remove, focus goes to the next row (else the previous, else the header). Back leaves the page onto the control that opened it. Follows the focus contract in CONTRIBUTING.md. |

## Answers (user, 2026-11-04)

| # | Answer |
|---|---|
| RP-Q1 | (a) A bell in the Sports section header (TV and phone) and in Search the Guide's header, plus "Manage reminders" on the TV reminder banner and in the notification. |
| RP-Q2 | (a) Reminders stay local to the device they were set on; the page says so. |

## Open questions (answered, kept for the options)

| # | Question | Options |
|---|---|---|
| RP-Q1 | Where the page opens from | (a) **A bell icon action in the Sports section header (TV and phone), in Search the Guide's header, and "Manage reminders" on the TV reminder banner and in the notification.** Recommended: it is where reminders are made and where they ring. (b) As (a), plus a row in Settings → Your Profile. (c) Only the Sports section. |
| RP-Q2 | Sync across devices | (a) **Keep reminders local, as today; the page says so.** Recommended: a reminder rings on the device it was set on, by design. (b) Sync them per profile: a separate, larger plan (alarms on every device, ringing on all of them). |

## Design

### L. Lead time (core/network, core/ui)

- `AppSettings.reminderLeadMinutes` (device key, not per profile, not in `SYNCED_SETTING_KEYS`),
  default 10, values `ReminderSchedule.LEAD_CHOICES` = 0, 5, 10, 15, 30, 60; a stored value not in
  the list reads as 10.
- `ReminderSchedule.triggerAtMs(startEpochSec, nowMs, leadSec)` and `alarm(...)` take the lead in
  place of `LEAD_SEC`; without exact alarms the window opens `leadSec` + 10 minutes before the start
  (today 20 minutes for a 10-minute lead), with the same 10-minute length. `GameReminders` reads the
  setting where it schedules.
- Setting it calls `GameReminders.rescheduleAll()`.
- The "Rings at" time on the page (RP-D3) uses the setting.
- Tests: `ReminderScheduleTest` for each lead (0 rings at the start; a past ring time rings at once;
  the inexact window), a rescheduling test.
- Strings: the row "Reminder Time" / its summary ("10 minutes before", "At the start"), the picker
  choices, the header action label; en, fr, mg.

### V. View model (core/ui)

- `core/ui/.../sports/RemindersViewModel.kt` (or next to `SportsPageViewModels.kt`):
  - `state: StateFlow<RemindersState>` = `Loading` | `Empty` | `Loaded(upcoming: List<ReminderRow>,
    teams: List<TeamRuleRow>)`, from `GameReminderDao.observeAll(activeProfileId)`, filtered and
    sorted by a pure `RemindersPage.build(rows, nowSec)`.
  - `ReminderRow(key, kind: Game | Programme, title, subtitle, startEpoch, ringsAtEpoch, open:
    ReminderTarget)`; `TeamRuleRow(teamKey, teamName, crest, nextGame, gameCount)`.
  - `remove(row)`, `stopTeam(row)`, `clearAll()` through `GameReminders` (a new
    `GameReminders.clearAll(profileId)` that cancels the alarms, then `deleteProfile`).
  - `launchGuarded` for every coroutine (CONTRIBUTING rule 13).
- Tests: `RemindersPageTest` (sorting, sections, muted rows hidden, team counts, "rings at"),
  `RemindersViewModelTest` (remove, stop, clear all go through `GameReminders`).

### T. TV

- `Screen.Reminders` route; `tv/.../feature/sports/TvRemindersScreen.kt`: page header (title, the
  note, Clear All), two `SectionHeader`s, rows as list rows with `TvIconAction`s, the empty state,
  the Clear All `CinemaAlertDialog`.
- Entry points per RP-Q1: a bell `TvIconAction` in `SportsSectionScreen`'s header; Search the
  Guide's header; "Manage reminders" on `TvGameReminderBanner` (opens the page and closes the
  banner).
- Tests: `RemindersFocusTest` (Robolectric, the focus walk of RP-D10), a `scripts/focus-walks`
  file.

### P. Phone

- `mobile/.../feature/sports/MobileRemindersScreen.kt` with `MobileScreenHeader` (Clear All icon
  action), sections, rows with `IconAction`s, the empty state, the dialog.
- Entry points per RP-Q1: a bell `IconAction` in `MobileSportsSectionScreen`'s top bar; Search the
  Guide's top bar; the notification's "Manage reminders" action (`GameReminderNotifications`: a
  second action whose intent opens the page).
- Tests: Compose tests of the rows and the empty state where the phone has them.

### S. Strings

New strings in core/ui `values`, `values-fr`, `values-mg`, in one change: the page title
("Reminders", Title Case in English), the two section headers ("Upcoming", "Every Game Of"), "Rings
at %1$s", "Reminders ring on this device only", the empty state, Clear All and its question, the
row actions' labels, "Manage reminders", "Today" / "Tomorrow" if the date helper has none. British
spelling; French and Malagasy sentence case; Malagasy follows the user's glossary (clock =
famataranandro).

## Risks

1. **An alarm left after a delete.** Removing through `GameReminders` (not the DAO) cancels the
   alarm. A test covers Clear All.
2. **A team rule that adds the game again.** Remove on a team game must mute it (as the bell does
   today), not delete it. A test covers it.
3. **TV focus after Remove.** The row goes; focus must not fall to the rail. The focus test covers it.
4. **A reminder that rings while the page is open.** It is deleted; the flow removes its row. Focus
   then follows RP-D10.

## Phases

| Phase | Owner | Work | Tests | Docs |
|---|---|---|---|---|
| 1 | lead | Design V, L and S; `GameReminders.clearAll`; the route; the This Device rows on TV and phone | `RemindersPageTest`, `RemindersViewModelTest`, `ReminderScheduleTest` | FEATURES (Reminders: lead time) |
| 2 | agent | Design T (TV page, entry points) | `RemindersFocusTest`, focus-walk file | FEATURES (Reminders), NAVIGATION_GUIDE |
| 3 | agent | Design P (phone page, entry points, notification action) | phone tests | FEATURES (Reminders) |
| W | lead | Merge, full suite, lint, gates, walks on both emulators (set a game reminder, a team rule and a programme reminder; open, remove, stop, clear all; let one ring with the page open) | | RELEASE_NOTES; this plan's Done notes |

Phases 2 and 3 run in parallel after Phase 1 (two agents, own worktrees).

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 | Done 2026-11-04: `RemindersViewModel` / `RemindersPage`, `GameReminders.clearProgramme` / `clearAll` / `setLeadMinutes`, Reminder Time setting (`AppSettings.reminderLeadMinutes`, This Device row on TV and phone), `Screen.Reminders`, strings | 26935d276 |
| 2 | Done 2026-11-04: `TvRemindersScreen` (header Reminder Time and Clear all, rows with Open and Remove reminder, team rows with Stop every game, focus per RP-D10; an empty page opens on Reminder Time, never on Clear all, by the focus contract's rule 7), bells in the Sports section and Search the Guide headers, Manage reminders as the banner's third choice. Merge: one "game gone" message for both apps | 9f7a248e6 |
| 3 | Done 2026-11-04: `MobileRemindersScreen` (Reminder Time and Clear All in the top bar, rows with Open and Remove, team rows with Stop), bells in the Sports tab and Search the Guide top bars, "Manage reminders" notification action (phone only). No Undo after Remove: the view model cannot restore a removed reminder | ab00c0446 |
| W | Done 2026-11-04. Walked on both emulators by scripted testers: all three kinds set, list and Rings at times, Reminder Time 30 (times moved, Settings shows it), Open game sheet and programme question, Remove (team game muted, count fell), Stop, a real ring with the page open (row left; phone notification Manage reminders; TV banner Manage reminders), Clear all. Walk fixes: a bell-off icon for Stop; the TV banner's buttons under its text (a long title lost its start time) (5e09e4607). Not changed: no bell in the rail Search's Guide tab; no Undo after Remove | 5e09e4607 |
