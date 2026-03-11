package org.njarasoa.fijerena.core.network.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.nio.MappedByteBuffer

/**
 * Encapsulates the TFLite Sentence-Transformer model for generating text embeddings.
 */
class SentenceEmbedder(private val context: Context) : Closeable {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    init {
        try {
            val tier = SearchCapabilityDetector(context).detectTier()
            val options = Interpreter.Options()

            if (tier == SearchCapabilityDetector.SearchTier.PREMIUM) {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                Log.i(TAG, "Initialized SentenceEmbedder with GPU acceleration")
            } else {
                options.setNumThreads(4)
                Log.i(TAG, "Initialized SentenceEmbedder with 4 CPU threads")
            }

            val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
            interpreter = Interpreter(modelBuffer, options)
            
            // Verify output dimension
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.i(TAG, "Model loaded successfully. Output shape: ${outputShape?.contentToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model: ${e.message}", e)
            close()
        }
    }

    /**
     * Generate an embedding vector for the given text.
     * Note: This assumes the model includes the tokenizer in the graph (BERT/MobileBERT style).
     * If using a raw model, a separate tokenization step would be required here.
     */
    fun encode(text: String): FloatArray? {
        val interp = interpreter ?: return null
        
        return try {
            // Input: String (if supported by model) or tokenized IDs
            // For now, we assume a model that accepts String input directly or 
            // handle the most common output: [1, DIM]
            val outputTensor = interp.getOutputTensor(0)
            val outputDim = outputTensor.shape()[1]
            val output = Array(1) { FloatArray(outputDim) }
            
            // This is a simplified call. Real BERT models usually take 3 inputs (ids, mask, segments).
            // Many 'all-in-one' mobile models take a single string input.
            val inputs = arrayOf<Any>(text)
            val outputs = mutableMapOf<Int, Any>(0 to output)
            
            interp.runForMultipleInputsOutputs(inputs, outputs)
            output[0]
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            null
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    companion object {
        private const val TAG = "SentenceEmbedder"
        private const val MODEL_PATH = "sentence_transformer.tflite"
    }
}
