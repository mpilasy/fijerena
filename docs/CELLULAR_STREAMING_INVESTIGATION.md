# Cellular Streaming Investigation (2026-02-21)

## Problem
Live TV streams are choppy/rebuffering on cellular (T-Mobile 5G). The same streams play perfectly in iMPlayer v2.4.3 on the same phone, same network, same credentials.

## Phone & Network
- **Device:** OnePlus CPH2655, Android 16, API 36
- **Carrier:** T-Mobile USA (NAT64/DNS64, IPv6-only with CLAT for IPv4)
- **Actual bandwidth:** 399 Mbps down (Speedtest to Triplebit)
- **CDN bandwidth:** ~1.4-1.5 Mbps per connection (rate-limited by Xtream CDN)
- **CDN infrastructure:** 302 redirects to Cloudflare edge nodes

## Stream Characteristics
- **Format:** HLS (.m3u8) — single quality variant (no adaptive bitrate options)
- **Resolution:** 1920x1080 (FHD), 60 FPS
- **Codec:** AVC (H.264), hardware decoded via `c2.qti.avc.decoder`
- **Audio:** MP4A-LATM, Stereo, 24kHz
- **Stream bitrate:** ~1.2-1.4 Mbps (close to CDN throttle limit)
- **Margin:** ~100-200 Kbps headroom between stream bitrate and download speed

## iMPlayer Configuration (from screenshots)
- **Media3 version:** 1.7.1 (confirmed from User-Agent string)
- **User-Agent:** `iMPlayer/2.3.2 (Linux;Android 16) AndroidXMedia3/1.7.1`
- **Buffer size:** "Not set" (ExoPlayer defaults: 50s min/max, 2.5s playback, 5s rebuffer)
- **Real time live streams:** OFF ("Will reduce loading time and delay of the stream")
- **Use audio software decoder:** OFF (hardware audio)
- **Auto stream reconnect:** OFF
- **Timeshift Stream Delay:** 5 Seconds
- **Timeshift Time Limit:** 1 Hour
- **Auto Timeshift:** OFF
- **Catch-up source:** Xtream Codes (XC)
- **HTTP stack:** Play Services Cronet (confirmed `play-services-cronet.properties` in APK)
- **Data sources bundled:** Both `CronetDataSource` and `DefaultHttpDataSource` present in DEX
- **Native Cronet:** Uses GMS Cronet (`com.google.android.gms.net.GmsCoreCronetProvider`), NOT embedded
- **Cronet on device:** v145.0.7582.0 via `split_CronetDynamite_installtime.apk`

## What We Tried (all failed to fix choppy playback)

### HTTP Stack Changes
| Approach | Result |
|----------|--------|
| CronetDataSource (original, pre-session) | Choppy |
| OkHttpDataSource (Media3 library) | Choppy |
| DefaultHttpDataSource (HttpURLConnection) | Choppy |
| Dedicated OkHttpClient (maxRequestsPerHost=16, HTTP/1.1, pool=8) | Choppy |
| CronetDataSource restored with QUIC+HTTP/2 enabled | Choppy (but longer between pauses) |

### User-Agent Strings Tested
All gave ~1.4 Mbps via curl from phone:
- `VLC/3.0.21 LibVLC/3.0.21`
- `iMPlayer/2.3.2 (Linux;Android 16) AndroidXMedia3/1.7.1`
- TiviMate UA
- Smarters UA
- ExoPlayer default UA
- No User-Agent header
- `MediaPlayer/1.0 (Linux; Android)`

### Stream Format Changes
| Format | Result |
|--------|--------|
| `.m3u8` (HLS) | Choppy — HLS playlist overhead eats into bandwidth margin |
| `.ts` (progressive MPEG-TS) | Choppy — no improvement despite eliminating playlist overhead |

### Buffer Configuration Changes
| Config | Result |
|--------|--------|
| Cellular live: 50s/50s/2.5s/5s (ExoPlayer defaults) | Choppy |
| Cellular live: 15s playback / 15s rebuffer threshold | Still choppy — buffer never grows past ~5s |
| Stock ExoPlayer (no custom LoadControl at all) | Choppy |
| Custom BandwidthMeter with 50 Mbps initial estimate | Misleading stats, still choppy |

### Other Approaches
| Approach | Result |
|----------|--------|
| MediaItem.LiveConfiguration (30s target offset, 1.0x speed lock) | No improvement |
| Remove custom TrackSelector (stock defaults) | No improvement |
| Cap maxVideoBitrate to 800 Kbps on cellular | Ignored — only one quality variant exists |
| Media3 upgrade 1.5.1 → 1.7.1 | No visible improvement alone |

## Key Observations
1. **Buffer never exceeds ~5s** — download speed ≈ stream bitrate, no headroom to accumulate buffer
2. **CDN throttles per-connection to ~1.5 Mbps** — confirmed via curl from phone; Cloudflare speed test gives 80+ Mbps proving the bottleneck is CDN-side
3. **Single quality HLS** — Xtream server only offers one 1080p variant, so adaptive bitrate selection is irrelevant
4. **No Android policy differences** — checked netpolicy, appops, battery restrictions; both apps treated equally
5. **Hardware decode is fine** — `c2.qti.avc.decoder` shows steady 60fps input/output/render with 0-1 discardFps
6. **Cronet Log.i() calls never appeared in logcat** — unclear if Cronet actually initialized in our app; may be falling back to DefaultHttpDataSource silently

## Unsolved Mystery
iMPlayer plays the same 1080p stream smoothly with the same ~1.5 Mbps bandwidth, same Media3 1.7.1, same phone. The difference is NOT in:
- HTTP client library
- User-Agent
- Buffer configuration
- Track selection
- Stream URL format (.m3u8 vs .ts)
- Android network policies

## Next Steps to Investigate
1. **Packet capture comparison** — Use PCAPdroid to capture traffic from both apps simultaneously and compare at the TCP/TLS level
2. **Verify Cronet is actually active** — Our Log statements never appeared; need to confirm CronetDataSource is being used (not silently falling back)
3. **Check for background network activity** — Our app may be making API calls (EPG, categories, images) during playback that compete for the 1.5 Mbps budget
4. **iMPlayer's "Real time live streams: OFF"** — Need to determine what this actually does in ExoPlayer terms; may control whether HLS live edge tracking is used
5. **iMPlayer APK deep analysis** — Install jadx and decompile properly to find exact ExoPlayer configuration code
6. **Test on WiFi first** — Confirm the issue is cellular-specific and playback is smooth on WiFi

## Current State of Code (after this session)
- **Media3:** 1.7.1 (upgraded from 1.5.1)
- **HTTP stack:** CronetDataSource with QUIC+HTTP/2, fallback to DefaultHttpDataSource
- **Stream format:** `.m3u8` (HLS)
- **User-Agent:** `MediaPlayer/1.0 (Linux; Android)`
- **Buffer config:** Cellular live 50s/50s/2.5s/5s (ExoPlayer defaults)
- **CronetEngineProvider.kt:** Deleted (init moved to StreamingMediaSourceFactory)
- **gradle.properties:** Added `android.uniquePackageNames=false` for Cronet namespace conflict
- **Root build.gradle.kts:** Force resolution statements removed
- **libs.versions.toml:** Added `media3-datasource-cronet`, `play-services-cronet` v18.1.1; kept `media3-datasource-okhttp`
