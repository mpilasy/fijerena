package org.njarasoa.fijerena.core.ai.audio

import android.content.Context
import android.util.Log
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.device.DeviceType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Two-stage DTLN (Dual-signal Transformation LSTM Network) speech enhancer.
 *
 * DTLN processes audio in two stages:
 *   1. Model 1 (STFT domain): magnitude spectrum + LSTM state → noise mask + new state
 *      Apply mask: estimated = mag * mask, reconstruct via IFFT with original phase
 *   2. Model 2 (time domain): estimated block + LSTM state → enhanced block + new state
 *
 * The pipeline operates on 512-sample windows with 128-sample hops at 16kHz.
 * Each call to [processBlock] accepts 128 new samples, shifts them into the
 * internal window, runs both models, and returns 128 enhanced samples via
 * overlap-add.
 *
 * Reference: https://github.com/breizhn/DTLN
 * License: MIT (Nils L. Westhausen, Interspeech 2020)
 */
class AiSpeechEnhancer(private val context: Context) : Closeable {

    private var interpreter1: Interpreter? = null
    private var interpreter2: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // LSTM states carried between frames (shapes read from model at init)
    private var state1: ByteBuffer? = null
    private var state2: ByteBuffer? = null
    private var state1Size: Int = 0
    private var state2Size: Int = 0

    // Sliding window buffers (maintained across processBlock calls)
    private val inBuffer = FloatArray(BLOCK_LEN)
    private val outBuffer = FloatArray(BLOCK_LEN)

    private var initialized = false

    fun initialize(): Boolean {
        if (initialized) return true

        return try {
            val deviceCaps = DeviceDetector.detect()
            val options1 = createOptions(deviceCaps.deviceType)
            val options2 = createOptions(deviceCaps.deviceType)

            // Verify both model files exist
            val dtlnAssets = context.assets.list("dtln") ?: emptyArray()
            if (!dtlnAssets.contains("model_quant_1.tflite") || !dtlnAssets.contains("model_quant_2.tflite")) {
                Log.e(TAG, "DTLN model files not found in assets/dtln/. AI will run in passthrough mode.")
                initialized = true
                return true
            }

            val model1Buffer = FileUtil.loadMappedFile(context, MODEL_1_PATH)
            val model2Buffer = FileUtil.loadMappedFile(context, MODEL_2_PATH)

            interpreter1 = Interpreter(model1Buffer, options1)
            interpreter2 = Interpreter(model2Buffer, options2)

            // Read state tensor shapes from models
            val interp1 = interpreter1!!
            val interp2 = interpreter2!!

            // Model 1 inputs: [0]=magnitude [1,1,257], [1]=LSTM state
            // Model 1 outputs: [0]=mask, [1]=new LSTM state
            val state1Shape = interp1.getInputTensor(1).shape()
            state1Size = state1Shape.fold(1) { acc, v -> acc * v }

            // Model 2 inputs: [0]=estimated block [1,1,512], [1]=LSTM state
            // Model 2 outputs: [0]=enhanced block, [1]=new LSTM state
            val state2Shape = interp2.getInputTensor(1).shape()
            state2Size = state2Shape.fold(1) { acc, v -> acc * v }

            resetStates()
            initialized = true

            Log.i(TAG, "DTLN initialized. Model1 inputs: ${interp1.inputTensorCount}, " +
                    "Model2 inputs: ${interp2.inputTensorCount}, " +
                    "State1 size: $state1Size, State2 size: $state2Size")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DTLN: ${e.message}", e)
            close()
            false
        }
    }

    private fun createOptions(deviceType: DeviceType): Interpreter.Options {
        val options = Interpreter.Options()
        try {
            val delegate = GpuDelegate()
            options.addDelegate(delegate)
            if (gpuDelegate == null) gpuDelegate = delegate
            Log.i(TAG, "Using GPU delegate for $deviceType")
        } catch (e: Throwable) {
            Log.w(TAG, "GPU delegate not available, using CPU: ${e.message}")
            options.setNumThreads(2)
        }
        return options
    }

    /**
     * Process a block of [BLOCK_SHIFT] (128) new samples through the DTLN pipeline.
     *
     * Internally shifts the 512-sample window, runs FFT → Model1 → IFFT → Model2,
     * and returns 128 enhanced samples via overlap-add.
     *
     * @param newSamples exactly [BLOCK_SHIFT] (128) PCM float samples at 16kHz
     * @return [BLOCK_SHIFT] enhanced samples, or null if inference fails
     */
    fun processBlock(newSamples: FloatArray): FloatArray? {
        val interp1 = interpreter1 ?: return null
        val interp2 = interpreter2 ?: return null
        if (newSamples.size != BLOCK_SHIFT) {
            Log.w(TAG, "Expected $BLOCK_SHIFT samples, got ${newSamples.size}")
            return null
        }

        return try {
            // Shift input buffer: slide left by BLOCK_SHIFT, append new samples
            System.arraycopy(inBuffer, BLOCK_SHIFT, inBuffer, 0, BLOCK_LEN - BLOCK_SHIFT)
            System.arraycopy(newSamples, 0, inBuffer, BLOCK_LEN - BLOCK_SHIFT, BLOCK_SHIFT)

            // --- Stage 1: STFT domain ---
            val (magnitude, phase) = DtlnFft.rfft(inBuffer)

            // Prepare Model 1 inputs: magnitude [1,1,257] and LSTM state
            val magInput = createFloatBuffer(FFT_BINS)
            for (v in magnitude) magInput.putFloat(v)
            magInput.rewind()

            val maskOutput = createFloatBuffer(FFT_BINS)
            val newState1 = createFloatBuffer(state1Size)

            interp1.runForMultipleInputsOutputs(
                arrayOf(magInput, state1!!),
                mapOf(0 to maskOutput, 1 to newState1)
            )
            state1 = newState1

            // Apply mask in frequency domain: estimated = magnitude * mask
            maskOutput.rewind()
            val maskedMag = FloatArray(FFT_BINS)
            for (i in 0 until FFT_BINS) {
                maskedMag[i] = magnitude[i] * maskOutput.getFloat()
            }

            // IFFT back to time domain using original phase
            val estimatedBlock = DtlnFft.irfft(maskedMag, phase, BLOCK_LEN)

            // --- Stage 2: Time domain ---
            val estInput = createFloatBuffer(BLOCK_LEN)
            for (v in estimatedBlock) estInput.putFloat(v)
            estInput.rewind()

            val enhancedOutput = createFloatBuffer(BLOCK_LEN)
            val newState2 = createFloatBuffer(state2Size)

            interp2.runForMultipleInputsOutputs(
                arrayOf(estInput, state2!!),
                mapOf(0 to enhancedOutput, 1 to newState2)
            )
            state2 = newState2

            // --- Overlap-add output ---
            // Shift output buffer left by BLOCK_SHIFT, zero the tail
            System.arraycopy(outBuffer, BLOCK_SHIFT, outBuffer, 0, BLOCK_LEN - BLOCK_SHIFT)
            for (i in (BLOCK_LEN - BLOCK_SHIFT) until BLOCK_LEN) {
                outBuffer[i] = 0f
            }

            // Add enhanced block to output buffer
            enhancedOutput.rewind()
            for (i in 0 until BLOCK_LEN) {
                outBuffer[i] += enhancedOutput.getFloat()
            }

            // Return the first BLOCK_SHIFT samples
            outBuffer.copyOfRange(0, BLOCK_SHIFT)
        } catch (e: Exception) {
            Log.e(TAG, "DTLN inference failed: ${e.message}")
            null
        }
    }

    /**
     * Legacy single-frame interface for compatibility with DialogueBoostProcessor.
     * Internally processes the frame as multiple BLOCK_SHIFT-sized hops.
     *
     * @param inputFrame [FRAME_SIZE] (512) PCM float samples at 16kHz
     * @return enhanced [FRAME_SIZE] samples, or null on failure
     */
    fun enhance(inputFrame: FloatArray): FloatArray? {
        if (inputFrame.size != FRAME_SIZE) return null

        val result = FloatArray(FRAME_SIZE)
        val hopsPerFrame = FRAME_SIZE / BLOCK_SHIFT

        for (hop in 0 until hopsPerFrame) {
            val hopSamples = inputFrame.copyOfRange(hop * BLOCK_SHIFT, (hop + 1) * BLOCK_SHIFT)
            val enhanced = processBlock(hopSamples) ?: return null
            System.arraycopy(enhanced, 0, result, hop * BLOCK_SHIFT, BLOCK_SHIFT)
        }
        return result
    }

    /**
     * Reset LSTM states and buffers — call on seek, track change, or stream switch.
     */
    fun resetStates() {
        state1 = allocateZeroBuffer(state1Size)
        state2 = allocateZeroBuffer(state2Size)
        inBuffer.fill(0f)
        outBuffer.fill(0f)
    }

    private fun createFloatBuffer(floatCount: Int): ByteBuffer {
        return ByteBuffer.allocateDirect(floatCount * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
    }

    private fun allocateZeroBuffer(floatCount: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(floatCount * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
        for (i in 0 until floatCount) {
            buffer.putFloat(0f)
        }
        buffer.rewind()
        return buffer
    }

    override fun close() {
        interpreter1?.close()
        interpreter1 = null
        interpreter2?.close()
        interpreter2 = null
        gpuDelegate?.close()
        gpuDelegate = null
        state1 = null
        state2 = null
        initialized = false
    }

    companion object {
        private const val TAG = "AiSpeechEnhancer"
        private const val MODEL_1_PATH = "dtln/model_quant_1.tflite"
        private const val MODEL_2_PATH = "dtln/model_quant_2.tflite"

        /** DTLN window size: 512 samples at 16kHz = 32ms */
        const val FRAME_SIZE = 512

        /** DTLN hop size: 128 samples at 16kHz = 8ms */
        const val BLOCK_SHIFT = 128

        /** DTLN block length (same as FRAME_SIZE) */
        const val BLOCK_LEN = 512

        /** Number of FFT bins for real FFT of 512 points: 512/2 + 1 = 257 */
        private const val FFT_BINS = BLOCK_LEN / 2 + 1

        /** DTLN model sample rate */
        const val MODEL_SAMPLE_RATE = 16000

        private const val FLOAT_SIZE = 4
    }
}
