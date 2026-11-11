package org.njarasoa.fijerena.core.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.combine
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.SourceShortNames
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.newepisodes.LocalNewEpisodes
import org.njarasoa.fijerena.core.ui.newepisodes.NewEpisodesStore

/**
 * The profile in use is a Nerd: it sees the stats and extra information, source badges first. Read
 * by every composable that shows them; view models read [AppSettings.isNerdMode]. See
 * the Nerd role plan.
 */
val LocalNerd = compositionLocalOf { false }

/**
 * The sources' badge short names ("X4P", [SourceShortNames]): by source id, and by name for the
 * callers that only have the name (the first source wins when two share one).
 */
data class SourceShortNameMap(
    val byId: Map<Long, String> = emptyMap(),
    val byName: Map<String, String> = emptyMap(),
)

val LocalSourceShortNames = compositionLocalOf { SourceShortNameMap() }

/**
 * Provides [LocalNerd], [LocalSourceShortNames] and [LocalNewEpisodes] to [content], following profile
 * switches, the profile's Nerd flag ([AppSettings.observeNerdMode]) and every source's name and
 * short name.
 */
@Composable
fun ProvideSourceBadges(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val nerd by remember(context) { AppSettings(context).observeNerdMode() }.collectAsState(initial = false)
    val shortNames by remember(context) {
        val repository = AppContainer.getInstance(context).providerRepository
        combine(repository.getAllProviders(), repository.sourceShortNames()) { sources, byId ->
            SourceShortNameMap(
                byId = byId,
                byName =
                    sources
                        .sortedBy { it.id }
                        .reversed()
                        .mapNotNull { s -> byId[s.id]?.let { s.name to it } }
                        .toMap(),
            )
        }
    }.collectAsState(initial = SourceShortNameMap())
    val newEpisodes by remember(context) { NewEpisodesStore.getInstance(context).state }.collectAsState()
    CompositionLocalProvider(
        LocalNerd provides nerd,
        LocalSourceShortNames provides shortNames,
        LocalNewEpisodes provides newEpisodes,
        content = content,
    )
}

/** [providerId]'s name, once read (null before, and for a source this device doesn't have). */
@Composable
fun rememberSourceName(providerId: Long): String? {
    val context = LocalContext.current.applicationContext
    val name by produceState<String?>(null, providerId) {
        value =
            AppContainer
                .getInstance(context)
                .providerRepository
                .getProviderById(providerId)
                ?.name
    }
    return name
}

/**
 * The label of a filter's row for one source: the source's name with its count in the dialog's one
 * count style, "bears (64)", as "All sources (835)" and the decade rows. The name is the row's own
 * label, so it stays in the words in every mode; the source badge follows it only while badges show.
 */
fun sourceFilterLabel(
    name: String,
    count: Int,
): String = "$name ($count)"
