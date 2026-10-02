# Fijerena — Feature Reference

A native Android media player supporting Xtream IPTV, Jellyfin, SMB shares, Local files, and Remote M3U. Targets Android phones/tablets and Android TV (NVIDIA Shield, Chromecast with Google TV, Sony Bravia).

---

## Provider Types

| Provider | Live TV | Movies | TV Shows | Search | Progress Sync | Auth |
|----------|---------|--------|----------|--------|---------------|------|
| **Xtream** | Yes | Yes | Yes | Client-side | No | Username/password |
| **Jellyfin** | No | Yes | Yes | Server-side | Yes | Username/password or Quick Connect |
| **SMB** | No | Yes | No | Filename | No | Optional |
| **Local** | M3U only | Yes | No | Filename | No | No |
| **Remote M3U** | Yes | No | No | Title match | No | No |

Multiple providers can be configured simultaneously. Switch active provider from Settings → Manage Providers.

### Jellyfin Quick Connect
When adding a Jellyfin provider, tap **Use Quick Connect** instead of entering a password. The app displays a 6-digit code that you approve on the Jellyfin web UI or another client. On approval the app receives and stores the access token automatically — no password is ever stored or required.

---

## Content

### Live TV
Channels organized by provider-defined categories. D-pad Up/Down switches channels without leaving the player (Xtream/Remote M3U). Toasts display channel name for 3 seconds on switch.

**Preview pane (embedded playback):** Browsing Live TV always has a channel playing alongside the list, backed by the same `StreamingPlaybackService` connection used for full-screen — promoting/demoting never restarts the stream.
- **TV:** Focus-driven split layout (`LiveTvSplitLayout`) — arrowing to a channel debounce-previews it; OK/center promotes to full-screen. Entry always lands on a real "browse" screen underneath the preview (a silently-pushed `CategoryList(showPreviewPane=false)`), so Back from the preview never exits Live TV outright. Leaving the app (Home, screensaver, HDMI input switch) pauses it and, after 30 s, stops it; coming back plays the channel again at the live edge if it was playing — a pause the viewer made in full screen stays paused.
  - Channel panel shows Recent; Right switches to Favorites, Left back. A row's favourite/remove icons stay hidden until asked for with the key pointing away from the other list (Left on Recent, Right on Favorites); repeating it walks outward through them.
- **Mobile:** Tap-driven docked mini-player — tapping a channel docks and plays it immediately above the scrollable list; tapping the dock (or its expand affordance) promotes to full-screen. The dock auto-seeds from the last-played channel on entry so Live TV never opens to a bare list. Back from full-screen collapses to the dock; Back from the dock clears it back to the bare list before a further Back leaves Live TV.

### Movies (VOD)
Movie details screen with plot, cast, director, genre, rating, year, duration, video/audio tech info. Play or Resume (if progress saved). Auto-resume saves position every 5 seconds, resumes if 2–95% complete. A watched/unwatched toggle sits beside the favorite toggle — marking watched hides the resume bar immediately; unmarking re-derives it from a fresh lookup.

### TV Shows
Season accordion with episode list. Auto-expands the next unwatched season. Episode thumbnails, per-episode metadata, resume support. Series-level metadata with season fallback.
- **TMDB Enrichment:** Fetches per-episode synopses from TMDB when available, ensuring high-quality metadata even when IPTV providers offer minimal descriptions. The TMDB id comes from the series info, else from the series listing. The player's info panel shows the episode's own synopsis only — never the series' — and an episode without one has it fetched from TMDB when it starts playing.
- **Episode Navigation:** Swipe (mobile) or D-pad Left/Right (TV) to jump between episodes directly from the player.
- **Play next episode automatically** (Settings → Playback, per profile, off by default; Xtream TV shows only — not Jellyfin, which keeps its own play state): near the end of an episode that has a next one — the one the player's Next button plays, into the next season if needed — a small translucent "Up next" card appears in the top-right corner over the still-playing video (TV: inside the safe margins, under the clock while the controls are up; mobile: below the status bar and cutout), one line like "Up next · S1:E2 · 45 s" with Play now and Cancel beside it. It appears when the time left is at most 90 s or 15 % of the episode's length, whichever is shorter (`CinemaAnimation.upNextLeadMs`, `UP_NEXT_LEAD_FRACTION`: 90 s for a 22-minute episode, 45 s for a 5-minute one), or straight away when playback starts or is moved inside that window; with an unknown duration it never appears early. "N" is the playback time left, so it stops with the video (paused, buffering, app in the background). **Play now** skips to the next episode at once, the way Next does. **Cancel** — or Back — hides the card for this episode (it doesn't come back, even after seeking) and playback carries on; that episode then ends as before, back to the episode list. If the episode ends with the card not cancelled, the next one plays at once (only once the app is in the foreground; a phone in picture-in-picture counts, and the card is hidden there). TV: the card takes focus on "Play now" as it appears; with the controls up, both stay usable, and Down from the player reaches the card when it isn't focused. Off, with no next episode, or on Jellyfin, the player leaves at the end as before; movies and Live TV are unaffected.
- **Mark Watched:** TV — long-press an episode card (the existing D-pad long-press convention). Mobile — tap the watched badge itself, shown filled or outline.

### EPG Guide (TV Guide)
Live TV only. Full grid: channel list (20%) + time slots (80%), 48 × 30-minute slots. Auto-scrolls to "now". Date navigation (prev/next day, jump to today). Click channel or programme to start playback. Backed by `XmltvEpgService`'s 12-hour SharedPreferences cache (`PARSED_CACHE_TTL_MS`).

---

## Search

### Global Search ("ALL")
Unified search across all content types (Live TV, Movies, TV Shows) and categories. Accessible via the search button on the Content Type Selection screen.
- **Grouped Results:** Results are organized by content type headers.
- **Collapsible Headers:** Each group (Live TV, Movies, TV Shows) can be expanded or collapsed to manage long result lists.
- **Combined View:** Matches for both categories and individual streams are shown within their respective content type groups.

### Xtream (local FTS)
Each word becomes an FTS4 prefix term (`the*`) matched against the synced catalogue (`xtream_streams_fts` / `xtream_series_fts`), up to 200 results per content type; no network call. Matches in categories hidden by the provider's category filters are counted per type (the "N hidden" note on each group) with a second FTS query.

Minimum 2 characters to trigger.

### Jellyfin (server-side)
Native Jellyfin REST search. Returns movies and series matching the query.

### Local / Remote M3U
Direct provider-level search matching titles against loaded item list via `BaseM3uMediaProvider.search`.

### SMB
No provider-level search; falls back to `SearchViewModel`'s client-side cache scan (filename match against already-loaded items).

---

## EPG Search (EPG Browser)

Standalone programme title search across all indexed XMLTV data.

- Access: Content Type Selection → calendar / date range icon (visible when EPG index is ready)
- **Freshness Tracking:** Displays last index update time in the header.
- **Customizable Refresh:** Configurable refresh interval (4h, 8h, 12h, 24h, 48h) or "Never".
- **Robust Retries:** Automatic retry mechanism (5 attempts with exponential backoff: 1m to 16m) for failed updates.
- **Smart Refresh:** Shows a "Refresh Data" button when indexed programmes are stale according to the selected interval.
- Results grouped by start date (Today, Tomorrow, weekday name, or "EEEE, MMM d" for later dates), then by programme within each date
- Time window: every programme that hasn't ended yet, no upper limit (ingest keeps everything ahead the source provides), max 500 results per query. Channel search: what's on now or starts in the next 2 hours
- SQLite FTS4 MATCH for fast search (<100ms): a raw query first, then a sanitized "safe" AND-style retry if that returns nothing — no XML-scan fallback
- **During a refresh:** search keeps working on the previous guide — the new guide and its FTS index are switched in together, in one transaction. When the index can't be used (a low-storage refresh writing straight into the guide, or an interrupted rebuild), search falls back to a slower title-only scan, flagged on screen as possibly incomplete and rerun in full once the index is ready. If even that times out, the search says why ("The guide is updating…" or, after an interrupted rebuild, "The search index is being rebuilt…") and reruns by itself once the index is ready. Opening the browser mid-refresh no longer claims there is no guide.
- Programme titles and channel names scroll with `basicMarquee` when they overflow
- Mobile: sticky date headers with expandable programme cards showing up to 3 airings (tap to expand all)
- TV: date headers with GlassPanel programme cards in a LazyColumn

---

## EPG Management (Multi-Source)

Settings → Manage EPG Data. Add, edit, and delete XMLTV source URLs.

- Per-source label and timezone offset override (applies during parsing)
- Status indicator: green = ingested < interval, yellow = > interval stale, red = error, gray = disabled
- Download-ingest-delete pipeline: XML downloaded to temp, parsed into SQLite, file deleted immediately
- TV/fixed devices: stream directly from network to DB (zero disk I/O)
- Mobile: download to cache dir first, then ingest
- Parallel ingestion: sources download concurrently (3 on mobile, 2 on TV); ingestion into SQLite uses 2 parallel workers
- Per-source progress: shows download % and ingestion % with channel/programme counts
- Cancel button: running or queued EPG refreshes can be cancelled mid-operation
- Auto-refresh on startup and periodic background sync (configurable 4h–48h) with real-time "Next refresh" display
- Intelligent Retries: 5-attempt retry loop with exponential backoff (1m, 2m, 4m, 8m, 16m) for the entire task; background WorkManager retries back off linearly at 10 minutes
- Change detection: a source that hasn't changed since the last refresh (server returns `304`, or the payload hash matches) skips both download and ingestion, and its row reads **"Unchanged"** in place of the download/ingest durations. A hash match is overridden once a day so the guide window keeps moving forward
- Truncated downloads are detected against `Content-Length` and retried, instead of failing later as a parse error
- First source clears existing data (full rebuild); subsequent sources append
- Selective refresh: can refresh selected sources, failed sources, or outdated sources
- Source deletion cleans up associated channels and programmes from the index
- Import date filter: programmes ending before yesterday are skipped during ingestion
- Stray file cleanup: detects and removes orphaned cache files not tied to any source
- Clear All Data: instant DB destroy+recreate (not row-by-row delete), shows blocking overlay, sources preserved
- Actions: Refresh All, Refresh Selected, Refresh Failed, Refresh Outdated, Cleanup Files, Purge >2 days, Clear All Data

**Indexer states:** NotIndexed → Indexing(progress%) → Indexed(programmes, channels) → Failed(reason)

---

## Settings Export / Import

Settings → Export Settings / Import Settings.

**Exported:** all provider configs (name, URL, username, type, config JSON, per-provider settings), all EPG source URLs, per-provider favorites (item ID, name, category, content type), and global AppSettings (theme, UI scale, the active profile's dev mode, EPG auto-refresh, cellular buffer multipliers).

**Not exported:** passwords (EncryptedSharedPreferences), cache, EPG programme data.

---

## Architecture & Performance

### Dependency Injection (AppContainer)
A custom, manual dependency injection container (`AppContainer`) provides singletons for the app's core repositories (`ProviderRepository` and `MediaRepository`). This ensures:
- Single source of truth for the database and network layers across both mobile and TV apps.
- Prevention of redundant repository instantiations, reducing memory overhead and database lock contention.
- Thread-safe, Mutex-protected asynchronous repository resolution.

### Asynchronous UI State
All ViewModels (e.g., `CategoryViewModel`, `SearchViewModel`, `EpgViewModel`) initialize their repository dependencies asynchronously. This completely eliminates UI thread blocking (`runBlocking`) during the crucial composition phase, ensuring the app remains perfectly smooth and responsive on constrained TV hardware (like older Fire TV sticks or Sony Bravia TVs) during startup or intensive search operations.

**Selective import:** On import, a "Select What to Import" dialog presents checkboxes for each section — General Settings, Providers, EPG Sources, Favorites. Only checked sections are imported.

**Import conflict resolution:** when an imported provider name matches an existing one, a dialog offers:
- **Overwrite** — update URL, username, type, config, and per-provider settings in place
- **Duplicate** — add as a new provider with `(imported)` suffix
- **Skip** — leave the existing entry unchanged

EPG sources are merged by URL; duplicates are skipped silently. Favorites are merged with existing ones; duplicates (by item ID) are skipped.

---

## Player

### Format Support
HLS (`.m3u8`), DASH (`.mpd`), MPEG-TS (`.ts`, `.mpeg`), MP4, MKV, WebM, and other containers via Media3/ExoPlayer.

### Jellyfin Transcoding
Before playback, the app POSTs a `DeviceProfile` to Jellyfin's `/Items/{id}/PlaybackInfo`. Jellyfin decides:
- **Direct play** — file sent as-is; ExoPlayer decodes natively (H.264, HEVC, VP9, AV1, AC3/DTS/TrueHD via FFmpeg ext, FLAC, Opus)
- **Transcode** — Jellyfin re-encodes to HLS/H.264+AAC for unsupported codecs; app receives `.m3u8`

`PlaySessionId` is included in all playback progress/stop reports so Jellyfin can manage the transcoding session.

### Buffer Strategy (network-aware)
`AdaptiveLoadControl` swaps buffer parameters at runtime based on network type and content type.

| Profile | Min | Max | Notes |
|---------|-----|-----|-------|
| WiFi Live TV | 2s | 8s | Low-latency |
| WiFi VOD | 5s | 50s | Fast startup |
| Cellular Live TV | 50s | 50s | (multiplier-scaled) |
| Cellular VOD | 40s | 100s | (multiplier-scaled) |

Cellular multipliers are tunable 0.5×–3.0× in dev mode via Settings → Cellular Buffer Settings.

### In-Player EPG (Live TV)
Current programme title, time range, progress bar, and **video resolution/codec** shown in stream info overlay. "Up Next" programme shown below. Fetched on stream start and channel switch. Degrades gracefully if no EPG data.

### Stats for Nerds
Double-tap OK (TV) or tap stats button (mobile) to **dismiss** the overlay. Fixed to the top-right corner on TV and non-focusable to allow concurrent stream control (inputs pass through to the player).

### Buffering Awareness
Instead of showing the stats overlay automatically on buffering, the app now shows a discrete "High Buffering" toast when excessive buffering is detected, ensuring minimal distraction from the content while keeping the user informed of network conditions.

**VIDEO:** Codec, Resolution, Frame Rate, Bitrate
**AUDIO:** Codec, Sample Rate, Channels, Bitrate
**NETWORK:** Speed, Measured Bandwidth, Buffer health, Buffered position, Rebuffer count/duration, ABR quality switches
**PLAYBACK:** Position, Duration
**PERFORMANCE:** Dropped frames (color-coded: <0.5% green, <2% yellow, ≥2% red), Drop Rate
**APP:** Heap used/max with percentage (color-coded: <60% green, <85% yellow, else red), GC count and time, UI frames skipped
**STREAM:** Type (Live/VOD), Retries, Stream health (Live only — healthy / unstable with recycle count / degraded with attempt count), Uptime, URL
**DEVICE:** Model, Android API Level
**Footer:** Build time and git hash (`BuildConfig.BUILD_TIME` / `GIT_HASH`), plus model and detected device type from `DeviceDetector`

Mobile stats overlay: dismissible only via X button.

---

### Track Selection
In-playback dialogs for audio track, subtitle track, and video quality. D-pad navigable on TV.

### Auto-Resume
Position saved every 10 seconds (Live TV) or based on progress (VOD). On re-open, resumes if progress is 2–95%. Resume prompt with "Continue" / "Start Over".

### Controls

**TV (D-pad remote):**
- **OK** = show/hide controls (never pauses playback)
- **Double-OK** = dismiss stats overlay (if visible)
- **Back** = dismiss stats (if visible) or exit player
- **D-pad Up/Down** = switch channel (Live TV only)
- **D-pad Left** = open category channel overlay (Live TV); if last-watched overlay is open, closes it instead
- **D-pad Right** = open last-watched channel overlay (Live TV); if category overlay is open, closes it instead
- **D-pad Left/Right** = seek −10s / +10s (VOD only, while controls visible)
- **KEYCODE_MEDIA_PLAY_PAUSE** = pause/resume (VOD only)
- **KEYCODE_MEDIA_REWIND** = seek −30s (VOD only)
- **KEYCODE_MEDIA_FAST_FORWARD** = move the scrub cursor forward like D-pad Right (10 s per press, accelerating to ~1, 3, then 10 min per second while held); OK commits the seek (VOD only)

**Mobile:**
- **Single tap** = show/hide controls
- **Double-tap** = pause/resume (VOD only; no effect on Live TV)
- **Swipe up/down** = switch channel (Live TV only)
- **Swipe right** = open category channel overlay (Live TV)
- **Swipe left** = open last-watched channel overlay (Live TV)
- **FF / Rewind buttons** = +5 min / −1 min seek (VOD only, shown in controls bar when `duration > 0`)

### Channel Overlays (Live TV)
Two side-panel overlays available during Live TV playback:
- **Category channels** — slides in from left edge. TV: D-pad Left. Mobile: swipe right.
- **Last watched** — slides in from right edge. TV: D-pad Right. Mobile: swipe left.

Overlays use semi-transparent `GlassPanel` (50% opacity). Background scrim is 30% black. Select a stream to switch channels; dismiss with Back or by opening the opposite panel. Overlays close automatically when a stream is selected.

---

## Watch State

Playback position and watched status are stored durably in SQLite (`watch_state`), kept forever and never truncated. The "Last Watched size" setting bounds only how long the Recent row is — not what the app remembers. Applies to Xtream, SMB, Local, and Remote M3U; Jellyfin keeps this state on the server and is untouched by any of the below.

### Mark Watched / Unwatched
Available from movie details, TV episode cards (long-press), mobile episode badges, TV content lists, and search results. Marking is manual and independent of playback: it never adds the item to the Recent row.

Completion is **sticky** — a brief accidental rewatch can't silently clear a mark. Only an explicit "mark unwatched" clears it.

### Cross-Variant Dedup (TMDB)
Providers routinely carry the same title several times (language, quality, or source variants). Watching one variant marks all of them, matched on TMDB ID:
- **Movies:** sibling rows in the catalogue sharing a `tmdbId`.
- **TV Shows:** each variant is a *separate series* with its own complete, separately-numbered episode list, and episode-level TMDB IDs are essentially never populated by providers. Siblings are found by shared **series-level** TMDB ID, then matched on `(season, episode)` — which is stable across variants.

Unmarking spreads the same way, clearing every sibling; otherwise a sibling's row would immediately drive the check straight back on. Dedup requires a cached catalogue, so it is Xtream-only; other providers degrade to no dedup rather than failing.

### Track Memory
The audio and subtitle tracks chosen during playback are saved per item and restored on replay. A newly started episode with no saved choice of its own falls back to the most recent choice made anywhere in the same series.

---

## Virtual Categories

Appear alongside provider categories in the category list:

| Category | Content Types | Description |
|----------|---------------|-------------|
| **Continue Watching** | Movies, TV Shows | Items with 2–95% progress, most recent first |

The home screen's **Continue Watching** shelf (Movies and TV Shows together) shows one card per
show: mid-watch, its episode resumes; once an episode is finished, the card offers the next one
("Up next", next season after a season's last episode), fetching the show's episode list from the
provider if this device never stored it (a show watched on another device). After a show's last
episode it leaves the shelf. Movies show only while mid-watch.
| **Favorites** | All | Starred items, configurable max display (10–500) |
| **Last Watched** | All | Chronological history (Live: 10s delay; VOD: 2% threshold), configurable display size (1–100) |
| **Recent Categories** | All | Recently browsed categories (max 20, deduplicated) |

Favorites and Last Watched/Continue Watching persist durably in SQLite via Room (`favorite_state` and `watch_state` tables in `xtream_v2.db` v16). Configurable size settings bound only the rendered category row, never what is stored. Recent Categories is stored as a capped convenience list in per-provider SharedPreferences.

---

## Profiles

"Who's watching?" picker at launch (and from the header avatar). Each profile has its own
favourites, watch history, search history, category filters per provider, dev-mode switch,
"Play next episode automatically" switch and Jellyfin login;
providers, EPG sources and other settings are shared. Which profile is in use is per device.
Each profile also remembers the provider it last picked (or added), on every device: switching to
a profile moves the device to that provider. A profile that hasn't picked one yet, or whose
provider was deleted, stays on the device's current provider. Another device's pick never moves a
device that is already on that profile — it applies at the next switch
(`docs/plans/20261002_profile-last-provider-plan.md`).
Switching takes a fraction of a second: only category rows carry the filter flag, and streams and
series follow their category at query time (see `docs/plans/20261001_fast-profile-switch-plan.md`).

---

## Live Sync

Settings → Live sync. Keeps profiles, providers, EPG sources, favourites, watch progress, category
filters and selected settings the same on every device of a group, through a sync server the user
runs (Cloudflare Worker or self-hosted Docker image, `server/`). Everything is encrypted on the
device; the server sees only keys and ciphertext. Off on a device until it is set up there.

- **Start a sync group:** enter the server address (checked first; a setup secret if the server
  asks for one).
- **Add a device:** a phone scans the invite QR code a member shows; a TV, which can't scan, shows
  a code for a phone of the group to scan. Codes work once, for 10 minutes.
- **Manage:** last sync, Sync now, the devices list with Remove, Leave the sync group (local data
  stays).
- Changes reach other open devices within seconds; a closed app catches up when opened.
- **Now playing:** with **Share what's playing with my sync group** on (per device, off by
  default, never synced), a device's row in every other member's devices list shows what it is
  playing — "▶ Playing · Malcolm X · Kid", "▶ Live · BBC World News — Newsday · Kid" (programme
  only when the channel has EPG), or "⏸ Paused · …". Updates within seconds of a change, refreshed
  every 60 s while playing or paused; a device silent for 3 minutes shows as idle. Sealed like every record.
  **Remote Stop (phone app only):** a phone's devices list has **Stop** on another device's playing
  or paused row; after a confirmation it shows "Stopping…" until that device's line clears (or
  "Couldn't reach <device>" after ~90 s). The device obeys only for the playback it has on right
  now (a per-playback session id, never a clock), saves the watch position as Back does, closes
  the player for Home and shows "Playback stopped from <device>". No Stop button in the TV app.
  Design: `docs/plans/20261001_live-sync-now-playing-plan.md`.
- Resilient by design: a record a device can't apply waits and is retried (never blocks the rest); a record the server can't accept is rejected on its own; records whose sealed timestamp or deletion flag were altered are dropped; reconnects back off; an unreadable sync link on a device resets it to unlinked rather than crashing.
- Not synced: which profile and provider a device is using (each profile's last picked provider
  is synced, and applied when a device switches to that profile), UI scale, cellular settings, and
  Jellyfin favourites and history (Jellyfin keeps those itself).

Design and protocol: `docs/plans/20260929_live-sync-plan.md`.

---

## Themes

4 dark themes switchable at runtime without restart:

| Theme | Accent | Background |
|-------|--------|------------|
| **Deep Night** (default) | Electric Blue `#2979FF` | `#0F1014` |
| **AMOLED Black** | Electric Blue `#2979FF` | `#000000` |
| **Emerald** | Green `#00C853` | `#0F1014` |
| **Crimson** | Red `#FF1744` | `#0F1014` |

---

## Developer Mode

Enable in Settings. Each profile has its own switch (off for a new profile). Features gated behind dev mode:

- **Payload size tracking:** API response sizes shown in category grid
- **EPG DB stats:** programme and channel counts in EPG Browser header
- **Source labels:** EPG source name shown on each airing in EPG Browser
- **Cellular Buffer Settings:** multiplier sliders (0.5×–3.0×) for Live and VOD profiles (mobile)
- **Diagnostics:** the on-device crash log and Android's record of why the app last closed (ANR, crash, low-memory kill), newest first; Share on mobile. See `docs/RUN_GUIDE.md` → Crash log and Diagnostics

---

## Settings Screen Reference

| Setting | Description |
|---------|-------------|
| Active Provider | Shows current provider name, URL, and subscription info (Xtream: expiry, max connections, trial status) |
| Last Sync | Timestamp plus what the sync actually changed — "No changes since last sync", or "N added • N updated • N removed". Xtream only, and hidden when the last sync errored (the counts belong to the last *successful* run and would read as a partial success) |
| Manage Providers | CRUD for all providers; set active |
| Theme | Select from 4 dark themes |
| Manage EPG Data | Add/edit/delete XMLTV sources, trigger refresh |
| Export Settings | Save providers + EPG sources + global config to JSON |
| Import Settings | Load JSON; conflict dialog for name clashes |
| Cache Management | View size breakdown; clear per content type or all |
| Shrink Database | Purges orphaned catalog rows from deleted providers and compacts `xtream_v2.db` with WAL truncation |
| UI Scale | 70–100%; scales category grid and item cards |
| Play next episode automatically | (Playback) Per profile and synced, off by default: near the end of an episode the next one is offered and starts when it ends (Xtream TV shows) |
| Developer Mode | Enables debug overlays and advanced settings |
| Cellular Buffer Settings | (dev mode) Tune cellular buffer multipliers |

### Per-Provider Settings (in Edit Provider)

| Setting | Default | Range |
|---------|---------|-------|
| Auto-Resume | On | — |
| Last Watched size | 25 | 1–100 |
| Favorites max | 100 | 10–500 |
| Category filters | Off | Xtream only |
| Caching | On | Xtream only |

---

## Device-Specific Behaviour

| Device | Behaviour |
|--------|-----------|
| **NVIDIA Shield** | AV1 → HEVC → AVC codec priority; hardware AV1 decode |
| **Sony Bravia** | HEVC → AVC priority; reduced animations on mid-range processors |
| **Chromecast with GTV** | Compact window layout |
| **Android phone/tablet** | Portrait locked except during playback (sensor-based) |

TV safe margins: 56dp horizontal, 32dp vertical on all root containers.
