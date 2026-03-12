package org.njarasoa.fijerena.core.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryVectorEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamVectorEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesVectorEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeVectorEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.MediaProviderFactory

/**
 * Background worker that processes Xtream streams, categories, series and episodes missing embeddings.
 * Also performs metadata "crawling" for VODs and Series to ensure AI has enough text to index.
 */
class AiVectorizationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val detector = SearchCapabilityDetector(applicationContext)
        if (detector.detectTier() != SearchCapabilityDetector.SearchTier.PREMIUM) {
            Log.i(TAG, "Device not premium tier. Skipping vectorization.")
            return@withContext Result.success()
        }

        val embedder = SentenceEmbedder(applicationContext)
        val providerRepo = ProviderRepository(applicationContext)
        
        try {
            var processedInThisRun = 0
            val maxPerRun = 1000 
            val batchSize = 50

            val xtreamDb = XtreamDatabase.getInstance(applicationContext)
            val settingsDb = SettingsDatabase.getInstance(applicationContext)
            val providers = settingsDb.providerDao().getAllProvidersList()

            // 0. Crawl metadata for all Xtream providers first
            for (p in providers) {
                if (p.type != "XTREAM") continue
                
                // Use MediaProviderFactory to get/create a provider instance
                val password = providerRepo.getPassword(p.id) ?: ""
                val provider = MediaProviderFactory.create(p, applicationContext, password)
                
                // Crawl VODs missing descriptions
                val vodsToCrawl = xtreamDb.streamDao().getStreamsMissingEmbeddings(10) // Small batch for crawling
                for (vod in vodsToCrawl) {
                    if (vod.type == XtreamStreamEntity.TYPE_VOD && vod.description.isNullOrBlank()) {
                        Log.d(TAG, "Crawling metadata for VOD: ${vod.name}")
                        provider.getMovieDetail(vod.streamId.toString())
                    }
                }

                // Crawl Series missing plots (episodes will be populated as a side effect)
                val seriesToCrawl = xtreamDb.seriesDao().getSeriesMissingEmbeddings(10)
                for (series in seriesToCrawl) {
                    if (series.plot.isNullOrBlank()) {
                        Log.d(TAG, "Crawling metadata for Series: ${series.name}")
                        provider.getSeriesDetail(series.seriesId.toString())
                    }
                }
            }

            // 1. Process Xtream Categories
            val categoryDao = xtreamDb.categoryDao()
            while (processedInThisRun < maxPerRun) {
                val categories = categoryDao.getCategoriesMissingEmbeddings(batchSize)
                if (categories.isEmpty()) break

                for (cat in categories) {
                    val inputText = "Category: ${cat.categoryName} (${cat.type})"
                    val vector = embedder.encode(inputText)
                    if (vector != null) {
                        categoryDao.insertVector(XtreamCategoryVectorEntity(cat.categoryId, cat.providerId, cat.type, VectorUtils.toByteArray(vector)))
                    } else {
                        categoryDao.insertVector(XtreamCategoryVectorEntity(cat.categoryId, cat.providerId, cat.type, ByteArray(0)))
                    }
                }
                processedInThisRun += categories.size
                if (isStopped) return@withContext Result.retry()
            }

            // 2. Process Xtream Streams (VOD/Live)
            val streamDao = xtreamDb.streamDao()
            while (processedInThisRun < maxPerRun) {
                val streams = streamDao.getStreamsMissingEmbeddings(batchSize)
                if (streams.isEmpty()) break

                for (stream in streams) {
                    val inputText = buildString {
                        append(stream.name)
                        if (!stream.description.isNullOrBlank()) append(". ").append(stream.description)
                        if (stream.type == XtreamStreamEntity.TYPE_VOD) {
                            if (!stream.director.isNullOrBlank()) append(". Director: ").append(stream.director)
                            if (!stream.cast.isNullOrBlank()) append(". Cast: ").append(stream.cast)
                            if (!stream.genre.isNullOrBlank()) append(". Genre: ").append(stream.genre)
                        }
                    }.trim()
                    
                    if (inputText.isBlank()) {
                        streamDao.insertVector(XtreamStreamVectorEntity(stream.streamId, stream.providerId, stream.type, ByteArray(0)))
                        continue
                    }

                    val vector = embedder.encode(inputText)
                    if (vector != null) {
                        streamDao.insertVector(XtreamStreamVectorEntity(stream.streamId, stream.providerId, stream.type, VectorUtils.toByteArray(vector)))
                    } else {
                        streamDao.insertVector(XtreamStreamVectorEntity(stream.streamId, stream.providerId, stream.type, ByteArray(0)))
                    }
                }
                processedInThisRun += streams.size
                if (isStopped) return@withContext Result.retry()
            }

            // 3. Process Xtream Series
            val seriesDao = xtreamDb.seriesDao()
            while (processedInThisRun < maxPerRun) {
                val seriesList = seriesDao.getSeriesMissingEmbeddings(batchSize)
                if (seriesList.isEmpty()) break

                for (series in seriesList) {
                    val inputText = buildString {
                        append(series.name)
                        if (!series.plot.isNullOrBlank()) append(". ").append(series.plot)
                        if (!series.genre.isNullOrBlank()) append(". Genre: ").append(series.genre)
                        if (!series.cast.isNullOrBlank()) append(". Cast: ").append(series.cast)
                    }.trim()

                    if (inputText.isBlank()) {
                        seriesDao.insertVector(XtreamSeriesVectorEntity(series.seriesId, series.providerId, ByteArray(0)))
                        continue
                    }

                    val vector = embedder.encode(inputText)
                    if (vector != null) {
                        seriesDao.insertVector(XtreamSeriesVectorEntity(series.seriesId, series.providerId, VectorUtils.toByteArray(vector)))
                    } else {
                        seriesDao.insertVector(XtreamSeriesVectorEntity(series.seriesId, series.providerId, ByteArray(0)))
                    }
                }
                processedInThisRun += seriesList.size
                if (isStopped) return@withContext Result.retry()
            }

            // 4. Process Xtream Episodes
            val episodeDao = xtreamDb.episodeDao()
            while (processedInThisRun < maxPerRun) {
                val episodes = episodeDao.getEpisodesMissingEmbeddings(batchSize)
                if (episodes.isEmpty()) break

                for (ep in episodes) {
                    val inputText = buildString {
                        append(ep.title)
                        if (!ep.overview.isNullOrBlank()) append(". ").append(ep.overview)
                    }.trim()

                    if (inputText.isBlank()) {
                        episodeDao.insertVector(XtreamEpisodeVectorEntity(ep.id, ep.providerId, ByteArray(0)))
                        continue
                    }

                    val vector = embedder.encode(inputText)
                    if (vector != null) {
                        episodeDao.insertVector(XtreamEpisodeVectorEntity(ep.id, ep.providerId, VectorUtils.toByteArray(vector)))
                    } else {
                        episodeDao.insertVector(XtreamEpisodeVectorEntity(ep.id, ep.providerId, ByteArray(0)))
                    }
                }
                processedInThisRun += episodes.size
                if (isStopped) return@withContext Result.retry()
            }

            Log.i(TAG, "Vectorization run complete. Processed $processedInThisRun rows across Xtream databases.")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Vectorization failed: ${e.message}", e)
            Result.retry()
        } finally {
            embedder.close()
        }
    }

    companion object {
        private const val TAG = "AiVectorizationWorker"
        private const val UNIQUE_WORK_NAME = "provider_vectorization"

        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                .build()

            val request = androidx.work.OneTimeWorkRequestBuilder<AiVectorizationWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
