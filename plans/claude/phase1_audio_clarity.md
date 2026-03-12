# Phase 1: Audio & Dialogue Clarity — Implementation Plan

## Context

TV speakers are notoriously bad at reproducing dialogue clearly, especially over action-heavy soundtracks. Phase 1 addresses this with three features: an AI-powered dialogue booster, a smart night mode, and Sony Bravia Voice Zoom integration. This plan covers architecture, files to change, new artifacts, estimated effort, performance risks, and APK size impact for each.

---

## Current Audio Pipeline (as of March 2026)

- **Player:** Media3 1.7.1 ExoPlayer, configured in `StreamingPlaybackService.kt:104-159`
- **RenderersFactory:** Custom `DefaultRenderersFactory` subclass already exists (overrides `buildVideoRenderers`). Float audio output enabled (`.setEnableAudioFloatOutput(true)`).
- **Audio decoders:** FFmpeg extension (`media3-ffmpeg-decoder:1.6.1+1`) handles AC3, EAC3, DTS, TrueHD. Preferred via `EXTENSION_RENDERER_MODE_PREFER`.
- **Audio effects:** None. No `AudioProcessor`, `DynamicsProcessing`, `LoudnessEnhancer`, or `Equalizer` in the codebase.
- **Device tier gating:** `SearchCapabilityDetector` classifies devices as PREMIUM (GPU TFLite) or STANDARD (CPU only). `DeviceDetector` identifies Shield, Sony Bravia, Chromecast, generic TV, and mobile.
- **AI infra:** `core:ai` module with TFLite 2.16.1, GPU delegate, sentence transformer model. Only included in `full` build flavor.

### Key injection point

The custom `DefaultRenderersFactory` in `StreamingPlaybackService.kt:109` currently only overrides `buildVideoRenderers`. To inject `AudioProcessor` instances, we need to override `buildAudioSink` or pass processors via `DefaultAudioSink.Builder`. Media3 1.7.1 supports this via `DefaultRenderersFactory.setAudioProcessorChain()` or by building a custom `AudioSink`.

---

## Feature 1a: AI Dialogue Booster

### What it does
Real-time speech isolation — boosts dialogue frequencies while attenuating background noise/music. Uses a quantized TFLite model (RNNoise or similar) processing PCM audio frames inline.

### Architecture

```
ExoPlayer Audio Decoder
        |
        v
  [DialogueBoostProcessor]  <-- new AudioProcessor
        |  reads PCM float frames
        |  runs TFLite inference per frame
        |  outputs enhanced PCM
        v
  DefaultAudioSink -> speakers
```

### Implementation steps

#### Step 1: Model selection & conversion (research, ~3 days)
- Evaluate RNNoise (GRU-based, ~85KB original, but needs TFLite conversion) vs. DTLN (Dual-signal Transformation LSTM Network, already available as TFLite, ~2MB quantized) vs. PercepNet.
- **Recommendation:** Start with **DTLN** — it's specifically designed for real-time speech enhancement, has published TFLite models, and runs at 16kHz with ~10ms latency per frame on mobile GPUs.
- Convert/quantize to int8 TFLite. Target: <5MB model size.
- Validate on Shield (Tegra X1+) and a Snapdragon 8-series device.

#### Step 2: TFLite speech enhancer wrapper (~3 days)
**New file:** `core/ai/src/main/java/org/njarasoa/fijerena/core/ai/audio/AiSpeechEnhancer.kt`

Wraps the TFLite `Interpreter` with per-device delegate selection:

```kotlin
class AiSpeechEnhancer(private val context: Context) {

    private var interpreter: Interpreter? = null
    // DTLN carries LSTM hidden/cell states between frames for temporal continuity
    private var lstmState1: ByteBuffer? = null  // hidden state for first LSTM block
    private var lstmState2: ByteBuffer? = null  // hidden state for second LSTM block

    fun initialize(deviceType: DeviceType) {
        val options = Interpreter.Options()
        // Per-device hardware delegate selection
        when (deviceType) {
            DeviceType.NVIDIA_SHIELD -> options.addDelegate(GpuDelegate())  // Tegra X1+ GPU
            DeviceType.GENERIC_MOBILE -> options.addDelegate(NnApiDelegate()) // Hexagon DSP on Snapdragon
            else -> options.addDelegate(GpuDelegate()) // default to GPU, fallback to CPU
        }
        options.setNumThreads(2)
        interpreter = Interpreter(loadModel(), options)
        resetStates()
    }

    /** Run inference on a single 512-sample frame. States are carried forward automatically. */
    fun enhance(inputFrame: FloatArray): FloatArray { ... }

    /** Reset LSTM states — call on seek, track change, or stream switch. */
    fun resetStates() {
        lstmState1 = allocateZeroBuffer(STATE_SIZE)
        lstmState2 = allocateZeroBuffer(STATE_SIZE)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
```

This separation from the AudioProcessor allows independent lifecycle management and testability. The `Interpreter` must be explicitly closed to prevent native memory leaks — add `close()` calls in `StreamingPlaybackService.onDestroy()`. The `DialogueBoostProcessor.flush()` method (called by Media3 on seek) must call `enhancer.resetStates()`.

#### Step 3: AudioProcessor implementation (~1 week)
**New file:** `core/ai/src/main/java/org/njarasoa/fijerena/core/ai/audio/DialogueBoostProcessor.kt`

```kotlin
class DialogueBoostProcessor(
    private val enhancer: AiSpeechEnhancer
) : AudioProcessor {

    private var enabled: Boolean = false
    private var sampleRateHz: Int = 48000
    private var channelCount: Int = 2
    private val resampleBuffer = FloatArray(512) // DTLN frame size at 16kHz

    // AudioProcessor interface
    override fun configure(inputFormat: AudioFormat): AudioFormat
    override fun isActive(): Boolean = enabled
    override fun queueInput(inputBuffer: ByteBuffer)
    override fun getOutput(): ByteBuffer
    override fun isEnded(): Boolean
    override fun flush()
    override fun reset()
}
```

Key considerations:
- **Resampling:** DTLN expects 16kHz mono. Input is typically 48kHz stereo/surround. Use a **sinc-based resampler** (or Media3's `Sonic` utilities) for the 48kHz→16kHz downsampling — a naive linear resampler introduces aliasing artifacts that degrade the enhanced audio. Upsample back to original rate after inference.
- **Channel handling: M/S decomposition as the universal default.** Extract Mid `(L+R)/2` and Side `(L-R)`, enhance only the Mid signal (where dialogue lives), then reconstruct: `L = Mid + Side`, `R = Mid - Side`. This works for both true stereo *and* stereo-downmixed surround content (common in IPTV streams that report 2.0 but emulate surround). For discrete 5.1/7.1 where a real center channel exists, the processor *may* optionally extract channel index 2 directly as an optimization, but M/S is the safe default that handles all cases without guessing channel layout.
- **Frame buffering:** DTLN uses 512-sample frames at 16kHz = 32ms per frame. Maintain a **ring buffer** that accumulates resampled samples until a full 512-sample frame is ready, then dispatch to inference.
- **LSTM state management:** DTLN is a stateful model — it carries LSTM hidden/cell states between consecutive frames for temporal continuity. The `AiSpeechEnhancer` must **persist these states across `enhance()` calls** and **reset them on seek, track change, or stream switch** (via `flush()`). Failing to do this degrades enhancement quality significantly.
- **Intensity control:** Expose a `strength` parameter (0.0–1.0) that blends between original and enhanced audio: `output = (1 - α) * original + α * enhanced`. This maps to a UI slider rather than a binary toggle, giving users fine-grained control.
- **Thread safety:** `queueInput()` is called on the audio rendering thread. TFLite inference must complete within the frame duration (~32ms). If inference exceeds 25ms (timing guard), skip enhancement for that frame and pass through unmodified audio. If N consecutive frames miss the deadline (e.g., 10), auto-disable enhancement and log a warning — the device can't sustain real-time inference.
- **Resource lifecycle:** The `AiSpeechEnhancer` holds native TFLite resources. Must be explicitly closed when the player is destroyed. Test with repeated play/stop/destroy cycles to verify no native memory leaks.

#### Step 4: Inject into player pipeline (~2 days)
**Modify:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`

In `initializePlayer()`, modify the `DefaultRenderersFactory` setup:

```kotlin
// Before creating ExoPlayer, build the audio processor chain
// Note: Night Mode uses DynamicsProcessing via audio session (see Feature 1b),
// so it is NOT in this chain — only AI-based processors go here.
val audioProcessors = mutableListOf<AudioProcessor>()

// Dialogue boost (only on full flavor + PREMIUM tier)
val dialogueProcessor = dialogueBoostProcessor // injected or lazy-init
if (dialogueProcessor != null) {
    audioProcessors.add(dialogueProcessor)
}

val audioSink = DefaultAudioSink.Builder(this)
    .setAudioProcessors(audioProcessors.toTypedArray())
    .setEnableFloatOutput(true)
    .build()
```

Then pass the custom `AudioSink` to the renderers factory or use `ExoPlayer.Builder` with a custom `RenderersFactory` that provides it.

**Important:** Night Mode is wired separately via `onAudioSessionIdChanged` (see Feature 1b), not through the AudioProcessor chain. This keeps the two features architecturally independent — Night Mode works on all devices (API 28+), Dialogue Booster only on PREMIUM tier.

#### Step 5: Tier gating & runtime enable/disable (~2 days)
**Modify:** `core/ai/src/main/java/org/njarasoa/fijerena/core/ai/SearchCapabilityDetector.kt`

Rename to `DeviceTierDetector` (or extract a shared tier enum) since it now gates more than search. Add an `AudioProcessingTier`:

```kotlin
enum class AudioProcessingTier {
    /** Can run real-time TFLite audio inference (GPU/NPU). */
    REALTIME,
    /** Cannot run real-time inference. Audio effects only via platform APIs. */
    BASIC
}
```

Gate: only create `DialogueBoostProcessor` if tier is `REALTIME`. On `BASIC` devices, the processor is simply not added to the chain.

#### Step 6: UI controls (~3 days)
**Modify (TV):** `tv/.../overlays/PlayerControlsOverlay.kt` — add a "Clear Voice" **slider** (0–100%) in an "Audio Enhancement" panel, accessible from the existing controls bar. The slider maps to the `strength` parameter (0.0 = off/passthrough, 1.0 = full enhancement). A long-press on the button opens the slider; a short-press toggles on/off at the last-used strength.
**Modify (Mobile):** equivalent mobile player controls.

State: store strength value as a float in SharedPreferences (`app_settings`). The processor reads this to set its blend factor. `isActive()` returns `false` when strength is 0.

#### Step 7: Latency testing & benchmarking (~3 days)
- Measure end-to-end audio latency with processor enabled vs disabled on Shield, OnePlus, and a low-end device.
- A/V sync test: play a lip-sync test video, verify no visible desync.
- CPU/GPU usage profiling during playback with enhancement on.
- If latency > 1 frame on any PREMIUM device, investigate: reduce model complexity, try NNAPI delegate, or fall back to CPU with smaller model.

### New artifacts
| Artifact | Size | Location |
|----------|------|----------|
| `dtln_quantized.tflite` (or similar) | ~2-5 MB | `core/ai/src/main/assets/` |
| `DialogueBoostProcessor.kt` | new file | `core/ai/.../audio/` |
| `AudioEnhancementManager.kt` | new file | `core/ai/.../audio/` (lifecycle, enable/disable) |

### Files modified
| File | Change |
|------|--------|
| `StreamingPlaybackService.kt` | Inject AudioProcessor chain into player |
| `SearchCapabilityDetector.kt` | Add audio processing tier (or extract shared tier) |
| `PlayerControlsOverlay.kt` (TV) | Add "Clear Voice" toggle |
| `PlaybackViewModel.kt` | Expose dialogue boost state |
| `core/ai/build.gradle.kts` | No new dependencies (reuses existing TFLite) |

### Estimates
- **Effort:** 3-4 weeks (1 dev)
- **APK size:** +2-5 MB (model only; TFLite libs already bundled)
- **Runtime memory:** +30-50 MB (model + audio buffers)
- **Performance risk:** MEDIUM. Must hit <32ms inference per frame. Shield GPU should handle it. Lower-end PREMIUM devices may struggle — must benchmark.

### Risks & mitigations
| Risk | Mitigation |
|------|------------|
| Inference too slow, causing audio crackling | Graceful passthrough: if a frame takes too long, output unprocessed audio. Log & disable after N consecutive misses. |
| A/V desync from buffering | Measure and compensate: report the processor's latency to Media3 via `AudioProcessor` output timing so the video renderer can adjust. |
| Model quality poor for non-English content | Test with Malagasy, French, English content. DTLN is language-agnostic (operates on spectral features, not speech recognition). |
| Surround sound breakage | Only process center channel for 5.1/7.1 content. Pass LFE and surrounds through unmodified. |
| Battery drain on mobile | Continuous TFLite inference adds ~5-10% battery consumption during playback. Show a subtle indicator when Clear Voice is active. Consider auto-disabling on low battery (<15%). |

---

## Feature 1b: Smart Night Mode

### Recommendation: Non-ML approach first

A predictive LSTM for volume spikes is over-engineered. Android's `DynamicsProcessing` API (API 28+) provides a multi-band compressor/limiter that achieves 80%+ of the goal with zero model overhead. The Shield and all target TV devices run Android 9+ (API 28+).

If the team still wants ML-based prediction, it can be added later as a Phase 1.5.

### Architecture: Audio session attachment (not AudioProcessor)

`DynamicsProcessing` is designed to attach to an **audio session ID**, not to operate inline as an `AudioProcessor`. Forcing it into the AudioProcessor chain would be fighting the API. Instead, we attach it directly to ExoPlayer's audio session via `Player.Listener.onAudioSessionIdChanged`:

```
ExoPlayer Audio Decoder
        |
        v
  [DialogueBoostProcessor]  (optional, from 1a — in AudioProcessor chain)
        |
        v
  DefaultAudioSink
        |
        v
  Android Audio Framework
        |
        v
  [DynamicsProcessing]  <-- attached to audio session ID at HAL level
        |
        v
  speakers
```

This means Night Mode operates **entirely outside** the player's internal signal chain. Zero CPU overhead in the rendering thread, no buffer management, no risk of audio glitches from processing delays.

### Implementation steps

#### Step 1: NightModeManager (~3 days)
**New file:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/audio/NightModeManager.kt`

This lives in `core:player` (not `core:ai`) since it uses no ML.

```kotlin
class NightModeManager {

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    /** Attach to a new audio session. Called from onAudioSessionIdChanged. */
    fun attach(audioSessionId: Int) {
        release() // clean up previous session
        currentSessionId = audioSessionId
        if (!enabled) return

        val config = DynamicsProcessing.Config.Builder(
            /* variant */ DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount */ 2,
            /* preEqInUse */ true,
            /* preEqBandCount */ 3,
            /* mbcInUse */ true,
            /* mbcBandCount */ 3,
            /* postEqInUse */ false,
            /* postEqBandCount */ 0,
            /* limiterInUse */ true
        ).build()

        dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
            // Multi-band compressor: tame loud passages
            setMbcBandByChannelIndex(0, 0, MbcBand(true, 200f, 4f, -20f, -20f, 5f, 100f, ...))
            // Limiter: hard ceiling
            setLimiterByChannelIndex(0, Limiter(true, true, 0, -6f, 50f, 5f, 100f))
            // Pre-EQ: boost speech frequencies
            setPreEqBandByChannelIndex(0, 1, EqBand(true, 3000f, 3f)) // +3dB at 2-4kHz
            enabled = true
        }
    }

    /** Release resources. Called on player destroy or disable. */
    fun release() {
        dynamicsProcessing?.release()
        dynamicsProcessing = null
    }

    var enabled: Boolean = false
        set(value) { field = value; if (value) attach(currentSessionId) else release() }
}
```

**In `StreamingPlaybackService`**, add a `Player.Listener`:

```kotlin
player.addListener(object : Player.Listener {
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        nightModeManager.attach(audioSessionId)
    }
})
```

#### Step 2: UI toggle (~2 days)
Add "Night Mode" toggle in the "Audio Enhancement" panel (shared with Clear Voice slider). Persist in SharedPreferences.

#### Step 3: Testing (~2 days)
- Play action movie scenes (explosions, gunshots) and verify volume peaks are tamed.
- Verify dialogue remains intelligible and not over-compressed.
- Test with headphones (night mode should still work but may be less necessary).
- Verify that toggling Night Mode during playback doesn't cause audio glitches (session re-attachment).
- Test interaction: both Night Mode and Clear Voice active simultaneously — verify they don't conflict.

### New artifacts
| Artifact | Size | Location |
|----------|------|----------|
| `NightModeManager.kt` | new file | `core/player/.../audio/` |

### Files modified
| File | Change |
|------|--------|
| `StreamingPlaybackService.kt` | Add `onAudioSessionIdChanged` listener for Night Mode |
| `PlayerControlsOverlay.kt` (TV) | Add "Night Mode" toggle in Audio Enhancement panel |
| `PlaybackViewModel.kt` | Expose night mode state |

### Estimates
- **Effort:** 1-1.5 weeks (1 dev)
- **APK size:** +0 MB (platform API, no model)
- **Runtime memory:** negligible (DynamicsProcessing runs in the audio HAL, not app heap)
- **Performance risk:** VERY LOW. Operates at the HAL level, zero CPU overhead in app process.

---

## Feature 1c: Sony Bravia "Voice Zoom" Sync

### Status: Research required

Sony's Voice Zoom (ClearAudio+) is a hardware feature of the XR Cognitive Processor on Bravia TVs. It is **not** exposed via a public Android API. Possible integration paths:

1. **SettingsProvider properties** — Try reading/writing `voice_zoom_level` via `Settings.System` or `Settings.Global` on Bravia devices. If accessible, this gives programmatic control without Sony SDK. Requires testing on actual Bravia VH2 hardware.
2. **Settings intent** — Launch `com.sony.dtv.settings/.SoundSettingsActivity` (or similar) to let the user toggle Voice Zoom from the system UI. Low-tech but reliable.
3. **HDMI-CEC command** — Some Sony TVs respond to vendor-specific CEC commands to toggle audio modes. Fragile and model-specific.
4. **Sony Bravia SDK** — Requires a business relationship and NDA.

### Recommendation

**Try the SettingsProvider approach first** (quick test on a Bravia VH2). If that doesn't work, **fall back to launching Sony's sound settings activity** via intent. This costs <1 day of effort and avoids a research rabbit hole.

If the team has contacts at Sony or access to the Bravia SDK, revisit this.

### Estimates
- **Effort (shortcut approach):** 1-2 days
- **Effort (full integration):** Unknown — depends on Sony SDK access
- **APK size:** 0 MB

---

## Overall Phase 1 Summary

| Feature | Effort | APK Impact | Memory | Perf Risk | Device Gating |
|---------|--------|-----------|--------|-----------|---------------|
| 1a: Dialogue Booster | 3-4 weeks | +2-5 MB | +30-50 MB | Medium | PREMIUM only |
| 1b: Night Mode (DSP) | 1-1.5 weeks | +0 MB | negligible | Low | All devices (API 28+) |
| 1c: Sony Voice Zoom | 1-2 days | +0 MB | none | None | Bravia only |
| **Total** | **~5-6 weeks** | **+2-5 MB** | **+30-50 MB** | | |

### Suggested execution order
1. **Night Mode first** — lowest risk, no ML dependencies, immediately useful, and validates the player integration pattern (`onAudioSessionIdChanged` listener). Can ship independently.
2. **Dialogue Booster second** — highest impact but requires model research, AudioProcessor chain setup, and careful latency tuning. Model research can happen in parallel with Night Mode dev.
3. **Sony Voice Zoom last** — research-dependent; do the shortcut approach and move on.

### Verification plan
1. **Unit tests:** Test `NightModeProcessor` and `DialogueBoostProcessor` with synthetic PCM buffers — verify output is modified, verify passthrough when disabled, verify no crashes on edge cases (empty buffer, format change).
2. **Integration test on Shield:** Play a movie with dialogue over action. Toggle Clear Voice on/off, verify audible improvement. Measure A/V sync with lip-sync test clip.
3. **Integration test on mobile (OnePlus):** Same as above. Verify no UI jank during playback with both processors active.
4. **Regression:** Play HLS live stream, VOD with subtitles, surround sound content — verify no audio glitches or crashes with processors in chain but disabled.
5. **Resource leak test:** Repeatedly play, stop, and destroy the service (10+ cycles) to ensure the TFLite `Interpreter` and native buffers are correctly released. Monitor with `adb shell dumpsys meminfo` for native heap growth.
6. **APK size check:** Compare `full` APK size before and after. Target: <5 MB increase.
