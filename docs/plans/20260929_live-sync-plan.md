# Live Sync Plan

**Requirement:** 3–4 devices on one account stay in sync as things change. Pause on the TV, pick
up the phone, and it resumes at the same position within seconds. Favorites, watch state and
selected settings follow the user across devices without a manual export/import. A household
sharing devices gets **user profiles**: each person's favorites and watch history are their own
and follow them to any device.

**Status:** design agreed 2026-09-29 (see Decisions); no open questions. Phases 1–2 landed
2026-09-29; Phases 3–9 not started.

---

## Why not upload the export file

`SettingsExportManager` already produces a whole-account JSON (version 5). Measured on a real
export and a simulated upper bound (2 providers × 100 favorites × 1000 watch rows):

| | Pretty JSON | Compact JSON | Gzip |
|---|---|---|---|
| Real file today (275 watch rows) | 171 KB | 110 KB | 11 KB |
| Upper bound | ~1.0–1.1 MB | ~680 KB | ~80–90 KB |

Uploading the whole blob works for occasional sync, but not live: every position save would ship
~80 KB, and last-writer-wins on the whole file makes two devices overwrite each other's progress.
A watch-state row is ~300 bytes compact. **Sync records, not files.** The export stays as it is: a
point-in-time backup (see Decisions).

## Design

### Record model

Every synced thing is one record:

```
key        = (profileKey, providerKey, kind, itemId, contentType)
payload    = the row's fields (JSON, encrypted — see Security)
updatedAt  = hybrid logical clock value (see Conflicts)
deleted    = tombstone flag
seq        = server-assigned, monotonically increasing per account
```

`kind` is one of `watch`, `watch_clear`, `favorite_stream`, `favorite_category`, `setting`,
`provider`, `provider_login`, `category_filters`, `epg_source`, `profile`.

`profileKey` applies to per-person data (`watch`, `watch_clear`, `favorite_*`,
`provider_login`, `category_filters`, and the per-profile `setting` dev mode). Shared data
(`provider`, `epg_source`, other `setting`s, `profile`) uses a fixed `shared` value in that slot.

**`providerKey` is not `providerId`.** `providerId` is a local autoincrement `Long` — the same
provider has different ids on different devices. `providerKey` is a random UUID generated when the
provider is created and carried in its `provider` record, so it survives the user editing the
provider's URL or name. (A hash of URL + username would not: editing the URL would orphan every
record keyed on the old hash.)

Providers that already exist on several devices before sync is turned on get different UUIDs.
On first sync, a device adopts the remote UUID of any provider matching one of its own by
normalized URL + username — the same identity the importer matches on today (name + URL), with
username added because two accounts on one host are distinct providers.

### Flow

1. **Local write.** `MediaRepository` writes the row (as today) *and* an outbox row, in one Room
   transaction.
2. **Push.** A sync client drains the outbox: `POST /changes` with a batch of records. Server
   assigns `seq`, stores, responds with the new head `seq`.
3. **Notify.** Server broadcasts the new head `seq` over WebSocket to the account's other
   connected devices.
4. **Pull.** A notified device calls `GET /changes?since=<cursor>`, applies records, advances its
   cursor.
5. **Catch-up.** On app start/resume (and once on network regain) every device pulls from its
   cursor. A closed or dozing app needs no push — it catches up when opened. **No FCM / Firebase
   dependency**, and nothing that fights Doze (see `project_epg_doze_dns_failure`).

WebSocket is only held while the app is in the foreground.

### Write cadence for watch state

Progress is written far more often than the other devices need it. Push on pause, stop, exit,
episode change, and every ~30 s during playback — coalesced in the outbox by key so only the
latest position per item is sent.

### Applying remote records

- Apply through the same DAOs, inside a transaction, **without** writing to the outbox (a
  `fromRemote` flag on the write path) — otherwise every applied record echoes back.
- `MediaRepository` keeps an in-memory snapshot (`cachedFavorites`, `favoriteIdSet`,
  `cachedWatchHistory`, `recentItemsFlows`) because Compose reads it synchronously (see
  [20260828_favorites-durable-storage-plan.md](20260828_favorites-durable-storage-plan.md)). A
  remote apply must invalidate/refresh that snapshot and re-emit the Recent flows, or the UI shows
  stale state until restart.
- A `provider` record for a `providerKey` not present locally adds the provider (password
  included). Pull order: providers first, then the records that belong to them.

### Conflicts

Per-record last-writer-wins on `updatedAt`. With 3–4 devices, simultaneous edits to the same
item are rare; losing one of two near-simultaneous position saves is acceptable.

Device clocks can't be trusted (TV boxes drift). Use a **hybrid logical clock**: each device's
clock is `max(wallClock, lastSeenRemoteClock + 1)`, so a device with a slow clock can't keep
losing and one with a fast clock can't keep winning forever.

No per-field or per-kind exceptions: the server applies the same rule to every record (it can't
read payloads anyway). A stale save from a device that was playing concurrently carries an older
HLC value and loses; rewatching a completed item from the start is a genuinely newer write and
wins, as it should.

### Deletions (tombstones)

The schema has none today: `removeFavorite` hard-deletes the `favorite_state` row, and
`clearWatchHistory` does `watchStateDao.deleteAll`. Without tombstones, a removed favorite is
resurrected by the next device that syncs.

- Deletes become a synced record with `deleted = true`.
- Locally: either a `deletedAt` column on the tables (reads filter it) or a separate
  `sync_tombstone` table. **Prefer the separate table** — no read path changes, and the 26
  favorite consumers stay untouched.
- Server keeps tombstones ≥ 90 days; a device whose cursor is older than the tombstone horizon does
  a full resync instead of an incremental pull.
- Deleting a provider writes a `provider` tombstone. Receiving devices delete the provider and
  its local rows (watch state, favorites, EPG sources linked to it); the server drops that
  provider's other records when it stores the tombstone. The server can't read keys, so each
  record carries an opaque `provider_tag = HMAC(accountKey, providerKey)` column to group by.
- "Clear watch history" becomes a single `kind=watch_clear` record with a timestamp: every watch
  row for that provider with `updatedAt` below it is dropped.

### What syncs

| Data | Sync? | Notes |
|---|---|---|
| Watch state | yes, **except Jellyfin** | Primary use case |
| Favorite streams / categories | yes, **except Jellyfin** | |
| Providers (name, URL, username, **password**, type, config) | yes | Password inside the E2E payload — see Security |
| EPG sources | yes | |
| Theme, EPG auto-refresh | yes | |
| Dev mode | yes, **per profile** | `setting` record with the profile's `profileKey` |
| Category filters | yes, **per profile and provider** | `category_filters` record (`profileKey` + `providerKey`) |
| UI scale, cellular multipliers | **no** | Per-device by nature (TV vs phone) |
| Caches, EPG programme data | no | Re-downloaded |

### Jellyfin: provider only, not its user data

Jellyfin already keeps favorites, watch history and resume positions on its own server and syncs
them across every Jellyfin client. Syncing them through us as well would give two sources of truth
that can disagree. So for a provider whose `capabilities.supportsServerUserData` is true (Jellyfin
today):

- The **provider record** syncs (URL, username, password, config), so adding a Jellyfin server on
  one device adds it everywhere.
- **No `watch`, `watch_clear`, `favorite_stream` or `favorite_category` records** are written to
  the outbox for it, and any such record received for it is ignored.

`MediaRepository` already branches on `usesServerUserData` for every user-data read and write,
so the outbox hook goes on the local-storage side of those branches and Jellyfin never reaches it.
Phase 4 must verify that no local user-data write path for a Jellyfin provider bypasses that gate.

### User profiles

Separate favorites and watch history per person on shared devices. Netflix-style picker, no PIN.

**What is per profile vs shared:**

| Per profile | Shared by the household |
|---|---|
| Watch state, resume positions, Recent rows | Providers: URL, type, config; login of non-Jellyfin providers |
| Favorite streams and categories | EPG sources |
| Recent Categories, last-browsed bookmarks | |
| **Jellyfin login** (username + password) | Global settings (theme etc.), per-device settings |
| **Category filters** (per provider), **dev mode** | |

**Identity.** A profile is a random UUID (`profileKey`) plus a name and avatar colour, synced as a
`profile` record. Creating, renaming or deleting one on any device applies everywhere. The one
exception is the profile every install starts with: its id is the fixed string `default`, not a
UUID, so every device's pre-existing data lands in the same profile once sync is on instead of each
device contributing its own "Default".

**Existing data.** On upgrade, one `Default` profile is created and every existing
`watch_state` / `favorite_state` row is assigned to it. Users who never add a second profile see no
picker and no change.

**Where profiles appear in the UI** (decided 2026-09-29):

1. **"Who's watching?" picker** — full screen, before Content Type Selection, only with 2+
   profiles. TV: centred row of large D-pad cards (colour circle with initial, name below), focus
   starts on this device's last profile. Mobile: 2-column grid. Last card is "+ Add profile".
   **TV shows it on every launch** (shared screen, avoids watching as the wrong person);
   **mobile skips it** and reopens as the last profile.
2. **Header avatar** on Content Type Selection, next to Settings, **always shown** (so profiles
   are discoverable with only one). Opens the same picker. Switching evicts cached
   `MediaRepository` instances and reloads the home screen for the new profile.
3. **Jellyfin sign-in** — a profile with no login for the active Jellyfin server gets a sign-in panel
   on home in place of the library; its button (and, once per process, an automatic redirect) opens
   that server's sign-in (existing Jellyfin form, Quick Connect included).
4. **Settings → Profiles** — add, rename, change colour, delete (confirmation, warns that the
   profile's favourites and history go with it). The last profile can't be deleted, nor can the
   profile the device is using — switch away first. `default` can be renamed and deleted like any
   other: its Jellyfin logins (`providers.username` + password/session in `provider_creds_<id>`, on
   Jellyfin providers only), Recent Categories, bookmarks and legacy blobs in `media_cache_<id>` are
   cleared, while other providers' shared logins and the migration flags stay. Once it's gone, adding
   a Jellyfin server stores no login for it.

**Category filters and dev mode** are per profile (decided 2026-09-30; design in
`docs/plans/20260930_profile-scoped-settings-plan.md`). Each profile has its own complete filter
set per provider, stored in the `category_filters` prefs (`<providerId>_<profileId>`); Xtream's shared
`excluded` flags are recomputed for the active profile on switch. Dev mode is a per-profile flag.
On upgrade both were copied to every existing profile.

**Active profile** is per device, not synced (the TV and a phone are used by different people at
the same time). Remembered across restarts. With more than one profile: picker on app start, and
"Switch profile" in settings on `:tv` and `:mobile`.

**Local schema.** `watch_state` and `favorite_state` primary keys gain `profileId`:
`(providerId, profileId, itemId, contentType[, kind])`. New `profiles` table (`providers.db`). Every DAO query takes
the active profile. `MediaRepository` keeps its in-memory snapshot (`cachedFavorites`,
`favoriteIdSet`, `cachedWatchHistory`, `recentItemsFlows`) for the **active profile only** and
reloads it on switch — the synchronous `isFavorite()` Compose reads, and the 26 favorite consumer
files, stay unchanged.

**Deleting a profile** writes a `profile` tombstone. Devices drop that profile's rows; a device
whose active profile was deleted falls back to the picker (or `Default`). The server cascades via
a `profile_tag = HMAC(accountKey, profileKey)` column, same mechanism as `provider_tag`. The last
remaining profile can't be deleted.

**Privacy:** none between profiles — one shared account key, no PIN. Profiles separate data, they
don't protect it.

**Jellyfin: one login per profile** (decided 2026-09-29). Jellyfin keeps favorites and history
per Jellyfin user, so each profile signs in as its own Jellyfin user and gets its own Jellyfin
data for free — nothing of it passes through our sync (see Jellyfin section).

- The Jellyfin *server* (URL, config) is shared, like any provider. The *login* is per profile.
- Storage (as built — no `provider_login` table): the `default` profile keeps the login providers
  always had, `providers.username` plus the `provider_creds_<id>` EncryptedSharedPreferences
  (password, `jellyfin_token`, `jellyfin_user_id`), so nothing moves on upgrade. Any other
  profile's Jellyfin login lives in its own `provider_creds_<id>_profile_<profileId>` file
  (username, password, session). `ProviderRepository.credsFileName`, `getLogin` and `hasLogin`
  pick the right one; provider and profile deletion remove the per-profile files.
- On upgrade, the existing Jellyfin login is therefore the `Default` profile's login.
- A profile with no login for the active Jellyfin server sees a sign-in panel on home in place of
  the library, with a "Sign in" button to that server's edit screen (username/password or Quick
  Connect). The first time per process the edit screen also opens by itself. The provider isn't
  hidden, so the profile can sign in, and the header stays usable to switch profile or provider.
- Switching profile rebuilds the Jellyfin `MediaProvider` with that profile's session.
- **Sync:** username + password sync per profile as a `provider_login` record
  (`profileKey` + `providerKey`), E2E encrypted. The session token does **not** sync: each device
  signs in itself and gets its own Jellyfin session, as Jellyfin expects one per device.
- Every other provider (Xtream, Local — anything whose `capabilities.supportsServerUserData` is
  false) is unchanged: one login, shared by every profile.

## Server

Tiny: one account = one ordered record table + a set of WebSocket connections.

### Pluggable host: a URL in settings

The app does not know or care where the server runs. Settings has a **Sync server URL** field;
the app speaks one protocol to whatever is there. Two supported deployments of the *same*
server:

- **Cloudflare:** Worker + one Durable Object per account. The DO holds the SQLite record table
  and the WebSockets (hibernation API — idle connections cost nothing).
- **Self-hosted:** the same TypeScript run under `workerd` (Cloudflare's open-source runtime) in a
  Docker container. `workerd` stores Durable Objects in SQLite; `durableObjectStorage = (localDisk
  = ...)` persists them to a mounted volume, and `enableSql = true` exposes the `storage.sql` API.
  One codebase, two targets. **Risk:** `localDisk` is marked *experimental, subject to
  backwards-incompatible change* in `workerd.capnp` — pin the `workerd` version in the image and
  back up the volume before upgrading. Fallback if it breaks: split into `server/core` + thin
  `cloudflare/` and `node/` (better-sqlite3 + ws) entry points; logic and tests stay shared.

**Language: TypeScript** (decided 2026-09-29). Lives in this repo under `server/`, so a protocol
change to app and server lands in one commit.

The client sends a WebSocket ping every 30 s, so idle connections survive reverse-proxy timeouts
(nginx drops idle upstreams after 60 s by default). The server answers with
`setWebSocketAutoResponse`, which replies without waking a hibernated Durable Object — a
server-initiated ping would wake it every 30 s and defeat hibernation.

### Self-hosting

```yaml
services:
  fijerena-sync:
    image: ghcr.io/<you>/fijerena-sync:<pinned>
    restart: unless-stopped
    volumes:
      - ./data:/data        # Durable Object SQLite files — back this up
    ports:
      - "8787:8787"
```

Behind Nginx Proxy Manager: proxy host → `http://<host>:8787`, **Websockets Support on**, usual
Let's Encrypt cert. App URL is then `https://sync.<domain>`.

Because the URL can point anywhere, **the protocol is identical and always end-to-end encrypted**,
self-hosted included. That is why the payload is an opaque blob and not columns: one client code
path, and a self-hosted box or a leaked Cloudflare account exposes nothing readable.

### Storage

```sql
CREATE TABLE records (
  key        TEXT PRIMARY KEY,   -- HMAC(accountKey, providerKey|kind|itemId|contentType)
  provider_tag TEXT,             -- HMAC(accountKey, providerKey); lets a provider delete cascade
  profile_tag  TEXT,             -- HMAC(accountKey, profileKey); lets a profile delete cascade
  seq        INTEGER NOT NULL,   -- server-assigned, increases on every write
  updated_at INTEGER NOT NULL,   -- HLC value from the device
  deleted    INTEGER NOT NULL,   -- tombstone flag
  payload    BLOB                -- AES-GCM ciphertext, ~300 B; null when deleted
);
CREATE INDEX records_seq ON records(seq);

CREATE TABLE devices (
  token_hash TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  last_seen  INTEGER NOT NULL,
  revoked    INTEGER NOT NULL DEFAULT 0
);
```

One row per item, not per edit: a write to an existing key overwrites it with a new `seq`.
A record whose `updated_at` is older than the stored one is rejected, so a delayed upload can't
roll state back. Tombstones purged after 90 days. ~2,200 rows / ~1–2 MB for the upper-bound
account.

### Endpoints

- `GET /info` — protocol version; the settings screen calls it to validate a URL before saving
- `POST /changes` — batch upsert, returns head `seq`
- `GET /changes?since=<seq>&limit=500` — paged pull
- `GET /ws` — WebSocket, server → client `{"head": <seq>}` messages only
- `POST /pair` — issue a device token for a pairing code

## Security

The records reveal provider hosts, usernames, passwords and full viewing history.

- **End-to-end encryption.** Account key (256-bit) generated on the first device. Payloads
  encrypted with AES-GCM on device. Record keys sent as `HMAC(accountKey, key)` so the server can
  upsert by key without learning item ids. Server sees opaque keys, ciphertext, `seq`.
- **Pairing.** New device scans a QR code shown by an already-paired device (TV shows, phone
  scans — or the other way round). QR carries the **server URL**, the account key and a one-time
  pairing token, so a new device never has the URL typed in by hand. Account
  key stored in EncryptedSharedPreferences.
- **Passwords sync** (decided 2026-09-29), inside the E2E-encrypted provider payload. A new
  device needs no re-entry. Consequence: the account key now unlocks provider credentials, so the
  pairing QR must only be shown on demand, never persisted as an image, and revoking a device
  should prompt to rotate the account key.
- Device tokens revocable from any paired device.

## Phases

Profiles first, then sync: profiles change the `watch_state` / `favorite_state` primary keys,
every DAO query and the `MediaRepository` snapshot — the same tables and write paths the outbox,
tombstones and merge engine are built on. Doing profiles first means sync is built once on the
final schema. Profiles are also useful on their own (a shared TV) and need no server.

**Profiles**

1. **Profiles schema** — *landed 2026-09-29.* `profiles` table in `providers.db` (v11) keyed by
   the profile id (the same value sync uses as `profileKey`, so no mapping table later), seeded
   with `default`. `profileId` added to the `watch_state` and `favorite_state` primary keys and
   indices (`xtream_v2.db` v20), existing rows assigned to `default`; the TMDB sibling joins in
   `XtreamStreamDao` / `XtreamEpisodeDao` are profile-scoped too. `MediaRepository` takes a
   `profileId` fixed for its lifetime; `AppContainer` passes `AppSettings.activeProfileId`
   (always `default` until Phase 2). Recent Categories and the `last_*` bookmarks moved per
   profile: `default` keeps `media_cache_<id>`, any other profile gets
   `media_cache_<id>_profile_<profileId>`. Provider delete and copy cover every profile;
   `SettingsExportManager` exports and restores the active profile's rows (format stays
   version 5). Unit tests prove two profiles can't see each other's rows; the 19→20 migration was
   checked against Room's generated schema and has an instrumented test (not yet run on a device).
2. **Profiles UI + Jellyfin login per profile** — *landed 2026-09-29*, in three commits:
   - *Management:* Settings → Profiles on `:tv` and `:mobile` (add, rename, colour, delete).
     `providers.db` v12 adds `profiles.colorIndex`, an index into `CinemaProfileColors.palette`.
     The active profile and the last one can't be deleted; deleting cascades to the profile's
     rows and per-profile prefs on every provider.
   - *Picker and switching:* `Screen.ProfilePicker`, launch picker on TV only, header avatar on
     both. `AppContainer.switchProfile` stores the new profile and closes every cached
     `MediaRepository`; the nav hosts rebuild the back stack from home.
   - *Jellyfin login:* **no `provider_login` table** — same pattern as the per-profile prefs
     instead, simpler and nothing moves on upgrade. `default` keeps the login providers always
     had (`providers.username` + `provider_creds_<id>`); any other profile's Jellyfin login lives
     in `provider_creds_<id>_profile_<profileId>` (username, password, session).
     `ProviderRepository.getLogin` / `hasLogin` pick the right one; `MediaProviderFactory` reads
     the active profile's session and drops Jellyfin providers on switch. Quick Connect from the
     edit screen now signs this profile in to that provider (it used to add a duplicate). Home
     sends a profile with no login for the active Jellyfin server to its edit screen, once per
     provider/profile per process so Back still reaches a usable home.
   - *Profile-scoped settings* (planned 2026-09-30): category filters per profile and provider,
     dev mode per profile. See `docs/plans/20260930_profile-scoped-settings-plan.md`.

**Sync**

3. **Stable provider identity + tombstones** — *landed 2026-09-30.* `providers.providerKey`, a
   unique random UUID (backfilled per existing provider; `providers.db` v13). A `sync_tombstone`
   table in **each** database (decided 2026-09-30) so every deletion is recorded in the same
   transaction as the row it deletes: `providers.db` for `provider` (by `providerKey`) and
   `profile` deletions, `xtream_v2.db` (v21) for `favorite_stream` / `favorite_category` removals
   and `watch_clear` markers, keyed there by local `providerId` — the sync client translates to
   `providerKey` when sending. Re-adding a favourite (or restoring it from an export or a provider
   copy) drops its tombstone; deleting a provider or profile drops its item tombstones, its own
   covers them. No `watch_clear` for Jellyfin. **Recorded always, sync on or not** (decided
   2026-09-30), pruned after 90 days at startup. `deletedAt` is wall-clock time until Phase 4's
   HLC. EPG source deletions aren't recorded yet — they need a stable key first, as providers did.
4. **Outbox + HLC.** `sync_outbox` and `sync_state` (cursor, HLC) tables; every write path in
   `MediaRepository` / settings writes an outbox row in the same transaction. Coalesce by key.
   Record keys carry `profileKey` from the start. Room migration + schema doc.
5. **Merge/apply engine.** Pure function `(local, remote) -> resolution` with unit tests for every
   conflict case (newer wins, tombstone vs update, unknown provider, unknown profile, user-data
   record for a Jellyfin provider ignored, clock skew). Apply path with `fromRemote` and snapshot
   refresh.
6. **Server.** Worker + DO, endpoints above, integration tests against Miniflare. Docker image
   running the same code under `workerd` for self-hosting.
7. **Sync client.** HTTP push/pull, WebSocket while foregrounded, catch-up on resume, retry with
   backoff, full resync past the tombstone horizon.
8. **Encryption + pairing.** Account key, AES-GCM, HMAC keys, QR pairing UI on `:tv` and `:mobile`.
9. **Settings UI.** Sync server URL (validated via `GET /info`), sync on/off, paired devices
   list, revoke, last-sync time; dev mode shows raw sync errors.

Phases 1–5 land without any server and are testable in isolation.

## Testing

- Unit: merge engine (Phase 5) exhaustively.
- Two emulators + one real device against a local server (Miniflare, then the `workerd` Docker
  image): pause/resume handoff,
  offline edits then reconnect, favorite removed on A while B offline, clock skewed ±1 h on one
  device, two profiles active on two devices at once, profile deleted while active elsewhere.
- **Ask before installing/clearing on any device**, emulators included.

## Future (not in this plan)

Carried over from the deleted `20260809_xtream-multi-device-sync-plan.md` (user profiles, also
from there, moved into this plan). Feasible on this design; not scheduled.

### Who's playing + kick

See which Fijerena device holds each Xtream connection, and stop a stream remotely when the
subscription's connection limit is reached.

- Presence is ephemeral, not a synced record. A device sends an encrypted "playing X on provider
  P" message over its WebSocket; the account's Durable Object keeps the list in memory and
  broadcasts changes. A closed WebSocket drops the device from the list.
- Kick: device A sends "stop B"; the server relays it; B stops its own playback.
- Limits: only Fijerena devices with sync on are visible. Xtream's API exposes only
  `active_cons` / `max_connections`, so the best display is "3/4 in use — 2 yours, 1 unknown".
  Another app's connection can't be seen or kicked. An offline device can't receive a kick.
- Needs the WebSocket held **while playing**, not only while foregrounded (background audio, PiP).
- Effort: one phase after the sync client. Small server change; a "who's playing" screen on
  `:tv` and `:mobile`.

## Decisions

All decided 2026-09-29.

1. **Hosting:** both Cloudflare and self-hosted, chosen by a sync server URL in settings. One
   TypeScript codebase (see Server).
2. **Passwords:** sync, E2E encrypted (see Security).
3. **Providers missing on a device:** auto-add. A provider added on one device appears on all,
   password included; deleting it deletes it everywhere (see Deletions).
4. **Theme:** syncs. UI scale and cellular multipliers stay per-device. Dev mode syncs per
   profile (2026-09-30).
5. **Export file:** no change, stays version 5. It remains a snapshot backup, no tombstones, no
   passwords (it is a plaintext file in `/sdcard/Download`). Format untouched; see 9 for profiles.
6. **User profiles:** in this plan, built first (see Design → User profiles, Phases 1–2).
7. **Jellyfin and profiles:** one Jellyfin login per profile; login syncs, session doesn't.
8. **Non-Jellyfin providers and profiles:** one login per provider, shared by every profile
   (Xtream, Local — every provider whose `capabilities.supportsServerUserData` is false). Their
   favorites and watch history are still per profile (stored locally, not on the provider).
9. **Export and profiles:** export covers the **active profile only**; import restores into the
   active profile. The version 5 format already fits (favorites and watch state per provider), so
   it stays version 5. Providers, EPG sources and settings export as today. Jellyfin logins of
   other profiles are not exported (no passwords in the export, as before). Category filters and
   the dev-mode flag export and import as the active profile's (2026-09-30).
10. **Category filters and dev mode per profile** (2026-09-30): each profile has its own complete
    filter set per provider (no provider-level base); both copied to every profile on upgrade.
    Sync as per-profile records.
