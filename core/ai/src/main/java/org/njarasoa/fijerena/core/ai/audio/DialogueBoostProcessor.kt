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
 * 3. Buffer 128-sample blocks and run DTLN processBlock() on each
 * 4. Upsample enhanced audio back to original rate
 * 5. Blend enhanced Mid with original using strength parameter
 * 6. Reconstruct stereo: L = Mid + Side, R = Mid - Side
 *
 * Thread safety: queueInput() runs on the audio rendering thread. If inference
 * exceeds 25ms, the block passes through unmodified. After 10 consecutive misses,
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

    // Ring buffer for accumulating resampled 16kHz mono samples (BLOCK_SHIFT = 128)
    private val ringBuffer = FloatArray(AiSpeechEnhancer.BLOCK_SHIFT)
    private var ringBufferPos: Int = 0

    // Output buffer
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputReady: Boolean = false

    // Timing guard for auto-disable
    private var consecutiveMisses: Int = 0
    private var autoDisabled: Boolean = false

    // Observable stats for diagnostics (read from UI thread via Stats overlay)
    @Volatile var totalFramesProcessed: Long = 0L
        private set
    @Volatile var totalFramesSkipped: Long = 0L
        private set
    @Volatile var lastInferenceMs: Long = 0L
        private set
    @Volatile var avgInferenceMs: Float = 0f
        private set
    private var inferenceSum: Long = 0L
    val isAutoDisabled: Boolean get() = autoDisabled

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat == AudioFormat.NOT_SET) {
            return AudioFormat.NOT_SET
        }

        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        resampleRatio = sampleRateHz.toFloat() / AiSpeechEnhancer.MODEL_SAMPLE_RATE

        inputFormat = inputAudioFormat
        // Return the same format — we process in-place without changing the encoding.
        // Changing the format mid-chain can cause DefaultAudioSink to misroute audio data.
        outputFormat = inputAudioFormat

        Log.i(TAG, "Configured: ${sampleRateHz}Hz, ${channelCount}ch, encoding: ${inputAudioFormat.encoding}")
        return outputFormat
    }

    override fun isActive(): Boolean {
        // Always active once configured to allow live toggling of strength
        // without re-configuring the AudioSink chain.
        return inputFormat != AudioFormat.NOT_SET && !autoDisabled
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.remaining() == 0) return

        // Increment total frames at the start of every non-empty buffer.
        // This confirms the processor is receiving data even if strength is 0.
        totalFramesProcessed++

        val remaining = inputBuffer.remaining()
        val isFloat = inputFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT
        val is16Bit = inputFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        val bytesPerSample = if (isFloat) 4 else if (is16Bit) 2 else 4

        if (outputBuffer == EMPTY_BUFFER || outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        if (!isActive() || strength <= 0.01f) {
            // Passthrough: copy bytes unchanged (same format in, same format out)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            outputReady = true
            return
        }

        // Convert input to float samples for processing
        val totalSamples: Int
        val inputSamples: FloatArray
        if (isFloat) {
            totalSamples = remaining / 4
            inputSamples = FloatArray(totalSamples)
            val floatBuf = inputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
            floatBuf.get(inputSamples)
            inputBuffer.position(inputBuffer.position() + remaining)
        } else if (is16Bit) {
            totalSamples = remaining / 2
            inputSamples = FloatArray(totalSamples)
            val shortBuf = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            for (i in 0 until totalSamples) {
                inputSamples[i] = shortBuf.get() / 32768f
            }
            inputBuffer.position(inputBuffer.position() + remaining)
        } else {
            // Unknown encoding: passthrough
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            outputReady = true
            return
        }

        val framesCount = totalSamples / channelCount

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

        // Process through DTLN in BLOCK_SHIFT (128) sample blocks
        val enhancedBlocks = mutableListOf<FloatArray>()
        for (sample in downsampled) {
            ringBuffer[ringBufferPos++] = sample
            if (ringBufferPos >= AiSpeechEnhancer.BLOCK_SHIFT) {
                val block = ringBuffer.copyOfRange(0, AiSpeechEnhancer.BLOCK_SHIFT)
                enhancedBlocks.add(processBlock(block))
                ringBufferPos = 0
            }
        }

        // Concatenate enhanced blocks into a 16kHz signal
        val enhancedLength = enhancedBlocks.size * AiSpeechEnhancer.BLOCK_SHIFT
        val enhanced16k = FloatArray(enhancedLength)
        for (i in enhancedBlocks.indices) {
            enhancedBlocks[i].copyInto(enhanced16k, i * AiSpeechEnhancer.BLOCK_SHIFT)
        }

        // Upsample enhanced signal back to original rate.
        val enhancedMid = FloatArray(framesCount)
        val coveredFrames: Int
        if (enhanced16k.isNotEmpty()) {
            coveredFrames = minOf((enhanced16k.size * resampleRatio).toInt(), framesCount)
            for (i in 0 until coveredFrames) {
                val srcPos = i / resampleRatio
                val srcIdx = srcPos.toInt().coerceIn(0, enhanced16k.size - 1)
                val nextIdx = (srcIdx + 1).coerceIn(0, enhanced16k.size - 1)
                val frac = srcPos - srcIdx
                enhancedMid[i] = enhanced16k[srcIdx] + (enhanced16k[nextIdx] - enhanced16k[srcIdx]) * frac
            }
            for (i in coveredFrames until framesCount) {
                enhancedMid[i] = midSamples[i]
            }
        } else {
            coveredFrames = 0
            midSamples.copyInto(enhancedMid)
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
                for (ch in 2 until channelCount) {
                    outputSamples[i * channelCount + ch] = inputSamples[i * channelCount + ch]
                }
            }
        } else {
            blendedMid.copyInto(outputSamples)
        }

        // Write output in the original format (same format in, same format out)
        if (isFloat) {
            val outFloat = outputBuffer.asFloatBuffer()
            outFloat.put(outputSamples)
            outputBuffer.position(totalSamples * 4)
        } else {
            // Convert back to 16-bit
            for (i in 0 until totalSamples) {
                val clamped = (outputSamples[i] * 32768f).toInt().coerceIn(-32768, 32767).toShort()
                outputBuffer.putShort(clamped)
            }
        }
        outputBuffer.flip()
        outputReady = true
    }

    /**
     * Process a single 128-sample block through the DTLN model.
     * If inference takes >25ms, returns the original block and increments miss counter.
     */
    private fun processBlock(block: FloatArray): FloatArray {
        val startNs = System.nanoTime()
        val enhanced = enhancer.processBlock(block)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        lastInferenceMs = elapsedMs

        if (enhanced == null || elapsedMs > DEADLINE_MS) {
            consecutiveMisses++
            totalFramesSkipped++
            if (elapsedMs > DEADLINE_MS) {
                Log.w(TAG, "Inference took ${elapsedMs}ms (deadline: ${DEADLINE_MS}ms), miss #$consecutiveMisses")
            }
            if (consecutiveMisses >= MAX_CONSECUTIVE_MISSES) {
                Log.w(TAG, "Auto-disabling dialogue boost after $MAX_CONSECUTIVE_MISSES consecutive misses")
                autoDisabled = true
            }
            return block
        }

        consecutiveMisses = 0
        totalFramesProcessed++
        inferenceSum += elapsedMs
        avgInferenceMs = if (totalFramesProcessed > 0) inferenceSum.toFloat() / totalFramesProcessed else 0f
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
