package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceEntity

class EpgManagementViewModel(
    private val context: Context
) : ViewModel() {

    private val epgFileManager = EpgFileManager.getInstance(context)
    private val indexer = EpgIndexer.getInstance(context)
    private val appSettings = AppSettings(context)

    // Generation counter — incremented after DB destroy/recreate so Flows re-subscribe
    private val _dbGeneration = MutableStateFlow(0)

    // Always get fresh DB instance (survives destroy/recreate)
    private fun db() = EpgIndexDatabase.getInstance(context)

    // Re-subscribes to the new DB after clearing via flatMapLatest on generation
    val sources: Flow<List<EpgSourceEntity>> = _dbGeneration.flatMapLatest {
        db().epgSourceDao().getAllSources().distinctUntilChanged()
    }

    val processingState: StateFlow<EpgFileManager.MultiSourceState> = epgFileManager.state

    val indexState: StateFlow<EpgIndexState> = indexer.state

    val queuedTaskIds: StateFlow<Set<String>> = RefreshQueue.queuedTaskIds

    val isDevMode: Boolean get() = appSettings.isDevMode

    val autoRefreshEnabled: Boolean get() = appSettings.epgAutoRefreshEnabled

    fun cancelProcessing() {
        epgFileManager.cancelProcessing()
    }

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

    sealed interface CellularConfirmDialog {
        data object Hidden : CellularConfirmDialog
        data class RefreshAll(val onConfirm: suspend () -> Unit, val onDismiss: () -> Unit) : CellularConfirmDialog
        data class RefreshSource(val sourceId: Long, val onConfirm: suspend () -> Unit, val onDismiss: () -> Unit) : CellularConfirmDialog
    }

    private val _cellularDialog = MutableStateFlow<CellularConfirmDialog>(CellularConfirmDialog.Hidden)
    val cellularDialog: StateFlow<CellularConfirmDialog> = _cellularDialog.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _hasStrayFiles = MutableStateFlow(false)
    val hasStrayFiles: StateFlow<Boolean> = _hasStrayFiles.asStateFlow()

    private val _staleProgrammeCount = MutableStateFlow(0)
    val staleProgrammeCount: StateFlow<Int> = _staleProgrammeCount.asStateFlow()

    init {
        refreshDbStats()
        refreshMaintenanceState()
    }

    fun refreshMaintenanceState() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _hasStrayFiles.value = epgFileManager.getStrayFiles().isNotEmpty()
                val twoDaysAgo = (System.currentTimeMillis() / 1000) - (2 * 24 * 3600)
                _staleProgrammeCount.value = indexer.countStaleProgrammes(twoDaysAgo)
            }
        }
    }

    fun refreshDbStats() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dao = db().epgIndexDao()
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
        db().epgIndexDao().getLatestProgrammeEndTimeForSource(sourceId)
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
                    db().epgSourceDao().insertSource(
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
                db().epgSourceDao().updateSource(source)
            }
        }
    }

    fun deleteSource(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db().epgSourceDao().deleteSource(id)
                db().epgIndexDao().deleteBySourceId(id)
                refreshDbStats()
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            val queued = RefreshQueue.queuedTaskIds.value

            if (queued.contains("epg_refresh_all")) {
                return@launch
            }

            epgFileManager.launchProcessAllSources(
                onComplete = {
                    refreshDbStats()
                    _cellularDialog.value = CellularConfirmDialog.Hidden
                },
                onCellularConfirm = {
                    _cellularDialog.value = CellularConfirmDialog.RefreshAll(
                        onConfirm = {
                            epgFileManager.launchProcessAllSources(onComplete = {
                                refreshDbStats()
                                _cellularDialog.value = CellularConfirmDialog.Hidden
                            })
                        },
                        onDismiss = { _cellularDialog.value = CellularConfirmDialog.Hidden }
                    )
                    false
                }
            )
        }
    }

    fun refreshFailed() {
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { db().epgSourceDao().getFailedSources() }
            if (sources.isEmpty()) return@launch

            epgFileManager.launchProcessSources(
                sources = sources,
                taskId = "epg_refresh_failed",
                onComplete = {
                    refreshDbStats()
                    _cellularDialog.value = CellularConfirmDialog.Hidden
                },
                onCellularConfirm = {
                    _cellularDialog.value = CellularConfirmDialog.RefreshAll(
                        onConfirm = {
                            val retryFailed = withContext(Dispatchers.IO) { db().epgSourceDao().getFailedSources() }
                            epgFileManager.launchProcessSources(
                                sources = retryFailed,
                                taskId = "epg_refresh_failed",
                                onComplete = {
                                    refreshDbStats()
                                    _cellularDialog.value = CellularConfirmDialog.Hidden
                                }
                            )
                        },
                        onDismiss = { _cellularDialog.value = CellularConfirmDialog.Hidden }
                    )
                    false
                }
            )
        }
    }

    fun refreshOutdated() {
        viewModelScope.launch {
            val thresholdMs = System.currentTimeMillis() - 24 * 3600 * 1000
            val sources = withContext(Dispatchers.IO) { db().epgSourceDao().getStaleSources(thresholdMs) }
            if (sources.isEmpty()) return@launch

            epgFileManager.launchProcessSources(
                sources = sources,
                taskId = "epg_refresh_outdated",
                onComplete = {
                    refreshDbStats()
                    _cellularDialog.value = CellularConfirmDialog.Hidden
                },
                onCellularConfirm = {
                    _cellularDialog.value = CellularConfirmDialog.RefreshAll(
                        onConfirm = {
                            val threshold = System.currentTimeMillis() - 24 * 3600 * 1000
                            val retrySources = withContext(Dispatchers.IO) { db().epgSourceDao().getStaleSources(threshold) }
                            epgFileManager.launchProcessSources(
                                sources = retrySources,
                                taskId = "epg_refresh_outdated",
                                onComplete = {
                                    refreshDbStats()
                                    _cellularDialog.value = CellularConfirmDialog.Hidden
                                }
                            )
                        },
                        onDismiss = { _cellularDialog.value = CellularConfirmDialog.Hidden }
                    )
                    false
                }
            )
        }
    }

    fun refreshSelected(selectedIds: Set<Long>) {
        viewModelScope.launch {
            val selectedSources = withContext(Dispatchers.IO) {
                db().epgSourceDao().getAllSourcesOnce().filter { it.id in selectedIds }
            }
            if (selectedSources.isEmpty()) return@launch

            epgFileManager.launchProcessSources(
                sources = selectedSources,
                taskId = "epg_refresh_selected",
                onComplete = {
                    refreshDbStats()
                    _cellularDialog.value = CellularConfirmDialog.Hidden
                },
                onCellularConfirm = {
                    _cellularDialog.value = CellularConfirmDialog.RefreshAll(
                        onConfirm = {
                            val retrySelected = withContext(Dispatchers.IO) {
                                db().epgSourceDao().getAllSourcesOnce().filter { it.id in selectedIds }
                            }
                            epgFileManager.launchProcessSources(
                                sources = retrySelected,
                                taskId = "epg_refresh_selected",
                                onComplete = {
                                    refreshDbStats()
                                    _cellularDialog.value = CellularConfirmDialog.Hidden
                                }
                            )
                        },
                        onDismiss = { _cellularDialog.value = CellularConfirmDialog.Hidden }
                    )
                    false
                }
            )
        }
    }

    fun refreshSource(sourceId: Long) {
        epgFileManager.launchProcessSingleSource(
            sourceId,
            onComplete = {
                refreshDbStats()
                _cellularDialog.value = CellularConfirmDialog.Hidden
            },
            onCellularConfirm = {
                _cellularDialog.value = CellularConfirmDialog.RefreshSource(
                    sourceId = sourceId,
                    onConfirm = {
                        epgFileManager.launchProcessSingleSource(sourceId, onComplete = {
                            refreshDbStats()
                            _cellularDialog.value = CellularConfirmDialog.Hidden
                        })
                    },
                    onDismiss = { _cellularDialog.value = CellularConfirmDialog.Hidden }
                )
                false
            }
        )
    }

    fun cleanupFiles() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                epgFileManager.cleanupStrayFiles()
            }
            _hasStrayFiles.value = false
            if (result.filesDeleted > 0) {
                _toastMessage.tryEmit("Cleaned up ${result.filesDeleted} file(s), freed ${formatBytes(result.bytesFreed)}")
            } else {
                _toastMessage.tryEmit("No stray files found")
            }
        }
    }

    fun clearDatabase() {
        epgFileManager.launchClearAllData {
            // DB was destroyed and recreated — bump generation so sources Flow re-subscribes
            _dbGeneration.value++
            // Re-query from fresh DB
            val dao = db().epgIndexDao()
            _dbStats.value = DbStats(
                channelCount = dao.getChannelCount(),
                programmeCount = dao.getProgrammeCount()
            )
            _staleProgrammeCount.value = 0
            _hasStrayFiles.value = false
            _toastMessage.tryEmit("All EPG data cleared")
        }
    }

    fun purgeOldProgrammes() {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                val twoDaysAgo = (System.currentTimeMillis() / 1000) - (2 * 24 * 3600)
                indexer.purgeOldProgrammes(twoDaysAgo)
            }
            refreshDbStats()
            refreshMaintenanceState()
            if (deleted > 0) {
                _toastMessage.tryEmit("Purged ${formatCount(deleted)} programme(s)")
            } else {
                _toastMessage.tryEmit("No old programmes to purge")
            }
        }
    }

    companion object {
        private fun formatBytes(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }

        private fun formatCount(count: Int): String = when {
            count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
            count >= 1_000 -> "%.1fK".format(count / 1_000.0)
            else -> count.toString()
        }
    }
}
