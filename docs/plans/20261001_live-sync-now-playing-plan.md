# Live Sync "Now Playing" Plan

**Status:** 📋 **PROPOSED** — not started.
**Date:** 2026-10-01
**Scope:** `core:player` (one flow), `core:network/sync`, `core:ui/sync`, TV + mobile Live sync screens. No server change.

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
  `channelName?`, `profileName` (who's watching on that device), `positionMs?`, `durationMs?`,
  `sentAt` (wall clock of the sender, for staleness).
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
- **When a record is sent:** on a change of item or of playing/paused/stopped, debounced ~2 s
  (channel zapping sends one record, not ten); plus a **heartbeat every 60 s while playing**;
  plus `stopped` when playback ends or the service is released.
- **Staleness instead of a guaranteed stop:** a TV switched off at the wall never sends `stopped`.
  The receiver treats a `playing` record older than **3 minutes** (3 missed heartbeats) as
  stopped.
- **Transport:** each send goes through the existing `SyncManager.requestSync(debounce)` path, so
  it rides the normal push and the server's `{"head": n}` broadcast wakes every open device in
  seconds. Sync only runs while the app is in the foreground — which a TV playing video is.
- **Off switch:** a per-device setting, **"Share what's playing with my sync group"**, in Live
  sync settings (see §6 for the default). Off: publish nothing, and send one `stopped` record
  so other devices don't keep showing stale state.

### 3.3 Showing it (receiver)

- `SyncSettingsViewModel` combines `ui.devices` (server list) with `NowPlayingStore` by device id.
- Each device row gains one line:
  - **▶ Playing · Malcolm X** / **▶ Playing · The King of Queens S1:E12** /
    **▶ Live · BBC World News** — with "· atr" (profile) and "2 min ago" when useful.
  - **⏸ Paused · …**, or nothing extra when stopped or stale (the existing "Last seen …" stays).
- TV `SyncSettingsScreen.kt` `DevicesPanel` (`:237`) and mobile `MobileSyncSettingsScreen.kt`
  `DevicesPanel` (`:275`). Strings in en/fr (mg falls back, like the rest of Live sync).
- **Freshness while looking:** the phone is in the foreground on that screen, so its WebSocket is
  open and a heartbeat lands within seconds. Opening the app later triggers the usual catch-up
  pass, which brings the latest heartbeat (≤ 60 s old) straight away.

### 3.4 Cleanup

- A removed or unlinked device stops heartbeating; its record goes stale in 3 minutes and the
  devices list no longer lists it anyway (records are only shown joined to a listed device).
- On **Leave sync group**, send `stopped` first (best effort).

## 4. What it costs

- **Network:** one small record per minute per *playing* device, plus one per state change.
- **Server:** one row per device, upserted; nothing accumulates (no tombstones — `stopped` is a
  normal record).
- **Battery:** nothing new runs in the background; heartbeats only while video is playing.

## 5. Phases

### Phase 0 — Optional quick win (independent, ~5 lines)
Set `MediaMetadata` (title, show, episode) on the `MediaItem` in `StreamingMediaSourceFactory`
from `PlayerMetadata`, so `adb shell dumpsys media_session` and the Android TV system UI show what
is playing. Useful for debugging on its own; not needed for the feature.

### Phase 1 — Sender
1. `SyncKind.NOW_PLAYING` + `SyncPayloads.NowPlaying`.
2. `StreamingPlaybackService` companion `nowPlaying: StateFlow<NowPlayingSnapshot?>` (title,
   show, episode label, live, state), fed from `currentMetadata` + `playbackState`.
3. `NowPlayingPublisher` (core:ui/sync): collect, debounce, heartbeat, `stopped` on end; the
   share-setting check.
4. `SyncEngine.push` sends the publisher's pending record; tests for encode/decode and that a
   heartbeat's `updatedAt` always increases.

### Phase 2 — Receiver and UI
1. `SyncApplier`: `NOW_PLAYING` → `NowPlayingStore`; no version, no tombstone. Test that it never
   writes to either database.
2. `SyncSettingsViewModel` join + staleness rule (3 min), unit-tested with a fake clock.
3. Devices rows on TV and mobile; the share setting in both Live sync screens.

### Phase 3 — End to end
Two emulators linked through a local `workerd` server (`server/` test harness, as used for the
stability plan's Phase 2): play on the TV emulator, watch the phone emulator's devices list —
starts within seconds, follows a channel change, shows paused, goes idle on stop, and goes stale
3 minutes after force-stopping the TV app. Then on the real devices with permission.

## 6. Open questions (need a decision before Phase 1)

1. **Default of the share setting:** on for every device (simplest for the parent use case) or
   off until turned on per device (more private)? The record is end-to-end encrypted either way,
   so only devices in the group can see it.
2. **Live TV:** show the channel name too, or only "Live TV"?
3. **Show it on the TV's own devices list as well**, or phone only? (Same code; just whether the
   TV screen gets the line.)
4. **Profile name:** show which profile is watching ("· Kid"), or leave it out?

## 7. Out of scope

- Remote control (pause or stop the TV from the phone).
- Push notifications when something starts playing — would need background work, which Live sync
  deliberately doesn't do.
- History or reports of past viewing per device.
