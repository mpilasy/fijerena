package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase

@OptIn(ExperimentalCoroutinesApi::class)
class EpgManagementViewModel(
    private val context: Context
) : ViewModel() {

    private val epgFileManager = EpgFileManager.getInstance(context)
    private val indexer = EpgIndexer.getInstance(context)
    private val appSettings = AppSettings(context)

    // Accessors for separated databases
    private fun indexDb() = EpgIndexDatabase.getInstance(context)
    private fun settingsDb() = SettingsDatabase.getInstance(context)

    // Generation counter for index DB — sources Flow now persistent
    private val _dbGeneration = MutableStateFlow(0)

    // Sources are now in the persistent SettingsDatabase
    val sources: Flow<List<EpgSourceEntity>> = settingsDb().epgSourceDao().getAllSources().distinctUntilChanged()

    val staleSourceCount: StateFlow<Int> = sources
        .map { list -> 
            val threshold = System.currentTimeMillis() - STALE_THRESHOLD_MS
            list.count { it.enabled && (it.lastIngestedAtMs == 0L || it.lastIngestedAtMs < threshold) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedSourceCount: StateFlow<Int> = sources
        .map { list -> list.count { it.enabled && it.lastError != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val processingState: StateFlow<EpgFileManager.MultiSourceState> = epgFileManager.state

    val indexState: StateFlow<EpgIndexState> = indexer.state

    val queuedTaskIds: StateFlow<Set<String>> = RefreshQueue.queuedTaskIds

    val activeTaskIds: StateFlow<Set<String>> = RefreshQueue.activeTaskIds

    val lastPipelineStats: StateFlow<org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity?> = settingsDb().epgPipelineStatsDao().getLatestStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // Flow that emits latest programme end times for all sources
    val latestProgrammeTimes: StateFlow<Map<Long, Long>> = sources
        .flatMapLatest { list ->
            // Re-query whenever sources change or DB generation increments
            _dbGeneration.map { gen ->
                withContext(Dispatchers.IO) {
                    list.associate { source ->
                        source.id to (indexDb().epgIndexDao().getLatestProgrammeEndTimeForSource(source.id) ?: 0L)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _taskSourceIds = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())
    val taskSourceIds: StateFlow<Map<String, Set<Long>>> = _taskSourceIds.asStateFlow()

    val isDevMode: Boolean get() = appSettings.isDevMode

    val autoRefreshEnabled: Boolean get() = appSettings.epgAutoRefreshEnabled

    val epgRefreshTime: String get() = appSettings.epgRefreshTime

    fun toggleSelection(id: Long) {
        _selectedIds.value = if (_selectedIds.value.contains(id)) {
            _selectedIds.value - id
        } else {
            _selectedIds.value + id
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun cancelProcessing() {
        epgFileManager.cancelProcessing()
        _cellularDialog.value = CellularConfirmDialog.Hidden
        _taskSourceIds.value = emptyMap()
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        appSettings.epgAutoRefreshEnabled = enabled
        epgFileManager.updateAutoRefreshSchedule()
    }

    fun setEpgRefreshTime(time: String) {
        appSettings.epgRefreshTime = time
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
        data class RefreshStale(val onConfirm: suspend () -> Unit, val onDismiss: () -> Unit) : CellularConfirmDialog
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
                    val dao = indexDb().epgIndexDao()
                    val channelCount = dao.getChannelCount()
                    val programmeCount = dao.getProgrammeCount()
                    
                    _dbStats.value = DbStats(
                        channelCount = channelCount,
                        programmeCount = programmeCount
                    )

                    // If we have data but indexer state is NotIndexed, force an initialization to sync up
                    if (programmeCount > 0 && indexer.state.value is EpgIndexState.NotIndexed) {
                        indexer.initialize()
                    }
                } catch (_: Exception) {
                    _dbStats.value = DbStats()
                }
            }
        }
    }

    suspend fun getLatestProgrammeTime(sourceId: Long): Long? = withContext(Dispatchers.IO) {
        indexDb().epgIndexDao().getLatestProgrammeEndTimeForSource(sourceId)
    }

    fun addSource(url: String, label: String, timezoneOffsetHours: Int, ingestMethod: String = "DOWNLOADED", enabled: Boolean = true) {
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
                    settingsDb().epgSourceDao().insertSource(
                        EpgSourceEntity(
                            url = u,
                            label = finalLabel,
                            timezoneOffsetHours = timezoneOffsetHours,
                            ingestMethod = ingestMethod,
                            enabled = enabled
                        )
                    )
                }
            }
        }
    }

    fun updateSource(source: EpgSourceEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsDb().epgSourceDao().updateSource(source)
            }
        }
    }

    fun deleteSource(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Delete from persistent settings
                settingsDb().epgSourceDao().deleteSource(id)
                // Also delete its transient data from the index (manual cascade)
                indexDb().epgIndexDao().deleteBySourceId(id)
                refreshDbStats()
            }
        }
    }

    fun refreshStale() {
        val taskId = "epg_refresh_stale"
        val queued = RefreshQueue.queuedTaskIds.value
        if (queued.contains(taskId)) {
            _toastMessage.tryEmit("Refresh already in queue...")
            return
        }

        viewModelScope.launch {
            // Instant feedback: calculate which IDs will be refreshed and put them in the map
            val thresholdMs = System.currentTimeMillis() - STALE_THRESHOLD_MS
            val sourcesToRefresh = withContext(Dispatchers.IO) { 
                settingsDb().epgSourceDao().getStaleSources(thresholdMs) 
            }.filter { !queued.contains("epg_refresh_source_${it.id}") }

            if (sourcesToRefresh.isEmpty()) {
                _toastMessage.tryEmit("No stale sources need refreshing.")
                return@launch
            }

            _taskSourceIds.value += (taskId to sourcesToRefresh.map { it.id }.toSet())

            epgFileManager.launchRefreshStale(
                onComplete = { 
                    refreshDbStats()
                    _taskSourceIds.value -= taskId
                },
                onCellularConfirm = {
                    suspendCancellableCoroutine { cont ->
                        _cellularDialog.value = CellularConfirmDialog.RefreshStale(
                            onConfirm = {
                                _cellularDialog.value = CellularConfirmDialog.Hidden
                                if (cont.isActive) cont.resume(true)
                            },
                            onDismiss = {
                                _cellularDialog.value = CellularConfirmDialog.Hidden
                                _taskSourceIds.value -= taskId
                                if (cont.isActive) cont.resume(false)
                            }
                        )
                    }
                }
            )
        }
    }

    fun refreshFailed() {
        val taskId = "epg_refresh_failed"
        val queued = RefreshQueue.queuedTaskIds.value
        if (queued.contains(taskId)) {
            _toastMessage.tryEmit("Refresh already in queue...")
            return
        }

        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { settingsDb().epgSourceDao().getFailedSources() }
            if (sources.isEmpty()) {
                _toastMessage.tryEmit("No failed sources found.")
                return@launch
            }

            _taskSourceIds.value += (taskId to sources.map { it.id }.toSet())

            epgFileManager.launchRefreshFailed(
                onComplete = { 
                    refreshDbStats()
                    _taskSourceIds.value -= taskId
                },
                onCellularConfirm = {
                    suspendCancellableCoroutine { cont ->
                        _cellularDialog.value = CellularConfirmDialog.RefreshStale(
                            onConfirm = {
                                _cellularDialog.value = CellularConfirmDialog.Hidden
                                if (cont.isActive) cont.resume(true)
                            },
                            onDismiss = {
                                _cellularDialog.value = CellularConfirmDialog.Hidden
                                _taskSourceIds.value -= taskId
                                if (cont.isActive) cont.resume(false)
                            }
                        )
                    }
                }
            )
        }
    }

    fun refreshSelected(selectedIds: Set<Long>) {
        val taskId = "epg_refresh_selected"
        val queued = RefreshQueue.queuedTaskIds.value
        if (queued.contains(taskId)) {
            _toastMessage.tryEmit("Refresh already in queue...")
            return
        }

        // Copy set to avoid concurrent modification issues
        val idsToRefresh = selectedIds.toSet()
        _taskSourceIds.value += (taskId to idsToRefresh)

        epgFileManager.launchRefreshSelected(
            selectedIds = idsToRefresh,
            onComplete = { 
                refreshDbStats()
                _taskSourceIds.value -= taskId
            },
            onCellularConfirm = {
                suspendCancellableCoroutine { cont ->
                    _cellularDialog.value = CellularConfirmDialog.RefreshStale(
                        onConfirm = {
                            _cellularDialog.value = CellularConfirmDialog.Hidden
                            if (cont.isActive) cont.resume(true)
                        },
                        onDismiss = {
                            _cellularDialog.value = CellularConfirmDialog.Hidden
                            _taskSourceIds.value -= taskId
                            if (cont.isActive) cont.resume(false)
                        }
                    )
                }
            }
        )
    }

    fun refreshSource(sourceId: Long) {
        val taskId = "epg_refresh_source_$sourceId"
        val queued = RefreshQueue.queuedTaskIds.value
        if (queued.contains(taskId)) {
            _toastMessage.tryEmit("Refresh already in queue...")
            return
        }

        _taskSourceIds.value += (taskId to setOf(sourceId))

        epgFileManager.launchProcessSingleSource(
            sourceId = sourceId,
            onComplete = { 
                refreshDbStats()
                _taskSourceIds.value -= taskId
            },
            onCellularConfirm = {
                suspendCancellableCoroutine { cont ->
                    _cellularDialog.value = CellularConfirmDialog.RefreshSource(
                        sourceId = sourceId,
                        onConfirm = {
                            _cellularDialog.value = CellularConfirmDialog.Hidden
                            if (cont.isActive) cont.resume(true)
                        },
                        onDismiss = {
                            _cellularDialog.value = CellularConfirmDialog.Hidden
                            _taskSourceIds.value -= taskId
                            if (cont.isActive) cont.resume(false)
                        }
                    )
                }
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
            // DB was destroyed and recreated
            _dbGeneration.value++
            // Re-query from fresh index DB
            val dao = indexDb().epgIndexDao()
            _dbStats.value = DbStats(
                channelCount = dao.getChannelCount(),
                programmeCount = dao.getProgrammeCount()
            )
            _staleProgrammeCount.value = 0
            _hasStrayFiles.value = false
            _taskSourceIds.value = emptyMap()
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
        private const val STALE_THRESHOLD_MS = 6L * 3600 * 1000

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
