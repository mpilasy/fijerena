package org.njarasoa.fijerena.core.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.ai.SemanticCandidate
import org.njarasoa.fijerena.core.network.ai.SemanticSearchResponse
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Orchestrates semantic search queries across Xtream provider databases.
 */
class SemanticSearchEngine(private val context: Context) {

    private val detector = SearchCapabilityDetector(context)

    data class ScoredResult(
        val candidate: SemanticCandidate,
        val score: Float
    )

    /**
     * Perform a semantic search for the given query across a specific provider's content.
     * Returns a response with top results and total AI candidates scanned.
     */
    suspend fun search(query: String, providerId: Long, limit: Int = 50): SemanticSearchResponse = withContext(Dispatchers.Default) {
        if (detector.detectTier() != SearchCapabilityDetector.SearchTier.PREMIUM) {
            return@withContext SemanticSearchResponse(emptyList(), 0)
        }

        val embedder = SentenceEmbedder(context)
        val queryVector = embedder.encode(query) ?: run {
            embedder.close()
            return@withContext SemanticSearchResponse(emptyList(), 0)
        }

        val candidates = mutableListOf<SemanticCandidate>()
        val xtreamDb = XtreamDatabase.getInstance(context)

        withContext(Dispatchers.IO) {
            // 1. Fetch Xtream Categories
            val categories = xtreamDb.categoryDao().getCategoriesWithEmbeddings(providerId)
            candidates.addAll(categories.map { catWithVector ->
                val cat = catWithVector.category
                SemanticCandidate(
                    itemId = cat.categoryId,
                    title = cat.categoryName,
                    description = "Category",
                    type = "CATEGORY",
                    providerId = cat.providerId,
                    embedding = catWithVector.embedding
                )
            })

            // 2. Fetch Xtream VOD/Live streams
            val streams = xtreamDb.streamDao().getStreamsWithEmbeddings(providerId)
            candidates.addAll(streams.map { streamWithVector ->
                val stream = streamWithVector.stream
                SemanticCandidate(
                    itemId = stream.streamId.toString(),
                    title = stream.name,
                    description = stream.description,
                    type = stream.type, // "VOD" or "LIVE"
                    providerId = stream.providerId,
                    thumbnailUrl = stream.streamIcon,
                    embedding = streamWithVector.embedding
                )
            })

            // 3. Fetch Xtream Series
            val seriesList = xtreamDb.seriesDao().getSeriesWithEmbeddings(providerId)
            candidates.addAll(seriesList.map { seriesWithVector ->
                val series = seriesWithVector.series
                SemanticCandidate(
                    itemId = series.seriesId.toString(),
                    title = series.name,
                    description = series.plot,
                    type = "SERIES",
                    providerId = series.providerId,
                    thumbnailUrl = series.cover,
                    embedding = seriesWithVector.embedding
                )
            })

            // 4. Fetch Xtream Episodes
            val episodes = xtreamDb.episodeDao().getEpisodesWithEmbeddings(providerId)
            candidates.addAll(episodes.map { epWithVector ->
                val ep = epWithVector.episode
                SemanticCandidate(
                    itemId = ep.id,
                    title = ep.title,
                    description = ep.overview,
                    type = "EPISODE",
                    providerId = ep.providerId,
                    thumbnailUrl = ep.movieImage,
                    embedding = epWithVector.embedding
                )
            })
        }

        val totalAiCandidates = candidates.size
        Log.d(TAG, "Comparing query against $totalAiCandidates provider candidates...")

        val results = candidates.mapNotNull { cand ->
            val progBytes = cand.embedding ?: return@mapNotNull null
            val progVector = VectorUtils.toFloatArray(progBytes)
            val score = VectorUtils.cosineSimilarity(queryVector, progVector)
            
            // Minimum threshold for relevance
            if (score > 0.35f) {
                ScoredResult(cand, score)
            } else {
                null
            }
        }.sortedByDescending { it.score }.take(limit)

        embedder.close()
        Log.i(TAG, "Provider semantic search complete. Found ${results.size} matches out of $totalAiCandidates candidates.")
        SemanticSearchResponse(results.map { it.candidate }, totalAiCandidates)
    }

    companion object {
        private const val TAG = "SemanticSearchEngine"
    }
}
