# Settings flow review

**Status:** In progress 2026-10-24: Phases 1 and 1b. Reviewed from the code only; no
emulator walk yet.

Follows [Settings reorganisation](./archive/20261017_settings-reorg-plan.md) and
[Settings audit fixes](./archive/20261017_settings-audit-fixes-plan.md) (archived, private).

## Goal

A Settings flow that suits three users: an expert (admin) who sets up the household, a grown-up
who is not Expert, and a child. Close the places where a child or guest can change shared setup,
keep a child in its own profile, put every setting where its name says, and shorten the admin's first run.

## Review (2026-10-24, read-only, code of TV and phone)

Settings today, both apps: Profiles / Your profile, Parental controls (TV group; phone row in
Profiles), Playback, Display, Sources & guide, Sync & backup, About & advanced / About. A non-Expert
and a child see Your profile, Playback, Display and About. Settings opens without a PIN; admin
actions ask the PIN of the profile in use (`PinSession`, unlocked until 60 min in the background or
a profile switch).

### Leaks (child or guest)

| # | Finding | Where |
|---|---|---|
| L1 | A child profile with no source sees "Add Source" on Home. Add Source needs a PIN, not Expert, so with no PIN the child adds an unfiltered source | `ContentTypeSelectionScreen.kt` (`HomeSourcesPanel`), phone TabRoot panel; `NavExtensions.kt` `isPinGuarded` |
| L2 | TV picker's Add profile is open to anyone while no Expert has a PIN: a child makes a grown-up profile | `ProfilePickerScreen.kt:150-178` |
| L3 | A child changes Language and Text & grid size for the whole device | `SettingsGroup.visibleFor` (TV `SettingsScreen.kt`), phone `SettingsScreen.kt` Display |
| L4 | A child renames itself and sets, changes or removes its own PIN | TV `ProfilesSettingsCard.kt`, phone `ProfileEditScreen.kt` |
| L5 | The PIN nudge shows only while a child profile exists; without one, 7 taps on the version make anyone Expert (accepted, SF-D13) | `ProfilesViewModel.needsAdminPin` |
| L6 | Remove PIN has no confirm | `ProfilePinFlow.kt:66-70` |

### Grouping and scope (non-Expert)

| # | Finding |
|---|---|
| G1 | Display has no theme row on either app (reorg D1 (b) kept "Theme and Look and Feel › your profile" there); Settings' Playback has one row while "Play next episode automatically" is on the profile page |
| G2 | The Profiles description says "Sources and settings are shared"; sources, look, autoplay, Expert and PIN are per profile. A `SettingsScope` chip type exists on the phone (`SettingsListRow.kt:124-127`) and nothing uses it |
| G3 | Two settings name one list two ways: "Add a channel to Last watched after" (a delay, Playback) and "Items in Recent" (a count, Display); the list is called "Last watched" in one and "Recent" in the other |
| G4 | Phone: Settings is behind the profile slot, labelled with the profile's name; nothing says Settings |
| G5 | Theme and Look and Feel are disabled until UI theme is Custom, with no reason shown |

### Expert (admin)

| # | Finding |
|---|---|
| E1 | First run: no welcome; with no source TV opens Settings on Profiles; after the first Add Source the app returns to the Sources list and Home appears only after Back |
| E2 | A new source is on only for the profile that added it (`ProviderRepository.kt:286-287`) |
| E3 | Every admin sees SMB (can't play) and Local (saves an empty config) in Add Source, because Expert is now the admin switch (`AddSourceTypes.kt`). The Expert switch's description names diagnostics and test source types, not what it grants |
| E4 | Phone: no way to fill a child's favourites; the Child profile summary says "On TV… adds here" |
| E5 | Household word lists (Parental controls) and a child's own Content filters (profile page) have no link to each other |
| E6 | TV Guide sources has 4 ways in (Settings row, Sources row Guide slot, ⋮ menu, Edit Source); the ⋮ menu's Edit does what OK does |
| E7 | Add profile asks the PIN of the profile in use in Settings, but any Expert's PIN on the TV picker |

### Wording and focus

| # | Finding |
|---|---|
| W1 | Section "Source" (singular) above "Manage sources"; title case "Add Source" / "Edit Source" is the project rule (CONTRIBUTING.md, Strings), so it stays |
| W2 | The nudge says "anyone can open Settings"; Settings opens without a PIN for everyone anyway |
| W3 | `profile_pin_desc` and `profile_pin_desc_expert` have the same text |
| W4 | TV Live sync setup panel sets no first focus when not linked (`SyncSettingsScreen.kt:94-96`) |
| W5 | Stale comments: `ThemeSettingsCard.kt:4,13`, `GuideSettingsRows.kt:8,13`, `ProfileEditPane` KDoc (Save / Cancel) |

## Decisions (user, 2026-10-24)

- **SF-D1** (L3) Hide the device-wide groups from a child profile: Display (Language, Text & grid
  size) and Playback (its one row, the Last watched delay, is per device too).
- **SF-D2** (L4) Hide Name and PIN on a child's own page.
- **SF-D3** (G5) Theme and Look and Feel stay disabled until UI theme is Custom. No change.
- **SF-D4** (G4) Phone: a small gear badge on the profile slot's avatar; the sheet is unchanged.
- **SF-D5** (E2) A new source stays on only for the profile that added it. No change.
- **SF-D10** (G2) No scope chips on rows; only the Profiles description is fixed.
- **SF-D11** (E4) The phone gets no Favourites pane; the Child profile text on the phone says a
  grown-up adds the favourites on TV. E1 and G1 kept as designed (user, 2026-10-24).
- All other items above go in without a further decision.

**Child lock (user, 2026-10-24).** This replaces the earlier rule "switching profile never asks a
PIN" (2026-10-17, re-confirmed 2026-10-19) for switches away from a child profile only.

- **SF-D6** Leaving a child profile asks any Expert's PIN (the rule used for becoming Expert).
- **SF-D7** No PIN is needed to make a child profile. While no Expert has a PIN, the lock does
  nothing; turning Child on (Add profile or a profile's page) then shows a warning: "No PIN is
  set: this child can switch to any profile. Set a PIN ›". Removing a PIN is not blocked (user,
  2026-10-24).
- **SF-D12** Add profile is for Experts only, everywhere (L2 on the TV picker; Settings already
  is). The first profile on a new install is Expert automatically (`ensureAnExpert`, already
  built).
- **SF-D8** TV launch: the picker still shows. When the last profile used on this device was a
  child, picking any other profile asks the PIN. This needs no extra code: the device keeps the
  last profile used, so the launch picker's switch is checked like any other.
- **SF-D9** A switch from a child to another child profile asks the PIN too.
- **SF-D13** (L5) The PIN nudge stays for households with a child profile only.
- **SF-D14** The child-lock prompt counts wrong PINs on its own, apart from the Settings PIN.
- **SF-D15** Leaving a child with the target profile's PIN unlocks that profile's Settings too.

## Design

- **L1** A non-Expert profile with no source sees "Ask a grown-up to add a source" instead of Add
  Source (TV and phone), as the empty kids' Home does.
  The nav gate also refuses a new-source `Screen.AddProvider` to a non-Expert (a PIN alone is not
  enough: with no PIN it is open). Edit mode stays open for the Browse sign-in path (a Jellyfin
  login is per profile).
- **L2** The TV picker shows Add profile only when the profile in use is Expert. At launch that is the
  last profile used, so after a child the launch picker has no Add profile (intended).
  Check on a new install that Settings shows the Expert layout at once: `ensureAnExpert` runs in
  the background at start and TV Settings re-reads Expert only on resume (possible race, not seen).
- **L3** A child's Settings: Your profile and About. Autoplay stays on the child's page.
- **L4** A child's page: Colour, Look, Playback. An Expert still edits the child's name and PIN.
- **L5** Dropped (SF-D13): the nudge keeps today's rule (a child profile exists, no Expert PIN);
  only its text changes (W2).
- **L6** Remove PIN asks "Remove the PIN?" first. When it is the last Expert PIN and a child
  profile exists, the confirm says the child lock stops on every linked device (the PIN syncs).
- **G1** Display gets back "Theme and Look and Feel › Your profile"; Playback gets "Play next
  episode automatically › Your profile". Both open the profile page at that row.
- **G2** The Profiles description says what a profile holds (its sources, look, favourites,
  history, PIN). No scope chips (user, 2026-10-24); the groups keep "Applies to this device only".
- **G3** The two rows use the same name for the list (Recent); the settings stay separate.
- **G4** Gear badge per SF-D4.
- **E1** With no source and an Expert profile in use, both apps open Add Source with Settings
  under it on the back stack (Back goes to Settings). After the first save, the app goes to Home
  with the stack cleared (today `AddProvider.onSuccess` only navigates up). A non-Expert in use
  gets Home with the L1 message instead.
- **E3** SMB and Local offered for a new source only in debug builds (an existing one can still be
  edited: `addSourceTypes`' `editedType` rule); the Expert switch's description says it can change
  sources, profiles, parental controls and backups.
- **E4** Per SF-D11.
- **E5** Parental controls ends with "Content filters of each profile ›"; a child's page has
  "Parental controls ›" under Admin.
- **E6** TV: the ⋮ menu loses Edit and Guide sources. The phone's card menu has no Edit; it
  keeps Guide sources (a card has no Guide slot).
- **E7** Add profile: if the profile in use is an Expert whose PIN session is unlocked (or has no
  PIN), nothing more is asked; otherwise any Expert's PIN (`PinRequest.AnyOf`). One rule in
  Settings and on the picker, never two prompts.
- **W1–W5** as listed.
- **Child lock** (SF-D6–D9):
  - The switch away from a child asks any Expert's PIN on the TV picker (launch and rail avatar),
    the phone profile sheet, and the profile page's "Switch to this profile". Picking the same
    child again asks nothing. Cancel stays on the child.
  - The PIN is checked against `ProfilePins` with its tries limit.
  - A pure check, `needsAdminPinToSwitch(targetId)`, reads the child flag, PINs and Expert state
    from `AppSettings` / the repository (not `profiles.value`, which is empty until collected), and
    fails closed. The 4 callers (TV picker, phone sheet, both apps' "Switch to this profile") show
    the prompt first; `switchTo` refuses a locked switch unless told the PIN was typed.
  - Sync: when another device deletes the child profile in use, `SyncManager.onActiveProfileDeleted`
    switches to another child profile if there is one; if not, it opens the picker, where the lock
    applies (today it takes the first other profile, which can be an Expert).
  - TV launch: covered by the same check (the device keeps `activeProfileId`).
  - No Expert PIN: the switch stays free; the nudge (L5) asks for a PIN.
  - Child switch with no Expert PIN: the warning of SF-D7 under the switch. The "Set a PIN ›" link
    only on the profile page; Add profile (a dialog, also on the TV picker) shows the text only.
  - The switch prompt has its own tries counter (SF-D14), so wrong PINs typed by a child never
    start the wait on the Expert's Settings PIN.
  - When the PIN typed is the target profile's own, the target's `PinSession` is unlocked too
    (SF-D15): opening Settings after the switch asks nothing more.
  - Not changed: the 60-min relock.

## Phases

1. **Leaks** (L1–L6, W2), TV and phone, with unit tests for the gates.
1b. **Child lock** (SF-D6–D9), TV and phone, with unit tests for who may switch where.

Every phase adds its own strings in en/fr/mg (British English) and re-records each TV focus walk
its screens change, in the same commit (CONTRIBUTING.md: picker, Settings rail, Sources menu).
2. **Grouping and wording** (G1–G4, W1, W3, W5): strings in en/fr/mg (British English), TV focus
   walk `settings.txt` re-recorded.
3. **Admin flows** (E1, E3–E7, W4).
4. **Docs and walks**: FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES; scripted walks with an Expert, a
   grown-up and a child profile on both emulators.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Leaks | In progress | |
| 1b. Child lock | In progress | |
| 2. Grouping and wording | Not started | |
| 3. Admin flows | Not started | |
| 4. Docs and walks | Not started | |
