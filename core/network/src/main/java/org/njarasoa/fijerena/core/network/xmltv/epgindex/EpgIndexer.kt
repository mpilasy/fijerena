package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xmltv.XmltvParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Singleton that indexes XMLTV files into SQLite for fast FTS search.
 *
 * Streaming parse with batch INSERT (1000 rows per transaction).
 * Memory bounded: ~200KB per batch.
 */
class EpgIndexer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EpgIndexer"
        private const val BATCH_SIZE = 1000
        private const val STREAM_BUFFER_SIZE = 65536
        private const val ESTIMATED_BYTES_PER_PROGRAMME = 200

        @Volatile
        private var instance: EpgIndexer? = null

        fun getInstance(context: Context): EpgIndexer {
            return instance ?: synchronized(this) {
                instance ?: EpgIndexer(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _state = MutableStateFlow<EpgIndexState>(EpgIndexState.NotIndexed)
    val state: StateFlow<EpgIndexState> = _state.asStateFlow()

    /**
     * Check whether the file has changed or timezone override differs since last index.
     */
    suspend fun needsReindex(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val metadata = db.epgIndexDao().getMetadata() ?: return@withContext true
            metadata.fileSizeBytes != file.length() ||
                metadata.fileLastModifiedMs != file.lastModified() ||
                metadata.timezoneOffsetHours != XmltvParser.timezoneOverrideHours
        } catch (e: Exception) {
            Log.w(TAG, "Error checking index metadata, will re-index", e)
            true
        }
    }

    /**
     * Restore Indexed state from stored metadata without re-indexing.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val db = EpgIndexDatabase.getInstance(context)
            val metadata = db.epgIndexDao().getMetadata()
            if (metadata != null && metadata.programmeCount > 0) {
                _state.value = EpgIndexState.Indexed(
                    channelCount = metadata.channelCount,
                    programmeCount = metadata.programmeCount,
                    indexedAtMs = metadata.indexedAtMs
                )
                Log.d(TAG, "Restored index: ${metadata.channelCount} channels, ${metadata.programmeCount} programmes")
            } else {
                _state.value = EpgIndexState.NotIndexed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore index state", e)
            _state.value = EpgIndexState.NotIndexed
        }
    }

    /**
     * Parse the XMLTV file and build the SQLite index.
     * Call from a coroutine scope on IO dispatcher.
     */
    suspend fun startIndexing(file: File) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting indexing of ${file.name} (${file.length() / (1024 * 1024)}MB)")
        _state.value = EpgIndexState.Indexing(
            progressPercent = 0,
            channelsIndexed = 0,
            programmesIndexed = 0
        )

        try {
            // Destroy and recreate database for clean state
            EpgIndexDatabase.destroy(context)
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            val fileSize = file.length()
            var bytesProcessed = 0L
            var channelCount = 0
            var programmeCount = 0

            val channelBatch = mutableListOf<EpgChannelEntity>()
            val programmeBatch = mutableListOf<EpgProgrammeEntity>()

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()

            // Use CountingInputStream to track bytes read
            val countingStream = CountingInputStream(
                BufferedInputStream(FileInputStream(file), STREAM_BUFFER_SIZE)
            )
            parser.setInput(countingStream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val channel = XmltvParser.parseChannelForIndex(parser)
                            if (channel != null) {
                                channelBatch.add(channel)
                                channelCount++
                                if (channelBatch.size >= BATCH_SIZE) {
                                    dao.insertChannels(channelBatch.toList())
                                    channelBatch.clear()
                                }
                            }
                        }
                        "programme" -> {
                            // Flush remaining channels before first programme
                            if (channelBatch.isNotEmpty()) {
                                dao.insertChannels(channelBatch.toList())
                                channelBatch.clear()
                            }

                            val programme = XmltvParser.parseProgrammeForIndex(parser)
                            if (programme != null) {
                                programmeBatch.add(programme)
                                programmeCount++
                                if (programmeBatch.size >= BATCH_SIZE) {
                                    dao.insertProgrammes(programmeBatch.toList())
                                    programmeBatch.clear()

                                    // Update progress
                                    bytesProcessed = countingStream.bytesRead
                                    val percent = if (fileSize > 0) {
                                        ((bytesProcessed * 100) / fileSize).toInt().coerceIn(0, 99)
                                    } else 0
                                    _state.value = EpgIndexState.Indexing(
                                        progressPercent = percent,
                                        channelsIndexed = channelCount,
                                        programmesIndexed = programmeCount
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            // Flush remaining batches
            if (channelBatch.isNotEmpty()) {
                dao.insertChannels(channelBatch.toList())
            }
            if (programmeBatch.isNotEmpty()) {
                dao.insertProgrammes(programmeBatch.toList())
            }

            // Rebuild FTS index
            Log.d(TAG, "Rebuilding FTS index...")
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')"
            )

            // Store metadata (including timezone so re-index triggers on change)
            val now = System.currentTimeMillis()
            dao.insertMetadata(
                EpgIndexMetadata(
                    fileSizeBytes = file.length(),
                    fileLastModifiedMs = file.lastModified(),
                    indexedAtMs = now,
                    channelCount = channelCount,
                    programmeCount = programmeCount,
                    timezoneOffsetHours = XmltvParser.timezoneOverrideHours
                )
            )

            _state.value = EpgIndexState.Indexed(
                channelCount = channelCount,
                programmeCount = programmeCount,
                indexedAtMs = now
            )
            Log.d(TAG, "Indexing complete: $channelCount channels, $programmeCount programmes")

        } catch (e: OutOfMemoryError) {
            System.gc()
            val msg = "Out of memory during indexing"
            Log.e(TAG, msg, e)
            _state.value = EpgIndexState.Failed(msg)
        } catch (e: Exception) {
            val msg = e.message ?: "Indexing failed"
            Log.e(TAG, msg, e)
            _state.value = EpgIndexState.Failed(msg)
        }
    }

    /**
     * Tracks bytes read from the underlying stream for progress reporting.
     */
    private class CountingInputStream(
        private val wrapped: java.io.InputStream
    ) : java.io.InputStream() {
        @Volatile
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val b = wrapped.read()
            if (b >= 0) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = wrapped.read(b, off, len)
            if (n > 0) bytesRead += n
            return n
        }

        override fun close() = wrapped.close()
        override fun available() = wrapped.available()
    }
}
