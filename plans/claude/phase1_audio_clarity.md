# Phase 1: Audio & Dialogue Clarity — Implementation Plan (WIP / EXPERIMENTAL)

## Status: WIP / EXPERIMENTAL (March 2026)

Features are integrated but **Clear Voice is currently non-functional** and **Sony Voice Zoom is unverified**.

---

## 1a: AI Dialogue Booster (WIP)

### Implementation
- **Model:** Two-stage DTLN (Dual-signal Transformation LSTM Network) int8 quantized.
- **Processor:** `DialogueBoostProcessor` in `:core:player`.
- **Status:** **NON-FUNCTIONAL.** The processor is integrated but does not correctly enhance speech in the current build.
- **Resampling:** Sinc-based 48kHz <-> 16kHz conversion.
- **Safety Valve:** 25ms latency guard implemented.

---

## 1b: Smart Night Mode (FUNCTIONAL)

### Implementation
- **HAL Integration:** `DynamicsProcessing` attached to audio session ID.
- **APP Fallback:** Internal compressor/limiter for devices lacking HAL support.
- **Stats:** Status reported in Stats for Nerds overlay.

---

## 1c: Sony Voice Zoom Sync (EXPERIMENTAL)

### Implementation
- **Hardware Integration:** Native Bravia XR Voice Zoom control implemented.
- **Status:** **UNVERIFIED.** Requires testing on compatible hardware.

---

## Overall Phase 1 Summary (Current)

| Feature | Status | APK Impact | Memory | Perf Risk | Device Gating |
|---------|--------|-----------|--------|-----------|---------------|
| 1a: Dialogue Booster | ⚠️ WIP | +4 MB | +40 MB | Medium | PREMIUM only |
| 1b: Night Mode (DSP) | ✅ DONE | +0 MB | negligible | Low | All devices (API 28+) |
| 1c: Sony Voice Zoom | 🧪 EXP | +0 MB | none | None | Bravia only |

---

## Verification Summary (Ongoing)
- **Shield TV (mdarcy):** REALTIME tier detected, but Clear Voice fails to process audio.
- **OnePlus 12:** REALTIME tier detected, Clear Voice non-functional.
- **Sony Bravia:** Night Mode functional, Voice Zoom unverified.
- **Emulators:** BASIC tier, AI DSP disabled.
