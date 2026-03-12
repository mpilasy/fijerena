package org.njarasoa.fijerena.core.ai.audio

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 AudioProcessor that enhances dialogue using a DTLN TFLite model.
 *
 * Processing pipeline:
 * 1. Extract Mid channel from stereo via M/S decomposition: Mid = (L+R)/2
 * 2. Downsample from input rate (typically 48kHz) to 16kHz using linear interpolation
 * 3. Buffer 512-sample frames and run DTLN inference on each
 * 4. Upsample enhanced audio back to original rate
 * 5. Blend enhanced Mid with original using strength parameter
 * 6. Reconstruct stereo: L = Mid + Side, R = Mid - Side
 *
 * Thread safety: queueInput() runs on the audio rendering thread. If inference
 * exceeds 25ms, the frame passes through unmodified. After 10 consecutive misses,
 * auto-disables to prevent persistent audio degradation.
 */
@androidx.media3.common.util.UnstableApi
class DialogueBoostProcessor(
    private val enhancer: AiSpeechEnhancer
) : AudioProcessor {

    /** Enhancement strength: 0.0 = passthrough, 1.0 = full enhancement */
    @Volatile
    var strength: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f) }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var sampleRateHz: Int = 48000
    private var channelCount: Int = 2
    private var inputEnded: Boolean = false

    // Resampling ratio: input rate / model rate
    private var resampleRatio: Float = 3f // 48000 / 16000

    // Ring buffer for accumulating resampled 16kHz mono samples
    private val ringBuffer = FloatArray(AiSpeechEnhancer.FRAME_SIZE * 2)
    private var ringBufferPos: Int = 0

    // Output buffer
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputReady: Boolean = false

    // Pending input that needs to accumulate into frames
    private val pendingInput = mutableListOf<ByteBuffer>()

    // Timing guard for auto-disable
    private var consecutiveMisses: Int = 0
    private var autoDisabled: Boolean = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat == AudioFormat.NOT_SET) {
            return AudioFormat.NOT_SET
        }

        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        resampleRatio = sampleRateHz.toFloat() / AiSpeechEnhancer.MODEL_SAMPLE_RATE

        inputFormat = inputAudioFormat
        // Output format is the same as input — we process in-place
        outputFormat = inputAudioFormat

        Log.i(TAG, "Configured: ${sampleRateHz}Hz, ${channelCount}ch, resample ratio: $resampleRatio")
        return outputFormat
    }

    override fun isActive(): Boolean {
        return strength > 0f && !autoDisabled
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive() || inputBuffer.remaining() == 0) {
            // Passthrough: copy input directly to output
            val size = inputBuffer.remaining()
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            outputReady = true
            return
        }

        val remaining = inputBuffer.remaining()
        val bytesPerSample = 4 // Float PCM = 4 bytes
        val totalSamples = remaining / bytesPerSample
        val framesCount = totalSamples / channelCount

        // Read input as float samples
        val inputSamples = FloatArray(totalSamples)
        val floatBuf = inputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
        floatBuf.get(inputSamples)
        inputBuffer.position(inputBuffer.position() + remaining)

        // M/S decomposition: extract Mid and Side from stereo
        val midSamples = FloatArray(framesCount)
        val sideSamples = FloatArray(framesCount)

        if (channelCount >= 2) {
            for (i in 0 until framesCount) {
                val l = inputSamples[i * channelCount]
                val r = inputSamples[i * channelCount + 1]
                midSamples[i] = (l + r) * 0.5f
                sideSamples[i] = (l - r) * 0.5f
            }
        } else {
            // Mono: Mid = signal, Side = 0
            for (i in 0 until framesCount) {
                midSamples[i] = inputSamples[i]
                sideSamples[i] = 0f
            }
        }

        // Downsample Mid to 16kHz using linear interpolation
        val downsampledLength = (framesCount / resampleRatio).toInt()
        val downsampled = FloatArray(downsampledLength)
        for (i in 0 until downsampledLength) {
            val srcPos = i * resampleRatio
            val srcIdx = srcPos.toInt()
            val frac = srcPos - srcIdx
            val s0 = midSamples[srcIdx.coerceIn(0, framesCount - 1)]
            val s1 = midSamples[(srcIdx + 1).coerceIn(0, framesCount - 1)]
            downsampled[i] = s0 + (s1 - s0) * frac
        }

        // Accumulate into ring buffer and process 512-sample frames
        val enhancedFrames = mutableListOf<FloatArray>()
        for (sample in downsampled) {
            ringBuffer[ringBufferPos++] = sample
            if (ringBufferPos >= AiSpeechEnhancer.FRAME_SIZE) {
                val frame = ringBuffer.copyOfRange(0, AiSpeechEnhancer.FRAME_SIZE)
                val enhanced = processFrame(frame)
                enhancedFrames.add(enhanced)
                ringBufferPos = 0
            }
        }

        // Reconstruct enhanced 16kHz signal from processed frames
        val enhancedLength = enhancedFrames.size * AiSpeechEnhancer.FRAME_SIZE
        val enhanced16k = FloatArray(enhancedLength)
        for (i in enhancedFrames.indices) {
            enhancedFrames[i].copyInto(enhanced16k, i * AiSpeechEnhancer.FRAME_SIZE)
        }

        // Upsample enhanced signal back to original rate
        val enhancedMid = FloatArray(framesCount)
        if (enhanced16k.isNotEmpty()) {
            val upRatio = enhanced16k.size.toFloat() / framesCount
            for (i in 0 until framesCount) {
                val srcPos = i * upRatio
                val srcIdx = srcPos.toInt().coerceIn(0, enhanced16k.size - 1)
                val nextIdx = (srcIdx + 1).coerceIn(0, enhanced16k.size - 1)
                val frac = srcPos - srcIdx
                enhancedMid[i] = enhanced16k[srcIdx] + (enhanced16k[nextIdx] - enhanced16k[srcIdx]) * frac
            }
        }

        // Blend: output = (1 - strength) * original + strength * enhanced
        val alpha = strength
        val blendedMid = FloatArray(framesCount)
        for (i in 0 until framesCount) {
            blendedMid[i] = (1f - alpha) * midSamples[i] + alpha * enhancedMid[i]
        }

        // Reconstruct stereo from M/S: L = Mid + Side, R = Mid - Side
        val outputSamples = FloatArray(totalSamples)
        if (channelCount >= 2) {
            for (i in 0 until framesCount) {
                outputSamples[i * channelCount] = blendedMid[i] + sideSamples[i]
                outputSamples[i * channelCount + 1] = blendedMid[i] - sideSamples[i]
                // Pass through any additional channels (5.1/7.1 surround) unmodified
                for (ch in 2 until channelCount) {
                    outputSamples[i * channelCount + ch] = inputSamples[i * channelCount + ch]
                }
            }
        } else {
            blendedMid.copyInto(outputSamples)
        }

        // Write to output buffer
        outputBuffer = ByteBuffer.allocateDirect(totalSamples * 4).order(ByteOrder.nativeOrder())
        val outFloat = outputBuffer.asFloatBuffer()
        outFloat.put(outputSamples)
        outputBuffer.limit(totalSamples * 4)
        outputBuffer.position(0)
        outputReady = true
    }

    /**
     * Process a single 512-sample frame through the DTLN model.
     * If inference takes >25ms, returns the original frame and increments miss counter.
     */
    private fun processFrame(frame: FloatArray): FloatArray {
        val startNs = System.nanoTime()
        val enhanced = enhancer.enhance(frame)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        if (enhanced == null || elapsedMs > DEADLINE_MS) {
            consecutiveMisses++
            if (elapsedMs > DEADLINE_MS) {
                Log.w(TAG, "Inference took ${elapsedMs}ms (deadline: ${DEADLINE_MS}ms), miss #$consecutiveMisses")
            }
            if (consecutiveMisses >= MAX_CONSECUTIVE_MISSES) {
                Log.w(TAG, "Auto-disabling dialogue boost after $MAX_CONSECUTIVE_MISSES consecutive misses")
                autoDisabled = true
            }
            return frame
        }

        consecutiveMisses = 0
        return enhanced
    }

    override fun getOutput(): ByteBuffer {
        if (outputReady) {
            outputReady = false
            val result = outputBuffer
            outputBuffer = EMPTY_BUFFER
            return result
        }
        return EMPTY_BUFFER
    }

    override fun isEnded(): Boolean {
        return inputEnded && !outputReady
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        outputReady = false
        inputEnded = false
        ringBufferPos = 0
        consecutiveMisses = 0
        autoDisabled = false
        // Reset LSTM states on seek/track change for clean temporal context
        enhancer.resetStates()
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
    }

    companion object {
        private const val TAG = "DialogueBoostProcessor"

        /** Maximum inference time per frame before it's considered a miss */
        private const val DEADLINE_MS = 25L

        /** Auto-disable after this many consecutive misses */
        private const val MAX_CONSECUTIVE_MISSES = 10

        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
