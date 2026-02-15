package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceEntity

class EpgManagementViewModel(
    private val context: Context
) : ViewModel() {

    private val db = EpgIndexDatabase.getInstance(context)
    private val sourceDao = db.epgSourceDao()
    private val epgFileManager = EpgFileManager.getInstance(context)
    private val indexer = EpgIndexer.getInstance(context)
    private val appSettings = AppSettings(context)

    val sources: Flow<List<EpgSourceEntity>> = sourceDao.getAllSources()

    val processingState: StateFlow<EpgFileManager.MultiSourceState> = epgFileManager.state

    val indexState: StateFlow<EpgIndexState> = indexer.state

    val isDevMode: Boolean get() = appSettings.isDevMode

    val autoRefreshEnabled: Boolean get() = appSettings.epgAutoRefreshEnabled

    fun setAutoRefreshEnabled(enabled: Boolean) {
        appSettings.epgAutoRefreshEnabled = enabled
        epgFileManager.updateAutoRefreshSchedule()
    }

    data class DbStats(
        val channelCount: Int = 0,
        val programmeCount: Int = 0
    )

    private val _dbStats = MutableStateFlow(DbStats())
    val dbStats: StateFlow<DbStats> = _dbStats.asStateFlow()

    init {
        refreshDbStats()
    }

    fun refreshDbStats() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dao = db.epgIndexDao()
                    _dbStats.value = DbStats(
                        channelCount = dao.getChannelCount(),
                        programmeCount = dao.getProgrammeCount()
                    )
                } catch (_: Exception) {
                    _dbStats.value = DbStats()
                }
            }
        }
    }

    suspend fun getLatestProgrammeTime(sourceId: Long): Long? = withContext(Dispatchers.IO) {
        db.epgIndexDao().getLatestProgrammeEndTimeForSource(sourceId)
    }

    fun addSource(url: String, label: String, timezoneOffsetHours: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val urls = url.split("\n", ",", " ")
                    .map { it.trim() }
                    .filter { it.startsWith("http://") || it.startsWith("https://") }

                for (u in urls) {
                    val finalLabel = if (urls.size == 1) {
                        label.ifBlank { EpgFileManager.extractLabel(u) }
                    } else {
                        EpgFileManager.extractLabel(u)
                    }
                    sourceDao.insertSource(
                        EpgSourceEntity(
                            url = u,
                            label = finalLabel,
                            timezoneOffsetHours = timezoneOffsetHours
                        )
                    )
                }
            }
        }
    }

    fun updateSource(source: EpgSourceEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                sourceDao.updateSource(source)
            }
        }
    }

    fun deleteSource(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                sourceDao.deleteSource(id)
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val enabledSources = sourceDao.getEnabledSources()
                epgFileManager.processAllSources(enabledSources)
                refreshDbStats()
            }
        }
    }

    fun refreshSource(sourceId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                epgFileManager.processSingleSource(sourceId)
                refreshDbStats()
            }
        }
    }

    fun cleanupFiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                epgFileManager.cleanupStrayFiles()
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                indexer.clearAll()
                refreshDbStats()
            }
        }
    }

    fun purgeOldProgrammes() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val sevenDaysAgo = (System.currentTimeMillis() / 1000) - (7 * 24 * 3600)
                indexer.purgeOldProgrammes(sevenDaysAgo)
                refreshDbStats()
            }
        }
    }
}
