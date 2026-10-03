package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import java.net.URI

/**
 * The guide source an Xtream source gets by itself: `<server>/xmltv.php?username=…&password=…`,
 * labelled `<host> (Bulk)`. There is at most one per Xtream source: a login with other credentials
 * rewrites its URL in place, and it exists only while the account has live channels.
 *
 * No column marks it: a source is automatic when its URL is the provider's server (same scheme,
 * host, port and path) plus `/xmltv.php?username=…&password=…` and its label ends with
 * [LABEL_SUFFIX] — see [isAutoXmltvSource]. Anything else is hand-added and never touched here.
 * A user who renames the automatic source turns it into a hand-added one.
 *
 * Every write goes through [EpgSourceDao], so live sync carries it to the group's other devices
 * (the URL update and the deletions included). See docs/plans/20261003_ux-overhaul-plan.md → GD0c.
 */
object AutoXmltvSources {
    private const val TAG = "AutoXmltvSources"
    const val LABEL_SUFFIX = " (Bulk)"
    private const val XMLTV_PATH = "/xmltv.php"
    private const val PROVIDER_TYPE_XTREAM = "XTREAM"

    /** The automatic source's URL. Built as it always was (no encoding), so existing rows still match. */
    fun xmltvUrl(
        providerUrl: String,
        username: String,
        password: String,
    ): String = "${providerUrl.trim().trimEnd('/')}$XMLTV_PATH?username=$username&password=$password"

    fun label(providerUrl: String): String = EpgFileManager.extractLabel(providerUrl) + LABEL_SUFFIX

    /** Whether [source] is the automatic guide source of the Xtream server at [providerUrl]. */
    fun isAutoXmltvSource(
        source: EpgSourceEntity,
        providerUrl: String,
    ): Boolean {
        // Only the part before `?` is parsed: the query carries a raw password, which may hold
        // anything (`#`, `%`, spaces) that a URI parser would choke on or misread.
        val base = source.url.substringBefore('?')
        val query = source.url.substringAfter('?', missingDelimiterValue = "")
        val server = serverOf(providerUrl.trim())
        val sourceServer = serverOf(base.removeSuffix(XMLTV_PATH))
        return source.label.endsWith(LABEL_SUFFIX) &&
            base.endsWith(XMLTV_PATH) &&
            query.startsWith("username=") &&
            query.contains("&password=") &&
            server != null &&
            server == sourceServer
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
     *   [currentUrl] (with its ingest state reset, so the next refresh downloads for real) and
     *   delete the others.
     * - With none left, [addMissing] and [hasLiveChannels] `true`, add one, unless a hand-added
     *   source already has that exact URL.
     *
     * [currentUrl] null (no credentials at hand) keeps the row's URL as it is. [previousProviderUrl]
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
    ): Plan {
        val own = sources.filter { it.providerId == providerId }
        val serverUrls = listOfNotNull(providerUrl, previousProviderUrl)
        val autos = own.filter { source -> serverUrls.any { isAutoXmltvSource(source, it) } }.sortedBy { it.addedAtMs }
        val keep = if (hasLiveChannels == false) null else autos.firstOrNull { it.url == currentUrl } ?: autos.firstOrNull()
        val deleteIds = autos.filter { it !== keep }.map { it.id }
        val update =
            if (keep != null && currentUrl != null && keep.url != currentUrl) {
                // A row carried over from the previous server takes the new server's label.
                val newLabel = if (isAutoXmltvSource(keep, providerUrl)) keep.label else label(providerUrl)
                keep.copy(url = currentUrl, label = newLabel).withIngestionStateReset()
            } else {
                null
            }
        val insert =
            if (keep == null && addMissing && hasLiveChannels == true && currentUrl != null && own.none { it.url == currentUrl }) {
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
     * user deletes a source. Returns whether a source was added or rewritten (it needs a refresh).
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
        return plan.insert != null || plan.update != null
    }

    /** [plan] then [apply] for one provider, after a login. Returns whether its guide needs a refresh. */
    suspend fun reconcile(
        sourceDao: EpgSourceDao,
        deleteIndexRows: suspend (List<Long>) -> Unit,
        providerId: Long,
        providerUrl: String,
        username: String,
        password: String,
        hasLiveChannels: Boolean?,
        previousProviderUrl: String? = null,
    ): Boolean {
        val plan =
            plan(
                providerId = providerId,
                providerUrl = providerUrl,
                sources = sourceDao.getAllSourcesOnce(),
                currentUrl = xmltvUrl(providerUrl, username, password),
                hasLiveChannels = hasLiveChannels,
                addMissing = true,
                previousProviderUrl = previousProviderUrl,
            )
        if (!plan.isEmpty) Log.i(TAG, "Provider $providerId: ${describe(plan)}")
        return apply(plan, sourceDao, deleteIndexRows)
    }

    /** An Xtream source as the one-time cleanup sees it. */
    data class ProviderState(
        val id: Long,
        val url: String,
        /** The automatic source's URL for the current login; null when the password can't be read. */
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
                                    if (login.username.isNotEmpty() && login.password.isNotEmpty()) {
                                        xmltvUrl(provider.url, login.username, login.password)
                                    } else {
                                        null
                                    },
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
