# Fijerena — Feature Reference

A native Android media player for Xtream IPTV, Jellyfin, SMB shares, local files and remote M3U playlists, on phones/tablets (`:mobile`) and Android TV (`:tv`: NVIDIA Shield, Chromecast with Google TV, Sony Bravia). This file describes what the user sees; architecture and file maps are in [design.md](design.md), screens and back-stack rules in [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md).

---

## Sources

| Source | Live TV | Movies | TV Shows | Search | Progress sync | Auth |
|--------|---------|--------|----------|--------|---------------|------|
| **Xtream** | Yes | Yes | Yes | Local catalogue | No | Username/password |
| **Jellyfin** | No | Yes | Yes | Server-side | Yes | Username/password or Quick Connect |
| **SMB** (developer mode) | No | Yes | No | Filename | No | Optional |
| **Local** (developer mode) | With an M3U file | Yes | No | Filename | No | No |
| **Remote M3U** | Yes | Playlist entries that are video files | No | Title match | No | No |

Add Source offers SMB and Local only in developer mode: SMB can't play yet (no SMB data source) and Local has no folder picker. An existing SMB or Local source stays editable. The source type can't be changed once a source is saved.

Several sources can be configured; one is active per device. Switch it from the source picker on Home (with two or more sources) or Settings → Source & guide → Manage sources.

**Jellyfin Quick Connect:** when adding a Jellyfin source, choose **Use Quick Connect** instead of a password. The app shows a 6-digit code to approve in Jellyfin's web UI or another client; on approval it stores the access token. No password is stored.

---

## Home

Live TV / Movies / TV Shows: on TV they are on the navigation rail (only the types the active source has; Live TV not when the source has no channels; with a single type, Home opens it straight away on launch). Home's section tiles went 2026-10-09. TV home opens on the first Continue Watching card when there is one; with no row at all, on the rail. Under it, **Channels** (TV): the last channel watched ("Last watched"), then favourite channels, then recent ones, each once, at most 20, with "Now: <programme>" and its progress when the guide has the channel (refreshed each minute). OK opens the channel's preview; Up/Down in full screen then zap through Favorites for a favourite, Recent for the others, and Back returns to Home. With nothing to resume, Home opens on the first channel. Then **Favorite movies** and **Favorite shows** (TV), the profile's favourites by name; OK opens the film's details or the show's episodes. The TV backdrop is the blurred art of the card in focus (after a quarter of a second, so moving along a row doesn't flash), kept while focus is on the tiles or the header; with no card focused yet, the last-played poster. The header holds the app name and, on TV, the clock (system 12/24 h); then the source name (a picker with two or more sources) — on TV with the source's catalogue sync status: "Updating…" while a sync of it runs, "Update failed" (the picker gives the reason, raw detail in developer mode), else "Updated <how long ago>"; nothing for a source that never synced (Jellyfin, M3U). With one source the TV pill shows only that status and takes no focus. Then **Search the guide** (when the guide index is ready); Search, the profile and Settings are on the navigation rail. The TV Guide grid opens from Search the guide, a category's header and the player's Guide button.

**Phone: no Home.** A bottom bar has a tab per section the source has — Live TV, Movies, TV Shows (Live TV only when the source has channels), then Settings. Each tab opens on its Recent list (in progress first), Live TV docked on the last channel; the bar stays on every screen inside a tab and in Settings (not the player, Add / Edit Source or a profile's page), so each keeps its place when you switch tabs — a film's details included — and tapping the tab you're on goes back to its start from anywhere in it. The app opens on the last tab used (per profile). A tab's header: the section's name, under it one line with the source and its sync status ("● jellyxtream · Updated 2 hours ago ▾" — tap to switch source; with one source, tap "Update failed" for the reason, which the picker also shows), then Search the guide (Live TV, when the index is ready), Search (everything, opened on the tab's section with an "All Content" chip one tap away) and the profile avatar, whose sheet switches profile. Back on a tab leaves the app.

**TV navigation rail:** faint icons down the left edge of every TV screen — your profile, Home, Live TV, Movies, TV Shows (the ones the source has), Search, Settings — lit on where you are. Press Left from the leftmost item of any screen and it slides out with names; pick a section and it opens where you left it (a film's details included), pick the one you're in to go back to its start; Right comes back to where you were. Back at a section's start opens the rail, Back again goes Home. Search there searches the section you're in. Not on the player or over the Live TV preview. The first time, it slides out on its own with "Press Left for sections". See [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md) → Back-Stack Rules.

**Continue Watching** (Movies and TV Shows together) shows one card per show: mid-watch, its episode resumes; once an episode is finished, the card offers the next one ("Up next", the next season after a season's last episode), fetching the show's episode list from the source if this device never stored it (a show watched on another device). After a show's last episode it leaves the shelf. Movies show only while mid-watch.

---

## Live TV

Channels by the source's categories, plus the lists under [Virtual categories](#virtual-categories). Browsing Live TV always has a channel playing beside the list, on the same `StreamingPlaybackService` engine used for full screen, so going full screen and back never restarts the stream.

- **TV:** Home → Live TV opens the preview on the last channel watched (no last channel: the browse screen). In browse, OK on a channel opens the preview on it. In the preview, moving through the list or across its tabs never changes the channel: OK on another row plays it ("Tuning · <channel>" until it plays); OK on the row playing goes full screen. Below the video: channel name, category, Now / Next with progress when the guide has them, and the hint "OK Play · OK again Full screen · Hold OK Options". The channel panel beside it has tabs for the channel's category, Recent and Favourites (the list Up/Down zap through in full screen) and a Refresh button; Left/Right switch tabs from anywhere in the list, not only on the tab row; Left on the first tab stays put — Back leaves the preview. Back goes full screen → preview → browse (on the channel that was playing) → Home. Leaving the app (Home button, screensaver, HDMI input switch) pauses the preview and stops it after 30 s; coming back plays the channel again at the live edge, unless the viewer had paused it in full screen.
- **Mobile:** tapping a channel docks it in a mini-player above the list and plays it; tapping the dock goes full screen. On entry the dock starts on the channel asked for (from Search or a guide) or the last one watched. Back goes full screen → dock → bare list → out. While a channel is docked the screen rotates with the device.

Search, the TV Guide and Search the guide open Live TV on the chosen channel; Back returns to them.

## Movies

Details screen: plot, cast, director, genre, rating, year, duration, video/audio tech info, related titles and the trailer when there is one. **Play**, or **Resume from …** with **Start from Beginning** when progress is saved (2–95 %). Favourite and Watched toggles sit beside it; marking watched hides the resume bar at once, unmarking re-reads it.

## TV Shows

Season tabs over the episode list, opening on the season of the episode to resume, else the first season with an unwatched episode. Episode thumbnails, per-episode metadata, resume bars; series metadata falls back to the season's.

- **Episode list caching (Xtream):** a show's episode list is stored on the device and reused until the source's catalogue shows the series changed (its `last_modified`, which panels bump when episodes are added), the viewer refreshes the show, or 30 days pass. Stored TMDB synopses are kept across those refetches, and TMDB is asked only for seasons still missing one.
- **TMDB synopses:** per-episode synopses come from TMDB when the source has none. The TMDB id comes from the series info, else from the series listing. The player's info panel shows the episode's own synopsis only — never the series' — and an episode without one has it fetched from TMDB when it starts playing.
- **Next episode:** from 80 % of an episode that has a next one (into the next season if needed), the player controls show a Next episode button (TV and mobile).
- **Play next episode automatically** (per profile, switched on the profile's page in Settings → Profiles, off by default; Xtream TV shows only — not Jellyfin, which keeps its own play state): near the end of an episode that has a next one — the one the Next episode button plays — a small translucent "Up next" card appears in the top-right corner over the still-playing video (TV: inside the safe margins, under the clock while the controls are up; mobile: below the status bar and cutout), one line like "Up next · S1:E2 · 45 s" with Play now and Cancel beside it. Under it a second panel of the same width gives the next episode's own title and up to 3 lines of synopsis on TV (2 on mobile), whichever the source provided (none, no panel); on TV it shows only while focus is on the card, on mobile whenever the card shows. It appears when the time left is at most 90 s or 15 % of the episode's length, whichever is shorter (`CinemaAnimation.upNextLeadMs`, `UP_NEXT_LEAD_FRACTION`: 90 s for a 22-minute episode, 45 s for a 5-minute one), or straight away when playback starts or is moved inside that window; with an unknown duration it never appears early. The countdown is playback time left, so it stops with the video (paused, buffering, app in the background). **Play now** skips to the next episode at once, as Next does. **Cancel** — or Back — hides the card for this episode (it doesn't come back, even after seeking) and playback carries on; that episode then ends back on the episode list. If the episode ends with the card not cancelled, the next one plays at once (only once the app is in the foreground; a phone in picture-in-picture counts, and the card is hidden there). TV: the card takes focus on "Play now" as it appears; with the controls up both stay usable, and Down from the player reaches the card when it isn't focused. Off, with no next episode, or on Jellyfin, the player leaves at the end; movies and Live TV are unaffected.
- **Mark watched:** TV — long-press OK on an episode card. Mobile — tap the watched badge (filled or outline).

## Titles

Providers bake tags into names ("EN - Breaking Bad", "NP:Kantipur", "4K-NF - …", "Breaking Bad (US)"). Lists, cards, related titles, Continue Watching and the player show the clean title with the tag as a small badge beside it (`parseDisplayTitle`, `BadgedTitle`). An episode shows its own name — "Whatever You Do" from "4K-A+ - Silo (2023) (US) - S03E04 - Whatever You Do" — and nothing when the provider's title only repeats the show and the number (`episodeOwnTitle`; the player uses `playerEpisodeName`, which also takes the last " - " segment of a title without a number). Stats for Nerds shows the raw names as sent.

---

## TV Guide

Live TV only, when the source has a guide (the indexed XMLTV guide, or the source's own EPG). Opened from a category's TV Guide button (that category), Search the guide's TV Guide button (Recent) or, on TV, the player's Guide button (the list being watched, on the playing channel's row). Recent / Favourites guides list those channels; category separators (`#### … ####`) are not channels. On TV the grid opens with the current time 30 minutes from its left edge; a programme too short for its title shows its start time; a channel with nothing that day says "No listings"; the channel column has the logo. A source with no channels gets a plain "No channels to show a guide for" with Back, not an error.

- The header says how many channels have listings, whether they came from the XMLTV guide or the source's own EPG, and when the guide was updated. A guide with nothing for its channels says "No listings" and why; when now is past the day's last listing it says "Listings end at …".
- A fixed channel column beside a time grid; each programme is as wide as it lasts and starts under its time on the ruler. A line marks now, past programmes are dimmed, the one on air is highlighted. The grid opens scrolled to now. Listings load a page of channels at a time.
- **TV:** Previous day / Now / Next day, Search and Refresh in the header. OK on a channel opens its preview; OK on a programme opens its details (title, channel, day and time, description) with Watch channel. Long-press OK or Menu on a channel or programme opens the channel's actions (favourite; Remove from Recent in the Recent guide). Focus rules: [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md#tv-focus).
- **Mobile:** date tabs (today and the next six days) with a Now chip. Tapping a channel docks it; tapping a programme opens its details sheet with Watch channel.
- Search opens "Search the guide" limited to the guide's channels.

## Search

Search across Live TV, Movies and TV Shows ("ALL", from Home) or one content type (from its category screen). At least 2 characters. Results are grouped by content type under collapsible headers, categories and items within each group; recent searches show before typing (per profile). A Live TV result opens Live TV on that channel; a movie opens its details, a show its episodes.

- **Xtream:** each word matches as a prefix against the synced catalogue on the device, no network call; up to 200 results per content type. Matches in categories hidden by the content filters are counted ("N hidden" on the group).
- **Jellyfin:** the server's search (movies and series).
- **Local / Remote M3U:** title match against the playlist.
- **SMB:** filename match against items already loaded.

## Search the guide (EPG Browser)

Programme-title search across the indexed XMLTV guide, from Home's book icon (when the index is ready) or a TV Guide's Search — then with an "In <category> only" toggle (on by default) that keeps results on that guide's channels. Airings on channels the profile can't open — excluded ones, or in a category it hides — are left out.

- One search, by programme title (there is no channel-name mode). A **Matched only** toggle keeps channels the active source has.
- Results grouped by date (Today, Tomorrow, weekday, then "EEEE, MMM d"), then by programme. Every programme that hasn't ended, with no upper limit; at most 500 results per query.
- ON AIR / SOON badges; Watch opens the channel (asking first when the show airs later); Add to calendar.
- The header shows how fresh the guide is (stale sources flagged; "Guide off: turn it on in Guide sources" when the source's guide sources are all switched off, "No guide sources configured" only when it has none) and, for a source with live channels, **TV Guide** (the grid for Recent; Back returns to the search), a button to refresh stale guide sources, and **Guide sources** (the source in use's).
- Search uses SQLite FTS4 (a raw query, then a sanitised AND-style retry). While a refresh runs, search keeps working on the previous guide: the new guide and its index are switched in together. When the index can't be used (a low-storage refresh writing straight into the guide, or an interrupted rebuild), search falls back to a slower title-only scan, flagged on screen as possibly incomplete and rerun in full once the index is ready; if even that times out, it says why ("The guide is updating…" or "The search index is being rebuilt…") and reruns by itself.
- Mobile: sticky date headers; a programme card shows up to 3 airings, the rest behind "N more airings". TV: date headers and glass programme cards.

## Guide Sources (EPG management)

Each source has its own XMLTV guide sources: Edit Source → Guide sources, Sources → a source's Guide button, or Search the guide's Guide sources button (the source in use). Add, edit and delete XMLTV URLs; turn each one on or off with the switch on its card (a guide source that is off is kept with its data but not used, searched or refreshed; for a source's own guide, `<host> (Bulk)`, the switch is the same setting as Provides a guide in Edit Source, set as the viewer's choice); refresh one, the stale or the failed ones; cancel a running or queued refresh.

- Per guide source: label and timezone offset (applied while parsing); its auto-refresh ("Refreshes daily", "Auto-refresh off"); status dot — green fresh, yellow older than the refresh interval, red error, grey disabled; download % and ingest % with channel/programme counts.
- Download → parse into SQLite → delete: TV and other fixed devices stream from the network straight into the database; phones download to the cache directory first. Up to 3 downloads at once on mobile, 2 on TV; 2 parallel ingest workers.
- Change detection: a source unchanged since the last refresh (`304`, or the same payload hash) skips download and ingest and reads **"Unchanged"**; a hash match is overridden once a day so the guide window keeps moving. Truncated downloads are caught against `Content-Length` and retried.
- Retries: a refresh task retries up to 5 times (1, 2, 4, 8, 16 min); background WorkManager runs back off linearly by 10 min.
- Programmes that ended more than 12 hours ago are skipped on import. Deleting a guide source removes its channels and programmes from the index.
- **Guide auto-refresh:** each guide source has its own interval — Off, every 6 hours, every 12 hours, daily (new ones) or weekly — shown on its row ("Off: not used, not refreshed" while the guide source is switched off) and changed with the row's **Auto-refresh** button (TV: a picker in place of the list, opening on the current value; phone: a picker dialog). On upgrade every guide source took the old device-wide setting, so nothing changed; an interval the picker doesn't offer (4, 8 or 48 hours from that setting) stays, shown as an extra checked option, until another is picked. One background job (WorkManager, run as a foreground service) runs at the shortest interval and refreshes the active source's guide sources that are due; there is no time of day and no device-wide setting (a guide source synced from an older app version, without an interval of its own, uses the old one). **Guide data maintenance** (Settings): Cleanup (stray files not tied to a source), Purge (programmes older than 2 days), Clear All Data (drops and recreates the index at once, keeping the guide source URLs).

Pipeline, index and search internals: [epg_guide.md](epg_guide.md), [EPG_INDEX_STORAGE.md](EPG_INDEX_STORAGE.md).

---

## Player

HLS, DASH, MPEG-TS, MP4, MKV, WebM and other containers through Media3/ExoPlayer, with the FFmpeg extension for AC3/EAC3/DTS/TrueHD audio. Jellyfin is asked how to play each item (direct play, direct stream, or an HLS transcode for codecs the device can't decode) — details in [design.md](design.md#jellyfin-playback).

### TV controls (D-pad)

- **OK** opens the controls (never pauses): one row of icon buttons; the focused one shows its name. Live: Channels, Guide, Favorite, Subtitles, Audio, Quality (each when available), More → Stats. VOD: Play/Pause in the centre and the progress bar, then Chapters, Favorite, Subtitles, Audio, Quality, Next episode, More → Stats. They hide 15 s after the last key. Focus details: [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md#live-tv-preview--dock-back-stack) (the OSD).
- **Up/Down** (Live TV) zap through the channel panel's list, with the controls up or not.
- **Left/Right** (Live TV, controls hidden) open the channel panel: the preview's tabs and rows over the video; inside it Left/Right switch tabs (Recent ↔ Favourites in one press), OK on a row tunes it, Back closes it.
- **Left/Right** (VOD, controls hidden) and **Rewind / Fast-forward** move a scrub cursor: 10 s per press, speeding up to about 1, 3, then 10 min per second while held; OK commits, Back cancels.
- **Play/Pause** key pauses and resumes VOD.
- **Back** hides the stats and controls when they are up, otherwise leaves the player (in full-screen Live TV: back to the preview).
- The live banner (LIVE · channel, Now with progress, Next) shows on a zap for 3 s and with the controls. The resolution/codec line is developer mode only.

### Mobile controls

- **Tap** shows/hides the controls (5 s auto-hide): title and resolution/codec at the top; −1 min / play-pause / +5 min (VOD with a known duration); a seek slider (VOD); Audio, Subtitles, Quality, Favorite, Stats, Info and Next episode.
- **Double-tap** the left or right 40 % of the screen seeks 10 s back or forward (VOD).
- **Swipe up/down** zaps (Live TV), with a 3 s channel toast.
- **Swipe right** opens the category's channels from the left edge, **swipe left** the Recent channels from the right edge (Live TV); picking a channel tunes it and closes the panel.
- The player turns the phone to landscape (sensor) and puts it back on exit; the app is otherwise portrait. Picture-in-picture when leaving the app during playback.

### Shared

- **In-player EPG (Live TV):** current programme with time range and progress, and the next one, fetched on start and on every zap; nothing shown without guide data.
- **Track selection:** audio, subtitle and video-quality pickers. The chosen audio and subtitle tracks are saved per item; a new episode with no choice of its own takes the last choice made in the same series.
- **Resume:** VOD position is saved every 10 s while playing, on pause and buffering changes, on a track pick and on exit; a title resumes when it is 2–95 % watched (per-source Auto-Resume). Live TV positions are not saved.
- **Slow connection:** a banner over the video — "Connection too slow for this video (needs ~60 Mbps, getting ~20 Mbps)" — when playback stops to buffer and the connection delivers less than the stream needs; without numbers when the stream doesn't state its bitrate (on the second stop within 2 minutes). Playback goes on; the banner goes after a minute without a stop.
- **Stalled connection:** buffering with no data at all for a minute stops with "Connection lost: no data for a minute…" and Retry, instead of retrying for 13 minutes.
- **Formats the device can't play:** a video with no decoder on the device (Dolby Vision profile 5 on most devices) or beyond it (8K on a 4K device) stops at once with "Video codec not supported on this device: <codec>", without retrying.
- **Shared logins (Xtream):** a source with extra logins (Edit Source → Logins) asks the panel, in parallel and for at most 3 s, how many streams each login has open and plays on the first free one: the one it used last, then main first (about half a second with bears, less on a reused connection; an answer is reused for 5 s, so Back then Play doesn't ask again). A channel change, or a new title while one is playing, keeps the login this device is playing on without asking: it replaces its own stream, so zapping is as fast as with one login. A source with only its main login asks nothing. When the panel refuses the stream as busy (HTTP 456/458/460/511), the same stream plays at once on another free login, at the same position, and the refused login is skipped on this device for 6 minutes. With none left the account-busy wait runs as before (retry every 20 s for up to 6 minutes), and its message says all N logins are in use.
- **Buffering** adapts to Wi-Fi or cellular and live or VOD at runtime without restarting the player; profiles in [design.md](design.md#buffer-strategy).

### Stats for Nerds

TV: More → Stats; a non-focusable panel in the top-right corner, so the player keeps the keys; double-OK or Back hides it. Mobile: the Stats button; closed with its X. Updated every second.

- **NAME:** the provider's raw name (and the series' raw name for an episode); for a source with extra logins, which one this stream plays on ("Login 2 of 3")
- **VIDEO:** codec, resolution, frame rate, bitrate
- **AUDIO:** codec, sample rate, channels, bitrate
- **NETWORK:** speed, measured bandwidth, buffer health, buffered position, rebuffer count/duration, ABR quality switches
- **PLAYBACK:** position, duration
- **PERFORMANCE:** dropped frames (<0.5 % green, <2 % yellow, ≥2 % red), drop rate
- **APP:** heap used/max (<60 % green, <85 % yellow, else red), GC count and time, UI frames skipped
- **STREAM:** type (Live/VOD), retries, stream health (live: healthy / unstable / degraded), uptime, URL
- **DEVICE:** model, API level
- **Footer:** build time and git hash, model and detected device type

---

## Watch State

Playback position and watched status are stored in SQLite (`watch_state`) for good; nothing is truncated. The Recent row size bounds only how many cards show. Applies to Xtream, SMB, Local and Remote M3U; Jellyfin keeps this state on the server.

- **Mark watched / unwatched** from movie details, TV episode cards (long-press OK), mobile episode badges, row actions (TV: long-press OK / Menu) and search results. Marking never adds the item to Recent. Completion is sticky — a brief accidental rewatch can't clear it; only "mark unwatched" does.
- **Cross-variant dedup (TMDB):** providers carry the same title several times (language, quality, source). Watching one variant marks all of them: movies by shared `tmdbId`; TV episodes by the series' shared TMDB id, then matched on (season, episode), since each variant is a separate series and episode-level ids are almost never filled. Unmarking spreads the same way. Needs the cached catalogue, so Xtream only.

## Virtual Categories

Listed before the source's categories:

| Category | Shown | Contents |
|----------|-------|----------|
| **Recent** | Always | Everything watched, resumable items first, then the rest, newest first; one card per series. Live: added after the watch delay (10 s by default); VOD: after 2 %. Per-source size, 1–100 (default 25) |
| **Favorites** | Always | Starred items, newest first, no limit |
| **Favorite Categories** | When there are any | Favourited categories |
| **Recent Categories** | When there are any | Recently browsed categories, up to 20, deduplicated |

Recent and Favorites live in `xtream_v2.db` (`watch_state`, `favorite_state`); Recent Categories in per-source SharedPreferences. All are per profile. Storage: [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md).

---

## Profiles

"Who's watching?" picker at launch on TV when there is more than one profile, and from TV Home's avatar; on the phone the tab header's avatar opens a sheet with the profiles (the one in use checked) and Settings. A profile's page in Settings → Profiles also has **Switch to this profile** (not shown for the profile in use). Switching lands on Home with that profile on TV, on that profile's last tab on the phone.

**A profile's page** (Settings → Profiles → a profile; TV: in place of the group's rows, Left or Back leaves it unsaved; mobile: its own screen): name, colour, the profile's own **Developer mode** and **Play next episode automatically**, **Content filters ›**, **Switch to this profile**, Save / Cancel, and **Delete profile** last (not for the profile in use or the last one). Everything on it is that profile's, whichever profile it is. **Content filters ›** (shown when the source in use is Xtream) opens the filter editor for the source in use and for this profile, titled with the source's name; nothing else of the source is shown or editable there, and the editor's own Save stores the filters at once. A profile not in use only has its filters stored — they apply when it becomes the one in use; the catalogue's hidden categories always follow the profile in use. Adding a profile asks only for a name and a colour (its filters are copied from the profile in use; developer mode starts off). Each profile has its own favourites, watch history, search history, content filters per source, dev-mode switch, "Play next episode automatically" switch and Jellyfin login; sources, guide sources and other settings are shared. Which profile is in use is per device. Each profile also remembers the source it last picked (or added), on every device: switching to a profile moves the device to that source. A profile that hasn't picked one yet, or whose source was deleted, stays on the device's current source. Another device's pick never moves a device that is already on that profile — it applies at the next switch (`docs/plans/archive/20261002_profile-last-provider-plan.md`). Switching takes a fraction of a second: only category rows carry the filter flag, and streams and series follow their category at query time (`docs/plans/archive/20261001_fast-profile-switch-plan.md`).

## Live Sync

Settings → Live sync. Keeps profiles, sources, guide sources, favourites, watch progress, content filters and selected settings the same on every device of a group, through a sync server the user runs (Cloudflare Worker or self-hosted Docker image, `server/`). Everything is encrypted on the device; the server sees only keys and ciphertext. Off on a device until it is set up there. While something plays, its watch progress is sent at most once a minute (and at once on pause, stop or finish); every other change goes within a few seconds.

- **Start a sync group:** enter the server address (checked first; a setup secret if the server asks for one).
- **Add a device:** a phone scans the invite QR code a member shows; a TV, which can't scan, shows a code for a phone of the group to scan. Codes work once, for 10 minutes.
- **Manage:** last sync, Sync now, the devices list with Remove, Leave the sync group (local data stays).
- Changes reach other open devices within seconds; a closed app catches up when opened.
- **Now playing:** with **Share what's playing with my sync group** on (per device, off by default, never synced), a device's row in every other member's devices list shows what it is playing — "▶ Playing · Malcolm X · Kid", "▶ Live · BBC World News — Newsday · Kid" (programme only when the channel has EPG), or "⏸ Paused · …". Updates within seconds of a change, refreshed every 60 s while playing or paused; a device silent for 3 minutes shows as idle. Sealed like every record.
- **Remote Stop (phone app only):** a phone's devices list has **Stop** on another device's playing or paused row; after a confirmation it shows "Stopping…" until that device's line clears (or "Couldn't reach <device>" after ~90 s). The device obeys only for the playback it has on right now (a per-playback session id, never a clock), saves the watch position as Back does, closes the player for Home and shows "Playback stopped from <device>". The TV app has no Stop button. Design: `docs/plans/archive/20261001_live-sync-now-playing-plan.md`.
- Resilient by design: a record a device can't apply waits and is retried (never blocks the rest); a record the server can't accept is rejected on its own; records whose sealed timestamp or deletion flag were altered are dropped; reconnects back off; an unreadable sync link on a device resets it to unlinked rather than crashing.
- Not synced: which profile and source a device is using (each profile's last picked source is synced, and applied when a device switches to that profile), UI scale, and Jellyfin favourites and history (Jellyfin keeps those itself).

Design and protocol: `docs/plans/archive/20260929_live-sync-plan.md`.

---

## Settings

Seven groups, in this order, on both platforms. **TV:** two panes — the groups in a rail (about 30 % of the width) and the selected group's rows beside it; the header shows the active profile and source. Choice settings open a one-column picker in the pane, starting on the current value. **Mobile:** one scrolling list with a header per group; pickers open as dialogs. Each row says what it applies to: this device, this profile, this source, or the sync group.

| Group | Rows |
|-------|------|
| **Profiles** | Profiles (which one this device uses) and Add profile; each profile opens its page: name, colour, its own Developer mode and Play next episode automatically, its content filters for the source in use, Switch to this profile, Delete |
| **Source & guide** | Manage sources, its value the source in use with its URL and subscription (Xtream: expiry, max connections, trial) — opens Sources: use, add, edit, copy to another source, delete, guide sources (each guide source's auto-refresh is set there) |
| **Playback** | Count as watched after: 5 / 10 / 15 / 30 / 60 / 120 s (default 10; mobile also takes a custom 5–120 s); mobile: a pointer to the per-source playback settings |
| **Display** | Theme; Look and feel; Text & grid size (TV only: 40 / 60 / 80 / 100 %, default 80 %); Language |
| **Live sync** | Opens Live sync |
| **Backup & storage** | Export / Import settings; Quick Import from Downloads (dev mode); Shrink Database; Guide data maintenance |
| **About & advanced** | Version, build hash and time; Device info; Diagnostics (when the profile in use has developer mode on) |

- **Device info** (everyone): facts about the app and the device, read from the device itself (no network call), refreshed on Refresh — App (version, build, debug or release, install and update time, source type in use, number of sources and profiles), Device (model, codename, Android and API level, security patch, processor architectures, TV or phone, detected device type), Memory (RAM free of total, low-memory state, the app's memory and its limit), Storage (internal storage free of total, each database with its WAL, image cache, caches, app files), Display (current mode, density, supported modes, HDR types), Video decoders (each AVC, HEVC, AV1, VP9 and Dolby Vision decoder: hardware or software, largest size and frame rate, HDR and Dolby Vision profiles), Audio output (formats passed through to the receiver — AC3, EAC3, EAC3-JOC, DTS, DTS-HD, TrueHD — as the player sees them), Network (connection type, internet working, metered, VPN on, Private DNS (off, automatic or strict, never the server's name), Wi-Fi link speed and band on Android 12+, estimated download bandwidth), Power and background (exempt from battery optimisation, standby bucket, background restricted, power source, battery present, charging as Doze sees it — a battery present and power plugged in, so a Shield on mains never counts — deep and light Doze now — light Doze readable from Android 13 — battery saver), Sync (each source's last catalogue sync with its time, duration and result; the last guide refresh; the state and next run of the background catalogue and guide jobs). Share on mobile sends it as text, logins masked. Nothing secret is shown: no passwords, usernames, server addresses, IP addresses or Wi-Fi names.
- **Shrink Database:** purges orphaned catalogue rows, cache files, credential files and guide sources of deleted sources, and compacts `xtream_v2.db` with WAL truncation. Never touches favourites or watch history. Developer mode adds the last run's time, rows and bytes.

### Edit Source (per-source settings)

The connection form (type shown read-only), then:

| Section | Settings |
|---------|----------|
| Logins (Xtream, editing only) | The main login and any extra logins on the same panel, each with what the panel says (expiry date, "Not active", "Password needed" for one this device has no password for). Add login checks the new one signs in and has the same stream ids as the main login (the first live and VOD category), else refuses ("on a different server", "didn't accept this username and password", a username already there). Make main swaps a login with the main one; Remove on the main login promotes the next; the last login can't be removed. Extra logins and their passwords reach linked devices through live sync, and Copy To with the connection copies them. Update every device first: an older version that saves the source's settings drops them. Playback shares them: see Player → Shared logins |
| Behaviour | Auto-Resume (on); Recent row size (chosen from 5–100, default 25; on TV a picker, a stored value outside the list kept and marked custom); Xtream: stream output format (m3u8 / ts), playlist type (m3u_plus / simple) — on TV rows that open Settings' picker; Enable Caching (on) |
| Guide (sources with live channels) | Xtream: Provides a guide — whether the source's own guide (`xmltv.php`) is added as a guide source. Detected — on, turned off when that guide comes back empty and has never had channels (an empty download of a guide that had channels is only that guide source's error, "came back empty": its old guide stays and the next refresh tries again) — until the viewer sets it; off disables that guide source (kept, with its stats), on enables it again. Guide sources ›, its value the count and last refresh: this source's guide sources |
| Library data | Item counts per content type; Xtream: last catalogue sync — "Last Sync: Finished at … • Took …" and what it changed ("No changes since last sync", or "N added • N updated • N removed"), hidden after a failed sync, which shows "Catalog sync failed" with the reason; Sync Data Now |
| Danger zone | Clear all favourites; clear all progress; clear the cached library (all, or per content type) |

A failed catalogue download (network, timeout, refused login) deletes nothing; a network failure is retried, a refused login isn't.

### Export / Import

Backup & storage → Export / Import, through the system file picker.

- **Exported:** every source (name, URL, username, type, config, per-source settings with the active profile's content filters, active flag), every guide source (with its auto-refresh interval), per-source favourites, favourite categories and watch state, and global settings (theme, UI scale, the active profile's dev mode). The device-wide guide auto-refresh is no longer exported; importing an older file that has it turned off sets that file's guide sources to Off.
- **Not exported:** passwords (EncryptedSharedPreferences), caches, guide programme data.
- **Import:** a "Select What to Import" dialog — General Settings, Sources, Guide sources, Favorites (favourites, favourite categories and watch state). Only the checked sections are imported. When an imported source's name matches an existing one: **Overwrite** (update URL, username, type, config and settings in place), **Duplicate** (add with an `(imported)` suffix) or **Skip**. Guide sources merge by URL and favourites by item id; duplicates are skipped. Passwords have to be entered again.

---

## Themes, Look and Feel, Language

- **Themes:** Deep Night (default, electric blue), AMOLED Black (near-white on black), Amethyst (purple) and Teal; all dark, switched without a restart. Palettes: [design.md](design.md#palettes).
- **Look and feel** (`UiStyle`), independent of the theme: **Material** (default), **Cupertino**, **Roku** (no focus zoom, outline only) and **BRAVIA** — shapes, type weight, grid spacing, focus effect, dialog style and icon style.
- **Language:** English (default), French and Malagasy (`AppSettings.language`). Untranslated strings fall back to English.

## Developer Mode

Per profile, switched on the profile's page in Settings → Profiles — any profile's, not only the one in use (off for a new profile). What it adds follows the profile in use:

- SMB and Local in Add Source
- Payload sizes beside a category's item count (TV)
- Programme and channel counts in Search the guide's header, and the guide source's name on each airing
- The resolution/codec line in the TV player (mobile always shows it)
- The source type next to its name on Home
- Quick Import from Downloads, and the last Shrink Database run's figures
- **Diagnostics:** the on-device crash log and Android's record of why the app last closed (ANR, crash, low-memory kill), newest first; Share on mobile. Logins are masked (`***`) in the crash log, the system log and the shared text: Xtream user/password in stream paths, `password`/`token`-style parameters, `user:password@` in addresses. See `docs/RUN_GUIDE.md` → Crash log and Diagnostics

## Resilience

- **Error screens recover on their own:** a TV or mobile error shown while the device was offline (default network without `INTERNET` + `VALIDATED`, or lost) retries once when the network comes back, so a TV that starts before its Wi-Fi is up after wake recovers without a key press. An error shown while online is never retried by itself, except an account in use elsewhere (below). TV error states land focus on Retry.
- **Account in use on another device:** when the provider refuses a stream because the account's connections are all in use (HTTP 456, 458, 460 or 511), the player says so within seconds instead of buffering through its retries, and tries again every 20 seconds for up to 6 minutes — some providers (bears: 460) keep a stopped stream counted for about 5 minutes when the next one comes from a different internet address (a phone on mobile data, a device routed through a VPN), so switching devices is refused for that long; devices on the same address take over from each other at once. The message stays up through the attempts and playback starts by itself, where it would have resumed; Retry and Back work as usual. After 6 minutes it stops and asks to stop playback on the other device, then press Retry.
- **Crash-loop safe mode:** when three launches within 10 minutes each end within 30 s of starting, the next launch opens a safe-mode screen instead of Home and skips the start-up work that could be the cause — EPG initialisation and auto-refresh, catalogue sync, live sync, the now-playing publisher, the start-up migrations, and the nav host's source lookups and orphan sweep. **Continue** restarts the app normally; **Clear caches** (confirmed) removes the EPG index, every source's downloaded catalogue and the poster cache, keeping sources, profiles, favourites and watch history; **Show diagnostics** opens Diagnostics. See `docs/RUN_GUIDE.md` → Crash-loop safe mode.
- **Data from a newer version:** if this device's sources were saved by a newer version of Fijerena (an older build installed over a newer one, or a newer backup restored), the app opens a screen saying so instead of crashing on every start. **Close** leaves everything as it is (install the newer version to keep using it); **Reset sources** (confirmed) sets the sources database aside as a backup and restarts with no sources, keeping favourites and watch history on the device.
