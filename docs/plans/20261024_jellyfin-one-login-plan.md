# One Jellyfin login per source

**Status:** Done 2026-10-25: built (4d93ac8e, 5e549d99, 5381405f, docs 03f1e104) and walked on both emulators: the upgrade ran cleanly, Edit Source shows the household note, and a profile without Expert mode browsed the Jellyfin library with no sign-in. "Ask an admin" (a source with no login) was not reachable on the emulators.

## Goal

A Jellyfin source works like every other source: an Expert sets it up once, with one login, in
Add Source or Edit Source, and turns it on or off for each profile. No profile signs in by itself.
Watch progress, watched marks and favourites on that source are the Jellyfin user's, so every
profile that has the source shares them (user, 2026-10-24).

## Today

- Each profile has its own Jellyfin login for a source (sync kind `provider_login`, per profile,
  keyed by the source's `providerKey`; `ProviderRepository`, `LocalRecords`, `SyncApplier`,
  `SettingsSyncQueue`).
- A profile with no login is sent to the source's sign-in (Edit Source). Since PR-12 (the profile
  rules plan) that screen is an Expert's only, and a non-Expert reads "Ask an admin to sign it in".
- Jellyfin keeps watch state on its server for the signed-in user (`usesServerUserData`).

## Design

1. **One login per Jellyfin source,** stored with the source as the Xtream password is, set in Add
   Source and Edit Source by an Expert.
2. **Moving today's logins (JF-D1):** on upgrade, each Jellyfin source keeps one of its profiles'
   logins as the source's login; the others are dropped.
3. **Sync (JF-D2):** the source's login syncs with the source, for the whole household; per-profile
   `provider_login` records stop being written, and those that arrive are applied as the source's
   login only when the source has none.
4. **Per profile:** only on or off, in the profile's Sources, as for any source.
5. **No login yet:** Browse and Home show the source as not set up: an Expert gets the way to Edit
   Source; anyone else reads "Ask an admin to sign it in".
6. **Watch state:** unchanged code: Jellyfin's server keeps it, now for the one user every profile
   shares.

## Decisions

- **JF-D1 Which login a source keeps on upgrade (decided, user 2026-10-24: the recommendation):** (a) the login of the Expert profile in use
  when it has one, else the oldest Expert's, else the most recently used profile's; or (b) the most
  recently used profile's. Recommend (a).
- **JF-D2 Older app versions on other devices (decided, user 2026-10-24: the recommendation):** a device not yet updated still reads and
  writes per-profile logins. (a) Update every device together and accept that a device on an older
  version keeps asking for sign-in; or (b) keep writing the per-profile record too, for one release.
  Recommend (a): the household's devices are updated together.

## Phases

1. **Storage and upgrade:** the source's login, the move of today's logins (JF-D1), tests.
2. **Sync:** the login with the source, old records (JF-D2), tests.
3. **Screens:** the login in Add Source and Edit Source (Expert), no per-profile sign-in, Browse
   and Home for a source with no login; TV and phone.
4. **Docs and walks:** FEATURES, NAVIGATION_GUIDE, DATABASE_SCHEMA if storage changes,
   RELEASE_NOTES; both emulators with the jellyxtream and a Jellyfin source.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Storage and upgrade | Done | 4d93ac8e |
| 2. Sync | Done | 5e549d99 |
| 3. Screens | Done | 5381405f |
| 4. Docs and walks | Done, walked on both emulators | 03f1e104 |

## Done notes

- **Storage:** the source's login is `providers.username` plus `password` in `provider_creds_<id>`,
  as for Xtream; a Jellyfin session (`jellyfin_token`, `jellyfin_user_id`) stays in the same file,
  per device. A profile switch keeps the Jellyfin session; deleting the Default profile keeps the
  login. No Room schema change (one new read query on `watch_state`).
- **Upgrade (JF-D1):** `ProviderRepository.moveJellyfinLoginsToSources`, a startup step after the
  profile settings move. "Most recently used" is the profile in use, then the profile with the
  latest `watch_state` change on this device. Each device upgrades on its own; when two devices
  pick different logins, the provider record's last writer wins.
- **Sync (JF-D2):** the login rides in the `provider` record (sealed end to end). No
  `provider_login` record is written; a pending one is dropped. One received becomes the source's
  login only while the source has none, and is then sent on with the source. A Jellyfin `provider`
  record with no username (an older device whose Default profile had none) keeps the login here.
- **Screens:** Add Source and Edit Source say the login is the household's; the "Ask an admin"
  text now names the source, not the profile. Exports still carry no password.
- **Not settled by the plan:** a Quick Connect login has no password and its token stays on the
  device, so the group's other devices get the username only and fail to connect until the source
  is signed in there with a password. Edit Source still offers Quick Connect only when adding.
