package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.Context
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase

/** The programmes the guide index holds for an Xtream source from its own `xmltv.php`: what [PanelGuideTimes] compares the panel's guide with. */
fun interface OwnXmltvListings {
    /** [channelIds]' programmes starting in [fromSec]..[toSec], or null when that source's `xmltv.php` is not an enabled guide source. */
    suspend fun listings(
        channelIds: Collection<String>,
        fromSec: Long,
        toSec: Long,
    ): Set<PanelGuideTimes.Listing>?
}

/** [OwnXmltvListings] from the automatic "<host> (Bulk)" guide source of provider [providerId] — never a guide source the user added. */
class OwnXmltvIndex(
    private val context: Context,
    private val providerId: Long,
) : OwnXmltvListings {
    override suspend fun listings(
        channelIds: Collection<String>,
        fromSec: Long,
        toSec: Long,
    ): Set<PanelGuideTimes.Listing>? {
        val settings = SettingsDatabase.getInstance(context)
        val provider = settings.providerDao().getProviderById(providerId) ?: return null
        val sourceIds =
            settings
                .epgSourceDao()
                .getEnabledSourcesForProvider(providerId)
                .filter { AutoXmltvSources.isAutoXmltvSource(it, provider.url) }
                .map { it.id }
        if (sourceIds.isEmpty()) return null
        val dao = EpgIndexDatabase.getInstance(context).epgIndexDao()
        return channelIds
            .chunked(CHUNK)
            .flatMap { chunk -> dao.getProgrammesInWindow(chunk, sourceIds, fromSec, toSec) }
            .map { PanelGuideTimes.listing(it.channelId, it.title, it.startEpoch) }
            .toSet()
    }

    private companion object {
        const val CHUNK = 500
    }
}
