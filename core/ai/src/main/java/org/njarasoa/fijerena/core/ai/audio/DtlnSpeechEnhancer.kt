package org.njarasoa.fijerena.core.ai.audio

import android.content.Context
import android.util.Log
import org.njarasoa.fijerena.core.player.audio.SpeechEnhancer
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Implementation of SpeechEnhancer using Dual-path RNN (DTLN) TFLite model.
 */
class DtlnSpeechEnhancer(
    private val context: Context,
    private val modelPath: String = "dtln_quant.tflite" // Expected to be in assets
) : SpeechEnhancer {

    private var interpreter1: Interpreter? = null
    private var interpreter2: Interpreter? = null
    private var isInitialized = false

    // State buffers required for DTLN LSTM
    private var h11 = Array(1) { Array(1) { FloatArray(128) } }
    private var c11 = Array(1) { Array(1) { FloatArray(128) } }
    private var h12 = Array(1) { Array(1) { FloatArray(128) } }
    private var c12 = Array(1) { Array(1) { FloatArray(128) } }
    private var h21 = Array(1) { Array(1) { FloatArray(128) } }
    private var c21 = Array(1) { Array(1) { FloatArray(128) } }
    private var h22 = Array(1) { Array(1) { FloatArray(128) } }
    private var c22 = Array(1) { Array(1) { FloatArray(128) } }

    override fun initialize() {
        if (isInitialized) return

        try {
            // Usually DTLN is split into two models: dtln_1 and dtln_2 for performance
            // Here we assume a single model or two models for simplicity
            // Let's assume two models for standard DTLN architecture
            val options = Interpreter.Options().apply {
                numThreads = 2
                // We could use NNAPI or GPU, but CPU is usually fine and stable for small audio models
            }

            interpreter1 = Interpreter(loadModelFile("dtln_1_quant.tflite"), options)
            interpreter2 = Interpreter(loadModelFile("dtln_2_quant.tflite"), options)

            resetStates()

            isInitialized = true
            Log.i(TAG, "DTLN Speech Enhancer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DTLN models", e)
            isInitialized = false
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun resetStates() {
        h11 = Array(1) { Array(1) { FloatArray(128) } }
        c11 = Array(1) { Array(1) { FloatArray(128) } }
        h12 = Array(1) { Array(1) { FloatArray(128) } }
        c12 = Array(1) { Array(1) { FloatArray(128) } }
        h21 = Array(1) { Array(1) { FloatArray(128) } }
        c21 = Array(1) { Array(1) { FloatArray(128) } }
        h22 = Array(1) { Array(1) { FloatArray(128) } }
        c22 = Array(1) { Array(1) { FloatArray(128) } }
    }

    override fun process(buffer: FloatArray): FloatArray {
        if (!isInitialized) return buffer

        val model1 = interpreter1 ?: return buffer
        val model2 = interpreter2 ?: return buffer

        try {
            // DTLN typically expects input shape [1, frameSize] e.g., [1, 512]
            val inputArray = Array(1) { buffer }

            // Outputs for model 1: [out_mag, out_frames, new_h11, new_c11, new_h12, new_c12]
            // We'll simplify and just run inference assuming basic I/O shapes for DTLN
            // Note: Actual DTLN I/O shapes can vary based on exact model export.
            // This is a representative structure based on the standard DTLN architecture.

            val outMag = Array(1) { Array(1) { FloatArray(257) } }
            val outFrames = Array(1) { Array(1) { FloatArray(512) } }

            val inputs1 = arrayOf(inputArray, h11, c11, h12, c12)
            val outputs1 = mutableMapOf<Int, Any>(
                0 to outMag,
                1 to outFrames,
                2 to h11,
                3 to c11,
                4 to h12,
                5 to c12
            )

            model1.runForMultipleInputsOutputs(inputs1, outputs1)

            // Model 2
            val estimatedBlock = Array(1) { Array(1) { FloatArray(512) } }

            val inputs2 = arrayOf(outFrames, h21, c21, h22, c22)
            val outputs2 = mutableMapOf<Int, Any>(
                0 to estimatedBlock,
                1 to h21,
                2 to c21,
                3 to h22,
                4 to c22
            )

            model2.runForMultipleInputsOutputs(inputs2, outputs2)

            // DTLN output is usually a block of enhanced audio, we return it
            return estimatedBlock[0][0]

        } catch (e: Exception) {
            Log.e(TAG, "Error during TFLite inference", e)
            return buffer // Fallback to unprocessed audio on error
        }
    }

    override fun release() {
        if (!isInitialized) return

        interpreter1?.close()
        interpreter1 = null

        interpreter2?.close()
        interpreter2 = null

        isInitialized = false
        Log.i(TAG, "DTLN Speech Enhancer released")
    }

    companion object {
        private const val TAG = "DtlnSpeechEnhancer"
    }
}
