# Admin settings

**Status:** Agreed 2026-10-28 (decisions below); in progress.

## Goal

One rule decides what the PIN guards: a setting for the whole household or the whole device is
an Admin setting, and a setting of one profile is not. All Admin settings move into one Settings
entry, **Admin**, which asks the PIN once on the way in. "Expert mode" becomes "Admin" in the UI,
so one word has one meaning.

## Today

- Settings has nine groups. Six are Expert groups that each ask the PIN on the way in: Profiles,
  Parental Controls, This Device, Sources & Guide, Sync & Backup, Advanced (the settings guarded
  by page plan). Playback, Display and About ask nothing.
- Playback and Display only open rows of the profile's own page (Play next episode; Theme and
  Look and Feel).
- A profile's page mixes personal rows (name, colour, PIN, look, playback) with rows a grown-up
  sets for the profile (its sources, content filters, Child switch, Expert switch, a kid's
  favourites). The second kind shows only to an Expert.

## Audit

| Row | Scope | Goes to |
|---|---|---|
| Your profile: name, colour, PIN, theme, Look and Feel, play next episode | Profile | Your Profile, open |
| About, Device info, Terms of use, Open-source licences | Read-only | About, open |
| Profiles list, Add profile, other profiles' pages, Delete profile | Household | Admin |
| A profile's sources, content filters, Child switch, Admin switch, a kid's favourites | Profile, set by a grown-up | Admin → Profiles → the profile (AS-D1) |
| Parental Controls | Household | Admin |
| Language, Text & grid size, Items in Recent, Add a channel to Recent after, Show Clock | Device | Admin (AS-D2) |
| Manage Sources, Guide sources, Guide data maintenance, TMDB key | Household | Admin |
| Live sync, Back up, Restore | Household | Admin |
| Diagnostics, Shrink Database | Device | Admin |

## Design

1. **Settings for an Admin:** Your Profile, Admin, About. **Without Admin:** Your Profile, About
   (as today; a kid as today). Playback and Display go: their rows are on Your Profile.
2. **Your Profile** shows only personal rows, for every profile, an Admin too, with no PIN.
3. **Admin** asks the PIN of the profile in use once on the way in (`PinSession`, as today), then
   shows one page with a section header per part: Profiles, Parental Controls, This Device,
   Sources & Guide, Sync & Backup, Advanced. TV: one rail row, the page in the pane. Phone: one
   row, the page in place of the list, Back returns.
4. **Admin → Profiles → a profile** opens that profile's page with every row: personal and Admin
   rows. This is the only place the Admin rows of a profile show, also for the Admin's own profile.
5. **The screen guard** (`isExpertOnly`, `isPinGuarded`) stays as the backstop.
6. **Rename:** "Expert mode" becomes "Admin" in every UI text (en, fr, mg) and in the docs. Code
   names (`isExpert`, `ExpertGroup`) stay (AS-D5).

## Decisions

- **AS-D1 A profile's Admin rows (decided, user 2026-10-28: the recommendation):** (a) in Admin →
  Profiles → the profile; your own page keeps only personal rows; or (b) on the profile page, each
  row asks the PIN.
- **AS-D2 This Device (decided, user 2026-10-28: the recommendation):** (a) all in Admin; (b)
  Language, Text size and Show Clock open, the Recent rows in Admin; or (c) all open.
- **AS-D3 Who sees Admin (decided, user 2026-10-28: the recommendation):** (a) only an Admin
  profile, with its own PIN, as today; or (b) every grown-up profile, and any Admin's PIN opens it.
- **AS-D4 The name (decided, user 2026-10-28):** the entry is Admin, and Expert mode becomes Admin
  everywhere in the UI.
- **AS-D5 Code names (decided, user 2026-10-28: the recommendation):** (a) keep `isExpert`, `ExpertGroup` and the other code names, rename
  only the UI text and docs; or (b) rename the code too. Recommend (a): a large change that the
  user does not see.

## Phases

1. **TV:** the rail becomes Your Profile, Admin, About; Admin's page with section headers; the
   PIN on the way in; Your Profile without Admin rows; Admin → Profiles → a profile with them.
   Focus tests and walks (settings.txt, ci-settings.txt).
2. **Phone:** the same.
3. **Rename:** Expert mode to Admin in the UI text (en, fr, mg).
4. **Docs and walks:** FEATURES (Settings, Profile PIN, Expert Mode), NAVIGATION_GUIDE,
   CONTRIBUTING, RELEASE_NOTES; both emulators, as an Admin with and without a PIN, a grown-up
   and a kid.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. TV | Not started | |
| 2. Phone | Not started | |
| 3. Rename | Not started | |
| 4. Docs and walks | Not started | |
