package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.EpgOffsetShift
import org.njarasoa.fijerena.core.network.xmltv.EpgRefreshSchedule
import org.njarasoa.fijerena.core.network.xmltv.GuideUrls
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.manager.AutoXmltvSources
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.utils.UiText
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
class EpgManagementViewModel(
    private val context: Context,
    private val providerRepository: ProviderRepository = ProviderRepository(context),
) : ViewModel() {
    private val epgFileManager = EpgFileManager.getInstance(context)
    private val indexer = EpgIndexer.getInstance(context)
    private val appSettings = AppSettings(context)

    /**
     * One card of the Guide Sources page: a guide source, shown once (the one guide source per URL
     * plan, OG-D11). [usedBy]: the sources that read it, in the Sources list's order.
     * [ownGuideOf]: the Xtream source whose own guide it is (`<host> (Bulk)`), else null.
     * [usedByProfile]: one of [usedBy] is a source of the profile in use.
     */
    data class GuideCard(
        val source: EpgSourceEntity,
        val usedBy: List<ProviderEntity>,
        val ownGuideOf: ProviderEntity?,
        val usedByProfile: Boolean,
    )

    /**
     * The Guide Sources page: its [cards], then the [sourcesWithoutGuide] (the sources with channels
     * that read no guide source). [providers]: every source with channels, in the Sources list's
     * order, the "Use for" list of Add and Edit.
     */
    data class GuidePage(
        val cards: List<GuideCard>,
        val sourcesWithoutGuide: List<ProviderEntity>,
        val providers: List<ProviderEntity>,
    )

    /** Every source on the device that can have channels, not only the profile's (GS-D2), in the Sources list's order. */
    private val guideProviders: Flow<List<ProviderEntity>> =
        providerRepository
            .getAllProviders()
            .map { list -> list.filter { MediaProviderFactory.hasLiveTv(it) } }
            .distinctUntilChanged()

    private fun indexDb() = EpgIndexDatabase.getInstance(context)

    private fun settingsDb() = SettingsDatabase.getInstance(context)

    // Generation counter for index DB
    private val _dbGeneration = MutableStateFlow(0)

    val page: Flow<GuidePage> =
        combine(
            guideProviders,
            providerRepository.profileSources(appSettings.activeProfileId),
            settingsDb().epgSourceDao().getAllSources(),
            settingsDb().epgSourceDao().getAllLinks(),
        ) { providers, profileSources, all, links ->
            buildPage(providers, profileSources.mapTo(HashSet()) { it.id }, all, links)
        }.distinctUntilChanged()

    /** Every guide source on the page, each once. */
    val cards: Flow<List<GuideCard>> = page.map { it.cards }.distinctUntilChanged()

    val sourcesWithoutGuide: Flow<List<ProviderEntity>> = page.map { it.sourcesWithoutGuide }.distinctUntilChanged()

    /** Every guide source on the page, card after card. */
    val sources: Flow<List<EpgSourceEntity>> =
        cards.map { list -> list.map { it.source } }.distinctUntilChanged()

    val staleSourceCount: StateFlow<Int> =
        sources
            .map { list ->
                val now = System.currentTimeMillis()
                val unsetHours = EpgRefreshSchedule.legacyIntervalHours(appSettings)
                list.count { it.enabled && EpgRefreshSchedule.isStale(it, now, unsetHours) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedSourceCount: StateFlow<Int> =
        sources
            .map { list -> list.count { it.enabled && it.lastError != null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val processingState: StateFlow<EpgFileManager.MultiSourceState> =
        guideProviders
            .map { list -> list.mapTo(HashSet()) { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { epgFileManager.stateFor(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EpgFileManager.MultiSourceState.Idle)

    val indexState: StateFlow<EpgIndexState> = indexer.state

    val queuedTaskIds: StateFlow<Set<String>> = RefreshQueue.queuedTaskIds

    val activeTaskIds: StateFlow<Set<String>> = RefreshQueue.activeTaskIds

    val lastPipelineStats: StateFlow<org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity?> =
        settingsDb()
            .epgPipelineStatsDao()
            .getLatestStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Flow that emits latest programme end times for all sources. Keyed off the set of source
    // IDs, not the full `sources` list: during an active sync, `sources` re-emits on every
    // progress field update (lastIngestedAtMs, lastError, ...), which used to restart this
    // flatMapLatest and re-run a MAX(end_epoch) full scan per source on every one of those
    // emissions. `_dbGeneration` (bumped below on sync completion, and on a full DB wipe) is
    // what actually signals new data to query for.
    val latestProgrammeTimes: StateFlow<Map<Long, Long>> =
        sources
            .distinctUntilChangedBy { list -> list.map { it.id } }
            .flatMapLatest { list ->
                _dbGeneration.map { gen ->
                    withContext(Dispatchers.IO) {
                        list.associate { source ->
                            source.id to (indexDb().epgIndexDao().getLatestProgrammeEndTimeForSource(source.id) ?: 0L)
                        }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _taskSourceIds = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())
    val taskSourceIds: StateFlow<Map<String, Set<Long>>> = _taskSourceIds.asStateFlow()

    val isAdmin: Boolean get() = appSettings.isAdmin

    /** How old [source] may get before its status reads stale — see [EpgRefreshSchedule.staleAfterMs]. */
    fun staleThresholdMs(source: EpgSourceEntity): Long = EpgRefreshSchedule.staleAfterMs(refreshIntervalHours(source))

    /** [source]'s auto-refresh interval in hours, [EpgSourceEntity.REFRESH_OFF] = off; its row shows it. */
    fun refreshIntervalHours(source: EpgSourceEntity): Int =
        EpgRefreshSchedule.intervalHours(source, EpgRefreshSchedule.legacyIntervalHours(appSettings))

    /**
     * The card's on/off switch. A source's own guide ([AutoXmltvSources.isAutoXmltvSource]) follows
     * "Provides a guide", so the switch sets that, as the viewer's choice, exactly as Edit Source
     * does; any other guide source is turned on or off by itself.
     */
    fun setSourceEnabled(
        card: GuideCard,
        enabled: Boolean,
    ) {
        viewModelScope.launchGuarded("EpgManagementViewModel.setSourceEnabled") {
            withContext(Dispatchers.IO) {
                val owner = card.ownGuideOf
                if (owner != null) {
                    providerRepository.setProvidesGuide(owner.id, enabled, byUser = true)
                    AutoXmltvSources.reconcileStored(context, owner.id)
                } else {
                    // On the row as it is now: the page's copy can be older than an import since.
                    val dao = settingsDb().epgSourceDao()
                    dao.getSourceById(card.source.id)?.let { dao.updateSource(it.copy(enabled = enabled)) }
                }
            }
        }
    }

    /**
     * Sets one guide source's auto-refresh, from its row's picker ([refreshIntervalOptions]); the
     * row and the periodic work follow from the database (the sources guide profiles plan → P5b).
     */
    fun setRefreshInterval(
        sourceId: Long,
        hours: Int,
    ) {
        viewModelScope.launchGuarded("EpgManagementViewModel.setRefreshInterval") {
            withContext(Dispatchers.IO) { epgFileManager.setRefreshInterval(sourceId, hours) }
        }
    }

    data class DbStats(
        val channelCount: Int = 0,
        val programmeCount: Int = 0,
    )

    private val _dbStats = MutableStateFlow(DbStats())
    val dbStats: StateFlow<DbStats> = _dbStats.asStateFlow()

    /**
     * On mobile data a guide refresh asks first: the page shows this dialog while it is set, and
     * the refresh waits for [onConfirm] or [onDismiss].
     */
    data class CellularConfirmDialog(
        val onConfirm: () -> Unit,
        val onDismiss: () -> Unit,
    )

    private val _cellularDialog = MutableStateFlow<CellularConfirmDialog?>(null)
    val cellularDialog: StateFlow<CellularConfirmDialog?> = _cellularDialog.asStateFlow()

    private val _userMessage = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<UiText> = _userMessage.asSharedFlow()

    private val _hasStrayFiles = MutableStateFlow(false)
    val hasStrayFiles: StateFlow<Boolean> = _hasStrayFiles.asStateFlow()

    private val _staleProgrammeCount = MutableStateFlow(0)
    val staleProgrammeCount: StateFlow<Int> = _staleProgrammeCount.asStateFlow()

    init {
        refreshDbStats()
        refreshMaintenanceState()
        viewModelScope.launchGuarded("EpgManagementViewModel.init") {
            processingState.collect { state ->
                if (state is EpgFileManager.MultiSourceState.Completed) {
                    _dbGeneration.value++
                }
            }
        }
    }

    fun refreshMaintenanceState() {
        viewModelScope.launchGuarded("EpgManagementViewModel.refreshMaintenanceState") {
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

                    _dbStats.value =
                        DbStats(
                            channelCount = channelCount,
                            programmeCount = programmeCount,
                        )

                    // If we have data but indexer state is NotIndexed, force an initialization to sync up
                    if (programmeCount > 0 && indexer.state.value is EpgIndexState.NotIndexed) {
                        indexer.initialize()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _dbStats.value = DbStats()
                }
            }
        }
    }

    suspend fun getLatestProgrammeTime(sourceId: Long): Long? =
        withContext(Dispatchers.IO) {
            indexDb().epgIndexDao().getLatestProgrammeEndTimeForSource(sourceId)
        }

    /**
     * Adds one guide source for each URL, read by [providerIds] (OG-D11). When a hand-added guide
     * source has the same URL ([GuideUrls.matchKey]), the sources are linked to it and its label,
     * offset and interval stay (OG-Q4); a message names the sources added.
     */
    fun addSource(
        url: String,
        label: String,
        timezoneOffsetHours: Int,
        providerIds: Collection<Long>,
        ingestMethod: String = "DOWNLOADED",
        enabled: Boolean = true,
    ) {
        viewModelScope.launchGuarded("EpgManagementViewModel.addSource") {
            withContext(Dispatchers.IO) {
                val urls =
                    url
                        .split("\n", ",", " ")
                        .map { it.trim() }
                        // The scheme in any case, as GuideUrls.matchKey reads it.
                        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
                val wanted = providerIds.distinct()
                if (wanted.isEmpty()) return@withContext

                val dao = settingsDb().epgSourceDao()
                for (u in urls) {
                    val existing = handAddedGuideAt(u)
                    if (existing != null) {
                        val current = dao.getProviderIds(existing.id)
                        val added = wanted.filter { it !in current }
                        if (added.isNotEmpty()) dao.setLinks(existing.id, current + added)
                        _userMessage.tryEmit(
                            if (added.isNotEmpty()) {
                                UiText.StringResource(R.string.epg_source_already_added_for, sourceNames(added))
                            } else {
                                UiText.StringResource(R.string.epg_source_already_used_for, sourceNames(current))
                            },
                        )
                        continue
                    }
                    val finalLabel =
                        if (urls.size == 1) {
                            label.ifBlank { EpgFileManager.extractLabel(u) }
                        } else {
                            EpgFileManager.extractLabel(u)
                        }
                    dao.insertGuide(
                        EpgSourceEntity(
                            url = u,
                            label = finalLabel,
                            timezoneOffsetHours = timezoneOffsetHours,
                            ingestMethod = ingestMethod,
                            enabled = enabled,
                        ),
                        wanted,
                    )
                }
            }
        }
    }

    /** The hand-added guide source whose URL is [url] ([GuideUrls.matchKey]); a source's own guide never matches (OG-D3). */
    private suspend fun handAddedGuideAt(url: String): EpgSourceEntity? {
        val key = GuideUrls.matchKey(url)
        return settingsDb()
            .epgSourceDao()
            .getAllSourcesOnce()
            .firstOrNull { GuideUrls.matchKey(it.url) == key && ownGuideOf(it) == null }
    }

    /** The Xtream source whose own guide [source] is, from its only link; null for a hand-added guide source. */
    private suspend fun ownGuideOf(source: EpgSourceEntity): ProviderEntity? =
        settingsDb()
            .epgSourceDao()
            .getProviderIds(source.id)
            .singleOrNull()
            ?.let { providerRepository.getProviderById(it) }
            ?.takeIf { AutoXmltvSources.isAutoXmltvSource(source, it.url) }

    /** The names of [providerIds], joined: "A, B". */
    private suspend fun sourceNames(providerIds: List<Long>): String =
        providerIds.mapNotNull { providerRepository.getProviderById(it)?.name }.joinToString(", ")

    /**
     * Saves [source]. A changed offset moves the times already in the guide index at once; when it
     * cannot ([EpgOffsetShift.Result.NEEDS_IMPORT]), the source is imported again now. A
     * hand-added guide source is then read by exactly [providerIds] ("Use for"); empty keeps its
     * sources, and a source's own guide keeps its one source (OG-D3).
     */
    fun updateSource(
        source: EpgSourceEntity,
        providerIds: Collection<Long> = emptyList(),
    ) {
        viewModelScope.launchGuarded("EpgManagementViewModel.updateSource") {
            val dao = settingsDb().epgSourceDao()
            // Only the fields the dialog edits, on the row as it is now: the page's copy can be
            // older than an import that finished since (its stats and offsets).
            withContext(Dispatchers.IO) {
                val current = dao.getSourceById(source.id) ?: return@withContext
                dao.updateSource(
                    current.copy(
                        url = source.url,
                        label = source.label,
                        timezoneOffsetHours = source.timezoneOffsetHours,
                        ingestMethod = source.ingestMethod,
                        enabled = source.enabled,
                    ),
                )
                if (providerIds.isNotEmpty() && ownGuideOf(current) == null) dao.setLinks(source.id, providerIds)
            }
            when (EpgOffsetShift.reconcile(source.id, dao, indexer)) {
                EpgOffsetShift.Result.SHIFTED -> {
                    _dbGeneration.value++
                    refreshDbStats()
                }

                EpgOffsetShift.Result.NEEDS_IMPORT -> {
                    refreshSource(source)
                }

                EpgOffsetShift.Result.IN_STEP -> {
                    Unit
                }
            }
        }
    }

    /** Deletes guide source [id] for every source that reads it (OG-Q8), with its guide data. */
    fun deleteSource(id: Long) {
        viewModelScope.launchGuarded("EpgManagementViewModel.deleteSource") {
            withContext(Dispatchers.IO) {
                settingsDb().epgSourceDao().deleteGuide(id)
                // Also delete its transient data from the index (manual cascade)
                indexDb().epgIndexDao().deleteBySourceId(id)
                refreshDbStats()
            }
        }
    }

    /** Refreshes the stale guide sources of every group (GS-D3). */
    fun refreshStale() {
        viewModelScope.launchGuarded("EpgManagementViewModel.refreshStale") {
            val providerIds = guideProviders.first().map { it.id }
            val taskId = EpgFileManager.refreshStaleTaskId(providerIds)
            val queued = RefreshQueue.queuedTaskIds.value
            if (queued.contains(taskId)) {
                _userMessage.tryEmit(UiText.StringResource(R.string.epg_refresh_in_queue))
                return@launchGuarded
            }
            // Instant feedback: calculate which IDs will be refreshed and put them in the map
            val now = System.currentTimeMillis()
            val unsetHours = EpgRefreshSchedule.legacyIntervalHours(appSettings)
            val sourcesToRefresh =
                withContext(Dispatchers.IO) {
                    // Each guide source once, however many of the sources read it.
                    settingsDb().epgSourceDao().getEnabledSourcesForProviders(providerIds)
                }.filter { EpgRefreshSchedule.isStale(it, now, unsetHours) && !queued.contains("epg_refresh_source_${it.id}") }

            if (sourcesToRefresh.isEmpty()) {
                _userMessage.tryEmit(UiText.StringResource(R.string.epg_no_stale_sources))
                return@launchGuarded
            }

            _taskSourceIds.value += (taskId to sourcesToRefresh.map { it.id }.toSet())

            epgFileManager.launchRefreshStale(
                providerIds = providerIds,
                onComplete = {
                    refreshDbStats()
                    _taskSourceIds.value -= taskId
                },
                onCellularConfirm = { confirmCellular(taskId) },
            )
        }
    }

    /** Retries the failed guide sources of every group (GS-D3). */
    fun refreshFailed() {
        viewModelScope.launchGuarded("EpgManagementViewModel.refreshFailed") {
            val providerIds = guideProviders.first().map { it.id }
            val taskId = EpgFileManager.refreshFailedTaskId(providerIds)
            if (RefreshQueue.queuedTaskIds.value.contains(taskId)) {
                _userMessage.tryEmit(UiText.StringResource(R.string.epg_refresh_in_queue))
                return@launchGuarded
            }
            val sources = withContext(Dispatchers.IO) { settingsDb().epgSourceDao().getFailedSourcesForProviders(providerIds) }
            if (sources.isEmpty()) {
                _userMessage.tryEmit(UiText.StringResource(R.string.epg_no_failed_sources))
                return@launchGuarded
            }

            _taskSourceIds.value += (taskId to sources.map { it.id }.toSet())

            epgFileManager.launchRefreshFailed(
                providerIds = providerIds,
                onComplete = {
                    refreshDbStats()
                    _taskSourceIds.value -= taskId
                },
                onCellularConfirm = { confirmCellular(taskId) },
            )
        }
    }

    fun refreshSource(source: EpgSourceEntity) {
        val sourceId = source.id
        val taskId = "epg_refresh_source_$sourceId"
        val queued = RefreshQueue.queuedTaskIds.value
        if (queued.contains(taskId)) {
            _userMessage.tryEmit(UiText.StringResource(R.string.epg_refresh_in_queue))
            return
        }

        _taskSourceIds.value += (taskId to setOf(sourceId))

        epgFileManager.launchProcessSingleSource(
            sourceId = sourceId,
            onComplete = {
                refreshDbStats()
                _taskSourceIds.value -= taskId
            },
            onCellularConfirm = { confirmCellular(taskId) },
        )
    }

    /** Shows [cellularDialog] and waits for the answer; a no drops [taskId]'s rows from the progress. */
    private suspend fun confirmCellular(taskId: String): Boolean =
        suspendCancellableCoroutine { cont ->
            _cellularDialog.value =
                CellularConfirmDialog(
                    onConfirm = {
                        _cellularDialog.value = null
                        if (cont.isActive) cont.resume(true)
                    },
                    onDismiss = {
                        _cellularDialog.value = null
                        _taskSourceIds.value -= taskId
                        if (cont.isActive) cont.resume(false)
                    },
                )
        }

    // A refresh still waiting for the mobile data answer when the page closes is dropped.
    override fun onCleared() {
        _cellularDialog.value?.onDismiss?.invoke()
    }

    fun cleanupFiles() {
        viewModelScope.launchGuarded("EpgManagementViewModel.cleanupFiles") {
            val result =
                withContext(Dispatchers.IO) {
                    epgFileManager.cleanupStrayFiles()
                }
            _hasStrayFiles.value = false
            if (result.filesDeleted > 0) {
                _userMessage.tryEmit(
                    UiText.PluralResource(
                        R.plurals.epg_cache_cleaned,
                        result.filesDeleted,
                        result.filesDeleted,
                        NumberUtils.formatBytes(context.resources, result.bytesFreed),
                    ),
                )
            } else {
                _userMessage.tryEmit(UiText.StringResource(R.string.epg_no_stray_files))
            }
        }
    }

    fun clearDatabase() {
        epgFileManager.launchClearAllData {
            // DB was destroyed and recreated
            _dbGeneration.value++
            val dao = indexDb().epgIndexDao()
            _dbStats.value =
                DbStats(
                    channelCount = dao.getChannelCount(),
                    programmeCount = dao.getProgrammeCount(),
                )
            _staleProgrammeCount.value = 0
            _hasStrayFiles.value = false
            _taskSourceIds.value = emptyMap()
            _userMessage.tryEmit(UiText.StringResource(R.string.epg_data_cleared))
        }
    }

    fun purgeOldProgrammes() {
        viewModelScope.launchGuarded("EpgManagementViewModel.purgeOldProgrammes") {
            val deleted =
                withContext(Dispatchers.IO) {
                    val twoDaysAgo = (System.currentTimeMillis() / 1000) - (2 * 24 * 3600)
                    indexer.purgeOldProgrammes(twoDaysAgo)
                }
            refreshDbStats()
            refreshMaintenanceState()
            if (deleted > 0) {
                _userMessage.tryEmit(UiText.PluralResource(R.plurals.epg_purged_programmes, deleted, deleted))
            } else {
                _userMessage.tryEmit(UiText.StringResource(R.string.epg_no_old_programmes))
            }
        }
    }

    companion object {
        private const val DAILY_HOURS = 24

        /**
         * The page from every source with channels ([providers], in the Sources list's order), the
         * profile's sources ([inProfile]) and every guide source with its [links]. A guide source
         * that no source in [providers] reads is left out (Shrink Database deletes it). Cards are in
         * the order of their first source in the Sources list, then in the order they were added.
         */
        fun buildPage(
            providers: List<ProviderEntity>,
            inProfile: Set<Long>,
            all: List<EpgSourceEntity>,
            links: List<EpgSourceProviderEntity>,
        ): GuidePage {
            val place = providers.withIndex().associate { (i, provider) -> provider.id to i }
            val linksOf = links.groupBy({ it.sourceId }) { it.providerId }
            val cards =
                all
                    .mapNotNull { source ->
                        val linked = linksOf[source.id].orEmpty()
                        val usedBy = providers.filter { it.id in linked }
                        val owner =
                            usedBy.singleOrNull()?.takeIf { linked.size == 1 && AutoXmltvSources.isAutoXmltvSource(source, it.url) }
                        usedBy.firstOrNull()?.let { GuideCard(source, usedBy, owner, usedBy.any { it.id in inProfile }) }
                    }.sortedWith(compareBy({ place.getValue(it.usedBy.first().id) }, { it.source.addedAtMs }))
            val linkedIds = links.mapTo(HashSet()) { it.providerId }
            return GuidePage(cards, providers.filter { it.id !in linkedIds }, providers)
        }

        private const val WEEKLY_HOURS = 168

        /** A guide source's auto-refresh choices (D2): off, every 6 h, 12 h, daily, weekly. */
        val REFRESH_INTERVAL_CHOICES = listOf(EpgSourceEntity.REFRESH_OFF, 6, 12, DAILY_HOURS, WEEKLY_HOURS)

        /**
         * The picker's options for a source refreshing every [currentHours]: the choices, plus the
         * current value in its place when it is another one (4, 8 or 48 h, copied from the
         * retired device-wide setting), so opening the picker doesn't lose it.
         */
        fun refreshIntervalOptions(currentHours: Int): List<Int> =
            if (currentHours in REFRESH_INTERVAL_CHOICES) {
                REFRESH_INTERVAL_CHOICES
            } else {
                (REFRESH_INTERVAL_CHOICES + currentHours).sortedBy { if (it == EpgSourceEntity.REFRESH_OFF) Int.MIN_VALUE else it }
            }

        /** An option's label in the picker: "Off", "Every 6 hours", "Daily", "Weekly". */
        fun refreshIntervalLabel(hours: Int): UiText =
            when (hours) {
                EpgSourceEntity.REFRESH_OFF -> UiText.StringResource(R.string.common_off)
                DAILY_HOURS -> UiText.StringResource(R.string.epg_automation_freq_daily)
                WEEKLY_HOURS -> UiText.StringResource(R.string.epg_automation_freq_weekly)
                else -> UiText.PluralResource(R.plurals.epg_automation_freq_hours, hours, hours)
            }

        /** The interval as a source's row shows it: "Refreshes daily", "Auto-refresh off". */

        /** A guide source's refresh line: "Off" while it is switched off, whatever its interval, as nothing refreshes it then. */
        fun refreshSummary(
            enabled: Boolean,
            hours: Int,
        ): UiText = if (enabled) refreshIntervalSummary(hours) else UiText.StringResource(R.string.epg_source_off_summary)

        fun refreshIntervalSummary(hours: Int): UiText =
            when (hours) {
                EpgSourceEntity.REFRESH_OFF -> UiText.StringResource(R.string.epg_source_refresh_off)
                DAILY_HOURS -> UiText.StringResource(R.string.epg_source_refresh_daily)
                WEEKLY_HOURS -> UiText.StringResource(R.string.epg_source_refresh_weekly)
                else -> UiText.PluralResource(R.plurals.epg_source_refresh_hours, hours, hours)
            }
    }
}
