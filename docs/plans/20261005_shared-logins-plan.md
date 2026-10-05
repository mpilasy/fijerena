# Shared Logins Plan

**Status:** In progress (written 2026-10-05). Phases 1–3 done and checked on the emulators against the jellyxtream bridge's one-stream test mode (`tools/jellyfin-xtream/xtream_bridge.py`, `BRIDGE_TEST_PASSWORD`, `BRIDGE_MAX_CONNECTIONS`). Phase 4 (real bears, two devices) waits for the second bears login.

## Goal

The Xtream providers in use allow **one stream per login**. The household has 8 devices (4 TV
streamers, 3 phones, 1 tablet), with 2–3 streaming at once, rarely more. Instead of one login per
device, an Xtream source holds 2–3 logins on the same panel, and each playback takes whichever
login is free at that moment.

No server, proxy or Worker: the app reads the panel's own connection count. A Cloudflare Worker
was considered (2026-10-05) and dropped. Streaming through it would hit bears from data-centre
addresses, which bears blocks (404) and its cross-address lock (460) punishes. Using it only to
hand out logins adds a service to keep running for little gain over the in-app check.

## Facts measured (2026-10-05, bears login `1fb5318f4d`, panel "World 8K")

- `player_api.php?username=…&password=…` returns `user_info.active_cons` and
  `user_info.max_connections` (here `1`). These API calls don't use up a stream slot.
- The count is accurate in both directions: `1` while the Xperia played, `0` within ~20 s of
  stopping (12:29:33 still 1, 12:29:55 0).
- Known from 2026-10-04 (`AccountBusy.kt`): bears refuses a stream from a **different public
  address** with HTTP 460 for ~5 minutes after the previous stream stops, even when the count
  already reads 0. Home devices share one address (except the Bravia, which the router sends out
  another way); phones on mobile data don't.

## Design

### Where things live today

- `XtreamContentManager.buildStreamUrl` / `buildEpisodeStreamUrl`
  (`core/network/.../xtream/manager/XtreamContentManager.kt`) build every playback URL from the
  session's single login, through `XtreamApiService` (`core/player/.../api/XtreamApiService.kt`).
  `XtreamMediaProvider.resolvePlayableStream` is the only caller.
- `StreamingPlaybackService.handleAccountBusy` (`core/player`) handles 456/458/460/511: it shows
  "account busy", waits `AccountBusy.RETRY_INTERVAL_MS` (20 s) and replays the **same URL**,
  giving up after `AccountBusy.MAX_WAIT_MS` (6 min).
- `core:network` depends on `core:player`, not the reverse, so the player can't call the network
  layer directly. It gets an interface it defines and `core:network` implements (below).
- Passwords: `provider_creds_<id>` (encrypted prefs, `ProviderRepository.savePassword` /
  `getPassword`). Live sync carries the main password in `SyncPayloads.Provider.password`.
  Settings export doesn't carry passwords.

### Logins

- A source keeps its **main login** (`ProviderEntity.username` + `provider_creds_<id>`). It alone
  does catalogue sync, EPG (`xmltv.php`), search and everything else that isn't playback.
- **Extra logins:** 0–2 more per Xtream source (no cap needed in code; the UI allows adding
  while it makes sense). Usernames go in `ProviderSettings.extraLogins: List<String>`, which is
  JSON, so there is no Room migration. Passwords go in `provider_creds_<id>` under
  `extra_password_<username>`.
- **Same panel only.** Stream ids belong to a panel, so swapping the login in the URL works only
  when all logins share a catalogue. Adding an extra login logs in with it and checks: same
  `server_info.url` and port, and a sample of 20 live + 20 VOD stream ids from the main login's
  catalogue all present in the extra login's `get_live_streams` / `get_vod_streams`. If not, it
  refuses: "This login is on a different server and can't share this source."
- **Usernames are distinct.** Each login is its own username + password pair. Adding a login
  whose username is already on the source (main or extra) is refused. Passwords may repeat.
- **Logins come and go.** A login can be added or removed at any time, months apart; it syncs to
  every device and the next playback uses the new set. No catalogue download, no reinstall.
- **Each login expires on its own** (`user_info.exp_date`). Edit Source shows it per login
  ("Expires 2027-02-01", "Expired"), read from `player_api` when the screen opens.
- **Make main.** Any extra login can become the main one: it moves into `ProviderEntity.username`
  + the main password, and the old main becomes an extra. This is the existing credential-change
  path (`ProviderRepository.updateProvider`), which already keeps everything that matters:
  history, favourites and filters belong to the source (`providerId`, `providerKey` in sync), the
  downloaded catalogue stays (same panel, same ids), and `reconcileAutoXmltvSource` rewrites the
  automatic `xmltv.php` guide source with the new login after the next successful login.
- **Removing the main login** is Make main on the next login, then removing the old one, in one
  step with a confirmation ("Remove this login? *user2* becomes the main login."). The last
  login can't be removed; that is Delete Source.
- Xtream only. Jellyfin, M3U, SMB and local sources are untouched.

### Picking a login at play time

A new `XtreamLoginPicker` in `core:network`:

1. Ask every login's `player_api.php` in parallel, 3 s timeout each.
2. Drop logins with `active_cons >= max_connections`, logins not `auth = 1` with
   `status = "Active"` (expired, banned, disabled), logins this device marked busy (below), and
   logins whose check failed.
3. Of the rest, prefer the login this device used last (in memory, per source). Same device
   means same public address, so it never trips the 460 lock and the panel sees a takeover
   rather than a new stream.
4. Otherwise the first free login in list order (main first).
5. Nothing free, or every check failed: the main login, exactly as today.

`buildStreamUrl` / `buildEpisodeStreamUrl` become `suspend` and build the URL with the picked
login. They return the login used along with the URL, so the player can say which login is
playing.

### Switching login on refusal

- `core:player` gains an interface `AlternateLogin` with
  `suspend fun next(currentUri: String): String?`: a URL for the same stream on another login,
  or null when none is left. `core:network` implements it with the picker. The current login is
  marked busy for **6 minutes on this device** (longer than the 460 lock), so step 2 above skips it.
- `handleAccountBusy` asks `AlternateLogin.next` first. With a URL: play it at once (keep the
  resume position, no 20 s wait), and the "account busy" message doesn't show. With null: the
  existing `AccountBusyWait` loop on the current URL, unchanged.
- This covers both a stale count and two devices pressing play in the same second: the loser is
  refused and moves on, costing about a second.
- **Mid-stream:** reconnects (network blip, slow-connection retry, 60 s stall limit) keep the same
  URL. Only a refusal moves to another login.

### Screens

- **Edit Source (TV and mobile), Xtream only:** an "Extra logins" section listing usernames, with
  Add (username + password, runs the same-panel check) and Remove. Strings in `values`,
  `values-fr` and `values-mg`.
- **Developer mode:** the player stats overlay shows `Login 2 of 3` (never the username), under
  the rule that dev mode shows raw details.

### Sync and export

- Extra usernames ride in `providerSettings`, which live sync already carries.
- Extra passwords ride in `SyncPayloads.Provider.extraPasswords` (Decision 1).
- **No Room migration and no sync server change.** Usernames are a field in the existing
  `providerSettings` JSON column (`providers.db` stays at 16; `docs/DATABASE_SCHEMA.md` gets a line
  on the field). Passwords stay in encrypted prefs. The server only stores end-to-end encrypted
  records (`SyncCrypto`), so a new field inside one is invisible to it.
- **Update every device before adding extra logins.** Old builds parse the new fields without
  failing (`ignoreUnknownKeys = true`), but an old build that edits the source's settings writes
  them back without `extraLogins`, and live sync then erases the extra logins everywhere. Release
  notes say so.
- Settings export: usernames go out with `providerSettings`; passwords don't, like the main one.
  On import the extra logins show "password needed".

## Phases

One commit per phase on `main`. Each commit updates this plan's Progress table and the affected
docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES).

| Phase | Work | Tests |
|---|---|---|
| 1. Extra logins stored and edited | `ProviderSettings.extraLogins`, extra passwords in `provider_creds_<id>`, same-panel check, distinct usernames, Edit Source section on TV and mobile (add, remove, expiry per login, Make main, remove the main login), delete with the source, `extraPasswords` in live sync | Unit: same-panel check (same server, missing ids, different server); duplicate username refused; Make main swaps the two logins and keeps the source id; removing the main promotes the next; last login can't be removed; settings JSON round trip with and without the field; sync payload with and without `extraPasswords` |
| 2. Picker at play time | `XtreamLoginPicker`, `suspend` URL builders, `XtreamMediaProvider` passes the login on, dev-mode `Login n of m` | Unit (fake API): prefers last-used, skips full, skips expired or banned, skips failed, all full falls back to main, timeout falls back to main, single login does no `player_api` call |
| 3. Switch on refusal | `AlternateLogin` in `core:player`, implemented in `core:network`; `handleAccountBusy` tries it first; 6 min busy mark; busy message names the login count | Unit: busy mark expires after 6 min; order after refusals; null hands over to `AccountBusyWait` |
| 4. Device check | Two real devices, two bears logins: both play at once; stop one and the other device takes its login; a phone on mobile data gets 460 and moves on | Manual, recorded here |

Phase 2 with a single login must behave exactly like today: no extra requests, same URL.

## Decisions (2026-10-05)

1. **Extra passwords sync.** `SyncPayloads.Provider` gains `extraPasswords: Map<String, String>`
   (username → password), applied like the main password, so the logins are entered once and
   reach every device in the sync group. Part of Phase 1.
2. **All logins busy: keep today's wait** (retry every 20 s for up to 6 min, the message says
   playback starts by itself), but the message names how many logins are in use when the source
   has more than one ("All 3 logins are in use"). Part of Phase 3.
3. **Logins added and removed over time** (2026-10-05): adding a third login months later, and
   removing any login including the first, must just work. Hence per-login expiry, the picker
   skipping expired logins, Make main and removing the main login, all above.

## Phase 1 notes (2026-10-05)

- `SourceLogins` (`core:network/.../provider/SourceLogins.kt`): add, remove, make main as pure
  functions. Also refuses a main login without a password on this device (`PasswordNeeded`):
  removing the main login promotes the first extra login that has one, and Make main is refused
  on one that doesn't (an imported login). Remove it and add it again to give it its password.
- `ProviderRepository.getSourceLogins` / `saveSourceLogins` / `getExtraPasswords`;
  `updateProviderSettings` keeps the stored `extraLogins`; `applyRemoteProvider` applies
  `extraPasswords` (Xtream only, null keeps this device's). Copy To with the connection copies the
  extra logins too.
- `XtreamLoginCheck` (`core:network/.../xtream/`): `status` (`player_api.php` → active, expiry,
  connections) for Phase 2 to reuse, and `samePanel`, which compares the stream ids of the first live
  and the first VOD category, not the server name (bears answers under two domains).
- `ExtraLoginsViewModel` (`core:ui`) serves both screens; `statusText` words a login's line the
  same on TV and mobile. TV: `tv/.../provider/components/ProviderLoginsSection.kt`, between
  Behaviour and Guide, buttons on a line under each row. Mobile:
  `mobile/.../provider/components/ProviderLoginsSection.kt`, after the connection form. Both
  reload the screen's username and password fields when the main login changes, so Save connection
  can't write the old one back.

## Testing without a second account (2026-10-05)

The bridge's test mode stands in for a one-stream panel: `<user>+N` / the test password is an extra
login on the same catalogue, each username holds one stream for `BRIDGE_HOLD_SECONDS` (it only
redirects, so it can't see a stream stop), a stream over the limit gets HTTP 458, a reopen within
5 s counts as the same stream (mkv index, resume seek), and `BRIDGE_HIDE_CONS=1` hides the counts
so the refusal path runs. What it can't show: bears' 5-minute lock for a different public address
(460) and its real timings — Phase 4.

## bears measurements for Phase 4 (2026-10-05, darcy, one login)

- A movie stream stops counting ~20 s after it stops (Xperia, morning). A **live HLS** stream
  (`m3u8`) keeps the login counted **~46 s to ~1 min** after the player stops; **live MPEG-TS**
  (`.ts`) frees it at once (two runs). A `.ts` stream also takes ~10 s to start counting.
- Start time to first picture: HLS channel changes 0.8–1.8 s; `.ts` measured only as two first
  starts (3.1 s, 7.8 s after three "unexpected end of stream" retries) — not compared like for
  like. Decided: keep m3u8; revisit with the second login if logins sitting busy matters.
- A `player_api.php` check takes ~0.5 s from the host (0.22 s connect). Checks now reuse one
  connection per login, an answer is reused for 5 s, and a channel change keeps this device's
  login without asking (`3921cde9`) — the zap path is still untested with two logins.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 | Done; checked on both emulators 2026-10-05 (add, duplicate and wrong-password refusals, Make main, Remove); focus walk updated by hand for the Logins rows | db5f1c9c |
| 2 | Done; checked on both emulators against the bridge's test mode: with `tahiry` held, the phone played on `tahiry+2`, Stats "Login 2 of 3" | (branch) |
| 3 | Done; checked: a 458 moved the stream to the next login in ~0.3 s with no message; all three held gave "All 3 logins … in use". Found and fixed: after an all-busy moment the refused logins were skipped for 6 min even once free; a new playback now asks every login and only puts those last | (branch) |
| 4 | Not started | |
