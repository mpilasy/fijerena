# Web app: Fijerena in a browser, from anywhere

**Status:** Deferred on purpose, 2026-10-15 — a long-range plan, written so it can be picked up one
day. Decisions W1–W14 made 2026-10-15 in conversation with the user; open questions O1–O8 to settle
before the phase each one blocks. Nothing has been built. Before starting, work through
[Picking this up](#picking-this-up): versions and library support will have moved.

## Goal

Watch and browse everything Fijerena can (Xtream, Jellyfin, Remote M3U, SMB, Local) from a web
browser on any device, anywhere, without installing anything. The server runs on the user's home
Linux box. The Android TV and phone apps stay exactly as they are and coexist with it: favourites,
watch state, settings and Now Playing are shared with them through the existing live sync.

## Non-goals

- Public or multi-tenant hosting: one household, one server.
- Replacing the Android apps, or the TV app's 10-foot UI in a TV browser.
- macOS / iOS native apps. Parked; Phases 0 and 1 of this plan are also their first steps.
- Recording (DVR) and downloads for offline viewing.

## Why a browser alone cannot do it

| Problem | Effect | Answer |
|---|---|---|
| CORS | Xtream panels, M3U hosts and most XMLTV URLs send no CORS headers; `fetch` from a page fails | Every source call goes through our server |
| Mixed content | Streams are mostly `http://`; an `https://` page may not load them | Streams are served by our server, from the page's own HTTPS origin |
| Codecs and containers | Browsers lack AC3 / E-AC3 / DTS audio and MKV, and HEVC is patchy; the Android app relies on the FFmpeg decoder for these | A stream gateway that passes through, remuxes or transcodes per stream (Phase 5) |
| Data size | Catalogues, the FTS4 indexes and the EPG are too big and too slow for IndexedDB | The Room databases live on the server, as on a device today |
| Privacy | A browser fetching streams or logos itself shows the provider wherever the user is | All outbound traffic comes from the server (W9) |

## Decisions (2026-10-15)

| # | Question | Decision |
|---|---|---|
| W1 | Where it runs | The user's home Linux box, which already runs everything as Docker containers. Not a VPS or Cloudflare: providers often block datacenter IPs, Workers cannot proxy long streams, transcoding needs real CPU. |
| W2 | How it is reached | The existing Nginx Proxy Manager (NPM) in front: a proxy host with a Let's Encrypt certificate, Force SSL and the streaming settings under [Deployment](#deployment). No VPN needed. |
| W3 | Authentication | Built into the server; the box runs neither Authelia nor Authentik, and an NPM Access List is not enough. See [Authentication](#authentication). |
| W4 | Architecture | Thin client: the server does all source, database, EPG, sync and stream work; the browser renders and plays. |
| W5 | Server stack | Kotlin + Ktor on the JVM, reusing the existing `core` modules by making them Kotlin Multiplatform with an `android` and a `jvm` target. |
| W6 | Frontend | SvelteKit (Svelte 5) as a static SPA (`adapter-static`, `ssr = false`), served by the Ktor server: one image, one process, no Node at runtime. Chosen over Angular (thin client; runes map onto Compose state; smaller bundles) and Compose for Web (canvas rendering fights `<video>`). |
| W7 | Players in the browser | `hls.js` for HLS, `mpegts.js` for MPEG-TS passthrough, native `<video>` for progressive MP4. The server picks the mode per stream. |
| W8 | Android apps | Unchanged in behaviour. Every refactor of `core` keeps every unit and instrumented test and every `scripts/check-*.sh` green; nothing ships to Android users from this plan. |
| W9 | What providers see | Only the home IP. Every API call, EPG download, TMDB call, stream **and image** (logos and posters are often on the provider's panel) is fetched by the server; the browser only ever talks to our own domain. The Android apps keep connecting directly, as today. |
| W10 | Coexistence with the Android apps | The web server joins the user's live sync account as one more device (pairing like a TV: it shows a QR code that the phone scans). Favourites, watch state, settings and Now Playing are shared; a stream started in the browser resumes on the TV. |
| W11 | Code reuse | One copy of the core code. Files move between source sets (`git mv`, history kept) and Android calls go behind small interfaces; nothing in `core:network` is rewritten. `core:ui`'s non-UI logic is extracted into a shared module used by both the Android ViewModels and the server, never written twice. |
| W12 | Shared JVM code first | Code moves to a `jvmAndAndroid` intermediate source set, not `commonMain`: there `java.time`, `java.io`, `javax.crypto`, OkHttp, smbj and Room's blocking DAOs all keep working, so the move is mechanical. Moving to `commonMain` (needed only for iOS) is out of scope. |
| W13 | Traffic | Every remote stream crosses the home connection twice (provider → box, box → browser); the home **upload** is the limit (passthrough HD ≈ 5–10 Mbit/s per viewer). A lower-bitrate transcode profile is offered for remote and mobile viewing. At home, traffic stays on the LAN (split DNS if the router does not hairpin). |
| W14 | Licence | The web server is AGPL-3.0-or-later like the sync server (a network service); the SPA is GPL-3.0-or-later like the apps. Every new dependency must be GPLv3-compatible free software (AGENTS.md → License). |

## Open questions

| # | Question | Proposed | Needed by |
|---|---|---|---|
| O1 | One login for the household, or one login per Fijerena profile? | One admin login, then the profile picker (as on a device); per-profile logins later if wanted | Phase 3 |
| O2 | Does the box have an Intel/AMD iGPU to pass through as `/dev/dri` (VAAPI / QSV)? | Check `ls /dev/dri` and `vainfo` on the box; without it, CPU transcoding capped at one at a time | Phase 5 |
| O3 | Repository layout | `webserver/` (Ktor, a Gradle module in this build) and `web/` (SvelteKit, npm), next to `server/` (sync) | Phase 3 |
| O4 | Domain | A subdomain on the user's existing NPM domain, e.g. `tv.<domain>` | Phase 3 |
| O5 | How the web server's settings relate to a device's | It is its own device: device-scoped settings (player, look) are its own, account-scoped ones arrive by sync as on any device | Phase 4 |
| O6 | Remote bitrate profiles | Original / 8 Mbit/s 1080p / 4 Mbit/s 720p / 1.5 Mbit/s 480p, picked in the player, default by connection type | Phase 5 |
| O7 | Languages | en, fr, mg like the apps, sharing the translators' wording; string files generated from the Android `strings.xml` | Phase 6 |
| O8 | Admin-only screens (Diagnostics, Device info, Safe mode) | Diagnostics and a server info page yes; Safe mode becomes a server start flag | Phase 6 |

## Architecture

```
Browser (phone / laptop / anywhere)
   │  HTTPS (+ WebSocket)
   ▼
Nginx Proxy Manager  (TLS, Let's Encrypt)
   │  docker network "npm"
   ▼
fijerena-web  (one container)
   ├─ SvelteKit SPA (static files)
   ├─ REST/JSON API + WebSocket      ← Ktor; core modules on the JVM
   ├─ Stream gateway + image proxy   ← proxy / ffmpeg child processes; disk image cache
   ├─ Schedulers                     ← what WorkManager does on Android
   └─ Sync client                    ← existing live sync protocol, as one more device
        │
        ├──► Xtream / Jellyfin / M3U / XMLTV / TMDB (internet, from the home IP)
        ├──► SMB shares, local disks (LAN, mounts)
        └──► fijerena-sync (existing container or Cloudflare)
```

### Gradle modules after this plan

```
core/player      KMP: androidMain (Media3, service), jvmAndAndroidMain (domain, model, XtreamApiService, …)
core/network     KMP: androidMain (Context-backed actuals), jvmAndAndroidMain (almost everything), jvmMain (server actuals)
core/domain      NEW, KMP: the non-UI logic extracted from core:ui (MergedLists, ProfileSections, …)
core/ui          Android library as today, its ViewModels calling core:domain
core/navigation, core/data, mobile, tv   unchanged
webserver        NEW, JVM: Ktor app, gateway, schedulers, auth; depends on core:network, core:player, core:domain
web/             NEW, SvelteKit (outside Gradle; built in the Docker image)
```

`core:player` still does not depend on `core:network` (Constraint 1); `core:domain` may depend on
both, as `core:ui` does today.

### What happens to each part of the code

| Code | What happens | Kind of change |
|---|---|---|
| `core:network` (≈29k LOC) | Moves to `jvmAndAndroidMain`; Android calls replaced by interfaces | Updated, mechanical |
| `core:player` domain, models, `XtreamApiService`, `NetworkMonitor` | Move to `jvmAndAndroidMain` | Updated (moved) |
| `core:player` Media3, `StreamingPlaybackService`, FFmpeg decoder | Stay in `androidMain`; the browser plays with hls.js / mpegts.js | Untouched |
| `core:ui` logic: `history/MergedLists`, `home/ProfileSections`, `SearchTabs`, `SearchSourceFilter`, `RecentChannels`, rules inside the ≈8.5k LOC of ViewModels | Extracted to `core:domain`; ViewModels get thinner | Extracted (Phase 2) |
| Android actuals of the new interfaces | Thin wrappers over today's calls | New, small |
| JVM actuals | File-backed settings, SLF4J logger, coroutine scheduler, Room bundled driver, kxml2 | New, small |
| `mobile/`, `tv/` | — | Untouched |
| `webserver/`, `web/` | Display and wiring; the rules live in shared code | New |

## Network and privacy

- **Outbound:** only the server talks to providers, Jellyfin, M3U and XMLTV hosts, TMDB and logo
  hosts. The browser gets our URLs only (W9). Provider URLs and credentials never reach it.
- **Inbound:** only NPM's ports 80/443 are open on the router; `fijerena-web` publishes no host port.
- **Bytes:** a remote stream comes down from the provider and goes up to the browser (W13).
  Passthrough keeps the box's CPU idle but uses the full bitrate upstream; a transcode profile
  trades CPU for upload. The server shows live throughput per session on its info page.
- **Connection limits:** a browser stream is one more connection on the Xtream account, from the
  same home IP as the TV at home; it goes through `XtreamLoginPicker.forPlayback`, so it takes a
  free login when there are several.

## Authentication

The server holds provider credentials and faces the internet, so it is closed by default.

- **First run:** no account exists; the server prints a one-time setup token to its log
  (`docker logs fijerena-web`), like the sync server's `SETUP_SECRET`. The setup page asks for it
  and creates the admin login. Until then every other route returns 503.
- **Login:** username + password (Argon2id, parameters from OWASP at the time), TOTP second factor
  on by default (QR enrolment, recovery codes). Passkeys (WebAuthn) as a later option.
- **Sessions:** `__Host-` cookie, `HttpOnly; Secure; SameSite=Strict`, server-side session table
  (idle 7 days, absolute 30 days), list of sessions with "sign out" and "sign out everywhere".
  Every state-changing request checks `Origin`; a CSRF token is not needed with SameSite=Strict but
  `Origin` checks stay as defence in depth.
- **Stream and image URLs:** `<video>`, hls.js segment fetches and `<img>` cannot send headers
  reliably, so each such URL carries a short-lived HMAC token bound to the session and the
  resource (`/stream/{session}/…?t=…`, `/img/{hash}?t=…`); the gateway rejects anything else.
  Tokens die with the session.
- **Brute force:** per-IP and per-account login rate limits with backoff; failures logged in a fixed
  one-line format (fail2ban-friendly). The client IP comes from `X-Forwarded-For` only when the
  request arrives from NPM's container address; otherwise the socket address.
- **Secrets at rest:** provider credentials, extra logins, the TMDB key and sync keys are encrypted
  (AES-GCM) with a key read from a Docker secret file (`FIJERENA_SECRET_KEY_FILE`). The Android rule
  carries over: secrets never fall back to plaintext (Constraint 10); without a key file the server
  refuses to start.
- **Headers:** strict CSP (self only, no inline script; `media-src` self and `blob:`), HSTS (also
  set in NPM), `X-Content-Type-Options`, `Referrer-Policy: no-referrer`.

## Stream gateway

### Play request

`POST /api/play` with the item (source, id, kind, catch-up window, resume position) and what the
client can play (`MediaSource.isTypeSupported` for a list of codec strings,
`navigator.mediaCapabilities` for HEVC / 10-bit, screen size, connection type). The server resolves
the stream exactly as Android does (`resolvePlayableStream`, shared logins, catch-up URLs), probes it
(`ffprobe`, cached per stream URL for a day) and answers with the mode, a signed URL, audio and
subtitle tracks, and the resume position.

### Mode decision

| Source | The client can play | Mode | ffmpeg |
|---|---|---|---|
| HLS (incl. Xtream catch-up) | video and audio | Proxy: playlists rewritten to our signed URLs, segments passed through | none |
| MPEG-TS live | video and audio | Passthrough TS to `mpegts.js` (lowest latency) | none |
| Progressive MP4 VOD | video and audio | Byte-range proxy to native `<video>` | none |
| Any, MKV / AVI / TS VOD | video and audio | Remux to fMP4 HLS | `-c copy` |
| Any | video yes, audio no (AC3, E-AC3, DTS, MP2, …) | Audio transcode to fMP4 HLS | `-c:v copy -c:a aac` |
| Any | video no (HEVC on Firefox, 10-bit, …), or a bitrate profile chosen | Transcode to H.264 + AAC fMP4 HLS | VAAPI/QSV when `/dev/dri` is there (O2), else `libx264 -preset veryfast` |

Subtitles: text tracks (SRT, ASS, mov_text) extracted to WebVTT on demand; image subtitles (PGS,
DVB) burned in only when picked, which forces a transcode.

### Sessions

- One gateway session per playback: its ffmpeg process (if any), its tmpfs directory, its signed
  token, its Xtream login.
- Ends on Stop, when the client stops fetching for 60 s, on sign-out, or on a remote Stop from
  another device over sync. The process is killed, the directory deleted, the login released.
- Seeking within remux / transcode restarts ffmpeg at the new position (`-ss` before `-i`) when the
  target is outside what is already produced.
- Caps: total transcodes (1 on CPU, configurable with an iGPU) and total sessions; a refused
  session says why in the player.
- Supervision: ffmpeg's stderr goes to the session log; a crash ends the session with a
  `FriendlyError`-style message, never a silent stall (same 60 s stall limit as Android).
- Now Playing: sessions are published over live sync, so the TV and phone show what the browser is
  playing and can stop it.

### Image proxy

`/img/{hash}` fetches the original once, stores it in a disk cache (LRU, size-capped, under
`/data/cache/img`), resizes to the requested width (thumbnail / card / backdrop), and serves it with
long cache headers. Sources: channel logos, posters, backdrops, TMDB images, Jellyfin images.

## API surface (sketch)

Mirrors the `Screen` routes in `core:navigation`. All JSON, all under `/api`, all behind the session.
The OpenAPI spec is generated from the Ktor routes and the TypeScript client from it.

| Area | Endpoints |
|---|---|
| Auth | `POST setup`, `POST login`, `POST login/totp`, `POST logout`, `GET/DELETE sessions` |
| Profiles | `GET profiles`, `POST profiles/{id}/select`, profile edit (filters, developer mode, play next, source access) |
| Sources | CRUD (Add / Edit Source, Copy to, Duplicate), extra logins, `POST sources/{id}/sync`, sync status |
| Browse | `GET sections/{live\|movies\|series}/categories`, `GET categories/{source}/{id}/items` (paged), Recent Categories |
| Details | `GET items/{source}/{id}`, `GET series/{source}/{id}/episodes`, related (TMDB), mark watched (episode / season / show) |
| History | Recent, Continue Watching, favourites (add / remove / undo), watch state |
| Search | `GET search?q=&tab=catalogue\|guide&source=` |
| Guide | `GET guide?from=&to=&category=` (grid window), `GET guide/search`, guide sources (EPG management) |
| Playback | `POST play`, `POST play/{session}/progress`, `DELETE play/{session}` |
| Sync | pairing (handoff QR), status, devices, Now Playing |
| Settings | app settings, TMDB key, export / import |
| Server | info (version, disk, sessions, throughput), diagnostics log |
| Events | `GET /ws`: catalogue sync progress, EPG refresh, Now Playing, remote Stop, settings changed |

## Deployment

```yaml
services:
  fijerena-web:
    image: fijerena-web:<version>
    restart: unless-stopped
    networks: [npm]
    volumes:
      - ./data:/data                 # Room/SQLite DBs, EPG index, settings, sessions, image cache
      - /srv/media:/media:ro         # optional: Local source
    tmpfs:
      - /transcode:size=2g           # HLS segments
    devices:
      - /dev/dri:/dev/dri            # optional (O2)
    secrets: [fijerena_key]
    environment:
      FIJERENA_SECRET_KEY_FILE: /run/secrets/fijerena_key
      TZ: <the box's zone>           # guide times are shown in the browser's zone
secrets:
  fijerena_key: { file: ./fijerena.key }   # openssl rand -base64 32 > fijerena.key; chmod 600
networks:
  npm: { external: true }
```

The image: a JRE (Temurin, slim), ffmpeg (a GPL build with libx264 and VAAPI), the server jar and the
built SPA. Runs as a non-root user; `/data` owned by it.

NPM proxy host → `fijerena-web:8080`, Let's Encrypt, Force SSL, HTTP/2, HSTS, **WebSockets Support
on**, and under Advanced:

```nginx
proxy_buffering off;
proxy_request_buffering off;
proxy_read_timeout 3600s;
proxy_send_timeout 3600s;
client_max_body_size 50m;
```

No NPM Access List (W3). Backups: `/data` with the box's usual backup, plus the key file kept
somewhere else (without it the stored credentials are unreadable, by design).

## Phases

Estimates are for one developer working on it steadily; all of them are rough.

### Phase 0 — `core` becomes multiplatform, Android only (2–4 weeks)

The Android apps must behave identically after every step. One interface per commit series; the full
unit suite, the instrumented suite (nightly) and every `scripts/check-*.sh` green after each.

1. **Build plumbing.** Switch `core:player` and `core:network` to the Kotlin Multiplatform plugin
   with only an Android target (with AGP 9 that is the KMP Android library plugin, check the name
   and its Room / KSP support at the time). Source sets: `commonMain` (empty at first),
   `jvmAndAndroidMain` (depends on common), `androidMain` (depends on it). Room's KSP runs per target
   (`kspAndroid`). Schema export location unchanged; the migration tests still find it.
2. **`Logger`** (46 `android.util.Log` imports): `expect`/interface with an Android actual on `Log`.
   Mechanical replace.
3. **`KeyValueStore`** (`SharedPreferences` in ≈30 files: `AppSettings`, `AccountManager`,
   `ProviderRepository`, `ProfileRepository`, `CategoryFiltersStore`, `SyncAccountStore`,
   `SettingsSyncQueue`, `SyncApplier`, `MediaRepository`, `XtreamRepository`, the Xtream managers,
   the EPG classes, `LaunchCounter`, …): an interface with get/put/remove/observe and an edit block;
   the Android actual wraps `SharedPreferences` with the same file names and keys, so nothing on a
   device moves.
4. **`SecretStore`** (`TmdbKeyStore`, `AccountManager`, `SyncAccountStore`, extra logins,
   `CredentialStoreHealth`): an interface over today's encrypted prefs. Coordinate with the
   [secret store plan](./20260828_secret-store-migration-plan.md): whichever lands first defines
   the interface, the other implements it. Constraint 10 holds on both targets.
5. **`AppFiles`**: files, cache and database directories (`Context.filesDir`, `cacheDir`,
   `getDatabasePath`) for `EpgFileManager`, `EpgIndexer`, the database builders, export / import.
6. **`BackgroundScheduler`** (`XtreamSyncWorker`, `ProviderSyncManager`, `ProviderSyncRunner`,
   `EpgSyncWorker`, `EpgFtsRebuildWorker`, `EpgRefreshSchedule`, `TrySetForeground`): the work
   bodies move out of the `CoroutineWorker` classes into plain suspend functions in
   `jvmAndAndroidMain`; the workers stay in `androidMain` and call them. Constraints (unmetered,
   charging, foreground) become a small options type.
7. **`Connectivity`** (`NetworkMonitor`, `EpgSyncWorker`): online / metered as a `StateFlow`.
8. **Database builders**: the three `@Database` classes (`SettingsDatabase`, `XtreamDatabase`,
   `EpgIndexDatabase`) stay where they are; their `Room.databaseBuilder(context, …)` calls move to
   `androidMain` factories. The requery `SupportSQLiteDatabase` uses (migrations, FTS SQL, ≈59
   places) are listed: on the JVM they run against the bundled driver's connection, so they are
   written against Room's `SQLiteConnection` / `execSQL` API where the Android-only type is used.
9. **HTTP client factory**: the three `HttpClient(OkHttp)` sites (`XtreamApiService`,
   `JellyfinApiService`, `TmdbApiService`) keep OkHttp (it runs on the JVM); only the Android
   bits in their setup (user agent from `Build`, cache dir) come through the interfaces above.
10. **XML**: `XmltvParser` and `EpgIndexer` use `org.xmlpull` (built into Android): add kxml2 as the
    parser on the JVM through a tiny factory; the parsing code itself stays.
11. **Move**: with the Android calls behind interfaces, move the code to `jvmAndAndroidMain`
    (`git mv`, one package at a time). What still needs Android stays in `androidMain` with a note why.

**Done when:** both apps pass every test and check, a TV and a phone build behave the same on the
emulators (a short smoke walk: add source, browse, play live and VOD, guide, sync), and
`androidMain` in `core:network` holds only the actuals and the workers.

### Phase 1 — JVM target for the core (1–2 weeks)

1. Add the `jvm()` target to `core:player` and `core:network`.
2. JVM actuals: `KeyValueStore` on a JSON file per store with atomic writes; `SecretStore` encrypted
   with the server key; SLF4J `Logger`; `BackgroundScheduler` on coroutines with persisted next-run
   times; `Connectivity` always online; `AppFiles` under `/data`; Room with `BundledSQLiteDriver`
   (FTS4 available); kxml2.
3. Run the existing JVM unit tests (≈108 in `core:network`, ≈29 in `core:player`) on the `jvm`
   target as well; the 7 Robolectric ones stay Android-only.
4. A JVM integration test that logs into the test panel (`tools/` bridge), syncs a catalogue into
   Room, builds the EPG index and runs a guide search.

**Done when:** that integration test passes in CI on a plain JVM.

### Phase 2 — Shared logic out of `core:ui` (1–2 weeks)

1. Create `core:domain` (KMP, `jvmAndAndroidMain`).
2. Move the non-UI logic: `history/MergedLists`, `home/ProfileSections`, `SearchTabs`,
   `SearchSourceFilter`, `RecentChannels`, and the rules inside ViewModels (merging, filtering,
   ordering, next-episode choice, resume rules, catch-up availability use) as plain classes and
   functions.
3. Android ViewModels call them; their tests split into domain tests (in `core:domain`, run on both
   targets) and thin ViewModel tests.

**Done when:** no rule exists in both a ViewModel and `core:domain`; both apps unchanged; tests green.

### Phase 3 — Server skeleton (2–3 weeks)

1. `webserver/` Gradle module (O3): Ktor (Netty), kotlinx.serialization, the
   `AppContainer`-equivalent wiring for the server, `AppScopes` for every long-lived scope
   (Constraint 6), structured logs, `/health`.
2. Authentication as above (setup token, Argon2id, TOTP, sessions, rate limits, signed URLs,
   security headers).
3. Sources CRUD and profiles (O1), settings, export / import.
4. `web/` SvelteKit skeleton: setup, login, TOTP enrolment, profile picker, sources list and Add
   Source. Design tokens generated from the Kotlin token files (`CinemaSpacing`, `CinemaColors`,
   `CinemaCornerRadius`, `CinemaAnimation`, …) into CSS custom properties at build time; the
   "no hardcoded UI values" rule becomes "only `var(--…)` in styles", checked by a stylelint rule.
   Typed API client generated from the OpenAPI spec.
5. Dockerfile (multi-stage: Gradle build, npm build, runtime with JRE + ffmpeg), non-root user,
   compose file and an NPM section in the README (O4).

**Done when:** on the box, behind NPM, the user can set up, log in with TOTP, and add and list
sources from a phone browser on mobile data.

### Phase 4 — Catalogue API, schedulers and sync (3–4 weeks)

1. Endpoints for profiles, browse (merged sections, categories, paged items), details, episodes,
   Search (Catalogue and Guide tabs, source filter), Recent, Continue Watching, favourites, watch
   state and marking watched, TMDB, the guide grid and guide search, guide sources.
2. Schedulers for catalogue sync, EPG refresh and FTS rebuild through `BackgroundScheduler`,
   with progress on `/ws`. Same rules as Android: no destructive self-healing (Constraint 12),
   cancellation rethrown (Constraint 7), `suspendRunCatching`.
3. Live sync: pairing as a device without a camera (the browser shows the handoff QR, the phone
   scans it), then the existing `SyncEngine` / `SyncApplier` on the JVM. Device-scoped vs
   account-scoped settings per O5.
4. Server info page: version, disk use per database, sync status, schedulers.

**Done when:** the API serves everything the phone app's screens show, for the user's real
sources, and a favourite or watched mark made on the phone shows up through the API within seconds
(and back).

### Phase 5 — Stream gateway and image proxy (3–5 weeks)

1. `POST /api/play`: resolution through the shared code (shared logins, catch-up), `ffprobe` with a
   cache, the decision table, signed session URLs.
2. Modes: HLS proxy with playlist rewriting; TS passthrough; MP4 byte-range proxy; ffmpeg remux,
   audio transcode and full transcode (VAAPI / QSV / x264 per O2) to fMP4 HLS in `/transcode`.
3. Sessions: lifecycle, idle timeout, caps, seeking restarts, supervision, cleanup on start (a
   crash must not leave old directories), Now Playing and remote Stop over sync.
4. Subtitles: WebVTT extraction; burn-in for image subtitles.
5. Bitrate profiles (O6).
6. Image proxy with disk cache and resizing.

**Done when:** a live TS channel, an HLS channel, a catch-up programme, an MP4 film, an MKV film
with AC3 audio and an HEVC episode all play, seek and resume in Chrome and Firefox on Linux and in
Chrome on Android, at home and on mobile data; and the providers' logs (or a test panel's) show
only the home IP for streams and images.

### Phase 6 — Svelte frontend (4–6 weeks)

1. Shell: responsive layout from phone width to desktop; bottom tabs on narrow screens (like the
   phone app), a side rail on wide ones; keyboard shortcuts on desktop.
2. Browse: Live TV, Movies, TV Shows with merged categories and source badges; Recent Categories.
3. Details: film and show pages, seasons and episodes, mark watched, favourites, related.
4. Player: hls.js / mpegts.js / native per mode, controls, audio and subtitle tracks, seek, resume,
   next episode and Up next, live zapping, Start over and catch-up, bitrate profile, picture in
   picture, Media Session API (lock-screen controls on Android), fullscreen and orientation.
5. Search (both tabs, source filter), the TV Guide grid, guide search.
6. Settings: profiles, sources, guide sources, sync, TMDB key, look, server info, diagnostics (O8).
7. Languages (O7) and the wording rules from AGENTS.md (sources, guide sources).

**Done when:** every phone-app flow has a web equivalent, checked on desktop Chrome / Firefox and
phone Chrome, in both themes and all three languages.

### Phase 7 — Hardening and release (2–3 weeks)

1. Playwright end-to-end tests against the test panel in CI (login, browse, play each mode,
   favourite, sync round-trip with a fake second device).
2. Database upgrades on the server: the same Room migrations as Android (Constraint 9); a server
   started on a newer `/data` refuses to open it, like `ProvidersDbGuard`.
3. Backup and restore of `/data` documented and tested; key-file loss documented.
4. CI builds and publishes the image on tags, next to the APKs (`release.yml`); image signed; SBOM.
5. Security review of auth, signed URLs and the gateway (path traversal in the image proxy and the
   segment routes, SSRF limits on what the server will fetch).
6. `docs/WEB_APP.md` reference page (deploy, NPM, auth, gateway, bandwidth); README and
   FEATURES.md updated; release notes.

## Progress

| Phase | Status |
|---|---|
| 0 — `core` multiplatform, Android only | Not started |
| 1 — JVM target for the core | Not started |
| 2 — Shared logic out of `core:ui` | Not started |
| 3 — Server skeleton | Not started |
| 4 — Catalogue API, schedulers and sync | Not started |
| 5 — Stream gateway and image proxy | Not started |
| 6 — Svelte frontend | Not started |
| 7 — Hardening and release | Not started |

Total: roughly 4–7 months for one developer. Phases 0–2 are worth doing on their own (cleaner
seams, and the first steps of any desktop or iOS port); Phase 3 onwards only make sense together.

## Risks

| Risk | Effect | Mitigation |
|---|---|---|
| Phase 0 churn regresses the Android apps | Bugs in shipped apps | Small commits, one interface at a time; full tests and emulator smoke walk; W8 is a hard gate |
| KMP + AGP + Room + KSP support at pickup time | Build plumbing harder than planned | Spike step 0.1 alone first, on a branch; check current docs |
| Room raw SQL on `SupportSQLiteDatabase` (≈59 places) | Migrations / FTS code needs adapting for the bundled driver | Inventory first; adapt behind a small helper; migration tests on both targets |
| Home upload bandwidth | Stutter when away from home | Bitrate profiles; throughput shown per session; passthrough only when it fits |
| CPU-only transcoding | One transcode at a time | iGPU passthrough (O2); prefer remux / audio-only transcode, which are cheap |
| Provider connection limits | Web stream refused while the TV plays | Shared logins (`XtreamLoginPicker`); a clear message in the player; Now Playing shows who holds it |
| Public exposure | Credential theft, provider ban | Built-in auth with TOTP, signed URLs, rate limits, strict headers, security review (Phase 7) |
| Browser codec support changes | Decision table out of date | Client reports capabilities per play; table kept in one place with tests |
| Scope creep | Never finishes | Phases 0–2 stand alone; frontend parity is phone-app parity, not more |

## Picking this up

1. Re-read this plan and settle the open questions it blocks first (O1, O3, O4).
2. Check the stack still holds: Kotlin Multiplatform with the current AGP (plugin name, Room KMP,
   KSP per target), Ktor server, SvelteKit major version, hls.js / mpegts.js maintenance, browser
   support for HEVC and AC3. Update W5–W7 if something better exists.
3. Refresh the inventory that sizes Phase 0 (numbers above are from 2026-10-15):
   ```sh
   grep -rl "SharedPreferences" core --include=*.kt | grep /main/          # KeyValueStore sites
   grep -rhoE "^import android\.[A-Za-z.]+" core/*/src/main | sort | uniq -c | sort -rn
   grep -rl "CoroutineWorker\|WorkManager" core --include=*.kt | grep main  # scheduler sites
   grep -rn "SupportSQLiteDatabase\|requery" core --include=*.kt | grep main | wc -l
   grep -rn "HttpClient(" core --include=*.kt | grep main
   ```
4. Check the [secret store plan](./20260828_secret-store-migration-plan.md) status (Phase 0 step 4).
5. Check the box: Docker and NPM versions, `/dev/dri` (O2), upload speed (W13).
6. Mark this plan In progress, then start Phase 0 step 1 as a spike.
