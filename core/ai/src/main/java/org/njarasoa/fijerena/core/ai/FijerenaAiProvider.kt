package org.njarasoa.fijerena.core.ai

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.njarasoa.fijerena.core.network.ai.AiProvider
import org.njarasoa.fijerena.core.network.ai.VectorizationState
import org.njarasoa.fijerena.core.network.ai.VectorizationTier
import org.njarasoa.fijerena.core.network.ai.SemanticSearchResponse
import org.njarasoa.fijerena.core.network.ai.SemanticCandidate

/**
 * Concrete implementation of AiProvider using TFLite engines.
 */
class FijerenaAiProvider(private val context: Context) : AiProvider {
    
    private val engine = SemanticSearchEngine(context)
    private val detector = SearchCapabilityDetector(context)

    override fun detectTier(): VectorizationTier {
        return when (detector.detectTier()) {
            SearchCapabilityDetector.SearchTier.PREMIUM -> VectorizationTier.PREMIUM
            SearchCapabilityDetector.SearchTier.STANDARD -> VectorizationTier.STANDARD
        }
    }

    override suspend fun search(query: String, providerId: Long, limit: Int): SemanticSearchResponse {
        val response = engine.search(query, providerId, limit)
        return SemanticSearchResponse(
            results = response.results,
            totalAiCandidates = response.totalAiCandidates
        )
    }

    override fun scheduleVectorization() {
        AiVectorizationWorker.schedule(context)
    }

    override fun getVectorizationState(): Flow<VectorizationState> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData("provider_vectorization")
            .asFlow()
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map VectorizationState.Idle
                when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> VectorizationState.Processing()
                    WorkInfo.State.SUCCEEDED -> VectorizationState.Completed
                    else -> VectorizationState.Idle
                }
            }
    }
}
