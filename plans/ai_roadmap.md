# 🧠 Fijerena AI Integration Roadmap

This document outlines the strategic plan for integrating Artificial Intelligence into the Fijerena media player. The goal is to leverage on-device NPU/GPU power to enhance dialogue clarity, content discovery, and device-specific hardware features.

---

## 🎯 High-Level Objectives
1.  **Clarity:** Use AI to solve the "muffled dialogue" problem on TV speakers.
2.  **Discovery:** Move beyond keyword search to semantic, concept-based content finding.
3.  **Intelligence:** Make the player "context-aware" (device type, ambient light, user intent).
4.  **Performance:** Zero-latency AI execution using TFLite and hardware acceleration (NNAPI/GPU).

---

## 🛠 Phase 1: Audio & Dialogue Clarity (The "Clear Voice" Initiative)
*Focus: Improving the core listening experience.*

- [ ] **AI Dialogue Booster (v1.0)**
    - **Concept:** A TFLite-powered `AudioProcessor` that isolates speech from background noise/music.
    - **Tech:** Quantized RNNoise or Wave-U-Net model.
    - **Target:** All "PREMIUM" tier devices (Shield, Sony VH2, OnePlus 12R).
- [ ] **Smart "Night Mode"**
    - **Concept:** Look-ahead audio analysis to predict and suppress volume spikes (explosions) before they happen.
    - **Tech:** Small LSTM or Transformer model for audio amplitude prediction.
- [ ] **Sony Bravia "Voice Zoom" Sync**
    - **Concept:** Deep integration with Sony's XR Cognitive Processor to trigger hardware-level voice enhancement.

---

## 🔍 Phase 2: Metadata & Semantic Discovery
*Focus: Leveraging existing `:core:ai` infrastructure.*

- [ ] **Semantic EPG Browser**
    - **Concept:** Natural language browsing for live TV (e.g., "Find me some news about space exploration").
    - **Tech:** Sentence-Transformers (already integrated) applied to EPG FTS4 database.
- [ ] **"Scene Search" via Subtitles**
    - **Concept:** Indexing SRT/VTT embeddings to allow searching for specific dialogue moments.
    - **Tech:** Local vector storage for subtitle segments.
- [ ] **Contextual Recommendations**
    - **Concept:** "More like this" based on semantic similarity of titles/descriptions rather than just shared categories.

---

## 📱 Phase 3: Device-Specific Power Features
*Focus: Maximizing the hardware potential of flagship devices.*

- [ ] **NVIDIA Shield: AI Upscaling Context**
    - **Concept:** Detecting Shield's "AI-Enhanced" status and providing a UI toggle/status indicator.
    - **Optimization:** Offloading `AudioProcessor` tasks to the Tegra X1+ NPU.
- [ ] **OnePlus 12R: On-Device STT (Whisper)**
    - **Concept:** "What did they say?" feature. When paused, generate a transcript of the last 30 seconds.
    - **Tech:** OpenAI Whisper (Base/Tiny) TFLite implementation.
- [ ] **Mobile: Intelligent UI Contrast**
    - **Concept:** Real-time adjustment of `CinemaTheme` contrast based on camera/light sensor data using a vision model.

---

## 🚀 Phase 4: Advanced Media Intelligence
*Focus: Future-proofing and automation.*

- [ ] **AI-Powered Skip Intro/Recap**
    - **Concept:** Vision-based detection of opening credits and "Previously on..." segments.
    - **Tech:** Small convolutional neural network (CNN) analyzing frame signatures.
- [ ] **Smart Chapter Markers**
    - **Concept:** Automatically generating chapters for local files based on scene change detection and audio transitions.

---

## 📊 Technical Stack Reference
- **Engine:** TensorFlow Lite (TFLite)
- **Acceleration:** GPU Delegate, Hexagon (DSP) Delegate, NNAPI
- **Audio Pipeline:** Media3 `AudioProcessor` API
- **Models:**
    - `sentence_transformer.tflite` (Current: Vector Search)
    - *Proposed:* `rnnoise_quantized.tflite` (Dialogue)
    - *Proposed:* `whisper_tiny_int8.tflite` (Transcription)

---

## 📅 Status Tracking
*Last Updated: March 12, 2026*
*Current Focus: Researching TFLite Dialogue Enhancement models.*
