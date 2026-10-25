# One Jellyfin login per source

**Status:** Planned 2026-10-24. JF-D1 and JF-D2 open. Not started.

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

- **JF-D1 Which login a source keeps on upgrade (open):** (a) the login of the Expert profile in use
  when it has one, else the oldest Expert's, else the most recently used profile's; or (b) the most
  recently used profile's. Recommend (a).
- **JF-D2 Older app versions on other devices (open):** a device not yet updated still reads and
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
| 1. Storage and upgrade | Not started | |
| 2. Sync | Not started | |
| 3. Screens | Not started | |
| 4. Docs and walks | Not started | |
