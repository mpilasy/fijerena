package org.njarasoa.fijerena.core.player.audio

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * An internal AudioProcessor that implements Night Mode (Dynamic Range Compression).
 *
 * Moving this to an AudioProcessor ensures it works on all devices (including NVIDIA Shield)
 * by processing PCM frames before they reach the system audio HAL.
 */
@UnstableApi
class NightModeProcessor : AudioProcessor {
    private var inputAudioFormat = AudioFormat.NOT_SET
    private var reusableBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    @Volatile
    var enabled = false

    // Compressor parameters
    private var envelope = 0f
    private val attackTime = 0.005f // 5ms
    private val releaseTime = 0.200f // 200ms
    private val threshold = 0.15f // ~ -16dB
    private val ratio = 4f // 4:1 compression
    private val makeupGain = 1.8f // ~+5dB boost for audible quiet-lift

    // Pre-calculated constants to optimize inner loop
    private val thresholdDb = 20f * kotlin.math.log10(threshold)
    private val invRatioMinusOne = (1f / ratio - 1f)

    // Observable state for diagnostics
    var queueInputCallCount = 0L
        private set
    val configuredEncoding: Int get() = inputAudioFormat.encoding

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        Log.i(
            "NightModeProcessor",
            "Configured: ${inputAudioFormat.sampleRate}Hz, ${inputAudioFormat.channelCount}ch, encoding: ${inputAudioFormat.encoding}",
        )
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputAudioFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        queueInputCallCount++

        val remaining = inputBuffer.remaining()
        val encoding = inputAudioFormat.encoding
        val isFloat = encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT
        val is16Bit = encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        
        val bytesPerSample = if (isFloat) 4 else if (is16Bit) 2 else 4
        val sampleCount = remaining / bytesPerSample

        // Reuse buffer to avoid allocations on every call (essential for Sony Bravia performance)
        if (reusableBuffer.capacity() < remaining) {
            reusableBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            reusableBuffer.clear()
        }

        if (!enabled) {
            reusableBuffer.put(inputBuffer)
            reusableBuffer.flip()
            outputBuffer = reusableBuffer
            return
        }

        val sampleRate = inputAudioFormat.sampleRate.toFloat()
        val attackCoef = Math.exp(-1.0 / (sampleRate * attackTime)).toFloat()
        val releaseCoef = Math.exp(-1.0 / (sampleRate * releaseTime)).toFloat()

        if (isFloat) {
            for (i in 0 until sampleCount) {
                val sample = inputBuffer.getFloat()
                val absSample = abs(sample)

                val coef = if (absSample > envelope) attackCoef else releaseCoef
                envelope = coef * envelope + (1f - coef) * absSample

                var gain = 1.0f
                if (envelope > threshold) {
                    val envDb = 20f * kotlin.math.log10(max(envelope, 0.0001f))
                    val reductionDb = invRatioMinusOne * (envDb - thresholdDb)
                    gain = 10f.pow(reductionDb / 20f)
                }

                reusableBuffer.putFloat(sample * gain * makeupGain)
            }
        } else if (is16Bit) {
            for (i in 0 until sampleCount) {
                val rawSample = inputBuffer.getShort()
                val sample = rawSample / 32768f
                val absSample = abs(sample)

                val coef = if (absSample > envelope) attackCoef else releaseCoef
                envelope = coef * envelope + (1f - coef) * absSample

                var gain = 1.0f
                if (envelope > threshold) {
                    val envDb = 20f * kotlin.math.log10(max(envelope, 0.0001f))
                    val reductionDb = invRatioMinusOne * (envDb - thresholdDb)
                    gain = 10f.pow(reductionDb / 20f)
                }

                val compressed = (sample * gain * makeupGain * 32768f).toInt().coerceIn(-32768, 32767).toShort()
                reusableBuffer.putShort(compressed)
            }
        } else {
            reusableBuffer.put(inputBuffer)
        }

        reusableBuffer.flip()
        outputBuffer = reusableBuffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        envelope = 0f
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        reusableBuffer = AudioProcessor.EMPTY_BUFFER
    }
}
