package org.njarasoa.fijerena.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The file and the key of the Night Audio setting: this module reads it directly, it cannot see `AppSettings`. */
object NightAudioSetting {
    const val PREFS_NAME = "app_settings"
    const val KEY = "night_audio"
}

/**
 * Night Audio: quiet speech louder, loud sounds quieter, in one preset (the Night Audio plan,
 * NA-D1, NA-D3, NA-D4).
 *
 * A speech boost (the centre channel with 5.1 or more, a presence lift with stereo or mono), then a
 * feed-forward compressor with one gain for all channels so the image stays, then a peak limiter at
 * -1 dBFS. The compressor and the limiter work per frame, on the loudest channel of the frame.
 *
 * The processor is always in the chain. Off, it copies its input, bit-exact. [setEnabled] does not
 * reconfigure anything: the effect fades in or out over [RAMP_SECONDS], so there is no click. Runs on
 * the playback thread; [setEnabled] and [gainDb] are safe from any thread.
 */
@UnstableApi
class NightAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var enabled = false

    /** The gain now, in dB, make-up included. 0 while the effect is fully off. Any thread. */
    @Volatile
    var gainDb = 0f
        private set

    /** How much of the effect is in the output, 0 (dry) to 1 (wet). */
    private var mix = 0f
    private var mixStep = 0f

    private var attackCoefficient = 0f
    private var releaseCoefficient = 0f
    private var limiterReleaseCoefficient = 0f
    private var reductionDb = 0f
    private var limiterGain = 1f

    private var dry = FloatArray(0)
    private var wet = FloatArray(0)
    private var presenceLift = emptyArray<Biquad>()

    val isEnabled: Boolean get() = enabled

    fun setEnabled(on: Boolean) {
        enabled = on
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }

    // BaseAudioProcessor applies a new format here, and calls this on a seek or a new stream too:
    // every filter starts clean, and there is nothing to fade from, so the mix jumps to its target.
    override fun onFlush() {
        if (inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET) configureFilters(inputAudioFormat)
    }

    private fun configureFilters(format: AudioProcessor.AudioFormat) {
        val sampleRate = format.sampleRate.toFloat()
        attackCoefficient = smoothing(ATTACK_SECONDS, sampleRate)
        releaseCoefficient = smoothing(RELEASE_SECONDS, sampleRate)
        limiterReleaseCoefficient = 1f - smoothing(LIMITER_RELEASE_SECONDS, sampleRate)
        mixStep = 1f / (RAMP_SECONDS * sampleRate)
        mix = if (enabled) 1f else 0f
        reductionDb = 0f
        limiterGain = 1f
        gainDb = 0f
        dry = FloatArray(format.channelCount)
        wet = FloatArray(format.channelCount)
        presenceLift =
            if (format.channelCount <= MAX_LIFT_CHANNELS) {
                Array(format.channelCount) { Biquad.presenceLift(sampleRate) }
            } else {
                emptyArray()
            }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.hasRemaining()) {
            val on = enabled
            val output = replaceOutputBuffer(inputBuffer.remaining())
            if (!on && mix == 0f) {
                gainDb = 0f
                output.put(inputBuffer)
            } else {
                processFrames(inputBuffer, output, on)
            }
            output.flip()
        }
    }

    private fun processFrames(
        input: ByteBuffer,
        output: ByteBuffer,
        on: Boolean,
    ) {
        val format = inputAudioFormat
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val channels = format.channelCount
        val frames = input.remaining() / format.bytesPerFrame
        val centre = if (channels >= MIN_CENTRE_CHANNELS) CENTRE_CHANNEL else NO_CHANNEL
        var gainNowDb = 0f
        for (frame in 0 until frames) {
            mix = stepMix(on)
            var peak = 0f
            for (channel in 0 until channels) {
                val sample = if (isFloat) input.getFloat() else input.getShort() / PCM_16_SCALE
                dry[channel] = sample
                wet[channel] = speechBoost(channel, centre, sample)
                peak = maxOf(peak, abs(wet[channel]))
            }

            val targetDb =
                if (peak > THRESHOLD_LINEAR) {
                    (ln(peak) * DB_PER_NEPER - THRESHOLD_DB) * (1f - 1f / RATIO)
                } else {
                    0f
                }
            val coefficient = if (targetDb > reductionDb) attackCoefficient else releaseCoefficient
            reductionDb = targetDb + coefficient * (reductionDb - targetDb)
            val compressorDb = MAKE_UP_DB - reductionDb
            val compressorGain = exp(compressorDb * NEPER_PER_DB)

            val framePeak = peak * compressorGain
            val allowed = if (framePeak > CEILING_LINEAR) CEILING_LINEAR / framePeak else 1f
            limiterGain = min(allowed, limiterGain + (1f - limiterGain) * limiterReleaseCoefficient)
            val gain = compressorGain * limiterGain
            gainNowDb = compressorDb + ln(limiterGain) * DB_PER_NEPER

            for (channel in 0 until channels) {
                val value = dry[channel] + (wet[channel] * gain - dry[channel]) * mix
                if (isFloat) {
                    output.putFloat(value)
                } else {
                    output.putShort((value * PCM_16_SCALE).roundToInt().coerceIn(PCM_16_MIN, PCM_16_MAX).toShort())
                }
            }
        }
        gainDb = gainNowDb
    }

    private fun stepMix(on: Boolean): Float =
        if (on) {
            min(1f, mix + mixStep)
        } else {
            maxOf(0f, mix - mixStep)
        }

    private fun speechBoost(
        channel: Int,
        centre: Int,
        sample: Float,
    ): Float =
        when {
            channel == centre -> sample * CENTRE_BOOST_LINEAR
            presenceLift.isNotEmpty() -> presenceLift[channel].process(sample)
            else -> sample
        }

    override fun onReset() {
        mix = 0f
        reductionDb = 0f
        limiterGain = 1f
        gainDb = 0f
    }

    /** A peaking filter (the Audio EQ Cookbook form), for one channel. */
    private class Biquad(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float,
    ) {
        private var z1 = 0f
        private var z2 = 0f

        fun process(sample: Float): Float {
            val output = b0 * sample + z1
            z1 = b1 * sample - a1 * output + z2
            z2 = b2 * sample - a2 * output
            return output
        }

        companion object {
            fun presenceLift(sampleRate: Float): Biquad {
                val amplitude = 10f.pow(PRESENCE_GAIN_DB / DB_PER_AMPLITUDE_DECADE)
                val omega = 2f * PI.toFloat() * PRESENCE_CENTRE_HZ / sampleRate
                val alpha = sin(omega) / (2f * PRESENCE_Q)
                val a0 = 1f + alpha / amplitude
                return Biquad(
                    b0 = (1f + alpha * amplitude) / a0,
                    b1 = (-2f * cos(omega)) / a0,
                    b2 = (1f - alpha * amplitude) / a0,
                    a1 = (-2f * cos(omega)) / a0,
                    a2 = (1f - alpha / amplitude) / a0,
                )
            }
        }
    }

    companion object {
        private const val THRESHOLD_DB = -24f
        private const val RATIO = 3f
        private const val MAKE_UP_DB = 6f
        private const val ATTACK_SECONDS = 0.010f
        private const val RELEASE_SECONDS = 0.300f
        private const val LIMITER_RELEASE_SECONDS = 0.050f
        private const val RAMP_SECONDS = 0.200f
        private const val CENTRE_BOOST_DB = 3f
        private const val PRESENCE_GAIN_DB = 3f
        private const val PRESENCE_CENTRE_HZ = 2_000f

        // Two octaves wide: 1 kHz to 4 kHz.
        private const val PRESENCE_Q = 0.667f
        private const val MAX_LIFT_CHANNELS = 2
        private const val MIN_CENTRE_CHANNELS = 6

        // 5.1 and 7.1 PCM in Android order: front left, front right, centre, ...
        private const val CENTRE_CHANNEL = 2
        private const val NO_CHANNEL = -1

        private const val PCM_16_SCALE = 32768f
        private const val PCM_16_MIN = -32768
        private const val PCM_16_MAX = 32767
        private const val DB_PER_AMPLITUDE_DECADE = 40f
        private const val DB_PER_NEPER = 8.685889f
        private const val NEPER_PER_DB = 1f / DB_PER_NEPER

        private val THRESHOLD_LINEAR = exp(THRESHOLD_DB * NEPER_PER_DB)
        private val CEILING_LINEAR = exp(-1f * NEPER_PER_DB)
        private val CENTRE_BOOST_LINEAR = exp(CENTRE_BOOST_DB * NEPER_PER_DB)

        private fun smoothing(
            seconds: Float,
            sampleRate: Float,
        ): Float = exp(-1f / (seconds * sampleRate))
    }
}
