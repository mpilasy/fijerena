# Plan Review Request

The goal is to implement the "Clear Voice" (AI Dialogue Enhancement) and "Smart Night Mode" (DSP Dynamics Compression) as detailed in `plans/gemini/phase1_ai_audio.md`.

## Proposed Steps

1. **Smart Night Mode (DSP):**
   - Implement "Smart Night Mode" using Android's native `DynamicsProcessing` API.
   - We will create a `NightModeAudioProcessor` (or similar utility) in `core:player` to attach/detach the effect based on the current `audioSessionId` from `StreamingPlaybackService` (we can obtain the audio session id using player analytics listener or player listener if possible).

2. **AI Processor Infrastructure:**
   - Create `SpeechEnhancer` interface in `core:player` (`org.njarasoa.fijerena.core.player.audio.SpeechEnhancer`).
   - Create `AiAudioProcessor` implementing Media3 `AudioProcessor` in `core:player` (`org.njarasoa.fijerena.core.player.audio.AiAudioProcessor.kt`). This processor will handle:
     - 512-sample ring buffer and reporting 32ms fixed latency.
     - Stereo Mid/Side decomposition and isolating the center channel for 5.1/7.1 surround.
     - Graceful degradation: "Timing Guard" (bypass frame if > 25ms) and "Performance Safety Valve" (disable if >5 skipped frames in 2s).

3. **DTLN Model Integration:**
   - Implement `DtlnSpeechEnhancer` in `core:ai` (`org.njarasoa.fijerena.core.ai.audio.DtlnSpeechEnhancer.kt`) implementing `SpeechEnhancer` interface, using TFLite.

4. **Integration with Media3 Player:**
   - Modify `StreamingPlaybackService` and `PlayerConfigFactory` (or `initializePlayer`) to allow injecting/enabling `AiAudioProcessor` and managing the `DynamicsProcessing` API.

5. **Pre-commit Checks:**
   - Run `pre_commit_instructions` and follow them to ensure tests pass, styling is correct, etc.
