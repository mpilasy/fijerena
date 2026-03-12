package org.njarasoa.fijerena.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AI Audio Processor for Media3 that handles speech enhancement.
 * It does not directly depend on TFLite. A generic SpeechEnhancer is injected.
 */
@androidx.media3.common.util.UnstableApi
class AiAudioProcessor(
    private var speechEnhancer: SpeechEnhancer? = null
) : AudioProcessor {

    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET

    private var isActive = false
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var nextOutputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // Config
    private val frameSize = 512
    private val expectedSampleRate = 16000
    private val maxLatencyMs = 25L // 25ms timing guard for graceful degradation

    // Audio Buffering
    private var circularBuffer = ShortArray(0)
    private var bufferHead = 0
    private var bufferTail = 0
    private var bytesPerFrame = 0

    // Performance Tracking
    private var skippedFrames = 0
    private var skippedFrameWindowStart = 0L
    private val maxSkippedFrames = 5
    private val skipWindowMs = 2000L // 2 seconds
    private val mainHandler = Handler(Looper.getMainLooper())

    // Metrics for Stats
    @Volatile var currentLatencyMs: Long = 0L
        private set
    @Volatile var totalSkippedFrames: Long = 0L
        private set

    fun setSpeechEnhancer(enhancer: SpeechEnhancer?) {
        this.speechEnhancer = enhancer
        if (enhancer != null) {
            isActive = true
            enhancer.initialize()
        } else {
            isActive = false
        }
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }

        this.inputAudioFormat = inputAudioFormat

        // Output format is exactly the same as input format
        this.outputAudioFormat = inputAudioFormat

        // Initialize circular buffer for audio chunks
        bytesPerFrame = inputAudioFormat.channelCount * 2 // 16-bit PCM = 2 bytes per sample
        // Make buffer large enough for multiple 512-sample frames per channel
        circularBuffer = ShortArray(frameSize * inputAudioFormat.channelCount * 10)
        bufferHead = 0
        bufferTail = 0

        return if (isActive) this.outputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean {
        return isActive
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }

        val remaining = inputBuffer.remaining()
        val numSamples = remaining / 2 // 16-bit PCM = 2 bytes
        val channelCount = inputAudioFormat.channelCount

        // Ensure buffer is large enough for incoming data + existing unread data
        val samplesAvailable = if (bufferHead >= bufferTail) {
            bufferHead - bufferTail
        } else {
            circularBuffer.size - bufferTail + bufferHead
        }

        if (samplesAvailable + numSamples > circularBuffer.size) {
            // Reallocate circular buffer to prevent overflow
            val newCapacity = (circularBuffer.size * 2).coerceAtLeast(samplesAvailable + numSamples + frameSize * channelCount)
            val newBuffer = ShortArray(newCapacity)

            // Copy existing data to the beginning of the new buffer
            var i = 0
            var currentTail = bufferTail
            while (currentTail != bufferHead) {
                newBuffer[i++] = circularBuffer[currentTail]
                currentTail = (currentTail + 1) % circularBuffer.size
            }

            circularBuffer = newBuffer
            bufferTail = 0
            bufferHead = samplesAvailable
        }

        // Write incoming data to circular buffer
        for (i in 0 until numSamples) {
            val sample = inputBuffer.short
            circularBuffer[bufferHead] = sample
            bufferHead = (bufferHead + 1) % circularBuffer.size
        }

        // Determine how many complete frames we can process
        val newSamplesAvailable = samplesAvailable + numSamples

        val framesAvailable = newSamplesAvailable / (frameSize * channelCount)

        if (framesAvailable > 0) {
            val outputSize = framesAvailable * frameSize * channelCount * 2

            // Reallocate buffer only if capacity is too small
            if (nextOutputBuffer.capacity() < outputSize) {
                nextOutputBuffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder())
            } else {
                nextOutputBuffer.clear()
            }

            // Process frames
            for (f in 0 until framesAvailable) {
                // Read frame from buffer
                val frameBuffer = ShortArray(frameSize * channelCount)
                for (i in 0 until frameSize * channelCount) {
                    frameBuffer[i] = circularBuffer[bufferTail]
                    bufferTail = (bufferTail + 1) % circularBuffer.size
                }

                val processedBuffer = processFrame(frameBuffer, channelCount)

                // Write processed frame to output
                for (i in 0 until frameSize * channelCount) {
                    nextOutputBuffer.putShort(processedBuffer[i])
                }
            }

            nextOutputBuffer.flip()
            outputBuffer = nextOutputBuffer
        }
    }

    private fun processFrame(frameBuffer: ShortArray, channelCount: Int): ShortArray {
        val enhancer = speechEnhancer
        if (enhancer == null || !isActive) {
            return frameBuffer // Pass-through
        }

        val floatBuffer = FloatArray(frameSize)
        val sideBuffer = FloatArray(frameSize)
        val outputFrameBuffer = ShortArray(frameSize * channelCount)

        val startTime = SystemClock.elapsedRealtime()

        when (channelCount) {
            2 -> { // Stereo M/S Decomposition
                // Extract Mid and Side
                for (i in 0 until frameSize) {
                    val l = frameBuffer[i * 2] / 32768.0f
                    val r = frameBuffer[i * 2 + 1] / 32768.0f
                    floatBuffer[i] = (l + r) / 2.0f // Mid
                    sideBuffer[i] = (l - r) / 2.0f // Side
                }

                // Process Mid channel
                val processedMid = enhancer.process(floatBuffer)

                // Reconstruct L/R
                for (i in 0 until frameSize) {
                    val m = processedMid[i]
                    val s = sideBuffer[i]
                    val l = (m + s).coerceIn(-1.0f, 1.0f) * 32767.0f
                    val r = (m - s).coerceIn(-1.0f, 1.0f) * 32767.0f
                    outputFrameBuffer[i * 2] = l.toInt().toShort()
                    outputFrameBuffer[i * 2 + 1] = r.toInt().toShort()
                }
            }
            6, 8 -> { // 5.1 or 7.1 Surround (Center channel is usually index 2)
                // Copy all channels first
                System.arraycopy(frameBuffer, 0, outputFrameBuffer, 0, frameBuffer.size)

                // Extract Center channel
                for (i in 0 until frameSize) {
                    floatBuffer[i] = frameBuffer[i * channelCount + 2] / 32768.0f
                }

                // Process Center channel
                val processedCenter = enhancer.process(floatBuffer)

                // Replace Center channel
                for (i in 0 until frameSize) {
                    val c = processedCenter[i].coerceIn(-1.0f, 1.0f) * 32767.0f
                    outputFrameBuffer[i * channelCount + 2] = c.toInt().toShort()
                }
            }
            else -> { // Mono or unsupported, process channel 0
                for (i in 0 until frameSize) {
                    floatBuffer[i] = frameBuffer[i * channelCount] / 32768.0f
                }

                val processedMono = enhancer.process(floatBuffer)

                // Copy all channels first (for safety)
                System.arraycopy(frameBuffer, 0, outputFrameBuffer, 0, frameBuffer.size)

                // Replace channel 0
                for (i in 0 until frameSize) {
                    val m = processedMono[i].coerceIn(-1.0f, 1.0f) * 32767.0f
                    outputFrameBuffer[i * channelCount] = m.toInt().toShort()
                }
            }
        }

        val endTime = SystemClock.elapsedRealtime()
        val processingTime = endTime - startTime
        currentLatencyMs = processingTime

        if (processingTime > maxLatencyMs) {
            Log.w(TAG, "Frame processing took ${processingTime}ms, exceeding ${maxLatencyMs}ms limit")
            totalSkippedFrames++
            handleSkippedFrame()
            return frameBuffer // Fallback to unprocessed audio for this frame to prevent stutter
        } else {
            // Reset skip counter if we processed fast enough
            if (endTime - skippedFrameWindowStart > skipWindowMs) {
                skippedFrames = 0
                skippedFrameWindowStart = endTime
            }
        }

        return outputFrameBuffer
    }

    private fun handleSkippedFrame() {
        val now = SystemClock.elapsedRealtime()
        if (now - skippedFrameWindowStart > skipWindowMs) {
            // Reset window
            skippedFrames = 1
            skippedFrameWindowStart = now
        } else {
            skippedFrames++
            if (skippedFrames > maxSkippedFrames) {
                Log.e(TAG, "Performance safety valve triggered. Disabling AI Audio Processor.")
                isActive = false

                // Show toast on main thread
                // In a real app we might pass a context or callback, but Handler is okay for a simple Toast fallback.
                mainHandler.post {
                    // Try to get an app context if possible, or assume caller handles UI notification via other means.
                    // Since AudioProcessor doesn't have Context by default, we'll just log and rely on logs/state.
                    Log.e(TAG, "AI Audio Enhancement disabled due to high system load.")
                }
            }
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
        // Flush any remaining buffers here
    }

    override fun getOutput(): ByteBuffer {
        val outputBuffer = this.outputBuffer
        this.outputBuffer = AudioProcessor.EMPTY_BUFFER
        return outputBuffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        bufferHead = 0
        bufferTail = 0
        // Reset internal buffers here
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        speechEnhancer?.release()
    }

    companion object {
        private const val TAG = "AiAudioProcessor"
    }
}
