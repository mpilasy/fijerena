package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.GuideScope
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import java.util.Locale

/**
 * The countries whose UEFA broadcasters are read for the profile in use (the sports teams plan →
 * SP-D13): those of the live channels and live categories its sources show ([ChannelCountries]),
 * then Madagascar. With no channel country: Madagascar and the device's country.
 *
 * Read once per profile and kept in memory. A source synced later in the session counts after the
 * next profile switch or app start. M3U sources keep no channel rows, so they add no country.
 */
internal class ProfileCountries(
    private val context: Context,
) {
    private val mutex = Mutex()
    private var cached: Pair<String, List<String>>? = null

    suspend fun get(): List<String> =
        mutex.withLock {
            val profileId = AppSettings(context).activeProfileId
            cached?.takeIf { it.first == profileId }?.second ?: read().also { cached = profileId to it }
        }

    private suspend fun read(): List<String> =
        withContext(Dispatchers.IO) {
            val database = XtreamDatabase.getInstance(context)
            val providers = GuideScope.searchedProviders(ProviderRepository(context).profileSourcesList())
            val channels = providers.flatMap { database.streamDao().getVisibleLiveNames(it.id) }
            val categories = providers.flatMap { database.categoryDao().getVisibleLiveCategoryNames(it.id) }
            val found = ChannelCountries.channelCountries(channels, categories)
            val device = if (found.isEmpty()) listOfNotNull(Locale.getDefault().country.takeIf { it.length == 2 }) else emptyList()
            (found + MADAGASCAR + device).distinct()
        }

    companion object {
        const val MADAGASCAR = "MG"
    }
}
