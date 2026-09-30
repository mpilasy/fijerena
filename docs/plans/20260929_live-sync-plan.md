# Live Sync Plan

**Requirement:** 3–4 devices on one account stay in sync as things change. Pause on the TV, pick
up the phone, and it resumes at the same position within seconds. Favorites, watch state and
selected settings follow the user across devices without a manual export/import. A household
sharing devices gets **user profiles**: each person's favorites and watch history are their own
and follow them to any device.

**Status:** design agreed 2026-09-29 (see Decisions); two open questions on profiles. Nothing
implemented.

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
`provider`, `epg_source`, `profile`.

`profileKey` applies to per-person data (`watch`, `watch_clear`, `favorite_*`). Shared data
(`provider`, `epg_source`, `setting`, `profile`) uses a fixed `shared` value in that slot.

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
| Theme, dev mode, EPG auto-refresh | yes | |
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
| Watch state, resume positions, Recent rows | Providers (incl. passwords), EPG sources |
| Favorite streams and categories | Global settings (theme etc.), per-device settings |

**Identity.** A profile is a random UUID (`profileKey`) plus a name and avatar colour, synced as a
`profile` record. Creating, renaming or deleting one on any device applies everywhere.

**Existing data.** On upgrade, one `Default` profile is created and every existing
`watch_state` / `favorite_state` row is assigned to it. Users who never add a second profile see no
picker and no change.

**Active profile** is per device, not synced (the TV and a phone are used by different people at
the same time). Remembered across restarts. With more than one profile: picker on app start, and
"Switch profile" in settings on `:tv` and `:mobile`.

**Local schema.** `watch_state` and `favorite_state` primary keys gain `profileId`:
`(providerId, profileId, itemId, contentType[, kind])`. New `profile` table. Every DAO query takes
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

**Jellyfin:** unaffected. Its user data lives on the Jellyfin server under the Jellyfin account,
which profiles don't change (see Open questions).

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

1. **Profiles schema.** `profile` table keyed by the profile UUID (the same value sync uses as
   `profileKey`, so no mapping table later). `profileId` added to `watch_state` and
   `favorite_state` primary keys, existing rows backfilled to `Default`. DAO queries and
   `MediaRepository` snapshot scoped to the active profile. Room migration + schema doc.
   **Blocked on Open question 1** (Jellyfin login per profile or shared), which shapes the
   `profile` table.
2. **Profiles UI.** Picker on start (only with 2+ profiles), add/rename/delete, switch profile on
   `:tv` and `:mobile`, snapshot reload on switch.

**Sync**

3. **Stable provider identity + tombstones.** `providerKey` UUID on providers (backfilled for
   existing rows); `sync_tombstone` table; remove/clear paths write tombstones (local-user-data
   providers only — see Jellyfin). Room migration + `docs/DATABASE_SCHEMA.md`.
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
4. **Theme:** syncs. UI scale and cellular multipliers stay per-device.
5. **Export file:** no change, stays version 5. It remains a snapshot backup, no tombstones, no
   passwords (it is a plaintext file in `/sdcard/Download`). `SettingsExportManager` untouched.
6. **User profiles:** in this plan, built first (see Design → User profiles, Phases 1–2).

## Open questions

1. **Jellyfin and profiles.** Jellyfin user data is tied to the Jellyfin login, so every profile
   sees the same Jellyfin favorites and history. Accept that, or allow a Jellyfin login per
   profile (each person has their own Jellyfin user)?
2. **Export and profiles.** The export file carries favorites and watch state per provider. With
   profiles: export the active profile only, or all profiles (needs a version 6 format)?
