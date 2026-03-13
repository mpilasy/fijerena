# 📋 Plan: AI Phase 1 Implementation - Audio & Dialogue Clarity (v4 - WIP/EXPERIMENTAL)

## 🎯 Objective
Implement "Clear Voice" (AI Dialogue Enhancement) and "Smart Night Mode" (DSP Dynamics Compression). **Status: WIP / EXPERIMENTAL (March 2026)**.

---

## 🏗️ 1. Architecture & Core Components (WIP)

### 1.1 `AiAudioProcessor` (Integrated)
- **Status:** Implemented in `:core:player`. Gated by `AudioProcessingTier.REALTIME`.
- **Logic:** Two-stage DTLN (Dual-signal Transformation LSTM Network).
- **Latency Guard:** Bypasses frames if inference timing > 25ms.
- **Current Status:** **NON-FUNCTIONAL**. Audio enhancement fails to produce expected output.

---

## 🛠️ 2. Execution Order (Updated)

### Priority 1: Smart Night Mode - COMPLETED
- Functional on all devices (API 28+).

### Priority 2: AI Infrastructure - WIP
- Pipes are built, but the DTLN model processing is currently non-functional.

### Priority 3: DTLN Model Integration - WIP
- Integrated but requires debugging for correct speech enhancement output.

### Priority 4: Sony Voice Zoom - EXPERIMENTAL
- Integrated but unverified on hardware.

---

## 🚀 3. Performance & Diagnostics
- **Stats for Nerds:** Monitoring latency and frame skips, but enhancement is currently bypassed or non-functional.

---

## 📊 Phase 1 Implementation Summary (Current)

| Feature Component | Status | Effort | APK Impact | Memory | Perf Risk | Device Gating |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Smart Night Mode (DSP)** | ✅ DONE | 1 week | 0 MB | Negligible | Very Low | All (API 28+) |
| **2. AI Processor Infra** | ⚠️ WIP | 1.5 weeks | < 0.2 MB | ~5 MB | Low | All (Logic Only) |
| **3. DTLN AI Model** | ⚠️ WIP | 3 weeks | +4 MB | ~40 MB | Medium | PREMIUM (Full) |
| **4. Sony Voice Zoom** | 🧪 EXP | 2 days | 0 MB | None | None | Bravia Only |
| **Total** | | **~6 weeks** | **~4 MB** | **~45 MB** | | |

