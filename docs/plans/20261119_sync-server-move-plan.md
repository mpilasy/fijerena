# Move live sync to another server

**Status:** In progress since 2026-11-19 (go-ahead given 2026-11-19). SM-Q1 to SM-Q7 decided 2026-11-19 (see Open questions).

## Goal

From Live Sync settings, an admin moves the sync group to a new server address in one step:

1. The device copies the group's data from the current server to the new one and switches to it.
2. Each other device of the group switches by itself the next time it opens. There is no QR code
   and no new setup.
3. When every other device has moved or was removed from the group, a **Purge Old Server** button
   deletes the group's data from the old server.

The move works between any two servers (Docker to Docker, Cloudflare to Docker, Docker to
Cloudflare), because the data goes through the device, not from server to server.

## What exists (survey 2026-11-19)

- **Words:** the server and the code say **account** for what the app calls a **sync group**: the
  devices that share one account key and one log of records. This plan uses both words for the same
  thing.
- **The device's link** (`SyncAccountStore`, encrypted prefs `sync_account`):
  - `Link(serverUrl, accountId, deviceId, deviceToken, accountKey)`, plus `cursor`, `seeded`,
    `deferred` and `knownKinds`.
  - `saveLink` clears everything first. So a new link pulls everything from 0 and then queues all
    local data for upload (`seeded` false → `LocalRecords.seedEverything`).
  - A device has no way to change its server address in place.
- **Keys and payloads do not depend on the server:**
  - Record keys are HMACs made with the account key.
  - A payload is sealed with the record key as AAD (`SyncCodec`).
  - `accountId` does not enter the crypto.
  - So a record copied as it is from one server to another opens on every device of the group.
- **The server keeps one row per key** (the newest write wins). `GET /changes?since=0` therefore
  returns the group's current state: every live record and every tombstone younger than 90 days.
  - A pull does not return `cascade`. The cascaded rows are already gone, so a copy of the current
    state needs no `cascade`.
  - On an empty server every copied record is accepted: `updatedAt` is not newer than nothing, and
    it is not more than a day ahead. Copied tombstones get a new `stored_at`, so they live
    90 more days there.
  - The limits are 500 records per batch and a 64 KiB sealed payload per record (`server/src/account.ts`).
- **Pairing** (`server/src/account.ts`, `createPairing` / `pair`):
  - A code is `<accountId>.<random secret>`. The server keeps only its hash, and a code works once.
  - Codes expire after 10 minutes (`PAIRING_TTL_MS`). Codes are high-entropy, so a longer life is
    safe.
- **Removing a device:** `DELETE /devices/:id` with a device token sets `revoked = 1`.
  `SyncAccountManager.leave` already removes the device itself this way. `GET /devices` lists every
  device with its `revoked` flag.
- **Nothing deletes an account on the server.** A revoke only sets the flag.
- **`GET /info`** answers `{service, protocol, setupSecretRequired}`; `protocol` is 1.
- **Record kinds** (`SyncKind`):
  - **Volatile kinds:** `now_playing`, `remote_command`, `pin_*`, `play_*`.
  - **Records keyed by a server device id:** these volatile kinds, the `device_build:<deviceId>`
    setting, and the device reports (the device diagnostics plan).
  - **A new kept kind** makes every device pull everything once after the update. On a large
    account a TV needs minutes for this.
  - **A setting key that an older build doesn't know** is skipped (`applyRemoteSetting`), and so is
    a record kind it doesn't know (`SyncApplier`: "a kind from a newer app version").
- **Screens:**
  - TV: `tv/.../settings/SyncSettingsScreen.kt` with `LiveSyncSettingsCard.kt`.
  - Phone: `mobile/.../settings/MobileSyncSettingsScreen.kt`.
  - Both use `core/ui/.../viewmodels/SyncSettingsViewModel.kt`, and `SyncManager` runs the engine.
  - Live Sync is admin-only, behind the PIN.
- **Test servers:** a local `workerd` on host :8787 (memory: local sync server). A second instance on
  another port can be the "new" server.

## Design

### The move, on the device that starts it

0. **Check every device can move** (SM-Q7): each device of the group that the old server lists as
   not revoked must report the `server_move` feature in its device build record
   (`DeviceBuild.features`, as `play_request` does for Send to TV). If one doesn't, or has
   reported no build yet, **Move to Another Server** shows disabled, with the reason under it:
   "Update first: Living Room TV". Remove a device you can't update from the group first.
   The button is also disabled, with its reason, while the group has a move record on the current server (another
   admin device started a move), or an old-server record (an earlier move not yet purged or
   forgotten). Otherwise two moves could split the group, or the first old server would be
   forgotten with the group's data on it. The new address must differ from the current one.
1. **Check the new server:** `GET /info` must list the `move` feature (see Server). If the new
   server needs a setup secret, the dialog asks for it.
2. **Create the account on the new server:** `POST /accounts` with this device's name. Keep the
   same account key.
3. **Make one move code per device:** for each other device that the old server lists as not
   revoked, `POST /pairings {"purpose": "move"}` on the new server. A move code lives until it is
   used or the purge (SM-Q2) and works once.
4. **Copy the snapshot:** page through `GET /changes?since=0` on the old server and send each page
   to `POST /changes` on the new one, 500 records at a time. Records stay as they came: still
   sealed, with the same key and clock value. Skip the records that SM-Q3 leaves out; the device
   opens a record only to read its kind, and never seals it again. Show the progress:
   "Copying 1,200 of 4,000".
5. **Write the move record on the old server** (SM-Q1): the new address, and for each old device
   id its move code. The record is sealed like any other, so the old server can't read the address
   or the codes.
6. **Copy what came in during the copy:** records with a `seq` after the snapshot's head, but
   not the move record itself.
7. **Write the old-server record on the new server:** the old address, plus this device's old
   device id and token (SM-Q1 carries it, like the move record). The record is sealed, so the new
   server can't read it. This device does not remove itself from the old server: its token there
   is what every admin device uses to watch and purge the old server (see Purge Old Server).
8. **Switch:** save the new link with a cursor of 0, but keep `seeded`, `deferred` and the pending
   local changes (`SyncAccountStore.switchServer`, new; `saveLink` would clear them). Then pull and
   push as usual. The device does not queue all local data again: the copy already holds it, and
   a full re-send could bring back items deleted long ago (see Dormant devices).

**No sync during the move:** `SyncManager` runs no pass from step 1 until the switch, so this
device writes nothing to the old server while it copies. Its changes stay pending and go to the new
server after the switch.

**Resume after a failure:** the device writes the move's state to `SyncAccountStore` as it goes:
the new address and credentials, the codes, the copy cursor and the step reached. If the app stops part way, the
next open continues the move from that state. It does not create a second account on the new
server.

**As built (2026-11-19):**

- `SyncServerMove` (core/network) does steps 0 to 8. The resume state (`SyncAccountStore.moveProgress`)
  starts at step 2, when the account on the new server exists. A failure in step 0 or 1 leaves
  nothing, and sync goes on as before. From step 2 on, no pass runs until the move is finished.
- The copy also skips the `device_name:<deviceId>` records (the names the group gave its devices).
  They are keyed by old server device ids, which point at nothing on the new server. Each device
  joins the new server with its current name, so the names stay the same. The copy also skips the
  two move records themselves, and records that this device can't open.
- The two records are setting records with the shared profile. Their value is JSON text:
  `sync_move` = `{"serverUrl", "codes": {<old device id>: <move code>}}`;
  `sync_previous_server` = `{"serverUrl", "deviceId", "deviceToken"}`. The engine gives them to
  `SyncServerMove` and never to the applier. The device keeps the old-server record (with its clock
  value) in `SyncAccountStore`, and a newer record or a deletion replaces it.

### The move, on the other devices

On a pull from the old server, a device that reads the move record:

1. Finds its own old device id in it. If its id isn't there (it joined after the move started),
   it shows: "This group moved to `<address>`. Add this device from a device of the group."
2. Joins the new server with its code (`POST /pair`, with the device's current name).
3. Copies the old server's records that came after the move record to the new server. These are
   writes from devices that hadn't moved yet. If several devices copy the same record, the new
   server rejects the second copy as `stale`, which does no harm.
4. Removes itself from the old server (`DELETE /devices/<own id>`).
5. Switches like the starting device (step 8): cursor 0, but `seeded`, `deferred` and its pending
   local changes are kept. It pulls everything from the new server, then pushes its pending
   changes, so its changes that the old server never got reach the new one. It does not queue all
   local data again.

**As built (2026-11-19):**

- `SyncEngine` gives the move record to `SyncServerMove`, which stores what the device needs to
  follow (`SyncAccountStore.moveFollow`: the new address, its code, the move record's `seq`, then
  the new credentials as soon as the pairing gives them, and the copy cursor). The pass that brings
  the move record sends nothing to the old server. The engine then follows the move and does a pass
  on the new server in the same call (`Outcome.serverSwitched`), and `SyncManager` connects the
  socket to the new server. A follow that stopped continues at the start of the next pass.
- The device's current name is the group's name for its old id, else its name on the old server.
- A refused revoke (401 or 404: the device was removed already) counts as done.
- A device that is not in the move record keeps syncing with the old server and shows
  `Status.movedAway`. The state goes away when the device is linked again.

Step 0 makes sure no device of the group is on an app version from before the move. Such a
device would skip the move record and stay on the old server until it is updated.

### Dormant devices

A device (C) that stays closed for weeks moves correctly when it opens, if three things hold:

- **The old server still runs.** C learns the new address only from it. The purge stays disabled
  while C has not moved, so the old server can't be purged by mistake. Only removing C from the
  group unblocks the purge, and then C must be added again by hand (as today for a removed device).
- **C's move code still works.** A code that expires (SM-Q2 (a) or (b)) strands C. With SM-Q2 (c),
  the code lives until it is used or the purge deletes the account, so the purge rule is the only
  limit.
- **C does not send all its local data again.** C may still hold items that other devices deleted
  more than 90 days before the copy: the old server has purged those tombstones, so the new server
  has no tombstone to reject them. A full re-send would bring them back on every device. So C
  keeps `seeded` and pushes only its pending changes. A deletion younger than 90 days is in the
  copy as a tombstone, and C applies it on its first pull.

What C changed while it was closed is in its pending changes, so it reaches the new server.
Records that devices write to the old server after the move record are copied by each device that
moves (step 3), so a late writer loses nothing either.

### Purge Old Server

- **Where:** on any admin device of the group, while the new server holds the old-server record.
  All devices get that record by normal sync, so the device that started the move does not need to
  be the one that cleans up. It can even be lost or reset.
- **No device watches the old server in the background.** Only an open Live Sync screen calls the
  old server (`GET /info`, `GET /devices`), with the token from the old-server record. A device
  that has moved needs only the new server.
- **Shown only when** the old server's `/info` lists the `purge` feature. Otherwise a hint shows
  instead: update the old server, or delete its data yourself (the Docker `data` folder, or the
  Durable Object on Cloudflare), then select **Forget Old Server**. **Forget Old Server** shows
  there too, with the same confirmation: without it, the row and the `OldServerLeft` block would
  stay for good.
- **Disabled** while `GET /devices` on the old server lists another device that is not revoked.
  The row names those devices: "Not moved yet: Living Room TV, Bedroom TV". To unblock a device
  you can't open (lost, sold or reset), remove it from the group as you can today.
  The list leaves out the device whose token the record holds: it is the device that started the
  move, and it is already on the new server.
- **When pressed:** a confirmation, then `DELETE /account` on the old server, then a deletion of the
  old-server record on the new server. The row goes away on every device.
- **Old server unreachable:** the row says so and offers **Forget Old Server**, which deletes the
  old-server record and purges nothing (SM-Q5).
- **The token in the record:** it lets the holder read the device list and delete the account on
  the old server, and nothing on the new server. It is sealed with the account key, which already
  opens all the group's data, so it adds no new risk inside the group.

### Server

Additive changes; `protocol` stays 1.

- `POST /pairings` accepts `{"purpose": "move"}`. The code then has no time limit (SM-Q2) instead
  of 10 minutes. It still works once, and `DELETE /account` deletes it with the rest.
- `DELETE /account` (device token): answers `409` while a device other than the caller is not
  revoked. Otherwise it deletes the account's whole storage (records, devices, pairing codes; the
  Durable Object's `deleteAll`) and answers `200`.
- `GET /info` adds `features: ["move", "purge"]`. An older server has no `features` field, and the
  app reads that as "neither".
- The old server needs nothing new for the move itself (pulls, the move record and revokes exist
  today). Only the purge needs a new old server; the new server needs `move`.

### App interface

The screens see the move only through `SyncManager` and the types in
`core/network/.../sync/SyncMove.kt` (`MoveBlock`, `MoveState`, `OldServerState`):

| `SyncManager` member | What it does |
|---|---|
| `move: StateFlow<MoveState>` | How far a move started on this device is. |
| `suspend fun moveBlock(): MoveBlock?` | Step 0: why **Move to Another Server** is disabled; null when it is not. |
| `suspend fun checkMoveTarget(url): SyncWire.Info` | Step 1: throws `SyncApiException` (`INCOMPATIBLE` when the server has no `move` feature); `setupSecretRequired` tells the dialog to ask for the secret. |
| `fun startMove(url, setupSecret)` | Steps 1 to 8, or continues a move that stopped. |
| `oldServer: StateFlow<String?>` | The old address while the new server holds the old-server record; null otherwise. |
| `suspend fun oldServerState(): OldServerState` | Asks the old server (an open Live Sync screen only). |
| `suspend fun purgeOldServer()` / `suspend fun forgetOldServer()` | Purge Old Server / Forget Old Server. |
| `Status.movedAway: String?` | The group moved to this address, and this device is not in the move record. |

## Phases

| Phase | Work | Estimate |
|---|---|---|
| 1 | **Server:** move codes, `DELETE /account`, `/info` features. Tests in `server/test`, wire samples in `core/network/src/test/resources/fixtures/sync/` (`SyncWireSamplesTest`), API table in `server/README.md`. | ½ day |
| 2 | **App, the starting device:** the `server_move` device feature and the step 0 check, `SyncApi` calls, a `SyncMover` (steps 1–8, resume), the move state in `SyncAccountStore`, the old-server record, record filter for SM-Q3. Unit tests against a fake server: a device without `server_move` blocks the move, a move record or old-server record blocks it, full copy, resume after each step, setup secret, a new server without `move`. | 1–1½ days |
| 3 | **App, the other devices:** read the move record in `SyncEngine`, join, copy the records after the move record, remove itself, switch. Tests: two devices move, a device not in the record, a device whose pending changes reach the new server, a dormant device that holds an item deleted more than 90 days before the copy (it must not come back). | 1 day |
| 4 | **Screens, TV and phone:** **Move to Another Server** (address, setup secret, progress, failure text with the raw error for a Nerd), the old server row on every admin device with the "not moved yet" list, **Purge Old Server** with its states and confirmation, **Forget Old Server**. Strings in en, fr and mg. Docs: FEATURES, USER_GUIDE, server README, RELEASE_NOTES. | 1 day |
| 5 | **Emulator check:** both emulators on the local server (:8787); move to a second local server; check the TV moves when opened, the purge stays disabled until then, the phone can purge after the TV started the move, and the purge leaves the old server's data folder without the account. | ½ day |

## Progress

| Phase | Status |
|---|---|
| 1 | Done 2026-11-19: move codes without a time limit, `DELETE /account`, `/info` features, with server tests and wire samples. |
| 2 | Done 2026-11-19: `SyncServerMove` (steps 0–8, resume, SM-Q3 filter, old-server watch and purge), `server_move` feature, `SyncApi.createMoveCode`/`deleteAccount`, `SyncAccountStore.switchServer`, `SyncManager` move API; tests against two fake servers. |
| 3 | Done 2026-11-19: `SyncEngine` reads the move record and follows it (join, copy after the move record, revoke, switch, resume); `Status.movedAway`; tests: two devices move, a device not in the record, pending changes, dormant device, resume at each call. |
| 4 | Done 2026-11-19: **Move to Another Server** (reason line, address and setup secret, progress, failure), the Old Server row (not moved yet, Purge, Forget, the no-purge hint), the moved-away notice; TV and phone; en, fr, mg; FEATURES, USER_GUIDE, RELEASE_NOTES. |
| 5 | Not started |

## Open questions (all decided 2026-11-19)

- **SM-Q1, what carries the move record (on the old server) and the old-server record (on the new
  server):** **decided 2026-11-19: (a)**.
  - (a) Two shared setting keys, `sync_move` and `sync_previous_server`, handled by `SyncEngine`.
    Older builds skip them, and nobody has to pull everything again. Recommended.
  - (b) A new kept kind. Every device pulls everything once after the update.
- **SM-Q2, how long a move code lives:** **decided 2026-11-19: (c)**.
  - (a) 30 days. A device closed for longer must be added again by hand.
  - (b) 7 days.
  - (c) Until it is used or the purge deletes the old account. Recommended: the purge already
    waits for every device, so a time limit only strands dormant devices. The code is sealed in the
    move record, works once, and the server keeps only its hash.
- **SM-Q3, which records the copy skips:** **decided 2026-11-19: (a)**.
  - (a) The volatile kinds, and the records keyed by an old server device id (device builds,
    device reports). Devices report these again on the new server. Recommended.
  - (b) Copy everything.
- **SM-Q4, who can start a move:** **decided 2026-11-19: (a)**.
  - (a) Any admin device of the group, TV or phone. Recommended.
  - (b) The phone only.
- **SM-Q5, an old server that can't be reached:** **decided 2026-11-19: (a)**.
  - (a) Offer **Forget Old Server**, which drops the old link without a purge. Recommended.
  - (b) No such button; the purge row stays until the old server answers.
  - Forget also offered when the old server has no purge (2026-11-19).
- **SM-Q6, devices on an app version from before the move** (rare after SM-Q7: only a device added
  to the group on an old version after the move started): **decided 2026-11-19: (a)**.
  - (a) List them as "not moved yet", with a hint to update the app. Nothing else. Recommended.
  - (b) Also show their app version in the list (from their device build record).
- **SM-Q7, a device of the group on an app version that can't move:** **decided 2026-11-19: (a)**.
  - (a) Block: **Move to Another Server** shows disabled until every device reports
    `server_move`, with a line under it that names the devices to update or remove ("Update
    first: Living Room TV"). The user chose disabled with a reason over hiding the button or
    blocking only when pressed.
  - (b) Warn, and allow the move. The device stays on the old server until it is updated; its
    deletions older than 90 days at its move are lost.
