package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import java.net.URI

/**
 * The guide source an Xtream source gets by itself: `<server>/xmltv.php`, labelled `<host> (Bulk)`.
 * The row holds no login: [fetchUrl] adds `?username=…&password=…` from the source's encrypted
 * login when the guide is downloaded, so `providers.db` and exports never carry the password. Rows
 * written before that (and records from older builds) carry it in the query; they still count as
 * automatic, and [withoutLogin] strips it. There is at most one per Xtream source, and it exists
 * only while the account has live channels. It is
 * enabled while the source's "Provides a guide" ([ProviderSettings.providesGuide]) is on and
 * disabled, never deleted, while it is off — detected, turned off when the guide comes back empty
 * ([onEmptyIngest]), and changeable in Edit Source ([reconcileStored]).
 *
 * No column marks it: a source is automatic when its URL is the provider's server (same scheme,
 * host, port and path) plus `/xmltv.php` (with no query, or `?username=…&password=…`) and its label
 * ends with [LABEL_SUFFIX] — see [isAutoXmltvSource]. Anything else is hand-added and never touched
 * here. A user who renames the automatic source turns it into a hand-added one.
 *
 * Every write goes through [EpgSourceDao], so live sync carries it to the group's other devices
 * (the URL update and the deletions included). Live sync sends it with the login in the query
 * ([syncUrl], inside the end-to-end encrypted record) as older builds expect, and the receiver
 * strips it again. See the ux overhaul plan → GD0c and
 * the public release plan → R1.
 */
object AutoXmltvSources {
    private const val TAG = "AutoXmltvSources"
    const val LABEL_SUFFIX = " (Bulk)"
    private const val XMLTV_PATH = "/xmltv.php"
    private const val PROVIDER_TYPE_XTREAM = "XTREAM"

    /** The automatic source's URL as stored: the server's `xmltv.php`, without the login. */
    fun storedUrl(providerUrl: String): String = "${providerUrl.trim().trimEnd('/')}$XMLTV_PATH"

    /** [storedUrl] with the login, as the server wants it. Built as it always was (no encoding), so older builds still match it. */
    fun withLogin(
        storedUrl: String,
        username: String,
        password: String,
    ): String = "$storedUrl?username=$username&password=$password"

    /** The automatic source's URL with the login: what is downloaded, and what older builds stored. */
    fun xmltvUrl(
        providerUrl: String,
        username: String,
        password: String,
    ): String = withLogin(storedUrl(providerUrl), username, password)

    fun label(providerUrl: String): String = EpgFileManager.extractLabel(providerUrl) + LABEL_SUFFIX

    /** Whether [source] is the automatic guide source of the Xtream server at [providerUrl]. */
    fun isAutoXmltvSource(
        source: EpgSourceEntity,
        providerUrl: String,
    ): Boolean = isAutoXmltvSource(source.url, source.label, providerUrl)

    private fun isAutoXmltvSource(
        url: String,
        label: String,
        providerUrl: String,
    ): Boolean {
        val query = url.substringAfter('?', missingDelimiterValue = "")
        val noLogin = '?' !in url
        return label.endsWith(LABEL_SUFFIX) &&
            (noLogin || (query.startsWith("username=") && query.contains("&password="))) &&
            isServerXmltv(url, providerUrl)
    }

    /**
     * Whether [url] is the `xmltv.php` of the Xtream server at [providerUrl], whatever its label and
     * query: the automatic source, or one the viewer renamed. Either holds, or is downloaded with,
     * the source's login.
     */
    fun isServerXmltv(
        url: String,
        providerUrl: String,
    ): Boolean {
        // Only the part before `?` is parsed: the query carries a raw password, which may hold
        // anything (`#`, `%`, spaces) that a URI parser would choke on or misread.
        val base = url.substringBefore('?')
        val server = serverOf(providerUrl.trim())
        return base.endsWith(XMLTV_PATH) && server != null && server == serverOf(base.removeSuffix(XMLTV_PATH))
    }

    /** [url] as it is stored: without the login when it is the automatic source of [providerUrl], unchanged otherwise. */
    fun withoutLogin(
        url: String,
        label: String,
        providerUrl: String,
    ): String = if (isAutoXmltvSource(url, label, providerUrl)) url.substringBefore('?') else url

    /** Whether [source] is downloaded with its Xtream source's login: an automatic source stored without it. */
    internal fun needsLogin(
        source: EpgSourceEntity,
        provider: ProviderEntity?,
    ): Boolean = '?' !in source.url && provider?.type == PROVIDER_TYPE_XTREAM && isAutoXmltvSource(source, provider.url)

    /**
     * The URL to download [source] from: an automatic source's URL with its Xtream source's login
     * (null when this device has no password for it), anything else as stored.
     */
    suspend fun fetchUrl(
        context: Context,
        source: EpgSourceEntity,
    ): String? = loginUrl(ProviderRepository(context), source)

    /**
     * The URL live sync sends for [source]: with the login, as older builds store and fetch it.
     * It travels inside the end-to-end encrypted record, as the source's own password does.
     */
    suspend fun syncUrl(
        repository: ProviderRepository,
        source: EpgSourceEntity,
    ): String = loginUrl(repository, source) ?: source.url

    private suspend fun loginUrl(
        repository: ProviderRepository,
        source: EpgSourceEntity,
    ): String? =
        withContext(Dispatchers.IO) {
            val provider = repository.getProviderById(source.providerId)
            if (provider != null && needsLogin(source, provider)) {
                val login = repository.getLogin(provider)
                if (login.username.isNotEmpty() &&
                    login.password.isNotEmpty()
                ) {
                    withLogin(source.url, login.username, login.password)
                } else {
                    null
                }
            } else {
                source.url
            }
        }

    /** Scheme, host and port compared case-insensitively with default ports filled in; the path exactly, minus a trailing `/`. */
    private fun serverOf(url: String): String? =
        try {
            val uri = URI(url.trimEnd('/'))
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            val port =
                when {
                    uri.port != -1 -> uri.port
                    scheme == "https" -> 443
                    else -> 80
                }
            if (scheme == null || host == null) null else "$scheme://$host:$port${uri.rawPath.orEmpty().trimEnd('/')}"
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            null
        }

    /** What [reconcile] writes. Empty when the sources are already as they should be. */
    data class Plan(
        val insert: EpgSourceEntity? = null,
        val update: EpgSourceEntity? = null,
        val deleteIds: List<Long> = emptyList(),
    ) {
        val isEmpty: Boolean get() = insert == null && update == null && deleteIds.isEmpty()
    }

    /**
     * What to do with [providerId]'s automatic source, given all its sources.
     *
     * - [hasLiveChannels] `false`: delete every automatic source. `null` (unknown): add nothing,
     *   delete nothing beyond the duplicates.
     * - Otherwise keep one — the one already on [currentUrl], else the oldest — rewrite its URL to
     *   [currentUrl] (with its ingest state reset, so the next refresh downloads for real, unless
     *   only the login is dropped from it) and delete the others.
     * - With none left, [addMissing] and [hasLiveChannels] `true`, add one, unless a hand-added
     *   source already has that URL (with or without a login).
     * - [providesGuide] off ("Provides a guide", [ProviderSettings.providesGuideOn]): the kept one
     *   is disabled, never deleted for it, and none is added. On, [enableKept] (the viewer turned it
     *   back on) re-enables the kept one with its stats; otherwise its enabled state stays as it is.
     *
     * [currentUrl] is the [storedUrl] of the provider's server; null (no credentials at hand) keeps
     * the row's URL as it is. [previousProviderUrl]
     * is the server the provider used before a URL change: its automatic source is carried over.
     */
    fun plan(
        providerId: Long,
        providerUrl: String,
        sources: List<EpgSourceEntity>,
        currentUrl: String?,
        hasLiveChannels: Boolean?,
        addMissing: Boolean,
        previousProviderUrl: String? = null,
        providesGuide: Boolean = true,
        enableKept: Boolean = false,
    ): Plan {
        val own = sources.filter { it.providerId == providerId }
        val serverUrls = listOfNotNull(providerUrl, previousProviderUrl)
        val autos = own.filter { source -> serverUrls.any { isAutoXmltvSource(source, it) } }.sortedBy { it.addedAtMs }
        val keep = if (hasLiveChannels == false) null else autos.firstOrNull { it.url == currentUrl } ?: autos.firstOrNull()
        val deleteIds = autos.filter { it !== keep }.map { it.id }
        val rewritten =
            if (keep != null && currentUrl != null && keep.url.substringBefore('?') == currentUrl && keep.url != currentUrl) {
                // Same server, stored with the login (an older row): only the login goes.
                keep.copy(url = currentUrl)
            } else if (keep != null && currentUrl != null && keep.url != currentUrl) {
                // A row carried over from the previous server takes the new server's label.
                val newLabel = if (isAutoXmltvSource(keep, providerUrl)) keep.label else label(providerUrl)
                keep.copy(url = currentUrl, label = newLabel).withIngestionStateReset()
            } else {
                keep
            }
        val target =
            when {
                rewritten == null -> null
                !providesGuide -> rewritten.copy(enabled = false)
                enableKept -> rewritten.copy(enabled = true)
                else -> rewritten
            }
        val update = target?.takeIf { it != keep }
        val insert =
            if (keep == null &&
                providesGuide &&
                addMissing &&
                hasLiveChannels == true &&
                currentUrl != null &&
                own.none { it.url.substringBefore('?') == currentUrl }
            ) {
                EpgSourceEntity(url = currentUrl, label = label(providerUrl), enabled = true, providerId = providerId)
            } else {
                null
            }
        return Plan(insert = insert, update = update, deleteIds = deleteIds)
    }

    /** The bookkeeping [EpgSourceDao.resetAllIngestionState] clears, for one row: none of it is synced. */
    private fun EpgSourceEntity.withIngestionStateReset(): EpgSourceEntity =
        copy(
            lastIngestedAtMs = 0,
            lastChannels = 0,
            lastProgrammes = 0,
            lastDownloadBytes = 0,
            lastError = null,
            lastIngestionDurationMs = 0,
            lastDownloadDurationMs = 0,
            lastContentSha256 = null,
            etag = null,
            lastModifiedHeader = null,
        )

    /**
     * Applies [plan]: deleted sources lose their guide rows too ([deleteIndexRows]), as when the
     * user deletes a source. Returns whether an enabled source was added or rewritten (it needs a
     * refresh); disabling one doesn't.
     */
    suspend fun apply(
        plan: Plan,
        sourceDao: EpgSourceDao,
        deleteIndexRows: suspend (List<Long>) -> Unit,
    ): Boolean {
        if (plan.deleteIds.isNotEmpty()) {
            deleteIndexRows(plan.deleteIds)
            sourceDao.deleteSources(plan.deleteIds)
        }
        plan.update?.let { sourceDao.updateSource(it) }
        plan.insert?.let { sourceDao.insertSource(it) }
        return plan.insert != null || plan.update?.enabled == true
    }

    /**
     * [plan] then [apply] for one provider, after a login. Returns whether its guide needs a
     * refresh. [providesGuide] off ([ProviderSettings.providesGuideOn]) keeps the automatic source
     * disabled and never adds one. The row holds no login, so a login with other credentials
     * leaves it as it is; the next download uses them.
     */
    suspend fun reconcile(
        sourceDao: EpgSourceDao,
        deleteIndexRows: suspend (List<Long>) -> Unit,
        providerId: Long,
        providerUrl: String,
        hasLiveChannels: Boolean?,
        previousProviderUrl: String? = null,
        providesGuide: Boolean = true,
    ): Boolean =
        reconcile(
            sourceDao = sourceDao,
            deleteIndexRows = deleteIndexRows,
            providerId = providerId,
            providerUrl = providerUrl,
            currentUrl = storedUrl(providerUrl),
            hasLiveChannels = hasLiveChannels,
            previousProviderUrl = previousProviderUrl,
            providesGuide = providesGuide,
        )

    /** [reconcile] with the automatic source's URL as given ([currentUrl] null: no credentials at hand). */
    internal suspend fun reconcile(
        sourceDao: EpgSourceDao,
        deleteIndexRows: suspend (List<Long>) -> Unit,
        providerId: Long,
        providerUrl: String,
        currentUrl: String?,
        hasLiveChannels: Boolean?,
        previousProviderUrl: String? = null,
        providesGuide: Boolean = true,
        enableKept: Boolean = false,
    ): Boolean {
        val plan =
            plan(
                providerId = providerId,
                providerUrl = providerUrl,
                sources = sourceDao.getAllSourcesOnce(),
                currentUrl = currentUrl,
                hasLiveChannels = hasLiveChannels,
                addMissing = true,
                previousProviderUrl = previousProviderUrl,
                providesGuide = providesGuide,
                enableKept = enableKept,
            )
        if (!plan.isEmpty) Log.i(TAG, "Provider $providerId: ${describe(plan)}")
        return apply(plan, sourceDao, deleteIndexRows)
    }

    /**
     * [reconcile] for one Xtream source from what this device holds (its login and catalogue),
     * after the viewer flips "Provides a guide". Off disables the automatic source; on re-enables
     * it, or adds it when there is none and the catalogue has live channels (otherwise the next
     * login does), and refreshes it.
     */
    suspend fun reconcileStored(
        context: Context,
        providerId: Long,
    ) {
        withContext(Dispatchers.IO) {
            val repository = ProviderRepository(context)
            val provider = repository.getProviderById(providerId)
            if (provider != null && provider.type == PROVIDER_TYPE_XTREAM) {
                val login = repository.getLogin(provider)
                val providesGuide = repository.getProviderSettings(providerId).providesGuideOn
                // Live channels known only from the catalogue: none there may just mean not synced yet.
                val hasLive =
                    XtreamDatabase
                        .getInstance(
                            context,
                        ).streamDao()
                        .hasStreams(providerId, XtreamStreamEntity.TYPE_LIVE)
                        .takeIf { it }
                val needsRefresh =
                    reconcile(
                        sourceDao = SettingsDatabase.getInstance(context).epgSourceDao(),
                        deleteIndexRows = indexRowsDeleter(context),
                        providerId = providerId,
                        providerUrl = provider.url,
                        currentUrl = if (login.username.isNotEmpty() && login.password.isNotEmpty()) storedUrl(provider.url) else null,
                        hasLiveChannels = hasLive,
                        providesGuide = providesGuide,
                        enableKept = providesGuide,
                    )
                if (needsRefresh) EpgFileManager.getInstance(context).refreshOutdatedSources(providerId)
            }
        }
    }

    /**
     * Whether an ingest of [source] that found no channels turns "Provides a guide" off: it is the
     * automatic source of the Xtream server at [providerUrl] (its `xmltv.php` is empty) and the
     * viewer hasn't set the switch.
     */
    fun detectsNoGuide(
        source: EpgSourceEntity,
        providerUrl: String,
        settings: ProviderSettings,
    ): Boolean = !settings.providesGuideSetByUser && isAutoXmltvSource(source, providerUrl)

    /**
     * Detection, after a guide refresh: each of [sourceIds] (ingested with no channels) that
     * [detectsNoGuide] turns its source's "Provides a guide" off, which disables the automatic
     * source. Never throws: a failure is logged and the next empty ingest tries again.
     */
    suspend fun onEmptyIngest(
        context: Context,
        sourceIds: Collection<Long>,
    ) {
        try {
            val repository = ProviderRepository(context)
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
            sourceIds.forEach { sourceId ->
                val source = sourceDao.getSourceById(sourceId)
                val provider = source?.let { repository.getProviderById(it.providerId) }
                if (source != null &&
                    provider != null &&
                    provider.type == PROVIDER_TYPE_XTREAM &&
                    detectsNoGuide(source, provider.url, repository.getProviderSettings(provider.id))
                ) {
                    Log.i(TAG, "Provider ${provider.id}: its own guide is empty, Provides a guide turns off")
                    repository.setProvidesGuide(provider.id, enabled = false, byUser = false)
                    reconcile(
                        sourceDao = sourceDao,
                        deleteIndexRows = indexRowsDeleter(context),
                        providerId = provider.id,
                        providerUrl = provider.url,
                        currentUrl = null,
                        hasLiveChannels = null,
                        providesGuide = false,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Empty guide detection failed", e)
        }
    }

    /** An Xtream source as the one-time cleanup sees it. */
    data class ProviderState(
        val id: Long,
        val url: String,
        /** The automatic source's [storedUrl]; null when the password can't be read. */
        val currentXmltvUrl: String?,
        /** True / false when known from this device's catalogue, null when not. */
        val hasLiveChannels: Boolean?,
    )

    /**
     * The one-time cleanup of what the old code left: for each Xtream source keep only the
     * automatic source matching its current login (rewriting it if none does), delete the others,
     * and delete it too when the source is known to have no live channels. Adds nothing — the next
     * login does that. Idempotent.
     */
    suspend fun cleanUp(
        providers: List<ProviderState>,
        sourceDao: EpgSourceDao,
        deleteIndexRows: suspend (List<Long>) -> Unit,
    ) {
        providers.forEach { provider ->
            val plan =
                plan(
                    providerId = provider.id,
                    providerUrl = provider.url,
                    sources = sourceDao.getAllSourcesOnce(),
                    currentUrl = provider.currentXmltvUrl,
                    hasLiveChannels = provider.hasLiveChannels,
                    addMissing = false,
                )
            if (!plan.isEmpty) Log.i(TAG, "Cleanup, provider ${provider.id}: ${describe(plan)}")
            apply(plan, sourceDao, deleteIndexRows)
        }
    }

    /**
     * [cleanUp] once per install, at app start (`EpgFileManager.initialize`). The flag is set only
     * after every provider is done, so an interrupted run runs again. Live channels count as known
     * when the catalogue holds some, or as known absent when the last full catalogue sync
     * succeeded and the catalogue holds films but no live channels.
     */
    suspend fun cleanUpOnce(context: Context) {
        withContext(Dispatchers.IO) {
            val settings = AppSettings(context)
            if (!settings.autoXmltvSourcesCleaned) {
                try {
                    val repository = ProviderRepository(context)
                    val streamDao = XtreamDatabase.getInstance(context).streamDao()
                    val providers =
                        repository.getAllProvidersList().filter { it.type == PROVIDER_TYPE_XTREAM }.map { provider ->
                            val login = repository.getLogin(provider)
                            // "None" needs a populated catalogue (films present) and a clean last
                            // sync: a cleared cache also has no live rows, and must not read as
                            // "no live channels" — the next login settles those cases.
                            val hasLive =
                                when {
                                    streamDao.hasStreams(provider.id, XtreamStreamEntity.TYPE_LIVE) -> true

                                    provider.lastSyncedAtMs > 0 &&
                                        provider.lastSyncError == null &&
                                        streamDao.hasStreams(provider.id, XtreamStreamEntity.TYPE_VOD) -> false

                                    else -> null
                                }
                            ProviderState(
                                id = provider.id,
                                url = provider.url,
                                currentXmltvUrl =
                                    if (login.username.isNotEmpty() && login.password.isNotEmpty()) storedUrl(provider.url) else null,
                                hasLiveChannels = hasLive,
                            )
                        }
                    cleanUp(providers, SettingsDatabase.getInstance(context).epgSourceDao(), indexRowsDeleter(context))
                    settings.autoXmltvSourcesCleaned = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Automatic guide source cleanup failed; retried next start", e)
                }
            }
        }
    }

    /** Drops deleted sources' rows from the guide index, as `ProviderRepository.deleteProviderEpgSources` does. */
    fun indexRowsDeleter(context: Context): suspend (List<Long>) -> Unit =
        { ids -> EpgIndexDatabase.getInstance(context).epgIndexDao().deleteBySourceIds(ids) }

    // Ids only: the URLs carry passwords.
    private fun describe(plan: Plan): String = "insert=${plan.insert != null} update=${plan.update?.id} delete=${plan.deleteIds}"
}
