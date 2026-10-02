# Live Sync "Now Playing" Plan

**Status:** 🚧 **IN PROGRESS** — Phases 0-3 done (2026-10-01); Phase 4 in progress. Open questions answered 2026-10-01 (see §6); remote Stop added as Phase 4.
**Date:** 2026-10-01
**Scope:** `core:player` (one flow), `core:network/sync`, `core:ui/sync`, TV + mobile Live sync screens, mobile-only Stop button, TV/mobile player exit on remote stop. No server change.

---

## 1. The use case

A parent is at work with the phone; the kids are at home on the TV. Both are in the same sync
group. In **Settings → Live sync → Devices** on the phone, the parent wants to see, for the TV:

1. **Whether something is playing right now**, and
2. **What it is** — the movie, or the show and episode, or the live channel.

Today the devices list only shows each device's last-seen time.

## 2. Why today's data can't answer it

- **Watch records don't say which device wrote them.** By design (`docs/plans/20260929_live-sync-plan.md`
  → Security): records are end-to-end encrypted and keyed by item, not device. The phone's Recent
  row shows what *someone in the group* last played, not *which TV*, and not *now*.
- **Nothing means "playing now".** Live TV reaches history after 10 s and VOD after 2 %, positions
  update every ~10 s — "last watched, recently updated", with no "stopped" signal at all.
- **The server can't help:** it only sees ciphertext. Over adb, `dumpsys media_session` shows
  playing/paused but no title, because `StreamingMediaSourceFactory.kt:36-40` builds the
  `MediaItem` with a URI only.

## 3. Design

### 3.1 One new sync record per device: `now_playing`

- **Kind** `SyncKind.NOW_PLAYING = "now_playing"`, **key** `SyncKey(SHARED, "", NOW_PLAYING, <deviceId>, "")`
  — one record per device, upserted. `deviceId` is the server's id for this device
  (`SyncAccountStore.link.deviceId`), the same id the devices list returns, so the receiver can
  join them.
- **Payload** (sealed like every record, so the server learns nothing):
  `state` (`playing` / `paused` / `stopped`), `title`, `showTitle?`, `episodeLabel?`, `isLive`,
  `channelName?`, `programTitle?` (Live TV: the current EPG programme, when known), `profileName`
  (who's watching on that device), `positionMs?`, `durationMs?`, `sentAt` (wall clock of the
  sender, for staleness).
- **Not stored in a table on the sender.** Unlike every other kind it has no `sync_version` row:
  it is volatile state. `SyncEngine.push` sends the latest pending `now_playing` record (held in
  memory by the publisher) alongside `LocalRecords.pending()`, then forgets it. Its `updatedAt`
  comes from the settings sync clock (`SettingsSyncDao.tick()`), so each send is newer than the
  last and the server's last-write-wins accepts it.
- **Not stored in a table on the receiver either.** `SyncApplier` hands it to an in-memory
  `NowPlayingStore` (`StateFlow<Map<deviceId, NowPlaying>>`) and records nothing else — no
  version, no tombstone. After a restart the next heartbeat (below) refills it.
- **Older app versions** skip it: `SyncApplier.apply` already skips kinds not in `kindOrder`.

### 3.2 Publishing (sender)

- **Source:** `StreamingPlaybackService` already exposes `currentMetadata` and `playbackState`.
  `core:player` can't depend on `core:network`, so the service publishes a process-wide
  `StateFlow<NowPlayingSnapshot?>` on its companion; a `NowPlayingPublisher` in `core:ui/sync`
  collects it.
- **Live programme:** `PlayerMetadata` gains `programTitle`. `StreamLoaderViewModel` already
  resolves `currentEpgProgram` for a live channel (`StreamLoaderViewModel.kt:65, 380`); it patches
  the playing item through the existing `PlaybackViewModel.updateMetadata()` when that programme
  is known or changes. The 60 s heartbeat carries the hour-boundary change to other devices.
- **When a record is sent:** on a change of item or of playing/paused/stopped, debounced ~2 s
  (channel zapping sends one record, not ten); plus a **heartbeat every 60 s while playing**;
  plus `stopped` when playback ends or the service is released.
- **Staleness instead of a guaranteed stop:** a TV switched off at the wall never sends `stopped`.
  The receiver treats a `playing` record older than **3 minutes** (3 missed heartbeats) as
  stopped.
- **Transport:** each send goes through the existing `SyncManager.requestSync(debounce)` path, so
  it rides the normal push and the server's `{"head": n}` broadcast wakes every open device in
  seconds. Sync only runs while the app is in the foreground — which a TV playing video is.
- **Opt-in switch:** a per-device setting, **"Share what's playing with my sync group"**, in Live
  sync settings, **off by default** (§6). Stored in plain device-local prefs, deliberately *not* a
  synced `SyncKind.SETTING` — turning it on for the kids' TV must not turn it on everywhere.
  Off: publish nothing; switching it off sends one `stopped` record so other devices don't keep
  showing stale state.

### 3.3 Showing it (receiver)

- `SyncSettingsViewModel` combines `ui.devices` (server list) with `NowPlayingStore` by device id.
- Each device row gains one line:
  - **▶ Playing · Malcolm X · Kid** / **▶ Playing · The King of Queens S1:E12 · Kid** /
    **▶ Live · BBC World News — Newsday · Kid** (programme omitted when the channel has no EPG).
  - **⏸ Paused · …**, or nothing extra when stopped or stale (the existing "Last seen …" stays).
  - Shown on **both** TV and mobile devices lists (§6).
- TV `SyncSettingsScreen.kt` `DevicesPanel` (`:237`) and mobile `MobileSyncSettingsScreen.kt`
  `DevicesPanel` (`:275`). Strings in en/fr (mg falls back, like the rest of Live sync).
- **Freshness while looking:** the phone is in the foreground on that screen, so its WebSocket is
  open and a heartbeat lands within seconds. After the phone app restarts the line returns at the
  next heartbeat (≤ 60 s), not straight away: the store is in memory only.

### 3.4 Cleanup

- A removed or unlinked device stops heartbeating; its record goes stale in 3 minutes and the
  devices list no longer lists it anyway (records are only shown joined to a listed device).
- On **Leave sync group**, send `stopped` first (best effort).

### 3.5 Remote Stop (Phase 4)

The parent can stop what a device is playing from the **phone app only** — the Stop button is
never shown in the TV app (§6). Any device obeys a valid command; only phones can send one.

- **Session id.** Each playback on the sender gets a random `sessionId` (new on every
  `playStream`), published in its `now_playing` payload.
- **Command record.** Kind `SyncKind.REMOTE_COMMAND = "remote_command"`, key
  `SyncKey(SHARED, "", REMOTE_COMMAND, <targetDeviceId>, "")`; payload `{command: "stop",
  sessionId, fromDeviceName, issuedBy}`. Volatile like `now_playing`: pushed from memory, no
  `sync_version`, no tombstone.
- **Never fires twice, no clocks.** The target obeys only if `sessionId` equals the session it is
  playing *right now*. Records stay on the server, so a device that reinstalls or re-syncs from 0
  re-reads old commands — none can match a new session. Wall-clock windows are deliberately not
  used: TV clocks are often wrong (see the stability plan's F-25).
- **Delivery.** A playing device is in the foreground with its sync socket open, so the server's
  head broadcast makes it pull within seconds. Each 60 s `now_playing` heartbeat also runs a sync
  pass, which pulls — so even with the socket down the worst case is about a minute. Offline: the
  command waits; if that playback has ended by the time it arrives, it no longer matches and is
  ignored.
- **On the target.** `SyncApplier` hands a matching command to a `RemoteCommands` event flow in
  `core:ui`. The player screens (TV `TvPlayerScreen` / `LiveTvSplitLayout`, mobile
  `MobilePlayerScreen` / the Live TV dock) collect it: finalise the session as on Back
  (`finalizeSession`, so the watch position is saved), stop playback, leave the player for Home,
  and show **"Playback stopped from <device name>"**. The target then publishes `stopped`, so the
  phone's line clears.
- **On the phone.** The devices list (mobile `MobileSyncSettingsScreen` `DevicesPanel`) shows a
  **Stop** button on any other device's row that is `▶ Playing` or `⏸ Paused` (not stale); tapping
  it asks for confirmation, sends the command and shows "Stopping…" until that device's
  `now_playing` turns `stopped` (or "Couldn't reach <device>" after ~90 s).
- **Trust model.** Any device of the group can send it — the group shares one account key and is
  trusted by design. It only stops: playback can be started again on the TV straight away (a
  lock is out of scope, §7).

## 4. What it costs

- **Network:** one small record per minute per *playing* device, plus one per state change.
- **Server:** one row per device, upserted; nothing accumulates (no tombstones — `stopped` is a
  normal record).
- **Battery:** nothing new runs in the background; heartbeats only while video is playing.
- **Remote Stop:** one record per tap; one row per target device on the server, upserted.

## 5. Phases

### Phase 0 — Optional quick win (independent, ~5 lines)
Set `MediaMetadata` (title, show, episode) on the `MediaItem` in `StreamingMediaSourceFactory`
from `PlayerMetadata`, so `adb shell dumpsys media_session` and the Android TV system UI show what
is playing. Useful for debugging on its own; not needed for the feature.

### Phase 1 — Sender
1. `SyncKind.NOW_PLAYING` + `SyncPayloads.NowPlaying`.
2. `StreamingPlaybackService` companion `nowPlaying: StateFlow<NowPlayingSnapshot?>` (title,
   show, episode label, live, state), fed from `currentMetadata` + `playbackState`.
3. `PlayerMetadata.programTitle`, filled by `StreamLoaderViewModel` via `updateMetadata()` from
   `currentEpgProgram`.
4. `NowPlayingPublisher` (core:ui/sync): collect, debounce, heartbeat, `stopped` on end; the
   device-local share setting (default off).
5. `SyncEngine.push` sends the publisher's pending record; tests for encode/decode and that a
   heartbeat's `updatedAt` always increases.

**Done 2026-10-01 (Phases 0-1).** As planned, with these choices: the volatile outbox is generic
(`VolatileRecords`, keyed by `SyncKey`; `SyncKind.VOLATILE` lists the kinds — Phase 4 adds
`REMOTE_COMMAND` there), stamped from `SettingsSyncDao.nextClock()` so a key's `updatedAt` always
rises, and an entry is dropped only after the push that took it succeeds. A heartbeat is also sent
while **paused**, so a paused TV switched off at the wall goes stale too. The live programme is
patched from the four screens that play live (TV player, TV split preview, mobile player, mobile
dock) through `updateMetadata`, since `StreamLoaderViewModel` has no `PlaybackViewModel`; it
re-reads the guide when the programme ends (`followProgrammes()`). On mobile `channelName` holds the
provider name, so the item title is used as the channel. The share switch is the device pref
`share_now_playing`; switching it off sends `stopped` only if this process shared something.

### Phase 2 — Receiver and UI
1. `SyncApplier`: `NOW_PLAYING` → `NowPlayingStore`; no version, no tombstone. Test that it never
   writes to either database.
2. `SyncSettingsViewModel` join + staleness rule (3 min), unit-tested with a fake clock.
3. Devices rows on TV and mobile; the share setting in both Live sync screens.

**Done 2026-10-01 (Phase 2).** `SyncApplier` routes `NOW_PLAYING` to `NowPlayingStore` and leaves
volatile kinds out of the clock `receive()`, so nothing is written to either database (tested with
mocked databases). A record is current only if it is playing/paused and both `now - sentAt` and
`now - receivedAt` are ≤ 3 min — the second guards a sender whose clock runs ahead; a sender whose
clock is more than 3 min *behind* is handled by the Phase 3 fix. Stopping on **Leave** lives in
`SyncSettingsViewModel.leave()` (queue `stopped`, flush, leave, clear the store) because
`core:network` can't reach the publisher.

### Phase 3 — End to end
Two emulators linked through a local `workerd` server (`server/` test harness, as used for the
stability plan's Phase 2): play on the TV emulator, watch the phone emulator's devices list —
starts within seconds, follows a channel change, shows paused, goes idle on stop, and goes stale
3 minutes after force-stopping the TV app. Then on the real devices with permission.

**Done 2026-10-01 (Phase 3).** All 7 emulator checks passed: live within ~3 s including the EPG
programme; one push for 3 quick zaps; a movie playing → paused within 2 s; Back clears at once;
force-stop makes the line disappear after 3 min 7 s; share off publishes nothing; the media session
shows titles. Two fixes came out of the run: (A) a TV whose clock is more than 3 min slow never
showed, so `Entry.isCurrent(now, lastSeen)` now also accepts a record that looks old by the
sender's `sentAt` when the server's `lastSeen` for that device (devices list, server clock) is
within 3 min — an old record of a device switched off long ago has an old `lastSeen` and stays
hidden, and `receivedAt` > 3 min hides regardless; (B) the devices list was only loaded on open, so
`SyncSettingsViewModel` now reloads it (best effort, at most once per 30 s) when `NowPlayingStore`
gets a newer record, keeping "Last seen" and Fix A's `lastSeen` fresh.

### Phase 4 — Remote Stop (phone app only)
1. `sessionId` in `NowPlayingSnapshot` / the `now_playing` payload, new per `playStream`.
2. `SyncKind.REMOTE_COMMAND` + payload; volatile push like `now_playing`.
3. `SyncApplier` → `RemoteCommands` event flow, only when `sessionId` matches the current
   session; tests: matching command fires once, a stale or replayed one (old session id) never
   does, an unknown command is ignored.
4. Player screens on TV and mobile react: finalise, stop, back to Home, message.
5. Mobile devices list: Stop button + confirmation + "Stopping…" / "Couldn't reach" states. No
   Stop button anywhere in the TV app.
6. End to end on the emulators: phone stops the TV emulator's movie within seconds; the TV's
   watch position is saved; re-syncing the TV from 0 afterwards does not stop the next playback.

## 6. Decisions (answered 2026-10-01)

1. **Share setting: off by default**, turned on per device (e.g. once on the kids' TV). Device-local,
   never synced.
2. **Live TV: channel + current programme** ("BBC World News — Newsday") when EPG data is
   available; channel only otherwise.
3. **Shown on both phone and TV** devices lists.
4. **Profile name shown** ("· Kid").
5. **Remote Stop: yes, button in the phone app only** (never in the TV app). Any device obeys a
   valid stop command for its current session.

## 7. Out of scope

- Remote control beyond Stop (pause, resume, change channel), and a Stop button in the TV app.
- Keeping playback stopped (a lock or timer) — Stop is one-shot; the device can play again at once.
- Push notifications when something starts playing — would need background work, which Live sync
  deliberately doesn't do.
- History or reports of past viewing per device.
