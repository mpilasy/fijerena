# TODO - Known Issues & Status

## No Active Blockers

The build environment is fully operational. All recent commits compile and deploy successfully.

---

## Open Investigation

### Cellular Streaming Buffering
CDN throttles to ~1.5 Mbps per connection on cellular; stream is 1080p single-quality at ~1.3 Mbps. iMPlayer plays smoothly with same bandwidth — root cause still unknown. See `docs/CELLULAR_STREAMING_INVESTIGATION.md` for details.

**Next step:** Packet capture (PCAPdroid) comparison between our app and iMPlayer.

---

## Testing Notes

Prefer real hardware for validation:
- **NVIDIA Shield**: Best HEVC/4K codec support, AV1 hardware decode
- **Chromecast with Google TV**: General Android TV compatibility
- **Sony Bravia**: TV-specific behaviour, reduced animations
