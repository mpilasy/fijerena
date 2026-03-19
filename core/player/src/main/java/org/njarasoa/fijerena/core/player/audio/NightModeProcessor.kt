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

    // Observable state for diagnostics
    var queueInputCallCount = 0L
        private set
    val configuredEncoding: Int get() = inputAudioFormat.encoding

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        Log.e(
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
        val isFloat = inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT
        val is16Bit = inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        val bytesPerSample =
            if (isFloat) {
                4
            } else if (is16Bit) {
                2
            } else {
                4
            }
        val sampleCount = remaining / bytesPerSample

        if (outputBuffer == AudioProcessor.EMPTY_BUFFER || outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (isFloat) {
            val sampleRate = inputAudioFormat.sampleRate.toFloat()
            val attackCoef = Math.exp(-1.0 / (sampleRate * attackTime)).toFloat()
            val releaseCoef = Math.exp(-1.0 / (sampleRate * releaseTime)).toFloat()

            for (i in 0 until sampleCount) {
                val sample = inputBuffer.getFloat()
                val absSample = abs(sample)

                val coef = if (absSample > envelope) attackCoef else releaseCoef
                envelope = coef * envelope + (1f - coef) * absSample

                var gain = 1.0f
                if (envelope > threshold) {
                    val envDb = 20f * kotlin.math.log10(max(envelope, 0.0001f))
                    val thresholdDb = 20f * kotlin.math.log10(threshold)
                    val reductionDb = (1f / ratio - 1f) * (envDb - thresholdDb)
                    gain = 10f.pow(reductionDb / 20f)
                }

                outputBuffer.putFloat(sample * gain * makeupGain)
            }
        } else if (is16Bit) {
            val sampleRate = inputAudioFormat.sampleRate.toFloat()
            val attackCoef = Math.exp(-1.0 / (sampleRate * attackTime)).toFloat()
            val releaseCoef = Math.exp(-1.0 / (sampleRate * releaseTime)).toFloat()

            for (i in 0 until sampleCount) {
                val sample = inputBuffer.getShort() / 32768f
                val absSample = abs(sample)

                val coef = if (absSample > envelope) attackCoef else releaseCoef
                envelope = coef * envelope + (1f - coef) * absSample

                var gain = 1.0f
                if (envelope > threshold) {
                    val envDb = 20f * kotlin.math.log10(max(envelope, 0.0001f))
                    val thresholdDb = 20f * kotlin.math.log10(threshold)
                    val reductionDb = (1f / ratio - 1f) * (envDb - thresholdDb)
                    gain = 10f.pow(reductionDb / 20f)
                }

                val compressed = (sample * gain * makeupGain * 32768f).toInt().coerceIn(-32768, 32767).toShort()
                outputBuffer.putShort(compressed)
            }
        } else {
            // Unknown encoding: passthrough
            outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
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
    }
}
