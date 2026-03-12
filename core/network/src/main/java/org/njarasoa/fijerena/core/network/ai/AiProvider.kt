package org.njarasoa.fijerena.core.network.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * State of the background AI vectorization process.
 */
sealed interface VectorizationState {
    data object Idle : VectorizationState
    data class Processing(val progress: Float = -1f) : VectorizationState
    data object Completed : VectorizationState
}

/** Device capability tier for AI features. */
enum class VectorizationTier { PREMIUM, STANDARD }

/** Response for a semantic search query. */
data class SemanticSearchResponse(
    val results: List<SemanticCandidate>,
    val totalAiCandidates: Int
)

/**
 * Interface for AI-powered features. 
 * Allows the app to function without :core:ai in slim builds.
 */
interface AiProvider {
    /** Determine the device tier. */
    fun detectTier(): VectorizationTier

    /** Perform conceptual search for a specific provider. */
    suspend fun search(query: String, providerId: Long, limit: Int = 50): SemanticSearchResponse
    
    /** Trigger background vectorization pass. */
    fun scheduleVectorization()

    /** Observe the state of vectorization. */
    fun getVectorizationState(): Flow<VectorizationState>
}

/**
 * Registry for the AI implementation.
 */
object AiManager {
    private var provider: AiProvider? = null

    fun register(p: AiProvider) {
        provider = p
    }

    fun getProvider(): AiProvider? = provider

    fun detectTier(): VectorizationTier = provider?.detectTier() ?: VectorizationTier.STANDARD

    fun getVectorizationState(): Flow<VectorizationState> {
        return provider?.getVectorizationState() ?: flowOf(VectorizationState.Idle)
    }
}
