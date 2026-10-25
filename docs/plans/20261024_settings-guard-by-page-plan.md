# Settings guarded by page

**Status:** Planned 2026-10-24. SG-D1 to SG-D3 open. Not started.

## Goal

The PIN guards whole Settings groups, not single rows. What a profile may change of its own sits
in groups everyone opens freely; what an Expert changes (the household's shared setup and this
device) sits in Expert groups, which ask the Expert's PIN once, on the way in. A new row in an
Expert group is guarded without a line of its own.

## Today

- TV `SettingsGroup`: Profiles (Your Profile without Expert mode), Parental Controls (Expert),
  Playback, Display, Sources & Guide (Expert), Sync & Backup (Expert), About & Advanced (About
  without Expert mode). The phone lists the same groups in one scrolling page.
- Each Expert row asks the PIN by itself (`guarded { }`): 15 places on TV, 9 on the phone.
  Playback and Display mix this device's rows (Expert only since 0.8.2) with rows that open the
  profile's own page.
- The screen guard (`isExpertOnly`, `isPinGuarded`) closes the household screens to non-Experts and
  asks an Expert's PIN, however they were reached. It stays as the backstop.

## Design

1. **Groups everyone sees, no PIN:**
   - **Your Profile** (an Expert: Profiles, the list; see SG-D2).
   - **Playback** and **Display:** only the rows that open the profile's own page (Play Next
     Episode; Theme and Look and Feel).
   - **About.**
2. **Expert groups, the Expert's PIN once on the way in:**
   - **This Device** (new): Language, Text & Grid Size (TV), Items in Recent, Add a Channel to
     Recent After, Share What This Device Plays (SG-D3).
   - **Parental Controls**, **Sources & Guide**, **Sync & Backup**, **Advanced** (Diagnostics,
     Shrink Database).
   A non-Expert does not see them. A kid sees Your Profile and About only, as today.
3. **Entering a group:** TV: Right (or OK) from the rail into an Expert group asks the PIN first;
   Cancel leaves focus on the rail. Phone: see SG-D1. Once typed, the PIN holds for the session
   (`PinSession`), as today.
4. **The rows lose `guarded { }`;** the group does it. The screens they open keep the screen guard.

## Decisions

- **SG-D1 The phone's Expert groups (open):** (a) Settings shows the Expert groups' headers, and
  tapping one opens it as its own page, after the PIN; or (b) the page stays one list, and an
  "Unlock Expert settings" row asks the PIN once, then shows the Expert groups below. Recommend (a):
  the same shape as TV (a page per group).
- **SG-D2 Profiles for an Expert (open):** the list of every profile opens a profile's page, which
  changes another profile. (a) An Expert group too, PIN on the way in; or (b) open, PIN only on
  opening another profile's page, as today. Recommend (a).
- **SG-D3 Share What This Device Plays (open):** today it is in Live sync. (a) Move it to This Device;
  or (b) leave it in Live sync. Recommend (b): it only matters with live sync.

## Phases

1. **TV groups:** This Device, the Playback and Display split, Advanced, the PIN on entering an
   Expert group, rows without `guarded`; focus tests and walks (settings.txt, ci-settings.txt).
2. **Phone groups:** the same, by SG-D1.
3. **Docs and walks:** FEATURES (Settings table, profile PIN paragraph), NAVIGATION_GUIDE,
   RELEASE_NOTES; both emulators, as an Expert with and without a PIN, a grown-up and a kid.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. TV groups | Not started | |
| 2. Phone groups | Not started | |
| 3. Docs and walks | Not started | |
