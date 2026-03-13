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
import java.nio.MappedByteBuffer

/**
 * Wraps a DTLN (Dual-signal Transformation LSTM Network) TFLite model for
 * real-time speech enhancement. The model operates on 512-sample frames at
 * 16kHz (32ms per frame) and carries LSTM hidden/cell states between frames
 * for temporal continuity.
 *
 * Per-device hardware delegate selection:
 * - NVIDIA Shield: GPU delegate (Tegra X1+ GPU)
 * - Mobile (Snapdragon): NNAPI delegate (Hexagon DSP)
 * - Other: GPU delegate with CPU fallback
 */
class AiSpeechEnhancer(private val context: Context) : Closeable {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // DTLN carries LSTM hidden/cell states between frames for temporal continuity
    private var lstmState1: ByteBuffer? = null
    private var lstmState2: ByteBuffer? = null

    private var initialized = false

    fun initialize(): Boolean {
        if (initialized) return true

        return try {
            val deviceCaps = DeviceDetector.detect()
            val options = Interpreter.Options()

            when (deviceCaps.deviceType) {
                DeviceType.NVIDIA_SHIELD -> {
                    try {
                        gpuDelegate = GpuDelegate()
                        options.addDelegate(gpuDelegate)
                        Log.i(TAG, "AiSpeechEnhancer using GPU delegate (Shield)")
                    } catch (e: Throwable) {
                        Log.w(TAG, "GPU delegate failed, falling back to CPU: ${e.message}")
                        gpuDelegate = null
                        options.setNumThreads(2)
                    }
                }
                DeviceType.GENERIC_MOBILE -> {
                    // Prefer GPU on mobile too; NNAPI can be unstable across vendors
                    try {
                        gpuDelegate = GpuDelegate()
                        options.addDelegate(gpuDelegate)
                        Log.i(TAG, "AiSpeechEnhancer using GPU delegate (Mobile)")
                    } catch (e: Throwable) {
                        Log.w(TAG, "GPU delegate not available on mobile, using CPU: ${e.message}")
                        gpuDelegate = null
                        options.setNumThreads(2)
                    }
                }
                else -> {
                    try {
                        gpuDelegate = GpuDelegate()
                        options.addDelegate(gpuDelegate)
                        Log.i(TAG, "AiSpeechEnhancer using GPU delegate")
                    } catch (e: Throwable) {
                        Log.w(TAG, "GPU delegate not available, using CPU: ${e.message}")
                        gpuDelegate = null
                        options.setNumThreads(2)
                    }
                }
            }

            // Verify model existence before loading
            val assets = context.assets.list("") ?: emptyArray()
            val modelExists = assets.contains(MODEL_PATH)
            
            if (!modelExists) {
                Log.e(TAG, "CRITICAL: AI Model $MODEL_PATH not found in assets! AI will run in dummy passthrough mode.")
                initialized = true // Mark as initialized so processor stays in chain
                return true
            }

            val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
            interpreter = Interpreter(modelBuffer, options)
            resetStates()
            initialized = true

            Log.i(TAG, "AiSpeechEnhancer initialized. Inputs: ${interpreter?.inputTensorCount}, Outputs: ${interpreter?.outputTensorCount}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AiSpeechEnhancer: ${e.message}", e)
            close()
            false
        }
    }

    /**
     * Run inference on a single 512-sample frame at 16kHz.
     * States are carried forward automatically for temporal continuity.
     *
     * @param inputFrame 512 PCM float samples
     * @return enhanced 512-sample frame, or null if inference fails
     */
    fun enhance(inputFrame: FloatArray): FloatArray? {
        val interp = interpreter ?: return null
        if (inputFrame.size != FRAME_SIZE) {
            Log.w(TAG, "Expected frame size $FRAME_SIZE, got ${inputFrame.size}")
            return null
        }

        return try {
            // Prepare input: [1, 512]
            val inputBuffer = ByteBuffer.allocateDirect(FRAME_SIZE * FLOAT_SIZE)
                .order(ByteOrder.nativeOrder())
            for (sample in inputFrame) {
                inputBuffer.putFloat(sample)
            }
            inputBuffer.rewind()

            // Prepare output buffers
            val outputBuffer = ByteBuffer.allocateDirect(FRAME_SIZE * FLOAT_SIZE)
                .order(ByteOrder.nativeOrder())
            val outputState1 = ByteBuffer.allocateDirect(STATE_SIZE * FLOAT_SIZE)
                .order(ByteOrder.nativeOrder())
            val outputState2 = ByteBuffer.allocateDirect(STATE_SIZE * FLOAT_SIZE)
                .order(ByteOrder.nativeOrder())

            val inputs = arrayOf<Any>(inputBuffer, lstmState1!!, lstmState2!!)
            val outputs = mutableMapOf<Int, Any>(
                0 to outputBuffer,
                1 to outputState1,
                2 to outputState2
            )

            interp.runForMultipleInputsOutputs(inputs, outputs)

            // Update states for next frame
            lstmState1 = outputState1
            lstmState2 = outputState2

            // Extract enhanced audio
            outputBuffer.rewind()
            val result = FloatArray(FRAME_SIZE)
            outputBuffer.asFloatBuffer().get(result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            null
        }
    }

    /**
     * Reset LSTM states — call on seek, track change, or stream switch.
     * Zeroing the states forces the model to start fresh without prior context.
     */
    fun resetStates() {
        lstmState1 = allocateZeroBuffer(STATE_SIZE)
        lstmState2 = allocateZeroBuffer(STATE_SIZE)
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
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        lstmState1 = null
        lstmState2 = null
        initialized = false
    }

    companion object {
        private const val TAG = "AiSpeechEnhancer"
        private const val MODEL_PATH = "dtln_quantized.tflite"

        /** DTLN frame size: 512 samples at 16kHz = 32ms */
        const val FRAME_SIZE = 512

        /** DTLN model sample rate */
        const val MODEL_SAMPLE_RATE = 16000

        /** LSTM state size (model-dependent, typical for DTLN) */
        private const val STATE_SIZE = 128

        private const val FLOAT_SIZE = 4
    }
}
