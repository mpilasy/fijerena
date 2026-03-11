package org.njarasoa.fijerena.core.network.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgProgrammeEntity

/**
 * Orchestrates semantic search queries.
 */
class SemanticSearchEngine(private val context: Context) {

    private val detector = SearchCapabilityDetector(context)

    data class SemanticResult(
        val programme: EpgProgrammeEntity,
        val score: Float
    )

    /**
     * Perform a semantic search for the given query.
     * Returns a list of programmes sorted by conceptual similarity.
     */
    suspend fun search(query: String, limit: Int = 50): List<SemanticResult> = withContext(Dispatchers.Default) {
        if (detector.detectTier() != SearchCapabilityDetector.SearchTier.PREMIUM) {
            return@withContext emptyList()
        }

        val embedder = SentenceEmbedder(context)
        val queryVector = embedder.encode(query) ?: run {
            embedder.close()
            return@withContext emptyList()
        }

        val db = EpgIndexDatabase.getInstance(context)
        val dao = db.epgIndexDao()

        // Search window: last 24h to next 48h
        val now = System.currentTimeMillis() / 1000L
        val windowStart = now - 86400
        val windowEnd = now + 172800

        val candidates = withContext(Dispatchers.IO) {
            dao.getProgrammesWithEmbeddings(windowStart, windowEnd)
        }

        Log.d(TAG, "Comparing query against ${candidates.size} candidates...")

        val results = candidates.mapNotNull { prog ->
            val progBytes = prog.embedding ?: return@mapNotNull null
            val progVector = VectorUtils.toFloatArray(progBytes)
            val score = VectorUtils.cosineSimilarity(queryVector, progVector)
            
            // Minimum threshold for relevance (empirical value)
            if (score > 0.3f) {
                SemanticResult(prog, score)
            } else {
                null
            }
        }.sortedByDescending { it.score }.take(limit)

        embedder.close()
        Log.i(TAG, "Semantic search complete. Found ${results.size} relevant results.")
        results
    }

    companion object {
        private const val TAG = "SemanticSearchEngine"
    }
}
