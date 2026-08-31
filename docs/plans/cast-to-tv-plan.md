# Cast to TV (phone → Shield) — Plan

Status: **abandoned — not being pursued.** Kept as a decision record only.

Option A (standard Google Cast) was ruled out first — registration overhead not worth it for
personal-device casting (see "Option A registration details" below). Casting as a whole was
then dropped entirely; Option B was never started.

## Goal

Let a user start or hand off playback on their phone (mobile app) and have it play on the
Shield (or Chromecast with Google TV / Bravia — same `tv` build) instead of / in addition to
the phone.

## Current state (relevant facts)

- No Cast dependency exists anywhere in the repo today (`gradle/libs.versions.toml` has no
  `media3-cast` / `play-services-cast` entries, no NSD/mDNS code in `mobile`/`tv`/`core`).
- Media3 1.7.1 is already the player stack (`androidx.media3:media3-session`,
  `MediaSessionService`) — `core/player/service/StreamingPlaybackService.kt`.
- **Auth is header-based for Jellyfin**: `JellyfinApiService.kt` injects
  `X-Emby-Token` / `Authorization` on stream requests. `StreamingMediaSourceFactory` accepts a
  `headers: Map<String,String>` and calls `setDefaultRequestProperties(allHeaders)` on the
  HTTP data source. Xtream/SMB/Local mostly embed credentials in the URL itself.
- This header requirement is the single biggest constraint on the design below — a receiver
  that can't attach custom HTTP headers to its own stream fetch cannot play Jellyfin content.
- `core:player` must not depend on `core:network` (architectural rule in AGENTS.md) — any
  cast wiring that needs provider/session info has to go through the existing boundary
  (SharedPreferences / a passed-in header map), not a new direct dependency.
- Mobile and TV are separate app modules but share `core:player`, `core:navigation`,
  `core:ui`, `core:data`. Both can already independently connect to their own
  `StreamingPlaybackService` via `PlaybackServiceConnection`.

## Two architectures

### Option A — Standard Google Cast (CAF)

Phone becomes a real Cast **sender**; Shield runs a Cast **receiver**. Uses the actual
Chromecast icon/UX, and would work from any Cast sender app, not just fijerena's phone app.

Pieces needed:
1. Register an application in the Google Cast SDK Developer Console (see "Option A
   registration details" below) — unpublished apps are capped at 20 allow-listed receiver
   devices — fine for personal Shields, a blocker if this should work for arbitrary users'
   TVs without publishing.
2. **A custom receiver is required**, not the stock Default Media Receiver — DMR cannot send
   `X-Emby-Token`/`Authorization` headers on its media fetch, so Jellyfin content would fail.
   The custom receiver is a small hosted web app (CAF Receiver, HTML/JS + Shaka Player) that
   uses a `NetworkingEngine` request filter to attach headers from `customData` in the load
   request. Needs static hosting (e.g. a GitHub Pages / small static host) reachable over the
   internet — Cast receivers load over HTTPS from the internet even for LAN-local playback.
3. Sender side: add `media3-cast` (bridges Media3's `Player` interface to
   `com.google.android.gms.cast.framework`), a `CastContext`, `CastPlayer` +
   `SessionAvailabilityListener` in the mobile app to swap between local `ExoPlayer` control
   and `CastPlayer` when a session connects. A `MediaRouteButton` (or Compose equivalent) in
   `MobilePlayerScreen.kt`'s top bar.
4. Xtream/SMB/local streams (URL-embedded auth, no special headers) work against the custom
   receiver with no extra effort once headers are wired through `customData`.

Pros: standard UX, works with any Cast-enabled sender, no pairing/discovery code to write
(Google handles device discovery).
Cons: most moving parts — Google account/console registration, external hosting for the
receiver, a second codebase (JS) to maintain, device cap while unpublished, ongoing hosting
dependency for a purely local feature.

#### Option A registration details (why it was ruled out)

No APK gets installed on the Shield — it already ships a Cast receiver via Play Services /
Android TV. "Registration" is two separate things, both administrative, not code:

1. **Cast Application registration** — one-time signup at the Google Cast SDK Developer
   Console, one-time **$5 fee per Google account** (anti-spam fee, not per app). Register a
   "Custom Receiver" entry pointing at the hosted receiver URL, get back an **App ID** the
   sender uses to tell the Cast framework which receiver to load. Starts life "Unpublished."
2. **Device allow-list** — while Unpublished, the app only runs on Cast devices explicitly
   added to the console by serial number. Cap: **20 devices**. Shield's serial comes from its
   Cast device info (Settings / Cast SDK dev tools). Publishing removes the cap but requires
   Google review aimed at public consumer apps — overkill here.

So for personal-Shield-only use: $5 one-time + a receiver page hosted somewhere on the public
internet (Cast devices fetch it over HTTPS even for LAN playback) + manually adding each
Shield's serial to the console. No install, no per-Shield app, but real recurring maintenance
(hosting) and an external dependency for what is otherwise a local-network feature — the
deciding factor against Option A.

### Option B — In-app remote play (fijerena → fijerena)

Phone doesn't send Google a media session at all. It discovers the Shield's fijerena app on
the LAN (NSD/mDNS) and sends it a small "play this item" command; the Shield's own
`StreamingPlaybackService` builds the stream itself, through the existing
`StreamingMediaSourceFactory` / header logic, completely unchanged.

Pieces needed:
1. `NsdManager` service advertisement in the TV app's `StreamingPlaybackService` (or a
   lightweight foreground service) — register `_fijerena-cast._tcp` with a small
   loopback/local HTTP or WebSocket control port.
2. Phone side: `NsdManager` discovery, a picker UI ("Cast to Shield" → device list), and a
   command sent over that local socket: provider id, content id/type, resume position, track
   prefs — the same identifiers `PlaybackViewModel` already uses to start playback locally,
   just serialized instead of called in-process.
3. TV app receives the command, resolves it through its own `MediaRepository`/provider
   session (already logged in independently), and starts playback via its existing
   `StreamingPlaybackService` — no new auth path, no header workaround, works for every
   provider type (Xtream, Jellyfin, SMB, Local, Remote M3U) identically to local playback.
4. Basic transport control echo-back (play/pause/seek/stop from phone → TV) reuses
   `PlaybackServiceConnection`'s existing command surface, just routed over the socket instead
   of a local `Intent`/binder call.
5. Needs a pairing/trust story even on a private LAN — at minimum, restrict the control
   socket to the same account or require a one-time approval on the TV (a PIN or "Allow
   cast from [phone name]?" prompt) so an unrelated device on the LAN can't hijack playback.

Pros: reuses all existing provider/header/auth code untouched, no external accounts or
hosting, no header workaround, works identically across all 5 provider types, keeps
everything inside the existing `core:player`/`core:network` boundary.
Cons: only interoperates with another fijerena install (not a "real" Chromecast target other
apps can send to), needs new discovery/pairing/transport code from scratch, both devices need
their own session against the same provider (or the plan needs a token-handoff step — see
Open Questions).

### Comparison

| | Option A: Google Cast | Option B: In-app remote play |
|---|---|---|
| Standard Cast icon/UX | Yes | No (custom "Cast to Shield" affordance) |
| Works from non-fijerena senders | Yes | No |
| Handles Jellyfin header auth | Only via custom receiver (extra work) | Natively, no extra work |
| External accounts/hosting | Google Cast console + hosted receiver | None |
| New code surface | Sender (Kotlin) + Receiver (JS/HTML) | Discovery + control protocol (Kotlin, both apps) |
| Device cap while unpublished | 20 allow-listed | N/A |
| Reuses existing player/auth code | Partially | Fully |

## Recommendation

Option B, given this app's header-based Jellyfin auth and that both ends are already the same
codebase. Option A only pays off if "cast from other apps" or "no fijerena install on the TV"
is an actual requirement.

## Open questions (need answers before implementation starts)

1. Does the Shield already have a logged-in fijerena session for the same provider account as
   the phone, or does "cast" need to hand off/import phone credentials to the TV on first use?
2. Is casting from a phone that's on a *different* network than the Shield (mobile data, away
   from home) in scope, or LAN-only for v1? (LAN-only removes an entire remote-relay problem.)
3. Should the TV app be dumb-piped (mirror whatever plays) or should the phone become a full
   remote (browse-and-launch, transport controls, queue) once connected? Affects protocol
   surface size.
4. Multiple Shields on one LAN — does the user need to pick which one, or is there only ever
   one target in practice? (Memory: device↔APK mapping shows more than one TV device exists.)

## Phased plan (Option B)

- **Phase 0 — protocol & pairing design.** Define the wire protocol (command shapes: load,
  play/pause, seek, stop, state-push back to phone) and the trust/pairing model (PIN prompt on
  TV, or account-scoped trust). Answer Open Questions 1–4 first; this phase is design-only.
- **Phase 1 — TV-side receiver.** NSD advertisement + local control server embedded in (or
  alongside) `StreamingPlaybackService`. Accepts a load command, resolves via existing
  `MediaRepository`, starts playback exactly like a local launch. No phone-side UI yet —
  testable with a manual socket client.
- **Phase 2 — phone-side discovery & sender.** NSD discovery, device picker, "Cast" entry
  point in `MobilePlayerScreen.kt` (and anywhere else playback can start), sends the load
  command for whatever's about to play locally.
- **Phase 3 — remote transport controls.** Phone reflects TV playback state (playing/paused/
  position) and can send play/pause/seek/stop while cast is active; phone's own local player
  stays idle/hidden during an active cast session.
- **Phase 4 — resilience.** Reconnect on TV app restart/network blip, graceful fallback to
  local playback if the TV becomes unreachable, clear UI when the cast session drops.

## Phased plan (Option A — ruled out, kept for record only)

- **Phase 0 — Cast console setup.** Register app in Google Cast SDK Developer Console, decide
  publish-vs-unpublished (20-device cap) based on Open Question about scope beyond personal
  devices.
- **Phase 1 — custom receiver.** Build + host the CAF Receiver web app; wire header injection
  via `customData` → `NetworkingEngine` request filter; verify Jellyfin, Xtream, SMB, Local
  streams all play through it standalone (cast from a test sender / Cast simulator) before
  touching the phone app.
- **Phase 2 — sender integration.** Add `media3-cast` + Play Services Cast to `mobile`,
  `CastContext`/`CastPlayer`/`SessionAvailabilityListener`, `MediaRouteButton` in
  `MobilePlayerScreen.kt`, swap logic between local `ExoPlayer` and `CastPlayer`.
  `core:player` boundary rule means this wiring lives in the mobile app/UI layer, not inside
  `core:player` itself, or `core:player` gains an optional Cast-aware `Player` abstraction —
  needs a small design decision at implementation time.
- **Phase 3 — parity.** Track selection (audio/subtitle), resume position, watch-history
  reporting (`watch_state` table) all need to keep working when `CastPlayer` is the active
  player, not just local `ExoPlayer`.
- **Phase 4 — resilience.** Reconnect handling, receiver error surfacing back to the sender UI.
