# Profile rules

**Status:** Planned 2026-10-24. Rules PR-1 to PR-9 and decisions PR-D1, PR-D2 taken by the user.
In progress: Phases 1–4 and the route part of 5 run in three parallel lanes (A: 1–2, B: 3 and the route, C: 4); docs last.

## Goal

Who may do what to a profile is one written set of rules, enforced below the screens (not only by
hiding controls), and documented in FEATURES.

## The rules

Words: a **kid** is a profile with Child Profile on; a **grown-up** is any profile that is not a
kid; an **Expert** is a grown-up with Expert mode on.

- **PR-1 A kid changes only its own personal settings:** colour, UI Theme, Theme, Look and Feel,
  Poster in the Background, Play Next Episode. Not its name or PIN, nothing of another profile,
  nothing of the device. *Holds today.*
- **PR-2 Only an Expert makes a profile Expert,** on that profile's page. *Holds today* for
  another profile.
- **PR-3 7 taps on the version make a profile Expert only when no Expert exists.** The app always
  keeps an Expert (`ProfileRepository.ensureAnExpert`), so in practice 7 taps do nothing. *Change:*
  today any grown-up becomes Expert with 7 taps while no Expert has a PIN.
- **PR-4 Only an Expert deletes a profile.** *Holds today,* in the screens only.
- **PR-5 Any grown-up can add a profile:** name, colour and Child Profile; never Expert. A kid
  cannot add one. *Change:* today Add Profile is for an Expert only (the settings flow review
  plan, SF-D10).
- **PR-6 A grown-up that is not an Expert makes a profile a kid only when adding it.** On an
  existing profile, Child Profile stays on its page, for an Expert only. *Holds today* for existing
  profiles.
- **PR-7 Only an Expert turns Child Profile off.** A kid never turns off its own Child Profile.
  *Holds today* (the switch is Expert-only, and a kid is never Expert: PR-8).
- **PR-9 Nobody adds a profile from the profile picker.** On the picker no profile is in use yet
  (anonymous), so there is no Add Profile there; profiles are added in Settings. *Change:* today
  TV's picker shows Add Profile while the last profile used is an Expert.
- **PR-8 A kid is never an Expert.** *Holds today* for a change made on the device
  (`ProfileRepository`); *change:* also when live sync or a backup restore brings a profile in.

Unchanged and kept: an Expert's PIN is asked before the admin rows when the Expert in use has one
(the profile PIN plan); the last Expert keeps Expert mode and cannot become a kid; the profile in
use and the last profile cannot be deleted. The child lock is off and on hold (`SwitchLock.ON`);
these rules do not depend on it.

## Today (audit, 2026-10-24)

- The rules are enforced by hiding controls. `ProfilesViewModel.addProfile`, `updateProfile` and
  `deleteProfile` and `ProfileRepository` do not check who is doing it. They do check the last
  Expert, a kid's Expert mode (`ProfileRepository`, a change on the device), and the profile in use
  and the last profile on delete.
- Live sync applies a remote profile's Expert and Child flags as they come (`SyncApplier`), then
  `ensureAnExpert`; a profile can arrive both kid and Expert. A backup restore sets Expert mode on
  the profile in use from the file without looking at Child (`SettingsExportManager`).
- The phone's profile page (`ProfileEditScreen`) shows whatever profile its route names; only the
  screens before it keep a grown-up on its own page.

## Design

1. **One check, in `ProfilesViewModel`:** a small pure function `ProfileRules` takes the profile in
   use (kid, Expert) and the change (add, update with its fields, delete) and says allowed or not,
   with the fields a non-Expert may not touch put back. `addProfile`, `updateProfile` and
   `deleteProfile` apply it; the screens keep hiding what is not allowed. Unit tests cover every
   rule.
2. **Add Profile for every grown-up (PR-5):**
   - TV Settings: a grown-up without Expert mode has its own page as the Profiles pane; Add Profile
     goes at the end of that page (PR-D1).
   - Phone Settings: the Profiles group gets an Add Profile row under the profile's own row.
   - TV profile picker: no Add Profile (PR-9).
   - The Add dialog: name, colour, Child Profile; Expert mode only for an Expert. The PIN asked
     first: PR-D2.
3. **7 taps (PR-3):** the tap handler and `SettingsViewModel.enableDevMode` do nothing while an
   Expert exists. The "turn on Expert mode with 7 taps" part of the PIN reminder text goes.
4. **Kid never Expert on the way in (PR-8):** `SyncApplier` and the backup restore clear Expert
   mode on a profile that is a kid, then `ensureAnExpert` as today.
5. **The phone page's route:** `ProfileEditScreen` opens another profile's page only for an Expert;
   else it shows the profile in use's own page.

## Decisions

- **PR-D1 Where Add Profile goes on TV for a grown-up without Expert mode (decided, user
  2026-10-24: (a), a non-Expert can add a profile):** (a) a row at the
  end of its own page, above Switch and Delete; or (b) a second item in the Settings rail's
  Profiles pane before the page. Recommend (a): the pane stays the page, one more row.
- **PR-D2 PIN on Add Profile (decided, user 2026-10-24: (a), no PIN):** (a) none: any grown-up may add, so a PIN guards nothing; or
  (b) keep today's rule (an Expert's PIN while one has a PIN). Recommend (a), matching PR-5.

## Phases

1. **Rules and checks:** `ProfileRules`, applied in `ProfilesViewModel`; tests for PR-1 to PR-7.
2. **Add Profile for grown-ups:** TV Settings, phone Settings, none on the TV picker, the dialog's fields;
   focus tests and walks.
3. **7 taps:** PR-3, the reminder text in en, fr and mg.
4. **Sync and restore:** PR-8 in `SyncApplier` and the restore; tests.
5. **The phone page's route** (Design 5) **and docs:** FEATURES (a Profile rules list in the
   Profiles section, the profile PIN paragraph), NAVIGATION_GUIDE, RELEASE_NOTES; walks on both
   emulators.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Rules and checks | In progress | |
| 2. Add Profile for grown-ups | In progress | |
| 3. 7 taps | In progress | |
| 4. Sync and restore | In progress | |
| 5. Phone page route and docs | In progress | |
