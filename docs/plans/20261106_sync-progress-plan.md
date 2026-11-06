# Live Sync progress and delays

**Status:** Planned 2026-11-06. All phases done 2026-11-06 and walked on both emulators.

## Goal

The user (2026-11-06): live sync sometimes takes long, and things took a long time to sync; a
progress bar on the Live Sync page, so the wait does not look like a spinner that never ends.

Two problems: a **long pass** shows only "Syncing…", and a **change that arrives late** shows
nothing at all. This plan shows a pass's progress, shows why a change may be waiting, removes the
longest silent wait, and logs every pass so the next slow case can be explained.

## What exists (survey 2026-11-06)

- **A pass** (`SyncEngine.syncNow`): pull pages of up to 500 records (server `MAX_PAGE` 1000,
  default 500) until `more` is false, decrypting and applying each page; then push pending records
  in batches of 200. The server sends no total. `SyncManager.Status.syncing` is the only state the
  UI sees; the Live Sync page and its Settings card show "Syncing…" (`LiveSyncStatusText`).
- **Long passes:** a full pull after Full resync, on linking, past the 90-day tombstone horizon, or
  after an update that adds a kept kind. A pass also runs again at once when something changed
  while it ran (`passAgain`).
- **Late changes** (`SyncManager`):
  - Sync runs only while the app is in the foreground; the socket closes in the background (by
    design).
  - Watch progress waits up to 60 s while playing (`WATCH_PUSH_INTERVAL_MS`); other changes 3 s.
  - **After a socket failure** the reconnect waits 5 s, then doubles up to 5 minutes
    (`socketRetryDelayMs`). While the socket is down, nothing pulls the other devices' changes:
    only a local change or the next return to the foreground starts a pass.
  - After a failed pass, `retryLater` also doubles up to 5 minutes.
- **Logs:** only "Synced: pulled N, pushed M" after a pass with changes; no time, no pages, no
  socket state. darcy's log on 2026-11-06 showed passes of under 0.5 s and no socket failure; a Full
  resync on the TV emulator took 1.1 s (small account).

## Decisions (proposed)

| # | Subject | Decision |
|---|---|---|
| SP-D1 | Progress state | `SyncManager.Status` gets `progress: SyncProgress?` while a pass runs: the stage (`RECEIVING`: records fetched and applied, page by page; `SENDING`), records done, and the total when known. `SyncEngine` reports it per page and per batch through `Listener.onProgress`. Applying is part of receiving (each page is applied before the next is fetched), so there is no separate stage. |
| SP-D2 | Receive total | The server's pull response adds `remaining`: the records after this page (one `COUNT(*)` with `seq >` the page's last, only when `more`). An older server sends none (`remaining` null); the app then shows the count so far. `server/` and its tests change; the field is optional both ways. |
| SP-D3 | Send total | The pending count is read before the first push batch (`sync_version` and the `xtream_v2.db` versions with `pending = 1`). |
| SP-D4 | Display | On the Live Sync page, under the status line: "Receiving changes…" (nothing yet), "Receiving 1,500 of 4,200", "Sending 200 of 640", with a determinate bar when the total is known and the moving bar otherwise. The status line itself carries the text, so the Settings row shows it too; the bar is on the Live Sync page only. A pass under 1 s shows no progress (no flash). TV and phone, the theme's progress bar. |
| SP-D5 | Connection state | The Live Sync page shows the socket state under the last sync: "Connected: changes arrive at once", "Reconnecting in 2 min" (with **Reconnect now**), or "Not connected while the app is closed" as a note. A Nerd also sees the last error. |
| SP-D6 | No silent wait | While the socket is down and the app is in the foreground, a pull runs every 60 s (the socket reconnect still doubles). So a change waits at most about a minute, not up to 5 minutes. Stops when the socket opens or the app goes to the background. |
| SP-D7 | Logs | Each pass logs its time, its stage times, pages, records pulled, applied and pushed, and whether it was a full pull; each socket close logs its code and the next delay. In the crash and diagnostics log for a Nerd (`CrashLog.note`) when a pass takes over 10 s. |
| SP-D8 | Words | en/fr/mg: "Receiving %1$s of %2$s", "Receiving… %1$s so far", "Sending %1$s of %2$s", "Connected: changes arrive at once", "Reconnecting in %1$s", "Reconnect Now", "Changes arrive when the app is open". Numbers in the app language's format. |

## Phases

| Phase | Work | Status |
|---|---|---|
| SP1 | **Engine and server.** `SyncProgress` from `SyncEngine` through `SyncManager.Status`; the server's `remaining`; the pending count; pass and socket logs (SP-D7). Tests: progress per page with and without `remaining`, the server count, logs on a slow pass. | Done 2026-11-06 |
| SP2 | **No silent wait.** The 60 s pull while the socket is down (SP-D6), the socket state in `Status`, Reconnect now. Tests with the fake socket factory: a failed socket pulls every 60 s and stops when it opens or in the background. | Done 2026-11-06 |
| SP3 | **Display.** The progress line and bar, and the connection state, on the Live Sync page and its card, TV and phone. Docs: FEATURES (Live Sync), RELEASE_NOTES. Checked on both emulators with a Full resync on a larger account (seeded history) and with the sync server stopped and started. | Done 2026-11-06, walked on both emulators: connected, reconnecting with the server stopped, Reconnect now and its focus, progress recorded on a Full resync (548 records, 2 pages, with the 1 s threshold off in a test build) |

| SP4 | **Download ahead** (user, 2026-11-06: "why does the wait need to be synchronous?"). The next page downloads while the current one is applied (`supervisorScope` + `async` in `SyncEngine.pull`). Pages still apply in order, and the cursor is still saved after each applied page (F-09); a failed download fails the pass only when awaited, after the page before it was saved. Test: page 2 is fetched before page 1 is applied. | Done 2026-11-06 |
Each phase is one commit on main. The server change (SP1) needs the user's server redeployed for
the totals; without it the app still shows counts.

## Not in scope

- Background sync while the app is closed (by design, sync runs in the foreground only).
- Making the apply itself faster; SP-D7's logs first say where the time goes. (Downloading ahead, SP4, overlaps the network with it.)
