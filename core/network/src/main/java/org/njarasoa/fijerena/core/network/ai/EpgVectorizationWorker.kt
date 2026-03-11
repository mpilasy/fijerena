package org.njarasoa.fijerena.core.network.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase

/**
 * Background worker that processes EPG programmes missing embeddings.
 */
class EpgVectorizationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val detector = SearchCapabilityDetector(applicationContext)
        if (detector.detectTier() != SearchCapabilityDetector.SearchTier.PREMIUM) {
            Log.i(TAG, "Device not premium tier. Skipping vectorization.")
            return@withContext Result.success()
        }

        val db = EpgIndexDatabase.getInstance(applicationContext)
        val dao = db.epgIndexDao()
        
        val embedder = SentenceEmbedder(applicationContext)
        
        try {
            var processedInThisRun = 0
            val maxPerRun = 500 // Limit per worker execution to prevent indefinite background run
            val batchSize = 50

            while (processedInThisRun < maxPerRun) {
                val programmes = dao.getProgrammesMissingEmbeddings(batchSize)
                if (programmes.isEmpty()) break

                Log.d(TAG, "Processing batch of ${programmes.size} programmes...")

                for (prog in programmes) {
                    // Combine title and description for richer context
                    val inputText = "${prog.title}. ${prog.description ?: ""}".trim()
                    if (inputText.isBlank()) {
                        // Mark as empty to avoid re-processing
                        dao.updateEmbedding(prog.id, ByteArray(0))
                        continue
                    }

                    val vector = embedder.encode(inputText)
                    if (vector != null) {
                        val bytes = VectorUtils.toByteArray(vector)
                        dao.updateEmbedding(prog.id, bytes)
                    } else {
                        // If inference failed, we might want to retry later or mark as failed
                        // For now, we skip to next batch
                    }
                }
                
                processedInThisRun += programmes.size
                
                // Cooperative cancellation check
                if (isStopped) {
                    Log.i(TAG, "Worker stopped by WorkManager")
                    return@withContext Result.retry()
                }
            }

            Log.i(TAG, "Vectorization run complete. Processed $processedInThisRun rows.")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Vectorization failed: ${e.message}", e)
            Result.retry()
        } finally {
            embedder.close()
        }
    }

    companion object {
        private const val TAG = "EpgVectorizationWorker"
        private const val UNIQUE_WORK_NAME = "epg_vectorization"

        /**
         * Schedules a one-time vectorization pass.
         */
        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                .setRequiresCharging(false) // Allow on battery but prioritized when idle
                .build()

            val request = androidx.work.OneTimeWorkRequestBuilder<EpgVectorizationWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
            Log.i(TAG, "Scheduled EPG vectorization work")
        }
    }
}
