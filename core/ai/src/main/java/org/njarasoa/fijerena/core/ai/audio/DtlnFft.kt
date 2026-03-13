package org.njarasoa.fijerena.core.ai.audio

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal real-valued FFT/IFFT for DTLN speech enhancement.
 *
 * Implements radix-2 Cooley-Tukey for power-of-2 sizes.
 * Optimized for the fixed 512-point transform used by DTLN.
 */
object DtlnFft {

    /**
     * Real FFT: 512 real samples → 257 complex values (magnitude + phase).
     *
     * @param input 512 real-valued samples
     * @return Pair(magnitudes[257], phases[257])
     */
    fun rfft(input: FloatArray): Pair<FloatArray, FloatArray> {
        val n = input.size
        // Complex FFT on n points: interleave real/imag
        val re = FloatArray(n) { input[it] }
        val im = FloatArray(n)
        fft(re, im, false)

        // For real input, output is symmetric: only need n/2+1 bins
        val bins = n / 2 + 1
        val mag = FloatArray(bins)
        val phase = FloatArray(bins)
        for (i in 0 until bins) {
            mag[i] = sqrt(re[i] * re[i] + im[i] * im[i])
            phase[i] = atan2(im[i], re[i])
        }
        return Pair(mag, phase)
    }

    /**
     * Inverse real FFT: 257 complex values (magnitude + phase) → 512 real samples.
     *
     * @param magnitude 257 magnitude values
     * @param phase 257 phase values
     * @param outputSize expected output size (512)
     * @return 512 real-valued samples
     */
    fun irfft(magnitude: FloatArray, phase: FloatArray, outputSize: Int): FloatArray {
        val n = outputSize
        val re = FloatArray(n)
        val im = FloatArray(n)
        val bins = n / 2 + 1

        // Fill first half + DC and Nyquist
        for (i in 0 until bins) {
            re[i] = magnitude[i] * cos(phase[i])
            im[i] = magnitude[i] * sin(phase[i])
        }

        // Fill conjugate-symmetric second half
        for (i in 1 until n / 2) {
            re[n - i] = re[i]
            im[n - i] = -im[i]
        }

        fft(re, im, true)

        return re
    }

    /**
     * In-place Cooley-Tukey radix-2 FFT/IFFT.
     */
    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }

        // FFT butterfly
        val sign = if (inverse) 1.0 else -1.0
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = sign * 2.0 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until halfLen) {
                    val u = i + k
                    val v = u + halfLen
                    val tRe = curRe * re[v] - curIm * im[v]
                    val tIm = curRe * im[v] + curIm * re[v]
                    re[v] = re[u] - tRe
                    im[v] = im[u] - tIm
                    re[u] = re[u] + tRe
                    im[u] = im[u] + tIm
                    val newRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = newRe
                }
                i += len
            }
            len = len shl 1
        }

        // Normalize for inverse
        if (inverse) {
            val inv = 1f / n
            for (i in 0 until n) {
                re[i] *= inv
                im[i] *= inv
            }
        }
    }
}
