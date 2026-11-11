# Admin Sections Without the Admin List

**Status:** In progress since 2026-11-11: Phases 1 (TV) and 2 (phone) done; Phase 3 (emulator check) next.

## Goal

An Admin gets to an Admin section in one step less, on TV and phone. The list of Admin's five
sections goes; each section is reached straight from Settings. Who may change what does not
change.

## Today

- **TV:** the Settings rail is Your Profile, Admin, About (Your Profile, About for a profile that
  is not an Admin). Right on Admin asks the PIN once per session, then the pane shows Admin's list
  of five sections: Profiles, Parental Controls, This Device, Sources & Guide, Sync & Backup. OK on
  a section shows its rows in place of the list (`‹ Sources & Guide`); Left and Back close it. A
  choice row (Language, Text & Grid Size, TMDB key) opens a picker in place of the rows. An Admin
  goes rail → list → section → picker.
- **Phone:** Settings has an Admin header with one row, Open Admin. A tap asks the PIN, then shows
  Admin's list of sections as a page; a tap on a section opens its page (Parental Controls opens
  its own screen).
- The rules are below the screens: `SettingsViewModel` changes a device setting only for an Admin,
  and the screen guard closes the household screens to other profiles.

## Design

- **TV rail, for an Admin:** Your Profile, then a heading "Admin" (text only, not focusable), then
  Profiles, Parental Controls, This Device, Sources & Guide, Sync & Backup, then About. A profile
  that is not an Admin keeps Your Profile and About. A child profile is unchanged.
- **TV pane:** Up and Down on the rail show the focused group's rows, as for every group today,
  except a locked Admin section: while the PIN is not typed in this session, its pane says only
  "PIN needed. Press OK to enter it." (AD-D2). Right (or OK) on an Admin section asks the PIN once per session (`PinSession`), then focus goes
  to the section's first row; Cancel leaves focus on the rail row. Once the PIN is typed, the other
  Admin sections open without it. Left and Back in a section go to its rail row. A row that leads
  to another section (Parental Controls' Profiles row, a child's page → Parental Controls) moves the
  rail selection to that section and focuses its first row.
- **Phone:** under the Admin header, the five section rows (title and the summary they have today,
  as the Admin list shows them now). A tap asks the PIN once per session, then opens the section's
  page; Parental Controls opens its screen. Back from a section returns to Settings, on its row.
- **Unchanged:** the rows inside each section, the pickers, the PIN rules, the screen guard,
  `SettingsViewModel`'s checks, what a child and a grown-up who is not an Admin see.
- **Code:** TV `SettingsGroup` gets the five sections as `adminOnly` entries in place of `ADMIN`,
  and `AdminPart`, the Admin list, `adminSection` and the `‹ section` title go. Phone: the Admin
  list page (`adminOpen`, `ADMIN_LIST_PAGE`) goes; `adminSection` alone says which page shows.
  The strings `settings_open_admin` and `settings_group_admin_desc` go (en, fr, mg).

## Decisions

- **AD-D1 Rail heading (decided 2026-11-11):** a text heading "Admin" over the five sections on
  TV, so Profiles does not read as part of Your Profile.
- **AD-D2 Locked pane (decided 2026-11-11):** before the PIN, a locked section's pane shows only
  "PIN needed. Press OK to enter it." (`TvEmptyState`, lock icon, not focusable), not its rows.
  New string `settings_section_pin_needed` (en, fr, mg).

## Phases

One commit per phase; each phase builds and passes its tests, and updates its docs and this plan.

1. **TV:** `SettingsGroup` and the rail (AD-D1, AD-D2), the pane without the Admin list, focus
   moves between sections. Tests: `SettingsGroupTest`, `SettingsRailFocusTest`,
   `SettingsAdminGroupPinFocusTest`, `SettingsProfilesFocusTest`, `SettingsProfilePageFocusTest`,
   `SettingsParentalControlsFocusTest`, `SettingsThisDeviceFocusTest`,
   `SettingsSourcesGuideFocusTest`, `SettingsSyncBackupFocusTest`, `SettingsAboutFocusTest`, the
   test fixture; walks `scripts/focus-walks/settings.txt` and `ci-settings.txt`. Docs:
   FEATURES (Settings), NAVIGATION_GUIDE (Settings tree, TV navigation), RELEASE_NOTES.
2. **Phone:** the five rows under Admin, the Admin list page removed, the two strings removed.
   Tests: `SettingsAdminGroupTest`, `SettingsAdminTest`. Docs as in Phase 1.
3. **Check:** both emulators, as an Admin with a PIN, an Admin without one, a grown-up and a
   child (ask before installing).

## Progress

| Phase | What | State |
|---|---|---|
| 1 | TV rail and pane | Done 2026-11-11 |
| 2 | Phone Settings list | Done 2026-11-11 |
| 3 | Emulator check | Not started |
