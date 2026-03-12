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
| **Remote M3U** | Yes | No | No | No | No | No |

Multiple providers can be configured simultaneously. Switch active provider from Settings → Manage Providers.

### Jellyfin Quick Connect
When adding a Jellyfin provider, tap **Use Quick Connect** instead of entering a password. The app displays a 6-digit code that you approve on the Jellyfin web UI or another client. On approval the app receives and stores the access token automatically — no password is ever stored or required.

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

### AI Semantic Search (Hybrid)
In addition to keyword matching, the app uses a local vector embedding model to perform conceptual semantic searches.
- **Concept Understanding:** Searching for abstract concepts (e.g., "space wizards", "scary monsters") finds relevant movies/series.
- **Hybrid Strategy:** Combines SQLite FTS4 with Semantic Vector similarity.
- **Offline Capable:** Vector embeddings are generated locally in the background via `AiVectorizationWorker` and stored in `xtream_v2.db`.

### Global Search ("ALL")
Unified search across all content types (Live TV, Movies, TV Shows) and categories. Accessible via the search button on the Content Type Selection screen.
- **Grouped Results:** Results are organized by content type headers.
- **Collapsible Headers:** Each group (Live TV, Movies, TV Shows) can be expanded or collapsed to manage long result lists.
- **Combined View:** Matches for both categories and individual streams are shown within their respective content type groups.

### Xtream (two-phase client-side)
1. **Phase 1 (instant):** Sweeps cached categories for matches
2. **Phase 2 (network):** Fetches uncached categories in background

Minimum 2 characters to trigger. Background pre-fetch warms cache on category screen load.

### Jellyfin (server-side)
Native Jellyfin REST search. Returns movies and series matching the query.

### Local / SMB
Filename matching against scanned file list.

---

## EPG Browser

Standalone programme title search across all indexed XMLTV data.

- Access: Content Type Selection → book icon (visible when EPG index is ready)
- Results grouped by start date (Today, Tomorrow, weekday name, or "EEEE, MMM d" for later dates), then by programme within each date
- Time window: −1 to +6 days from now, max 500 results per query
- SQLite FTS4 MATCH for fast search (<100ms); falls back to LIKE if FTS returns empty
- Programme titles and channel names scroll with `basicMarquee` when they overflow
- Mobile: sticky date headers with expandable programme cards showing up to 3 airings (tap to expand all)
- TV: date headers with GlassPanel programme cards in TvLazyColumn

---

## EPG Management (Multi-Source)

Settings → Manage EPG Data. Add, edit, and delete XMLTV source URLs.

- Per-source label and timezone offset override (applies during parsing)
- Status indicator: green = ingested <24h, yellow = >24h, red = error, gray = disabled
- Download-ingest-delete pipeline: XML downloaded to temp, parsed into SQLite, file deleted immediately
- TV/fixed devices: stream directly from network to DB (zero disk I/O)
- Mobile: download to cache dir first, then ingest
- Parallel ingestion: sources download concurrently (3 on mobile, 2 on TV); ingestion into SQLite uses 2 parallel workers
- Per-source progress: shows download % and ingestion % with channel/programme counts
- Cancel button: running or queued EPG refreshes can be cancelled mid-operation
- Auto-refresh on startup and 24h periodic WorkManager background sync
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

**Exported:** all provider configs (name, URL, username, type, config JSON, per-provider settings), all EPG source URLs, per-provider favorites (item ID, name, category, content type), and global AppSettings (theme, UI scale, dev mode, EPG auto-refresh, cellular buffer multipliers).

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

**TV (D-pad remote):**
- **OK** = show/hide controls (never pauses playback)
- **Double-OK** = toggle stats overlay
- **Back** = exit player
- **D-pad Up/Down** = switch channel (Live TV only)
- **D-pad Left** = open category channel overlay (Live TV); if last-watched overlay is open, closes it instead
- **D-pad Right** = open last-watched channel overlay (Live TV); if category overlay is open, closes it instead
- **D-pad Left/Right** = seek −10s / +10s (VOD only, while controls visible)
- **KEYCODE_MEDIA_PLAY_PAUSE** = pause/resume (VOD only)
- **KEYCODE_MEDIA_REWIND** = seek −30s (VOD only)
- **KEYCODE_MEDIA_FAST_FORWARD** = seek +1 min (VOD only)

**Mobile:**
- **Single tap** = show/hide controls
- **Double-tap** = pause/resume (VOD only; no effect on Live TV)
- **Swipe up/down** = switch channel (Live TV only)
- **Swipe right** = open category channel overlay (Live TV)
- **Swipe left** = open last-watched channel overlay (Live TV)
- **FF / Rewind buttons** = +1 min / −30s seek (VOD only, shown in controls bar when `duration > 0`)

### Channel Overlays (Live TV)
Two side-panel overlays available during Live TV playback:
- **Category channels** — slides in from left edge. TV: D-pad Left. Mobile: swipe right.
- **Last watched** — slides in from right edge. TV: D-pad Right. Mobile: swipe left.

Overlays use semi-transparent `GlassPanel` (50% opacity). Background scrim is 30% black. Select a stream to switch channels; dismiss with Back or by opening the opposite panel. Overlays close automatically when a stream is selected.

---

## Virtual Categories

Appear alongside provider categories in the category list:

| Category | Content Types | Description |
|----------|---------------|-------------|
| **Continue Watching** | Movies, TV Shows | Items with 2–95% progress, most recent first |
| **Favorites** | All | Starred items, configurable max size (10–500) |
| **Last Watched** | All | Chronological history, configurable size (1–100) |
| **Recent Categories** | All | Recently browsed categories (max 20, deduplicated) |

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
| Active Provider | Shows current provider name, URL, and subscription info (Xtream: expiry, max connections, trial status) |
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
