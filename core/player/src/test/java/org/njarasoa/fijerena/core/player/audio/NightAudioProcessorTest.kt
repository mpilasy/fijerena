package org.njarasoa.fijerena.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class NightAudioProcessorTest {
    private fun processor(
        channels: Int = 2,
        encoding: Int = C.ENCODING_PCM_FLOAT,
        on: Boolean = true,
    ): NightAudioProcessor {
        val processor = NightAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(SAMPLE_RATE, channels, encoding))
        processor.setEnabled(on)
        processor.flush()
        return processor
    }

    private fun dbToLinear(db: Double) = 10.0.pow(db / 20.0)

    private fun toDb(value: Double) = 20.0 * log10(value)

    /** [seconds] of a sine at [db] dBFS on each channel (one level per channel). */
    private fun tone(
        seconds: Double,
        vararg db: Double,
        hz: Double = TONE_HZ,
    ): FloatArray {
        val frames = (seconds * SAMPLE_RATE).toInt()
        val samples = FloatArray(frames * db.size)
        for (frame in 0 until frames) {
            val wave = sin(2.0 * PI * hz * frame / SAMPLE_RATE)
            for (channel in db.indices) samples[frame * db.size + channel] = (dbToLinear(db[channel]) * wave).toFloat()
        }
        return samples
    }

    private fun ByteBuffer.floats(): FloatArray {
        val out = FloatArray(remaining() / Float.SIZE_BYTES)
        for (i in out.indices) out[i] = getFloat()
        return out
    }

    private fun ByteBuffer.shorts(): ShortArray {
        val out = ShortArray(remaining() / Short.SIZE_BYTES)
        for (i in out.indices) out[i] = getShort()
        return out
    }

    private fun NightAudioProcessor.runFloat(samples: FloatArray): FloatArray {
        val input = ByteBuffer.allocateDirect(samples.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        samples.forEach { input.putFloat(it) }
        input.flip()
        queueInput(input)
        return getOutput().floats()
    }

    private fun NightAudioProcessor.run16(samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        queueInput(input)
        return getOutput().shorts()
    }

    private fun FloatArray.to16() = ShortArray(size) { (this[it] * 32767f).toInt().toShort() }

    private fun FloatArray.peakDb(
        from: Int = 0,
        to: Int = size,
    ): Double = toDb((from until to).maxOf { abs(this[it]) }.toDouble())

    private fun FloatArray.rmsDb(
        from: Int = 0,
        to: Int = size,
    ): Double = toDb(sqrt((from until to).sumOf { (this[it] * this[it]).toDouble() } / (to - from)))

    private fun ShortArray.toFloats() = FloatArray(size) { this[it] / 32768f }

    @Test
    fun silenceStaysSilence() {
        val output = processor(encoding = C.ENCODING_PCM_16BIT).run16(ShortArray(4_096))
        assertTrue(output.all { it == 0.toShort() })
    }

    @Test
    fun aQuietToneComesOutAtLeast6DbLouder() {
        val output = processor().runFloat(tone(1.0, -40.0, -40.0))
        val settled = output.rmsDb(output.size / 2)
        val input = tone(1.0, -40.0, -40.0).rmsDb(output.size / 2)
        assertTrue("got ${settled - input} dB", settled - input >= 6.0)
    }

    @Test
    fun aLoudBurstComesOutQuieterAndNeverAboveTheCeiling() {
        val input = tone(1.0, -3.0, -3.0)
        val output = processor().runFloat(input)
        val tail = output.size / 2
        assertTrue(output.rmsDb(tail) < input.rmsDb(tail))
        assertTrue("peak ${output.peakDb()} dB", output.peakDb() <= CEILING_DB + TOLERANCE_DB)
    }

    @Test
    fun aLoudBurstOn16BitStaysUnderTheCeilingAtTheAttack() {
        val output = processor(encoding = C.ENCODING_PCM_16BIT).run16(tone(0.2, -3.0, -3.0).to16())
        assertTrue(output.toFloats().peakDb() <= CEILING_DB + TOLERANCE_DB)
    }

    @Test
    fun oneGainForAllChannelsKeepsTheBalance() {
        // Stereo: a -30 dB left and a -36 dB right stay 6 dB apart after the same gain.
        val output = processor().runFloat(tone(1.0, -30.0, -36.0))
        val frames = output.size / 2
        val left = FloatArray(frames) { output[it * 2] }
        val right = FloatArray(frames) { output[it * 2 + 1] }
        val difference = left.rmsDb(frames / 2) - right.rmsDb(frames / 2)
        assertEquals(6.0, difference, 0.01)
    }

    @Test
    fun sixteenBitAndFloatGiveTheSameResult() {
        val float = tone(0.5, -6.0, -18.0)
        val fromFloat = processor().runFloat(float)
        val fromSixteen = processor(encoding = C.ENCODING_PCM_16BIT).run16(float.to16()).toFloats()
        assertEquals(fromFloat.size, fromSixteen.size)
        for (i in fromFloat.indices) {
            assertEquals("sample $i", fromFloat[i], fromSixteen[i], SAMPLE_TOLERANCE)
        }
    }

    @Test
    fun flushResetsTheEnvelope() {
        val processor = processor()
        processor.runFloat(tone(0.5, -3.0, -3.0))
        processor.flush()
        val output = processor.runFloat(tone(0.1, -40.0, -40.0))
        // With no flush the 300 ms release would still hold the gain far down here.
        val firstMs = (0.001 * SAMPLE_RATE).toInt() * 2
        val gain = output.peakDb(0, firstMs * 4) - (-40.0)
        assertTrue("gain $gain dB", gain >= 5.0)
    }

    @Test
    fun theOffRampEndsBitExact() {
        val processor = processor(encoding = C.ENCODING_PCM_16BIT)
        processor.run16(tone(0.5, -6.0, -6.0).to16())
        processor.setEnabled(false)
        // Longer than the 200 ms ramp.
        processor.run16(tone(0.3, -6.0, -6.0).to16())
        val input = tone(0.5, -6.0, -6.0).to16()
        assertArrayEquals(input, processor.run16(input))
    }

    @Test
    fun offFromTheStartIsBitExact() {
        val input = tone(0.2, -6.0, -40.0).to16()
        assertArrayEquals(input, processor(encoding = C.ENCODING_PCM_16BIT, on = false).run16(input))
    }

    @Test
    fun theCentreChannelIsBoostedBeforeTheCompressorWith51() {
        // 5.1, quiet enough for no compression: front left and the centre differ by the boost.
        val output = processor(channels = 6).runFloat(tone(0.5, -40.0, -40.0, -40.0, -40.0, -40.0, -40.0))
        val frames = output.size / 6
        val left = FloatArray(frames) { output[it * 6] }
        val centre = FloatArray(frames) { output[it * 6 + 2] }
        assertEquals(3.0, centre.rmsDb(frames / 2) - left.rmsDb(frames / 2), 0.05)
    }

    @Test
    fun inactiveForANonPcmFormat() {
        val processor = NightAudioProcessor()
        assertEquals(
            AudioProcessor.AudioFormat.NOT_SET,
            processor.configure(AudioProcessor.AudioFormat(SAMPLE_RATE, 6, C.ENCODING_AC3)),
        )
        assertFalse(processor.isActive())
    }

    @Test
    fun aHighResolutionFormatIsNotAccepted() {
        val processor = NightAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_32BIT))
        assertFalse(processor.isActive())
    }

    @Test
    fun gainDbReportsTheCompressorWhileOn() {
        val processor = processor()
        processor.runFloat(tone(1.0, -3.0, -3.0))
        assertTrue("gain ${processor.gainDb}", processor.gainDb < 0f)
        processor.setEnabled(false)
        processor.runFloat(tone(0.3, -3.0, -3.0))
        processor.runFloat(tone(0.1, -3.0, -3.0))
        assertEquals(0f, processor.gainDb, 0f)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val TONE_HZ = 220.0
        const val CEILING_DB = -1.0

        // One step of 16-bit at the ceiling is under 0.001 dB; this also covers the float tests.
        const val TOLERANCE_DB = 0.01
        const val SAMPLE_TOLERANCE = 3f / 32768f
    }
}
