# Multi-device Xtream sync + "who's playing" presence/kick

## Context

Fijerena runs on phone + 2 Shields, all pointed at the same Xtream subscription. Xtream favorites/history/resume-position are currently **local-only**, stored per-device in `SharedPreferences` (`XtreamUserDataManager`) — nothing syncs between devices. Jellyfin already solves this server-side (favorites/history/resume via its own API, sessions via `/Sessions`), so **this plan is Xtream-only**; Jellyfin-backed providers are untouched.

Three related problems to solve:
1. **Data sync** — favorites/watch-history/resume-position should follow the user across phone/Shield/Shield.
2. **Connection visibility** — Xtream subscriptions cap concurrent connections. The Xtream API only exposes a connection *count* (`active_cons`/`max_connections`), never *what* is playing *where*. When the count is maxed and something looks stuck/errant, there's currently no way to see which device it is or stop it.
3. **User profiles** — the household shares devices (Alice and Bob both use either Shield), so sync can't be single-namespace. Alice's favorites/history should follow *Alice* between Shield 1, Shield 2, and her phone; Bob gets his own, separate. One shared Xtream subscription/catalog underneath — profiles only personalize favorites/history/position/presence, they don't imply separate Xtream accounts. (Same Jellyfin exception as above: Jellyfin already has native per-user accounts, so this is Xtream-only, same as the rest of this plan.)

User decisions (already made):
- **Backend hosting: undecided, three viable options being planned in parallel** — self-hosted Go server, Firebase, Supabase. See "Backend options" section below. Everything else in this document (data model concepts, phases, app-side profile/kick/presence logic) is written to apply to all three; only the sections marked per-option cover what differs.
- Presence is self-reported: each device heartbeats "I am playing X" to the server; other devices read that list.
- Kick = remote-stop signal, not just visibility: server flags a device, that device's own app stops its playback on the next heartbeat.
- Auth = per-device token, issued out-of-band (not self-service signup — personal use, 3 devices).
- Profiles: open picker, no PIN. Created in-app (Netflix-style "Add profile" in the picker, syncs to server immediately). Each device remembers its own last-used profile across launches until manually switched.

## Backend options

Three ways to host everything described in this plan. The data model, phases, profiles, kick, and app-side plumbing are the same regardless of which one gets picked — what changes is who runs the server, how auth/offline-writes/realtime work, and how much code this repo ends up owning. Decision is deferred; **Architecture** below is written for Option A in full detail (it's the default/fallback) with Options B and C as sibling subsections covering only what differs.

**Comparison**

| | A: Self-hosted (Go + SQLite) | B: Firebase (Firestore + Auth) | C: Supabase (Postgres + Realtime) |
|---|---|---|---|
| Ops burden | You run/patch/TLS the container | None — fully managed | None — fully managed (hosted tier) |
| Query model | Plain SQL — exact fit for `GET /history?profileId=&deviceId=` | NoSQL, composite indexes for compound filters | SQL (Postgres) — same schema as Option A ports over |
| Offline write queue | Hand-built (`pending_sync_ops` + `SyncPushWorker`) | Free — Firestore SDK does this natively | Partial — needs a thinner version of Option A's outbox |
| Presence/kick latency | ~20s heartbeat poll (custom) | Near-instant (realtime listeners) | Near-instant (Realtime, Postgres-replication-based) |
| Stale-presence cleanup | Manual TTL/cron logic | Native document TTL | Needs a scheduled job (pg_cron or similar) |
| Authorization model | Application code (Go handlers) | Firestore Security Rules (declarative) | Row-Level Security (SQL `USING`/`WITH CHECK`) |
| Self-hosted alignment | Full match | Conflicts with it | Conflicts with hosted tier; self-host path exists but is heavier than Option A |
| Vendor lock-in | None | High — no self-host path, real rewrite to leave | Low — open source, can self-host later without a data-model rewrite |
| Relative build effort | Most (full Phase 0 + outbox) | Least | Middle (no server ops, but still SQL/RLS design work + a partial outbox) |

**Option A — Self-hosted Go server**
- \+ Full control, no third-party account; matches the original self-hosted requirement exactly.
- \+ No usage-based cost or free-tier ceiling.
- \+ Data never leaves infrastructure you control.
- \+ Small, fully auditable stack — one Go binary, one SQLite file, nothing to trust blindly.
- \+ API shaped exactly to this app's needs, nothing extra.
- \- Most total code to write: full auth layer, schema/migrations, Docker, deploy, and you own uptime/patching/TLS (reverse proxy) yourself.
- \- Outbox/retry for offline writes is hand-built — the single biggest chunk of Phase 2.
- \- No realtime primitive — presence/kick latency is bounded by the heartbeat interval (~20s) unless a websocket/long-poll layer is added on top, and stale-presence cleanup is a manual job.

**Option B — Firebase (Firestore + Auth)**
- \+ Least code overall — Phase 0 shrinks to "mint a custom auth token" (a script, not a deployed service).
- \+ Offline writes are close to free — Firestore SDK has built-in persistence + automatic retry, eliminating most of the outbox.
- \+ Presence/kick becomes realtime (listeners) instead of poll-based — better UX for less code, and native document TTL replaces manual staleness cleanup.
- \+ Fully managed — no hosting/ops/TLS/uptime burden.
- \+ Mature Android SDK, large community, extensive docs.
- \- Conflicts directly with the original self-hosted requirement — data sits on Google's infrastructure.
- \- Security Rules replace application code for authorization; the cross-panel URL guard and device-token/profile-self-service trust model have to be re-expressed declaratively, a different (not obviously easier) paradigm to get right.
- \- NoSQL query model is a worse fit for ad hoc filtering like `GET /history?profileId=&deviceId=` — needs composite indexes, more rigid than SQL `WHERE`.
- \- Real vendor lock-in — no self-host escape hatch if you ever want to leave.
- \- New footprint: Google account/project, `google-services.json`, Play Services dependency (this repo already pulls in `play-services-auth`/Drive APIs for backup, so not entirely novel, but still growth).
- \- Usage-based billing exists past the free tier (unlikely to bite at 3-device personal scale, but it's there).

**Option C — Supabase (Postgres + Realtime + Auth)**
- \+ Middle ground: hosted/managed like Firebase, but Postgres underneath — the exact schema and `GET /history?profileId=&deviceId=`-style query design already written for Option A ports over almost unchanged, unlike a Firestore remodel.
- \+ Open source with a genuine self-host path later, without a data-model rewrite, if the hosted tier ever stops making sense — Firebase has no equivalent exit.
- \+ Built-in Realtime gives the same near-instant presence/kick benefit as Firebase.
- \+ Row-Level Security is SQL-based (`USING`/`WITH CHECK`) — maps naturally onto authorization logic already designed for Option A, arguably an easier mental transfer than Firestore rules.
- \- Same self-hosted-preference conflict as Firebase on the hosted tier; self-hosting Supabase yourself is a materially heavier deploy than the plain Go server (Postgres + Realtime + Auth + Studio, several containers vs. one).
- \- Smaller ecosystem and less mature Android tooling than Firebase — fewer examples, smaller community.
- \- Offline write queuing is less automatic/battle-tested than Firestore's — still likely need a thinner version of Option A's outbox, so less of a clean win than Firebase's "free" offline story.
- \- Still a hosted project with usage ceilings and keys/RLS policies to manage even though there's no server code to write.

## Architecture

### Option A — Self-hosted `server/` (Go + SQLite)

Everything in this **Architecture** section (through "Server-down / offline behavior") describes Option A specifically, in full detail as the default/fallback plan. Options B and C are covered afterward, each only spelling out what differs — data model shape, profile scoping, series-context, cross-panel guard *intent*, and everything in **App changes** is identical across all three.

### New component: `server/` (subfolder in this repo, not part of the Gradle build)

Lives at `server/` alongside `core/`, `mobile/`, `tv/`. `settings.gradle.kts` only builds modules it explicitly lists, so this folder is invisible to the Android build — no entanglement. Docker build just points at `server/Dockerfile`. Monorepo chosen deliberately: the API contract (sync fields, presence payload, kick semantics) will churn during build-out across both phases, and keeping client+server changes in one commit beats lockstep-versioning a two-party internal API. Can be split into its own repo later once the contract stabilizes and independent deploy cadence is actually wanted.

Minimal Go service (single static binary, tiny Docker image, no cgo) + SQLite. Single implicit *server* namespace (one Xtream subscription shared by all your devices) with **profiles as the actual data-ownership boundary** underneath it — no multi-tenant/multi-subscription modeling needed, just multi-profile.

**Data model (SQLite):**
- `devices(id, token_hash, name, created_at)` — identifies a trusted physical device/app install, not a person.
- `server_config(key, value)` — single row `xtream_server_url`, set from the first device's sync call and checked against on every subsequent one (see cross-panel guard below).
- `profiles(id, name, created_at)` — Alice, Bob, ... created in-app, synced to all devices.
- `favorites(profile_id, stream_id, content_type, stream_name, category_id, updated_at, deleted_at NULL)` — tombstone on delete, so an offline device pulling a stale list can't resurrect a favorite someone else removed. Unique on `(profile_id, stream_id, content_type)`.
- `watch_history(profile_id, stream_id, content_type, stream_name, category_id, position_ms, duration_ms, is_completed, updated_at, device_id, device_name)` — same uniqueness (`profile_id, stream_id, content_type`), scoped by profile. `device_id`/`device_name` record whichever device last wrote this row — derived server-side from the authenticated Bearer token on the write, never a client-supplied field, so it can't be spoofed. Note this is "last device to touch it," not a full multi-device play-by-play — if the same episode is watched partway on Shield 1 then finished on the phone, the row just ends up attributed to the phone, matching how resume position already collapses to one row per item today.
- `presence(device_id, device_name, profile_id, profile_name, content_type, stream_id, stream_name, series_name NULL, started_at, last_heartbeat_at, kick_requested)` — a presence row is "this device, currently being used by this profile, playing this." `series_name` is set only for `TV_SHOWS` playback, so the "Now Playing" panel can show "Breaking Bad, S1E3" instead of a bare episode title.

**API (Bearer token per device, validated against `devices.token_hash`; every sync/presence call also carries a `profileId`):**
- `GET /profiles` — list profiles for the picker.
- `POST /profiles {name}` — create profile in-app; any device can call this (device token is the trust boundary, not the profile itself, matching "open picker, no PIN").
- `PUT /profiles/{id}/favorites/{streamId}` / `DELETE /profiles/{id}/favorites/{streamId}` — mirrors `XtreamUserDataManager.addFavorite`/`removeFavorite` call sites 1:1.
- `PUT /profiles/{id}/history/{streamId}` — upsert position/duration/completed, mirrors `savePlaybackPosition`.
- `GET /profiles/{id}/state` — full current favorites + history snapshot for that profile (dataset is tiny — capped by `favoritesMaxSize`/`watchHistorySize` — so a full snapshot beats delta/cursor complexity). Used by `SyncPullWorker` to hydrate a device.
- `GET /history?profileId=&deviceId=` — API-only query endpoint (no app UI), at least one of the two params required: both given → that profile's history on that device; `profileId` alone → that profile across all devices; `deviceId` alone → everything watched on that device regardless of profile. Just filters `watch_history` by the columns above, sorted by `updated_at` desc — same table `/profiles/{id}/state` already reads, no new storage.
- `POST /presence/heartbeat` `{deviceName, profileId, profileName, contentType, streamId, streamName, seriesName?}` → response `{kick: bool}`. Sent on `onPlaybackStarted` and throttled during `onPlaybackProgress` (e.g. every ~20s, not every progress tick). Stale entries (no heartbeat for N seconds) auto-expire server-side — covers app kills/crashes without needing an explicit stop call to always land.
- `POST /presence/stop` — explicit stop on `onPlaybackStopped` (best-effort; TTL expiry is the fallback).
- `POST /presence/{deviceId}/kick` — sets `kick_requested`; picked up by that device's *own* next heartbeat response, which is the actual remote-stop trigger. Targets the device (whatever profile is currently active there), since stopping playback is inherently a device-level action.

No self-service *device* registration endpoint — devices are provisioned by inserting a row (CLI or one-off admin call) and copying the token into the app's Settings, same trust model as entering Xtream server URL/creds today. *Profiles*, by contrast, are self-service from any already-trusted device. Server expected to sit behind the user's own reverse proxy for TLS; the container itself just speaks plain HTTP.

### Cross-panel safety guard

The whole sync design assumes every device points at the *same* Xtream panel (confirmed — one shared subscription, multiple independently-configured devices). `stream_id` is only a stable, meaningful key for favorites/history/resume *within one panel's catalog*; if a device were ever pointed at a different Xtream server/reseller instance (even with identical login), the same `stream_id` can mean a different piece of content, and syncing it would silently favorite/resume the wrong title. Guarding against this cheaply:

- Every `PUT/DELETE /profiles/{id}/favorites/...`, `/history/...`, and `POST /presence/heartbeat` call includes a normalized Xtream server URL (scheme + host, no path/query/trailing slash) alongside the existing device token and `profileId` — read from the active `ProviderEntity.url` locally, not a new field the user has to enter.
- Server checks it against `server_config['xtream_server_url']`. First device to ever sync sets it. Every call after that must match, or the server rejects with a distinct error code (e.g. `409 xtream_server_mismatch`) instead of silently accepting mismatched data.
- App treats that rejection as non-fatal and visible, not a crash: surface "This device's Xtream server doesn't match the others — sync paused" in the Connected Devices screen (same non-blocking pattern as the reachability indicator), keep working purely locally otherwise.
- This is a cheap tripwire, not a migration path — if the panel URL legitimately changes (re-subscription, provider switch), the fix is an explicit admin action (update `server_config`), not automatic silent adoption of a new "same" URL.

### App changes (this repo)

**Playback-code impact — deliberately minimal.** Media3/ExoPlayer itself is never touched. Phases 0-2 (auth, profiles, data sync) only add code inside `core/network`, riding on `onPlaybackStarted/onPlaybackProgress/onPlaybackStopped` calls and `XtreamUserDataManager` mutation points that already exist and already fire from `StreamLoaderViewModel` today — no call-site or signature changes needed. Two small, deliberate exceptions:
- **Series context for presence (Phase 3):** `onPlaybackStarted`/`onPlaybackProgress` don't currently carry `seriesId`/`seriesName`, even though `StreamLoaderViewModel` already has both locally (constructor params, `StreamLoaderViewModel.kt:29,31`) — needed so the "Now Playing" panel can show "Breaking Bad, S1E3" instead of a bare episode title. Add an optional `seriesName: String? = null` param to both `MediaProvider` interface methods (`core/player`) and thread it through `MediaRepository` → `XtreamMediaProvider`. Default-null keeps Jellyfin's override source-compatible (it ignores the new param, matching how Jellyfin already handles this server-side anyway).
- **Kick (Phase 4):** `onPlaybackProgress` returns `Unit`, so there's no channel back up to the caller to say "stop now." `MediaRepository` (`core/network`) exposes a `Flow<KickEvent>`; `StreamLoaderViewModel` (`core/ui`) collects it and calls its own existing `stopPlayback()` + shows a message.

Both stay within the existing `core/network` → `core/ui` dependency direction (the interface param addition touches `core/player`'s domain layer, but as an optional, additive parameter — not a new dependency edge) and don't touch the "`core:player` must not depend on `core:network`" constraint (AGENTS.md).

**Reused hook points — already exist, no new plumbing needed for call timing:**
- `MediaProvider.onPlaybackStarted/onPlaybackProgress/onPlaybackStopped` (`core/player/src/main/java/org/njarasoa/fijerena/core/player/domain/MediaProvider.kt:57-79`), called from `StreamLoaderViewModel.updateProgress`/`stopPlayback` (`core/ui/.../StreamLoaderViewModel.kt:446,494`) via `MediaRepository` (`core/network/.../MediaRepository.kt:360-378`). `XtreamMediaProvider` currently no-ops these (inherits interface defaults) — this is where presence heartbeats get wired in.
- `XtreamUserDataManager.addFavorite/removeFavorite/savePlaybackPosition` (`core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/manager/XtreamUserDataManager.kt:207,238,301`) — where local mutations happen today; sync push calls go right next to the existing `commitAsync` writes. Favorites are series-level (`SeriesDetailsViewModel` favorites by `seriesId`), watch history/position is per-episode (`StreamLoaderViewModel`'s `streamId` is the episode's own Xtream stream ID) — both already keyed by `(streamId, contentType)` locally, matching the server's `(profile_id, stream_id, content_type)` uniqueness exactly. No new collision risk between movies/episodes/live channels beyond what already exists today.

**Not touched: category browsing, search, EPG.** Those are catalog reads served by `XtreamContentManager`/`XtreamEpgManager` off the *shared* per-provider cache (`xtream_cache_$providerId`) — same catalog for every profile under one subscription, no reason to scope it per-profile. `getCategories`/`getItems`/`search`/`getEpg`/`getEpgBulk` on `XtreamMediaProvider` are unchanged by every phase of this plan. Only `XtreamUserDataManager` (favorites/history/position/last-played) moves to a profile-scoped file.

**New pieces:**
1. `SyncClient` (Ktor, new file in `core/network/.../xtream/sync/`) — thin wrapper for the API above, holding the device token and the currently-active profile ID.
2. Device token + server URL storage: new `EncryptedSharedPreferences` entry, same pattern as `ProviderRepository`'s per-provider creds (`core/network/.../provider/ProviderRepository.kt:290-328`). Device name is user-editable in Settings, sent with every heartbeat.
3. **Active profile identity**: new small `ProfileManager` — persists `activeProfileId`/`activeProfileName` per device (plain `SharedPreferences`, not encrypted — not sensitive), matching the "remember per-device" decision. Exposes the active profile as a `StateFlow` so UI (and `XtreamRepository`) can react to a switch.
4. **Local cache re-scoping**: `XtreamUserDataManager` currently shares the same `SharedPreferences` file (`xtream_cache_$providerId`) as `XtreamContentManager`/`XtreamStatsManager`/`XtreamEpgManager` (catalog/EPG data — not profile-specific, stays shared). Give `XtreamUserDataManager` its own file, scoped by profile: `xtream_userdata_${providerId}_${profileId}`. `XtreamRepository`'s constructor takes the active `profileId` and passes this dedicated prefs file in; switching profiles means re-instantiating `XtreamUserDataManager` (or lazily swapping its backing prefs + clearing its in-memory caches) against the new file, then triggering a `SyncPullWorker` one-shot pull to hydrate it. `favoritesCacheMap`'s key (currently just `providerId`, `XtreamUserDataManager.kt:60`) becomes `Pair(providerId, profileId)`.
5. Wire pushes into `XtreamUserDataManager`'s three mutation points (fire-and-forget on the existing `writeScope`, so no new threading model) and into `XtreamMediaProvider`'s playback lifecycle overrides for presence — every push/heartbeat call includes the active `profileId`/`profileName` from `ProfileManager`.
6. `SyncPullWorker` — new `CoroutineWorker`, same shape as `XtreamSyncWorker` (`core/network/.../xtream/XtreamSyncWorker.kt`), periodic pull of `GET /profiles/{activeProfileId}/state`, merges into the profile-scoped local `SharedPreferences` by `updated_at` (server wins if newer) — also run once on app foreground/provider activation and immediately after a profile switch, not just on the periodic schedule.
7. **Profile picker screen** ("Who's watching?"): shown on first run and whenever the user explicitly chooses to switch (e.g. an avatar/profile entry point in Settings); pulls `GET /profiles`, lets the user pick one (sets active profile, re-scopes local cache per #4, triggers a pull) or "Add profile" (`POST /profiles`, then picks it). No PIN prompt.
8. New Settings screen "Connected Devices": server URL + token entry (paste-once setup), and a live "Now Playing" panel (`GET /presence`, polled while the screen is open) listing profile name / device name / title / since-when, each row with a "Boot" button calling `POST /presence/{deviceId}/kick`.
9. Kick receiving: the heartbeat call site checks the `{kick: true}` response and tells `StreamLoaderViewModel` to stop playback + surface a "disconnected from another device" message. Delivery latency = one heartbeat interval (~20s) — acceptable for this use case, avoids standing up a websocket/push channel.

**Gating:** all of the above only activates when the active `ProviderEntity.type == "XTREAM"` — no Jellyfin code paths touched.

### Server-down / offline behavior

Local `SharedPreferences` is always the source of truth for reads — browsing, favoriting, resuming playback all work exactly as they do today with the sync server fully unreachable. The server is a best-effort overlay, never a blocker, with two exceptions to that "fire-and-forget" simplicity called out below:

- **Favorite/history/position pushes need an outbox, not pure fire-and-forget.** A push issued while the server is down must not be silently dropped — persist pending mutations (e.g. a small `pending_sync_ops` list alongside the profile-scoped prefs file) and drain them via a `SyncPushWorker`, same retry/backoff shape as `XtreamSyncWorker` (`Result.retry()` + `MAX_RETRIES`, `XtreamSyncWorker.kt:29-34`). Runs opportunistically (network-available `Constraints`) and on the existing periodic schedule.
- **Presence heartbeat/kick stay pure fire-and-forget.** Ephemeral by nature — a failed heartbeat just means that device doesn't show up in "Now Playing" until connectivity returns. No queue, no retry needed, no data to lose.
- **`SyncPullWorker`** already retry-friendly via `CoroutineWorker` — reuses the same pattern.
- **Profile picker on first run with no connectivity:** cache the last-fetched `GET /profiles` list locally so the picker isn't hard-blocked by one bad fetch. If there's truly no cached list yet (very first launch, server unreachable), fall back to a local-only "offline" pseudo-profile so the app is usable immediately; real profile selection/creation happens next time the server is reachable, and `SyncPullWorker` reconciles once it comes back.
- Surface reachability non-intrusively: the "Connected Devices" Settings screen shows "Sync server: unreachable" when the last request failed, so it's diagnosable — never a modal/blocking error.

### Option B — Firebase (Firestore + Auth)

Same data ownership model as Option A (profiles are the scoping boundary, devices are trust boundary, one shared Xtream panel) — only the shapes and mechanisms differ.

**Data model (Firestore):**
- `devices/{deviceId}` — `{deviceName, createdAt}`. No token hash to store — Firebase Auth itself is the credential; `deviceId` is the signed-in user's UID.
- `config/xtreamServerUrl` — single doc, `{value}`. Same role as Option A's `server_config` row, but set once manually during setup rather than "first device to sync sets it" — Security Rules don't have Option A's easy "insert if not exists" transactional shortcut, so this becomes a one-time admin step alongside device provisioning.
- `profiles/{profileId}` — `{name, createdAt}`.
- `profiles/{profileId}/favorites/{streamId_contentType}` — same fields as Option A's `favorites` row (`streamName, categoryId, updatedAt, deletedAt`), as a subcollection.
- `profiles/{profileId}/history/{streamId_contentType}` — same fields as Option A's `watch_history` row, `deviceId`/`deviceName` set by Security Rules from `request.auth.uid`, not trusted from the client.
- `presence/{deviceId}` — one doc per device (not per session): `deviceName, profileId, profileName, contentType, streamId, streamName, seriesName, startedAt, lastHeartbeatAt, kickRequested`. Firestore's native per-collection TTL policy on `lastHeartbeatAt` auto-deletes stale docs — no cron needed.

**Auth:** Firebase Auth custom tokens, minted per device via a one-off script using the Admin SDK (service account key never ships on-device) — same out-of-band provisioning spirit as Option A's CLI. Device signs in once with its token and stays signed in; `request.auth.uid` is the device identity every Security Rule checks against.

**Cross-panel guard:** every favorites/history/presence write includes the normalized Xtream URL; a Security Rule compares it against `get(/databases/$(database)/documents/config/xtreamServerUrl)` and rejects the write (permission-denied) on mismatch — same intent as Option A's `409`, expressed declaratively instead of in a handler.

**Presence/kick:** each device attaches a realtime listener to its *own* `presence/{deviceId}` doc for `kickRequested` — near-instant, no polling, no heartbeat-interval latency. The Now Playing panel listens to the whole `presence` collection the same way. Kicking = the Connected Devices screen writes `kickRequested: true` to the target doc; the target's own listener fires immediately and clears it back to `false` after stopping.

**Offline behavior:** effectively free. Firestore's SDK queues writes made offline and replays them on reconnect, and serves reads from local cache — this replaces Option A's entire outbox/`SyncPushWorker` design. Profile-picker offline fallback is likewise mostly automatic (last-synced data is already cached locally by the SDK).

### Option C — Supabase (Postgres + Realtime + Auth)

Closest to Option A conceptually — same table shapes port over almost unchanged, since both are SQL.

**Data model (Postgres):** identical tables to Option A (`devices`, `server_config`, `profiles`, `favorites`, `watch_history` incl. `device_id`/`device_name`, `presence` incl. `series_name`) — same columns, same `(profile_id, stream_id, content_type)` uniqueness. One difference: no native per-row TTL like Firestore, so stale `presence` rows need a scheduled cleanup (`pg_cron` or a small scheduled Edge Function), same manual-cleanup shape as Option A.

**Auth:** one Supabase Auth identity minted per device via the Admin API (service-role key stays server-side, same one-off provisioning step as A/B), custom claim tagging `device_id`.

**API surface:** mostly no hand-written endpoints — Supabase's client SDK talks to Postgres tables directly via its auto-generated REST layer (PostgREST), so `PUT /profiles/{id}/favorites/{streamId}` becomes a client-side `.from('favorites').upsert(...)` call instead of a Go handler. `GET /history?profileId=&deviceId=` is a plain `SELECT ... WHERE` through the same layer — the one place Option A's endpoint had custom logic, this needs none. The cross-panel guard is the one place that needs real server-side logic: a Row-Level-Security `WITH CHECK` policy comparing the write's `xtream_server_url` column against `server_config`, same intent as Option A's check, expressed in SQL instead of a handler.

**Presence/kick:** Supabase Realtime subscribes to Postgres row changes on `presence` — same near-instant delivery as Firebase's listeners. Kick = update `kick_requested = true` on the target device's row; its own subscription fires immediately.

**Offline behavior:** the one place Option C doesn't fully match Firebase's "free" story — Supabase's client SDK doesn't have Firestore-grade automatic offline mutation queuing, so this still needs a thinner version of Option A's outbox (`pending_sync_ops` + a push worker), just pointed at Supabase's REST/RPC layer instead of a custom API.

## Implementation phases

**Note:** phases below are written against Option A's concrete endpoints/tables for specificity. The phase *boundaries and exit criteria* apply to all three options — only Phase 0's content and the specific mechanism behind "outbox" (Phase 2) and "heartbeat"/"kick" (Phases 3-4) differ per option, as described above.

Each phase ships something independently testable and doesn't require the next phase to be useful. Later phases build strictly on earlier ones (presence/kick needs profiles for attribution; sync needs profiles for scoping).

**Phase 0 — Server skeleton & device auth**
`server/` scaffold (Go + SQLite + Dockerfile), `devices` table only, Bearer-token auth middleware, health-check endpoint. Device provisioning via a small CLI/admin command (insert row, print token). No app changes yet.
*Exit criteria:* container builds and runs, curl with a provisioned token hits an authenticated endpoint, a bad/missing token gets 401.

**Phase 1 — Profiles**
Server: `profiles` table, `GET/POST /profiles`. App: `ProfileManager` (active-profile persistence + `StateFlow`), profile picker screen ("Who's watching?", incl. "Add profile"), local cache re-scoping in `XtreamUserDataManager`/`XtreamRepository` (dedicated per-profile prefs file, `favoritesCacheMap` keyed by `Pair(providerId, profileId)`). No remote favorites/history sync yet — this phase is purely about profile *identity* existing end-to-end.
*Exit criteria:* create Alice and Bob from one device, switch between them, confirm each gets an empty-but-isolated local favorites/history view; profile list appears on a second device after it fetches `GET /profiles`.

**Phase 2 — Favorites/history/position sync**
Server: `favorites`/`watch_history` tables (`watch_history` incl. `device_id`/`device_name`, set from the authenticated token) + `PUT/DELETE /profiles/{id}/favorites/{streamId}`, `PUT /profiles/{id}/history/{streamId}`, `GET /profiles/{id}/state`, `GET /history?profileId=&deviceId=`, `server_config` table + the cross-panel URL check on every write. App: `SyncClient` (sends the normalized Xtream server URL alongside every call), push wiring at `XtreamUserDataManager`'s three mutation points, outbox (`pending_sync_ops`) + `SyncPushWorker`, `SyncPullWorker`, basic "Connected Devices" Settings screen (server URL/token entry, sync status, reachability + cross-panel-mismatch indicator). Outbox/retry ships *in* this phase, not deferred — this is the phase where write-loss is possible, so it's the phase that has to close it. `GET /history` is server-API-only — no app UI for it.
*Exit criteria:* favorite a title as Alice on phone, it shows up as Alice on Shield 1 after next pull; kill the server mid-favorite, confirm the app doesn't lose the favorite locally and it lands once the server comes back; point a test device at a different URL, confirm its sync calls get rejected and the app shows the mismatch warning instead of corrupting data; watch something as Alice on Shield 1 then as Bob on the phone, confirm `GET /history?profileId=alice`, `GET /history?deviceId=shield1`, and `GET /history?profileId=alice&deviceId=shield1` each return the expected, distinct subset.

**Phase 3 — Presence visibility ("Now Playing")**
Server: `presence` table (incl. `series_name`), `POST /presence/heartbeat` (no kick logic yet, always returns `{kick: false}`), `POST /presence/stop`, staleness TTL expiry, `GET /presence`. App: optional `seriesName` param added to `MediaProvider.onPlaybackStarted/onPlaybackProgress` (see Playback-code impact note), heartbeat/stop wiring in `XtreamMediaProvider`'s playback lifecycle overrides, read-only "Now Playing" panel in the Connected Devices screen.
*Exit criteria:* play something as Alice on Shield 1, see "Alice — Shield 1 — <title>" on Shield 2's panel within one heartbeat interval; play a TV show episode, confirm the panel shows the series name + episode, not just the bare episode title; stop playback, confirm it disappears (immediately via explicit stop, or within TTL if the app is killed instead).

**Phase 4 — Kick / remote-stop**
Server: `POST /presence/{deviceId}/kick`, `kick_requested` flag flows into the next heartbeat response. App: "Boot" button on each Now Playing row; `MediaRepository`'s new `Flow<KickEvent>` emits on a `{kick: true}` heartbeat response, `StreamLoaderViewModel` collects it → stops playback + shows "disconnected from another device" message (see Playback-code impact note above).
*Exit criteria:* boot Shield 1 from Shield 2's panel while Shield 1 is mid-playback, confirm Shield 1 actually stops within ~20s and shows the message.

**Phase 5 — Offline polish**
Profile-picker offline fallback (cached list / local-only pseudo-profile on a connectivity-less first run), reachability indicator refinement, exercise the outbox and pull-retry paths under real intermittent connectivity (not just server-killed) to shake out edge cases the earlier phases' happy-path testing wouldn't catch.

## Verification
- Server: unit-test the SQLite layer + a scripted curl smoke test (register device row manually, create two profiles, PUT/GET favorites per profile, heartbeat + kick round-trip) before touching the app.
- App: manual end-to-end on real devices (matches this project's convention — TV D-pad/session testing isn't unit-testable):
  - Favorite a title as Alice on phone, confirm it appears on Shield 1 (as Alice) after `SyncPullWorker` runs/app foreground, and does *not* appear under Bob's profile on Shield 2.
  - Switch Shield 1 from Alice to Bob mid-session, confirm the local view swaps to Bob's favorites/history (not a merge of both), and Shield 1 remembers Bob on next launch.
  - Start playback as Alice on Shield 1, confirm Shield 2's "Now Playing" panel shows "Alice — Shield 1 — <title>", and that "Boot" actually stops playback there within ~20s.
- `./gradlew testDebugUnitTest` for any new Kotlin unit tests (`XtreamUserDataManager`/`SyncClient` merge logic).
