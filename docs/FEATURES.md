# Fijerena — Feature Reference

A native Android media player supporting Xtream IPTV, Jellyfin, SMB shares, Local files, and Remote M3U. Targets Android phones/tablets and Android TV (NVIDIA Shield, Chromecast with Google TV, Sony Bravia).

---

## Provider Types

| Provider | Live TV | Movies | TV Shows | Search | Progress Sync | Auth |
|----------|---------|--------|----------|--------|---------------|------|
| **Xtream** | Yes | Yes | Yes | Client-side | No | Yes |
| **Jellyfin** | No | Yes | Yes | Server-side | Yes | Yes |
| **SMB** | No | Yes | No | Filename | No | Optional |
| **Local** | M3U only | Yes | No | Filename | No | No |
| **Remote M3U** | Yes | No | No | No | No | No |

Multiple providers can be configured simultaneously. Switch active provider from Settings → Manage Providers.

---

## Content

### Live TV
Channels organized by provider-defined categories. D-pad Up/Down switches channels without leaving the player (Xtream/Remote M3U). Toasts display channel name for 3 seconds on switch.

### Movies (VOD)
Movie details screen with plot, cast, director, genre, rating, year, duration, video/audio tech info. Play or Resume (if progress saved). Auto-resume saves position every 5 seconds, resumes if 2–95% complete.

### TV Shows
Season accordion with episode list. Auto-expands the next unwatched season. Episode thumbnails, per-episode metadata, resume support. Series-level metadata with season fallback.

### EPG Guide (TV Guide)
Live TV only. Full grid: channel list (20%) + time slots (80%), 48 × 30-minute slots. Auto-scrolls to "now". Date navigation (prev/next day, jump to today). Click channel or programme to start playback. 30-minute cache TTL.

---

## Search

### Xtream (two-phase client-side)
1. **Phase 1 (instant):** Sweeps cached categories for matches
2. **Phase 2 (network):** Fetches uncached categories in parallel (semaphore = 20, up to 200 results)

Minimum 2 characters, explicit trigger. Background pre-fetch warms cache on category screen load.

### Jellyfin (server-side)
Native Jellyfin REST search. Returns movies and series matching the query.

### Local / SMB
Filename matching against scanned file list.

---

## EPG Browser

Standalone programme title search across all indexed XMLTV data.

- Access: Content Type Selection → book icon (visible when EPG index is ready)
- Results grouped by programme title, sorted by airing count
- Time window: −1 to +6 days from now, max 500 results per query
- SQLite FTS4 MATCH for fast search (<100ms); falls back to LIKE if FTS returns empty
- Programme titles and channel names scroll with `basicMarquee` when they overflow
- Mobile: expandable cards showing up to 3 airings (tap to expand all)
- TV: GlassPanel cards with TvLazyColumn

---

## EPG Management (Multi-Source)

Settings → Manage EPG Data. Add, edit, and delete XMLTV source URLs.

- Per-source label and timezone offset override (applies during parsing)
- Status indicator: green = ingested <24h, yellow = >24h, red = error, gray = disabled
- Download-ingest-delete pipeline: XML downloaded to temp, parsed into SQLite, file deleted immediately
- TV/fixed devices: stream directly from network to DB (zero disk I/O)
- Mobile: download to cache dir first, then ingest
- Auto-refresh on startup and 24h periodic WorkManager background sync
- First source clears existing data (full rebuild); subsequent sources append
- Actions: Refresh All, Cleanup Files, Purge >7 days, Clear All Data

**Indexer states:** NotIndexed → Indexing(progress%) → Indexed(programmes, channels) → Failed(reason)

---

## Settings Export / Import

Settings → Export Settings / Import Settings.

**Exported:** all provider configs (name, URL, username, type, config JSON, per-provider settings), all EPG source URLs, and global AppSettings (theme, UI scale, dev mode, EPG auto-refresh, cellular buffer multipliers).

**Not exported:** passwords (EncryptedSharedPreferences), cache, EPG programme data.

**Import conflict resolution:** when an imported provider name matches an existing one, a dialog offers:
- **Overwrite** — update URL, username, type, config, and per-provider settings in place
- **Duplicate** — add as a new provider with `(imported)` suffix
- **Skip** — leave the existing entry unchanged

EPG sources are merged by URL; duplicates are skipped silently.

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
| WiFi Live TV | 2s | 5s | Low-latency |
| WiFi VOD | 15s | 50s | Large pre-buffer |
| Cellular Live TV | 10s | 40s | (multiplier-scaled) |
| Cellular VOD | 75s | 150s | (multiplier-scaled) |

Cellular multipliers are tunable 0.5×–3.0× in dev mode via Settings → Cellular Buffer Settings.

### In-Player EPG (Live TV)
Current programme title, time range, and progress bar shown in stream info overlay. "Up Next" programme shown below. Fetched on stream start and channel switch. Degrades gracefully if no EPG data.

### Stats for Nerds
Double-tap OK (TV) or tap stats button (mobile) to show overlay. Repositionable to 4 corners on TV.

**VIDEO:** Codec, Resolution, Frame Rate, Bitrate
**AUDIO:** Codec, Sample Rate, Channels, Bitrate
**NETWORK:** Speed, Measured Bandwidth, Buffer health, Buffered position, Rebuffer count/duration, ABR quality switches
**PLAYBACK:** Position, Duration
**PERFORMANCE:** Dropped frames (color-coded: <0.5% green, <2% yellow, ≥2% red)
**STREAM:** Type (Live/VOD), Retries, Uptime, URL

Mobile stats overlay: dismissible only via X button.

### Track Selection
In-playback dialogs for audio track, subtitle track, and video quality. D-pad navigable on TV.

### Auto-Resume
Position saved every 5 seconds. On re-open, resumes if progress is 2–95%. Resume prompt with "Continue" / "Start Over".

### Controls
- **TV:** OK = show/hide controls, double-OK = stats, Back = exit, D-pad Up/Down = channel switch (Live TV)
- **Mobile:** tap = controls, swipe up/down = channel switch (Live TV), slider seek bar (VOD)

---

## Virtual Categories

Appear alongside provider categories in the category list:

| Category | Content Types | Description |
|----------|---------------|-------------|
| **Continue Watching** | Movies, TV Shows | Items with 2–95% progress, most recent first |
| **Favorites** | All | Starred items, configurable max size (10–500) |
| **Last Watched** | All | Chronological history, configurable size (1–100) |
| **Recently Viewed** | All | Recently browsed categories (max 20, deduplicated) |

Favorites and Last Watched are per-provider. Continue Watching is derived from saved progress.

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

Enable in Settings. Features gated behind dev mode:

- **Payload size tracking:** API response sizes shown in category grid
- **EPG DB stats:** programme and channel counts in EPG Browser header
- **Source labels:** EPG source name shown on each airing in EPG Browser
- **Cellular Buffer Settings:** multiplier sliders (0.5×–3.0×) for Live and VOD profiles

---

## Settings Screen Reference

| Setting | Description |
|---------|-------------|
| Active Provider | Shows current provider name and URL |
| Manage Providers | CRUD for all providers; set active |
| Theme | Select from 4 dark themes |
| Manage EPG Data | Add/edit/delete XMLTV sources, trigger refresh |
| Export Settings | Save providers + EPG sources + global config to JSON |
| Import Settings | Load JSON; conflict dialog for name clashes |
| Cache Management | View size breakdown; clear per content type or all |
| UI Scale | 70–100%; scales category grid and item cards |
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
