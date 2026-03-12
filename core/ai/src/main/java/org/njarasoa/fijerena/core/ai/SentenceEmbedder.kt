package org.njarasoa.fijerena.core.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.nio.MappedByteBuffer

/**
 * Encapsulates the TFLite Sentence-Transformer model for generating text embeddings.
 * Specialized for all-MiniLM-L6-v2.
 */
class SentenceEmbedder(private val context: Context) : Closeable {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var tokenizer: BertTokenizer? = null

    init {
        try {
            tokenizer = BertTokenizer(context, VOCAB_PATH)
            
            val tier = SearchCapabilityDetector(context).detectTier()
            val options = Interpreter.Options()

            if (tier == SearchCapabilityDetector.SearchTier.PREMIUM) {
                try {
                    gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate)
                    Log.i(TAG, "Initialized SentenceEmbedder with GPU acceleration")
                } catch (e: Throwable) {
                    // Catch LinkageError or ClassNotFound if GPU delegate is missing
                    Log.w(TAG, "GPU acceleration requested but not available: ${e.message}. Falling back to CPU.")
                    gpuDelegate = null
                    options.setNumThreads(4)
                }
            } else {
                options.setNumThreads(4)
                Log.i(TAG, "Initialized SentenceEmbedder with 4 CPU threads")
            }

            val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
            interpreter = Interpreter(modelBuffer, options)
            
            Log.i(TAG, "Model loaded successfully. Inputs: ${interpreter?.inputTensorCount}, Outputs: ${interpreter?.outputTensorCount}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model: ${e.message}", e)
            close()
        }
    }

    /**
     * Generate an embedding vector for the given text.
     */
    fun encode(text: String): FloatArray? {
        val interp = interpreter ?: return null
        val tok = tokenizer ?: return null
        
        return try {
            val tokenized = tok.tokenize(text)
            
            // Most TFLite BERT models expect Long inputs [1, SeqLen]
            val inputIds = Array(1) { LongArray(tokenized.inputIds.size) { i -> tokenized.inputIds[i].toLong() } }
            val attentionMask = Array(1) { LongArray(tokenized.attentionMask.size) { i -> tokenized.attentionMask[i].toLong() } }
            val tokenTypeIds = Array(1) { LongArray(tokenized.tokenTypeIds.size) { i -> tokenized.tokenTypeIds[i].toLong() } }

            // Output is typically [1, SeqLen, 384]
            val outputTensor = interp.getOutputTensor(0)
            val outputShape = outputTensor.shape() // [1, 128, 384]
            val seqLen = outputShape[1]
            val dim = outputShape[2]
            
            val outputBuffer = Array(1) { Array(seqLen) { FloatArray(dim) } }
            
            val inputs = arrayOf<Any>(inputIds, attentionMask, tokenTypeIds)
            val outputs = mutableMapOf<Int, Any>(0 to outputBuffer)
            
            interp.runForMultipleInputsOutputs(inputs, outputs)
            
            // Mean Pooling: Average the token embeddings weighted by attention mask
            val embedding = FloatArray(dim)
            var validTokenCount = 0
            for (i in 0 until seqLen) {
                if (tokenized.attentionMask[i] == 1) {
                    validTokenCount++
                    for (d in 0 until dim) {
                        embedding[d] += outputBuffer[0][i][d]
                    }
                }
            }
            
            if (validTokenCount > 0) {
                for (d in 0 until dim) {
                    embedding[d] /= validTokenCount.toFloat()
                }
            }
            
            embedding
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
        private const val VOCAB_PATH = "vocab.txt"
    }
}
