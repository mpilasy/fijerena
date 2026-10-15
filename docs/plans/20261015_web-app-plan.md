# Web app: Fijerena in a browser, from anywhere

**Status:** Proposed, 2026-10-15. Decisions W1–W8 made 2026-10-15; open questions O1–O5 to settle
before the phase they block. Nothing has been built.

## Goal

Watch and browse everything Fijerena can (Xtream, Jellyfin, Remote M3U, SMB, Local) from a web
browser on any device, anywhere, without installing anything. The Android TV and phone apps stay as
they are and keep working; watch state, favourites and settings are shared with them through live
sync.

## Why a browser alone cannot do it

| Problem | Effect | Answer |
|---|---|---|
| CORS | Xtream panels, M3U hosts and most XMLTV URLs send no CORS headers; `fetch` from a page fails | Every source call goes through our server |
| Mixed content | Streams are mostly `http://`; an `https://` page may not load them | Streams are served by our server, over the same HTTPS origin |
| Codecs and containers | Browsers lack AC3 / E-AC3 / DTS audio, MKV, often HEVC; the Android app relies on the FFmpeg decoder for these | A stream gateway that passes through, remuxes or transcodes per stream (Phase 4) |
| Data size | Catalogues, the FTS4 indexes and the EPG are too big and too slow for IndexedDB | Room databases live on the server, as on a device today |

## Decisions (2026-10-15)

| # | Question | Decision |
|---|---|---|
| W1 | Where it runs | The user's home Linux box, as a Docker container like everything else on it. Not a VPS or Cloudflare: providers often block datacenter IPs, Workers cannot proxy long streams, transcoding needs real CPU. |
| W2 | How it is reached | The existing Nginx Proxy Manager (NPM) in front: a proxy host with a Let's Encrypt certificate, Force SSL, and the streaming settings under Deployment. No VPN required. |
| W3 | Authentication | Built into the server (no Authelia / Authentik on the box). See Authentication. |
| W4 | Architecture | Thin client: the server does all source, database, EPG, sync and stream work; the browser renders and plays. |
| W5 | Server stack | Kotlin + Ktor on the JVM, reusing `core:network` (and `core:player`'s domain models) by turning them into Kotlin Multiplatform modules with `androidMain` and `jvmMain`. |
| W6 | Frontend | SvelteKit (Svelte 5) as a static SPA (`adapter-static`, `ssr = false`), served by the Ktor server: one image, one process, no Node at runtime. Picked over Angular (thin client, runes map onto Compose state, smaller bundles) and over Compose for Web (canvas rendering fights `<video>`). |
| W7 | Players in the browser | `hls.js` for HLS, `mpegts.js` for MPEG-TS passthrough; native `<video>` for progressive MP4. The server picks the mode per stream. |
| W8 | Android apps | Unchanged in behaviour. Phase 0's refactor must leave every existing test and CI check green; nothing ships to Android users from this plan. |

## Open questions

| # | Question | Needed by |
|---|---|---|
| O1 | One login for the household, or one login per Fijerena profile (a profile picker after a single login is the cheaper default)? | Phase 2 |
| O2 | Does the box have an Intel/AMD iGPU (VAAPI / QSV) to pass through as `/dev/dri`, or is video transcoding CPU-only (then capped at one at a time)? | Phase 4 |
| O3 | Repository layout: `web/` (SvelteKit) and `webserver/` (Ktor) in this repo, next to `server/` (sync)? Proposed yes. | Phase 2 |
| O4 | Licence for the web server: AGPL-3.0-or-later like the sync server (it is a network service) — proposed yes. | Phase 2 |
| O5 | Does the web server join the existing sync account as a device (shared watch state with TV and phone), or stay standalone at first? Proposed: join, in Phase 3. | Phase 3 |

## Architecture

```
Browser (phone / laptop / anywhere)
   │  HTTPS
   ▼
Nginx Proxy Manager  (TLS, Let's Encrypt)
   │  docker network "npm"
   ▼
fijerena-web  (one container)
   ├─ SvelteKit SPA (static files)
   ├─ REST/JSON API + WebSocket   ← Ktor; core:network on the JVM
   ├─ Stream gateway              ← proxy / ffmpeg child processes
   ├─ Schedulers                  ← what WorkManager does on Android (catalogue sync, EPG refresh, FTS rebuild)
   └─ Sync client                 ← existing live sync protocol, as one more device
        │
        ├──► Xtream / Jellyfin / M3U / XMLTV (internet)
        ├──► SMB shares, local disks (LAN, mounts)
        └──► fijerena-sync (existing container, docker network)
```

### What carries over and what does not

| Module | On the server |
|---|---|
| `core:network` | Reused. Android bits go behind interfaces (Phase 0): `Context` (90 imports), `SharedPreferences` (30 files), `Log` (46), WorkManager, `ConnectivityManager`, encrypted prefs. `java.*`, OkHttp, smbj, Room's blocking DAOs (with the bundled SQLite driver) and the XML parser (kxml2) all work on the JVM. |
| `core:player` | Domain models only (`domain/`, `model/`). Media3, `StreamingPlaybackService` and the FFmpeg decoder stay Android. |
| `core:ui` | ViewModels are not reused as code; their logic moves to server services or is ported to Svelte stores. Design tokens are exported as CSS custom properties. |
| `core:navigation`, `mobile`, `tv` | Not used. The SPA's routes mirror `Screen`. |
| `server/` (sync) | Unchanged; the web server is a client of it. |

## Authentication

The server holds provider credentials and is on the public internet, so it is closed by default.

- **First run:** no account exists; the server prints a one-time setup token to its log
  (`docker logs fijerena-web`), the same idea as the sync server's `SETUP_SECRET`. The setup page
  asks for it and creates the admin login.
- **Login:** username + password (Argon2id), TOTP as a second factor (on by default, skippable only
  with an explicit setting). Passkeys (WebAuthn) later, as an option.
- **Sessions:** `HttpOnly; Secure; SameSite=Strict` cookie, server-side session table, idle and
  absolute expiry, "sign out everywhere". State-changing requests check `Origin`.
- **Stream URLs:** `<video>` and the player libraries cannot send headers, so every stream and
  segment URL carries a short-lived HMAC token bound to the session and the stream; the gateway
  rejects anything else. Provider URLs and credentials never reach the browser.
- **Images too:** channel logos, posters and backdrops (often hosted on the provider's panel) go
  through a server image proxy with a disk cache, never loaded by the browser from their origin.
  So the providers, TMDB and logo hosts only ever see the home IP, never the IP of wherever the
  browser is.
- **Brute force:** per-IP and per-account login rate limits; failures logged with the client IP in
  a fixed format (fail2ban-friendly). The client IP comes from `X-Forwarded-For` only when the
  request arrives from NPM's container network, never trusted otherwise.
- **Secrets at rest:** provider credentials, the TMDB key and sync keys are encrypted with a key
  from a Docker secret / file (`FIJERENA_SECRET_KEY_FILE`). The Android rule carries over: secrets
  never fall back to plaintext (Constraint 10).

## Stream gateway

The client reports what it can play (`MediaSource.isTypeSupported`, `navigator.mediaCapabilities`)
when it asks to play; the server probes the stream (`ffprobe`, cached per stream) and picks:

| Source | Codecs the client can play | Mode |
|---|---|---|
| HLS (incl. Xtream catch-up) | all | Proxy: playlists rewritten to our URLs, segments passed through |
| MPEG-TS live | all | Passthrough TS to `mpegts.js` (lowest latency, no ffmpeg) |
| Progressive MP4 VOD | all | Byte-range proxy to native `<video>` |
| Any, MKV container | video and audio | Remux: `ffmpeg -c copy` to fMP4 HLS |
| Any | video yes, audio no (AC3, E-AC3, DTS, …) | `-c:v copy -c:a aac` to fMP4 HLS |
| Any | video no (HEVC on Firefox, 10-bit, …) | Transcode to H.264 + AAC; VAAPI/QSV when `/dev/dri` is there (O2), else `libx264 -preset veryfast`, capped concurrency |

- **Sessions:** each playback is a gateway session; ffmpeg processes are killed when the client
  stops fetching (idle timeout) or on Stop, segments live in a tmpfs and are deleted with the
  session. Seeking in remux / transcode restarts ffmpeg at the new position.
- **Provider connection limits:** Xtream playback goes through `XtreamLoginPicker.forPlayback` as
  on Android, so the web counts as one more connection and a refusal moves to another login.
- **Now playing:** the web server publishes its sessions over live sync, so TV and phone show what
  the browser is playing and can stop it (existing Now Playing / remote Stop).

## Deployment

```yaml
services:
  fijerena-web:
    image: fijerena-web:<version>
    restart: unless-stopped
    networks: [npm]
    volumes:
      - ./data:/data                 # Room/SQLite DBs, EPG index, settings, sessions
      - /srv/media:/media:ro         # optional: Local source
    tmpfs:
      - /transcode:size=2g           # HLS segments
    devices:
      - /dev/dri:/dev/dri            # optional (O2)
    secrets: [fijerena_key]
    environment:
      FIJERENA_SECRET_KEY_FILE: /run/secrets/fijerena_key
secrets:
  fijerena_key: { file: ./fijerena.key }
networks:
  npm: { external: true }
```

NPM proxy host → `fijerena-web:8080`, Let's Encrypt, Force SSL, HTTP/2, **WebSockets Support on**,
and under Advanced:

```nginx
proxy_buffering off;
proxy_request_buffering off;
proxy_read_timeout 3600s;
proxy_send_timeout 3600s;
client_max_body_size 50m;
```

No NPM Access List: authentication is the server's own (an Access List's basic auth breaks some
players and adds nothing on top of it).

Remote viewing goes out through the home upload: passthrough HD needs about 5–10 Mbit/s per
viewer; a lower-bitrate transcode profile is offered for mobile data.

## Phases

### Phase 0 — `core` becomes multiplatform, Android only

- `core:network` and `core:player` move to the Kotlin Multiplatform plugin with a single
  `androidMain` target; code moves to `commonMain` only where it is already free of Android, the
  rest stays in `androidMain`.
- New interfaces in `commonMain`, Android `actual`s wrapping what exists today: `KeyValueStore`
  (SharedPreferences), `SecretStore` (today's encrypted prefs, see
  [secret store plan](./20260828_secret-store-migration-plan.md)), `Logger`, `BackgroundScheduler`
  (WorkManager), `Connectivity`, `AppFiles` (`Context.filesDir`, cache dirs), Room database builders.
- **Done when:** both apps behave the same, every unit and instrumented test and every
  `scripts/check-*.sh` is green, and the Architectural Constraints still hold (`core:player` does
  not depend on `core:network`).

This phase is also the first step of any later desktop / iOS port.

### Phase 1 — JVM target for `core:network`

- Add `jvmMain` actuals: file-backed `KeyValueStore`, `SecretStore` encrypted with the server key,
  SLF4J `Logger`, a coroutine `BackgroundScheduler`, Room with `BundledSQLiteDriver`, kxml2 for
  `XmltvParser`.
- Run the existing JVM unit tests of `core:network` against the `jvm` target too.
- **Done when:** a JVM test logs into a real panel, syncs a catalogue into Room and builds the EPG
  index.

### Phase 2 — Server skeleton (`webserver/`)

- Ktor app, Docker image (JRE + ffmpeg + the SPA), health endpoint, structured logs.
- Authentication as above; settings; sources CRUD (Add / Edit Source, Copy to, Duplicate).
- `web/` SvelteKit skeleton: login, setup, sources list; CSS tokens generated from the Kotlin token
  files; typed API client generated from the server's OpenAPI spec.
- README section on deploying behind NPM.

### Phase 3 — Catalogue API and live sync

- Profiles, merged Live TV / Movies / TV Shows, categories, details, episodes, Search (Catalogue
  and Guide), Recent, Continue Watching, favourites, watch state, TMDB, the guide.
- Schedulers for catalogue sync, EPG refresh and FTS rebuild, honouring the same rules as Android:
  no destructive self-healing (Constraint 12), cancellation rethrown (Constraint 7), long-lived
  scopes from `AppScopes`.
- Join the sync account as a device (O5), using the existing handoff flow.

### Phase 4 — Stream gateway

- Probe, mode decision table, sessions, ffmpeg supervision, tmpfs cleanup, signed URLs, catch-up,
  `XtreamLoginPicker` integration, Now Playing over sync.
- **Done when:** a live TS channel, an HLS channel, a catch-up programme, an MP4 film, an MKV film
  with AC3 audio and an HEVC episode all play in Chrome and Firefox on Linux and Chrome on Android.

### Phase 5 — Svelte frontend

- Shell (responsive: phone width to desktop), browse, details, player (controls, subtitles, audio
  tracks, seek, resume, next episode), Search, TV Guide, Recent and favourites, Settings, profiles.
- Wording, languages (en, fr, mg) and the "no hardcoded UI values" rule carried over: only
  `var(--…)` tokens in styles.

### Phase 6 — Hardening and release

- Playwright end-to-end tests against a test panel (`tools/` bridge), in CI.
- Backup and restore of `/data`; upgrade path for the databases (Constraint 9 applies here too).
- Image built and published by CI on tags, alongside the APKs.
- `docs/` reference pages: WEB_APP.md (deploy, NPM, auth, gateway).

## Progress

| Phase | Status |
|---|---|
| 0 — `core` multiplatform, Android only | Not started |
| 1 — JVM target | Not started |
| 2 — Server skeleton | Not started |
| 3 — Catalogue API and sync | Not started |
| 4 — Stream gateway | Not started |
| 5 — Svelte frontend | Not started |
| 6 — Hardening and release | Not started |

## Out of scope

- Public multi-tenant hosting: one household, one server.
- Android TV in the browser (the TV app stays the TV experience).
- macOS / iOS native apps: parked; Phase 0 and Phase 1 are their first steps too.
