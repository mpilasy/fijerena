# 📋 Plan: AI Phase 1 Implementation - Audio & Dialogue Clarity (v4 - Production Grade)

## 🎯 Objective
Implement "Clear Voice" (AI Dialogue Enhancement) and "Smart Night Mode" (DSP Dynamics Compression). This version focuses on **Clean Architecture isolation**, **High-Fidelity Surround Sound**, and **Graceful Performance Degradation**.

---

## 🏗️ 1. Architecture & Core Components (Revised)

### 1.1 `AiAudioProcessor` (Modular Strategy)
- **Location:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/audio/AiAudioProcessor.kt`
- **Isolation:** This class **MUST NOT** import any TFLite libraries. It defines a generic `SpeechEnhancer` interface.
- **Dependency Injection:** `core:player` will look for a `SpeechEnhancer` implementation at runtime. If the `full` flavor is active, `core:ai` will provide the DTLN-based implementation.
- **Latency Reporting:** Explicitly reports **32ms fixed buffer latency** (one 512-sample frame at 16kHz) to ExoPlayer for automatic A/V sync compensation.

### 1.2 Channel Handling (Stereo & Surround)
- **Stereo (2.0):** Use **Mid/Side (M/S) Decomposition**. Enhance the "Mid" signal and re-mux with "Side".
- **Surround (5.1/7.1):** Directly isolate the **Center Channel** (usually channel index 2). Apply AI enhancement ONLY to this channel and pass L, R, SL, SR, and LFE through untouched. This preserves the original spatial soundstage.

### 1.3 `AiSpeechEnhancer` (TFLite Implementation)
- **Location:** `core/ai/src/main/java/org/njarasoa/fijerena/core/ai/audio/DtlnSpeechEnhancer.kt`
- **Linkage:** Reuses the **native TFLite .so libraries** already bundled in `core:ai` to avoid duplication. 
- **Model:** Quantized DTLN (INT8 or FP16), targeting **~2-4MB** additional APK size.

---

## 🛠️ 2. Execution Order (Priority-Based)

### Priority 1: Smart Night Mode (The "Quick Win")
- **Implementation:** Use Android's native **`DynamicsProcessing`** API.
- **Action:** Attach to the `audioSessionId` in `StreamingPlaybackService`.
- **Goal:** Validate the player's audio signal chain and provide an immediate feature (0% CPU/RAM cost) while AI research continues.

### Priority 2: AI Infrastructure & Resampling
- **Action:** Implement the `AiAudioProcessor` in `core:player` with Sinc-based resampling (48kHz <-> 16kHz) and the 512-sample ring buffer.
- **Goal:** Build the "pipes" for the AI without actually loading a model yet.

### Priority 3: DTLN Model Integration (The "Hard Labor")
- **Action:** Implement the `DtlnSpeechEnhancer` in `core:ai`.
- **Goal:** Perform the first real-time inference on the NVIDIA Shield and OnePlus 12R.

---

## 🚀 3. Performance & Graceful Degradation

### 3.1 Timing Guard (The "Single Frame" Bypass)
If a single inference frame takes **> 25ms**, that frame is bypassed (unprocessed audio) to prevent audio "crackling."

### 3.2 Performance Safety Valve (The "Auto-Kill")
If the processor skips **more than 5 frames in a 2-second window** (indicating a consistently slow device or high CPU load), the feature will:
1. **Auto-Disable** for the duration of the current stream.
2. **Notify the User** via a one-time Toast: *"AI Audio Enhancement disabled due to high system load."*
3. **Log the Event** for future performance tuning.

---

## 📦 4. Binary Size & Artifacts Impact
- **Isolation:** TFLite dependencies remain strictly in the **Full** flavor. The **Slim** flavor APK will see **0MB** increase.
- **Full Flavor:** Expected **+3MB to +5MB** (Model only, assuming shared TFLite libs).

---

## 🧪 5. Comprehensive Validation & Test Plan
1. **A/V Sync Stress:** Play a 10-hour HLS live stream on NVIDIA Shield and monitor for drift over time.
2. **Dynamic Format Switch:** Change from a 1080p 5.1 stream to a 480p Stereo stream mid-playback. The processor must re-configure without crashing.
3. **Surround Check:** Play a Dolby Digital 5.1 test file. Verify the "Side" and "Rear" channels are completely untouched by the AI (no "echo" or phasing).
4. **Multilingual Test:** Verify speech enhancement quality on **English, French, and Malagasy** IPTV channels.
5. **Memory Leak:** Open/Close the player 50 times in rapid succession on a Sony Bravia to ensure TFLite `Interpreter` resources are fully reclaimed.
6. **Thermal Test:** Run 2 hours of 4K content on OnePlus 12R. Verify the "Safety Valve" triggers if the device begins thermal throttling.
