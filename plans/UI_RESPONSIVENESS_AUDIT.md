# Plan: UI Responsiveness - Open Performance Findings

## Status
All UI Responsiveness Audit steps (§A1, B1, B2, B4, C2–C4, D1–D4, E1–E2) except §A2 are fixed and landed in `main`.

The open performance finding below remains for future optimization.

---

## Open Item — De-gate First-Frame Playback on EPG / Watch-History (§A2)

- **Issue:** Video initialization currently waits for metadata/EPG/watch-history lookups before starting playback.
- **Optimization:** Initiate stream decoding immediately using the stream URI. Defer EPG/history resolution so they populate asynchronously after the decoder begins playback.
