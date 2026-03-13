package org.njarasoa.fijerena.core.ai.audio

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Media3 AudioProcessor that enhances dialogue using a DTLN TFLite model.
 * 
 * This version uses persistent state to prevent "chopping" artifacts caused by
 * buffer boundaries and resampling phase resets.
 */
@androidx.media3.common.util.UnstableApi
class DialogueBoostProcessor(
    private val enhancer: AiSpeechEnhancer
) : AudioProcessor {

    @Volatile
    var strength: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f) }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    
    // Persistent buffers for Mid and Side signals to maintain continuity
    private var midInputBuffer = FloatArray(8192)
    private var sideInputBuffer = FloatArray(8192)
    private var bufferPos: Int = 0

    // Resampling state
    private var resampleRatio: Float = 3f
    private var inputSampleClock: Double = 0.0
    private var outputSampleClock: Double = 0.0

    // AI Processing state
    private val aiRingBuffer = FloatArray(AiSpeechEnhancer.BLOCK_SHIFT)
    private var aiRingBufferPos: Int = 0
    private var enhanced16kBuffer = FloatArray(8192)
    private var enhanced16kPos: Int = 0
    private var enhanced16kReadPos: Int = 0

    // Output buffer
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputReady: Boolean = false

    // Stats
    @Volatile var totalFramesProcessed: Long = 0L
        private set
    @Volatile var totalFramesSkipped: Long = 0L
        private set
    @Volatile var lastInferenceMs: Long = 0L
        private set
    @Volatile var avgInferenceMs: Float = 0f
        private set
    private var inferenceSum: Long = 0L
    private var autoDisabled: Boolean = false
    private var consecutiveMisses: Int = 0
    val isAutoDisabled: Boolean get() = autoDisabled

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat == AudioFormat.NOT_SET) return AudioFormat.NOT_SET
        
        inputFormat = inputAudioFormat
        outputFormat = inputAudioFormat
        resampleRatio = inputAudioFormat.sampleRate.toFloat() / AiSpeechEnhancer.MODEL_SAMPLE_RATE
        
        flush()
        Log.e(TAG, "Configured: ${inputAudioFormat.sampleRate}Hz, ${inputAudioFormat.channelCount}ch, encoding: ${inputAudioFormat.encoding}")
        return outputFormat
    }

    override fun isActive(): Boolean {
        return inputFormat != AudioFormat.NOT_SET && !autoDisabled && strength > 0.01f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Log occasionally to confirm life
        if (totalFramesProcessed % 100 == 0L) {
             Log.e(TAG, "queueInput: ${remaining} bytes, strength: ${strength}, frames: ${totalFramesProcessed}")
        }

        if (strength <= 0.01f) {
            // Passthrough mode: just copy input to output immediately
            if (outputBuffer.capacity() < remaining) {
                outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            outputReady = true
            return
        }

        val isFloat = inputFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT
        val channelCount = inputFormat.channelCount
        val bytesPerSample = if (isFloat) 4 else 2
        val inputFrames = remaining / (bytesPerSample * channelCount)

        // 1. De-interleave and decompose to Mid/Side
        ensureCapacity(inputFrames)
        if (isFloat) {
            val fb = inputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (i in 0 until inputFrames) {
                val l = fb.get()
                val r = if (channelCount >= 2) fb.get() else l
                // For 5.1 (6ch) or more, we take the average of L/R for dialogue.
                // Center channel (ch2 in many layouts) often has most dialogue, 
                // but for simplicity we boost the L/R Mid component.
                midInputBuffer[bufferPos + i] = (l + r) * 0.5f
                sideInputBuffer[bufferPos + i] = (l - r) * 0.5f
                
                // Skip extra channels (Center, LFE, Surround L, Surround R, etc)
                for (ch in 2 until channelCount) fb.get()
            }
        } else {
            val sb = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            for (i in 0 until inputFrames) {
                val l = sb.get() / 32768f
                val r = if (channelCount >= 2) sb.get() / 32768f else l
                midInputBuffer[bufferPos + i] = (l + r) * 0.5f
                sideInputBuffer[bufferPos + i] = (l - r) * 0.5f
                for (ch in 2 until channelCount) sb.get()
            }
        }
        inputBuffer.position(inputBuffer.position() + remaining)
        bufferPos += inputFrames

        // 2. Downsample and process through AI
        if (strength > 0.01f) {
            processAi()
        }

        // 3. Prepare output
        generateOutput()
    }

    private fun processAi() {
        // Downsample using the persistent clock to prevent phase resets
        while (inputSampleClock < bufferPos) {
            val idx = inputSampleClock.toInt()
            val nextIdx = min(idx + 1, bufferPos - 1)
            val frac = (inputSampleClock - idx).toFloat()
            
            // Simple low-pass + linear interpolation
            val s0 = midInputBuffer[idx]
            val s1 = midInputBuffer[nextIdx]
            val sample16k = s0 + (s1 - s0) * frac
            
            aiRingBuffer[aiRingBufferPos++] = sample16k
            if (aiRingBufferPos >= AiSpeechEnhancer.BLOCK_SHIFT) {
                val enhanced = runInference(aiRingBuffer.copyOf())
                appendEnhanced(enhanced)
                aiRingBufferPos = 0
            }
            
            inputSampleClock += resampleRatio
        }
    }

    private fun runInference(block: FloatArray): FloatArray {
        val startNs = System.nanoTime()
        val enhanced = enhancer.processBlock(block)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        lastInferenceMs = elapsedMs

        if (enhanced == null || elapsedMs > 25) {
            consecutiveMisses++
            totalFramesSkipped++
            if (consecutiveMisses >= 10 && !autoDisabled) {
                autoDisabled = true
                Log.w(TAG, "AI Dialogue Boost auto-disabled: consecutive misses or slow inference (last: $elapsedMs ms)")
            }
            return block
        }

        consecutiveMisses = 0
        totalFramesProcessed++
        inferenceSum += elapsedMs
        avgInferenceMs = inferenceSum.toFloat() / totalFramesProcessed
        return enhanced
    }

    private fun appendEnhanced(block: FloatArray) {
        if (enhanced16kPos + block.size > enhanced16kBuffer.size) {
            enhanced16kBuffer = enhanced16kBuffer.copyOf(enhanced16kBuffer.size * 2)
        }
        block.copyInto(enhanced16kBuffer, enhanced16kPos)
        enhanced16kPos += block.size
    }

    private fun generateOutput() {
        val channelCount = inputFormat.channelCount
        val isFloat = inputFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT
        
        // Determine how many frames we can output
        // If AI is on, we are limited by how much 16kHz data we've processed
        val availableFrames = if (strength > 0.01f) {
            val ready16k = (enhanced16kPos - enhanced16kReadPos)
            min(bufferPos, (ready16k * resampleRatio).toInt())
        } else {
            bufferPos
        }

        if (availableFrames <= 0) return

        val outByteCount = availableFrames * channelCount * (if (isFloat) 4 else 2)
        if (outputBuffer.capacity() < outByteCount) {
            outputBuffer = ByteBuffer.allocateDirect(outByteCount).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val alpha = strength
        for (i in 0 until availableFrames) {
            val originalMid = midInputBuffer[i]
            val side = sideInputBuffer[i]
            
            val processedMid = if (alpha > 0.01f) {
                val outPos16k = enhanced16kReadPos + (i / resampleRatio)
                val idx = outPos16k.toInt()
                val nextIdx = min(idx + 1, enhanced16kPos - 1)
                val frac = (outPos16k - idx).toFloat()
                val s0 = enhanced16kBuffer[idx]
                val s1 = enhanced16kBuffer[nextIdx]
                s0 + (s1 - s0) * frac
            } else {
                originalMid
            }

            val finalMid = (1f - alpha) * originalMid + alpha * processedMid
            val l = finalMid + side
            val r = finalMid - side

            if (isFloat) {
                outputBuffer.putFloat(l)
                if (channelCount >= 2) outputBuffer.putFloat(r)
                for (ch in 2 until channelCount) outputBuffer.putFloat(0f)
            } else {
                outputBuffer.putShort((l * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                if (channelCount >= 2) outputBuffer.putShort((r * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                for (ch in 2 until channelCount) outputBuffer.putShort(0)
            }
        }

        // Shift buffers
        val remaining = bufferPos - availableFrames
        System.arraycopy(midInputBuffer, availableFrames, midInputBuffer, 0, remaining)
        System.arraycopy(sideInputBuffer, availableFrames, sideInputBuffer, 0, remaining)
        bufferPos = remaining
        inputSampleClock -= availableFrames
        
        if (alpha > 0.01f) {
            val used16k = (availableFrames / resampleRatio).toInt()
            enhanced16kReadPos += used16k
            // Occasionally compact the 16k buffer
            if (enhanced16kReadPos > 4096) {
                val rem16k = enhanced16kPos - enhanced16kReadPos
                System.arraycopy(enhanced16kBuffer, enhanced16kReadPos, enhanced16kBuffer, 0, rem16k)
                enhanced16kPos = rem16k
                enhanced16kReadPos = 0
            }
        } else {
            enhanced16kPos = 0
            enhanced16kReadPos = 0
            inputSampleClock = 0.0
        }

        outputBuffer.flip()
        outputReady = true
    }

    private fun ensureCapacity(frames: Int) {
        if (bufferPos + frames > midInputBuffer.size) {
            val newSize = (bufferPos + frames) * 2
            midInputBuffer = midInputBuffer.copyOf(newSize)
            sideInputBuffer = sideInputBuffer.copyOf(newSize)
        }
    }

    override fun getOutput(): ByteBuffer {
        val result = if (outputReady) outputBuffer else EMPTY_BUFFER
        outputReady = false
        outputBuffer = EMPTY_BUFFER
        return result
    }

    override fun isEnded(): Boolean = bufferPos == 0 && !outputReady
    override fun queueEndOfStream() {}
    override fun flush() {
        bufferPos = 0
        inputSampleClock = 0.0
        aiRingBufferPos = 0
        enhanced16kPos = 0
        enhanced16kReadPos = 0
        outputReady = false
        enhancer.resetStates()
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
    }

    companion object {
        private const val TAG = "DialogueBoostProcessor"
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
