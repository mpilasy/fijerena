# Profile PIN

**Status:** In progress (started 2026-10-17). Decisions D1–D3 made.

An optional 4-digit PIN per profile. It locks the profile (picking it, launching into it) and, on
an Expert profile (the admin, for now — see `project_admin_is_expert`), it is the admin PIN: the
admin-only parts of the app ask for it. A profile without a PIN behaves exactly as today.

This keeps kids and guests out; it is not real security (adb or root reads the app's data), the
same level as a streaming service's profile lock.

## Decisions

| | Decision |
|---|---|
| D1 | Scope: a per-profile lock **and** admin protection on Expert profiles (not an admin-only PIN). |
| D2 | Length: exactly 4 digits. |
| D3 | Recovery: an Expert removes another profile's PIN; the last Expert who forgets theirs clears the app's data (or uses a second Expert). Setting a PIN on the last Expert warns about this. No other reset. |

Defaults taken (change before building if wrong):

- **Unlock lasts the session:** until the app has been in the background 5 minutes or the
  profile changes. One unlock covers every admin action in that session.
- **Throttling per device:** after 5 wrong PINs, a wait that doubles from 30 s (30 s, 1 min,
  2 min… capped at 15 min); a right PIN resets it.
- **Not in backups:** exports leave PINs out, so an import never locks anybody out; live sync
  carries them.

## What asks for the PIN

| Moment | TV | Phone |
|---|---|---|
| Picking a locked profile | "Who's watching?" | Profile menu (dock slot) |
| Launching into a locked last profile | Picker (or a PIN screen with one profile) | PIN screen before the last tab; Cancel opens the profile menu insisting on a choice |
| Expert profile, first admin action of a session (other profiles' pages, Add/Delete profile, Source & guide, Live sync, Backup & storage, Diagnostics) | PIN dialog | PIN dialog |
| The 7 taps on the version, on a profile whose PIN is unset | unchanged | unchanged |
| The 7 taps while any Expert has a PIN | PIN of an Expert profile asked; the right one turns Expert on for the profile in use | same |
| Changing or removing one's own PIN | current PIN first | current PIN first |

The 7-tap rule matters: without it a kid on an unlocked profile taps 7 times and is admin. With
no PIN anywhere it stays as today.

## Storage and sync

- Per profile: `pin_hash_<profileId>` = PBKDF2-HMAC-SHA256 (random 16-byte salt, ~100k
  iterations, sized to stay under ~150 ms on the Shield) as `salt:hash`, base64. Never the PIN.
- Added to `PER_PROFILE_SETTING_KEYS`, so it syncs like Expert mode, inside live sync's existing
  end-to-end encryption; removing a PIN syncs as a blank value. Deleting a profile drops it.
- Throttle state (`pin_failures_<profileId>`, `pin_locked_until_<profileId>`) is device-only.
- `ProfilePins` (core/network) holds hashing, verification and throttling; unit-tested on the JVM.

## UI

- **Profile page** (TV and phone): a **PIN** row — Set / Change / Remove. An Expert sees Remove
  on other profiles' pages (that is the "forgot my PIN" path). Setting asks twice.
- **PIN entry:** four dots and a 3×4 number pad (1–9, Back, 0, OK). TV: the D-pad moves on the
  pad, digit keys on a remote type directly; phone: the number keyboard. Wrong PIN shakes the
  dots and says how many tries are left, then shows the wait.
- All built from the design system's style classes (no raw values: the gate applies).

## Phases

| Phase | Work | Status |
|---|---|---|
| 1 | `ProfilePins` (hash, verify, throttle) + `AppSettings` keys, sync, deletion cleanup; tests | In progress |
| 2 | PIN entry component (TV pad, phone keyboard) and the profile page's PIN row | Not started |
| 3 | Locked profiles: picker, phone profile menu, launch | Not started |
| 4 | Admin gate: session unlock, admin-only screens, the 7 taps | Not started |
| 5 | Docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES), emulator walks on both apps | Not started |

## Risks

- **Sync race:** two devices setting different PINs at once — last writer wins, like any setting.
- **Locked out of admin:** the invariant (always an Expert, `ProfileRepository.ensureAnExpert`)
  still holds, but if every Expert has a forgotten PIN only clearing data helps (D3).
- **Older builds** in the group ignore the PIN key: a device not yet updated isn't locked.
