package org.njarasoa.fijerena.core.player.audio

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

    var enabled = false
        set(value) {
            if (field != value) {
                field = value
                // We don't reset the buffer here to avoid pops, 
                // just change the processing logic in queueInput
            }
        }

    // Compressor parameters
    private var envelope = 0f
    private val attackTime = 0.005f  // 5ms
    private val releaseTime = 0.200f // 200ms
    private val threshold = 0.15f    // ~ -16dB
    private val ratio = 4f           // 4:1 compression
    private val makeupGain = 1.2f    // +1.5dB boost

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            // We expect FLOAT PCM for high precision processing
            // The service is configured to enable float output
            this.inputAudioFormat = inputAudioFormat
            return inputAudioFormat
        }
        this.inputAudioFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputAudioFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val remaining = inputBuffer.remaining()
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        // Apply compression (Simplified single-band for performance)
        val sampleCount = remaining / 4 // Float size
        val sampleRate = inputAudioFormat.sampleRate.toFloat()
        
        val attackCoef = Math.exp(-1.0 / (sampleRate * attackTime)).toFloat()
        val releaseCoef = Math.exp(-1.0 / (sampleRate * releaseTime)).toFloat()

        for (i in 0 until sampleCount) {
            val sample = inputBuffer.getFloat()
            val absSample = abs(sample)

            // Envelope follower
            val coef = if (absSample > envelope) attackCoef else releaseCoef
            envelope = coef * envelope + (1f - coef) * absSample

            // Calculate gain reduction
            var gain = 1.0f
            if (envelope > threshold) {
                // Standard compressor equation: 
                // Gr(dB) = (1/ratio - 1) * (Env(dB) - Threshold(dB))
                val envDb = 20f * kotlin.math.log10(max(envelope, 0.0001f))
                val thresholdDb = 20f * kotlin.math.log10(threshold)
                val reductionDb = (1f / ratio - 1f) * (envDb - thresholdDb)
                gain = 10f.pow(reductionDb / 20f)
            }

            outputBuffer.putFloat(sample * gain * makeupGain)
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
