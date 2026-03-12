package org.njarasoa.fijerena.core.ai

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * A basic WordPiece tokenizer for BERT-based models.
 */
class BertTokenizer(context: Context, vocabPath: String) {
    private val vocab: Map<String, Int>
    private val maxSeqLen = 128

    init {
        val tempVocab = mutableMapOf<String, Int>()
        context.assets.open(vocabPath).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var index = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    tempVocab[line] = index++
                }
            }
        }
        vocab = tempVocab
    }

    data class TokenizedResult(
        val inputIds: IntArray,
        val attentionMask: IntArray,
        val tokenTypeIds: IntArray
    )

    fun tokenize(text: String): TokenizedResult {
        val tokens = mutableListOf<String>()
        tokens.add("[CLS]")
        
        val cleanText = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        val words = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
        
        for (word in words) {
            var remaining = word
            while (remaining.isNotEmpty()) {
                var found = false
                for (i in remaining.length downTo 1) {
                    val sub = if (tokens.size > 1 && tokens.last() != "[CLS]" && !found) "##" + remaining.substring(0, i) else remaining.substring(0, i)
                    val id = vocab[sub]
                    if (id != null) {
                        tokens.add(sub)
                        remaining = remaining.substring(i)
                        found = true
                        break
                    }
                }
                if (!found) {
                    tokens.add("[UNK]")
                    break
                }
            }
        }
        
        tokens.add("[SEP]")
        
        val inputIds = IntArray(maxSeqLen) { 0 }
        val attentionMask = IntArray(maxSeqLen) { 0 }
        val tokenTypeIds = IntArray(maxSeqLen) { 0 }
        
        for (i in 0 until minOf(tokens.size, maxSeqLen)) {
            inputIds[i] = vocab[tokens[i]] ?: vocab["[UNK]"]!!
            attentionMask[i] = 1
        }
        
        return TokenizedResult(inputIds, attentionMask, tokenTypeIds)
    }
}
